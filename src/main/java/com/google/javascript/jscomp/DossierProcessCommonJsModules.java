package com.google.javascript.jscomp;

import com.github.jleyba.dossier.Descriptor;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Processes all files flagged as CommonJS modules by wrapping them in an anonymous class and
 * generating {@code goog.provide} statements for each module.  This pass will also replace all
 * calls to {@code require} with a direct reference to the required module's {@code exports}
 * object. Finally, all references to global {@code this} will be replaced with reference's to the
 * current module's {@code exports} object.
 *
 * <p>For instance, suppose we had two modules, foo.js and bar.js:
 * <pre><code>
 *   // foo.js
 *   exports.sayHi = function() {
 *     console.log('hello, world!');
 *   };
 *   this.sayBye = function() {
 *     console.log('goodbye, world!');
 *   };
 *
 *   // bar.js
 *   var foo = require('./foo');
 *   foo.sayHi();
 * </code></pre>
 *
 * <p>Given the code above, this pass would produce:
 * <pre><code>
 *   var module__$foo = {};
 *   module__$foo.exports = {};
 *   (function(module, exports, require, __dirname, __filename) {
 *     exports.sayHi = function() {
 *       console.log('hello, world!');
 *     };
 *     exports.sayBye = function() {
 *       console.log('goodbye, world!');
 *     };
 *   })(module__$foo, module__$foo.exports, function(){}, '', '');
 *
 *   var module__$bar = {};
 *   module__$bar.exports = {};
 *   (function(module, exports, require, __dirname, __filename) {
 *     var foo = module__$foo.exports;
 *     foo.sayHi();
 *   })(module__$bar, module__$bar.exports, function(){}, '', '');
 * </code></pre>
 *
 * <p>Note this pass generates stub values for Node.js' {@code require}, {@code __dirname} and
 * {@code __filename} to avoid type-checking errors from the compiler.
 */
class DossierProcessCommonJsModules implements CompilerPass {

  private final AbstractCompiler compiler;
  private final ImmutableSet<String> commonJsModules;

  DossierProcessCommonJsModules(AbstractCompiler compiler, Iterable<Path> commonJsModules) {
    this.compiler = compiler;
    this.commonJsModules = ImmutableSet.copyOf(
        Iterables.transform(commonJsModules, Functions.toStringFunction()));
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new WrapCommonJsModuleCallback());
  }

  /**
   * Wraps the contents of a CommonJS script in an anonymous function, so only the top exported
   * variables are exposed to the global scope. For example, given the following for module
   * {@code foo}:
   * <pre><code>
   *   exports.helloWorld = function() {
   *     console.log('Hello, world!');
   *   };
   * </code></pre>
   *
   * <p>This callback would produce:
   * <pre><code>
   *   var module%foo = {exports: {}};
   *   (function(exports, module) {
   *     exports.helloWorld = function() {
   *       console.log('Hello, world!');
   *     };
   *   })(module%foo.exports, module%foo);
   * </code></pre>
   *
   * <p>Note that "%" is used as a separated in the generated variables names since that is an
   * invalid character and can never occur in valid JS, guaranteeing our generated code does not
   * conflict with existing code.
   */
  private class WrapCommonJsModuleCallback extends NodeTraversal.AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        visitScript(t, n);
      }

      if (n.isCall() && n.getChildCount() == 2
          && "require".equals(n.getFirstChild().getQualifiedName())
          && n.getChildAtIndex(1).isString()) {
        visitRequireCall(t, n, parent);
      }
    }

    private void visitScript(NodeTraversal t, Node script) {
      if (!commonJsModules.contains(script.getSourceFileName())) {
        return;
      }

      Path scriptPath = FileSystems.getDefault().getPath(script.getSourceFileName());
      String moduleName = Descriptor.getModuleName(scriptPath);

      t.getInput().addProvide(moduleName);

      // First things first: replace all global THIS references under the script with
      // references to the export object. This is necessary so we can properly wrap
      NodeTraversal.traverse(t.getCompiler(), script, new ReplaceGlobalThisCallback());

      // Node generate our module wrapper.
      Node wrappedContents = script.removeChildren();

      script.addChildrenToFront(
          generateModuleDeclaration(moduleName).copyInformationFromForTree(script));

      generateModuleWrapper(moduleName, script, wrappedContents);

      t.getCompiler().reportCodeChange();
    }

    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      FileSystem fileSystem = FileSystems.getDefault();

      Path currentFile = fileSystem.getPath(t.getSourceName().replaceAll("\\.js$", ""));

      String modulePath = require.getChildAtIndex(1).getString().replaceAll("\\.js$", "");
      Path moduleFile = currentFile.getParent().resolve(modulePath).normalize();
      String moduleName = Descriptor.getModuleName(moduleFile);

      t.getInput().addRequire(moduleName);

      Node moduleRef = IR.getprop(IR.name(moduleName), IR.string("exports")).srcrefTree(require);
      parent.replaceChild(require, moduleRef);

      Node script = getCurrentScriptNode(parent);
      script.addChildToFront(IR.exprResult(
          IR.call(IR.getprop(IR.name("goog"), IR.string("require")),
              IR.string(moduleName))).copyInformationFromForTree(require));
      compiler.reportCodeChange();
    }

    private Node getCurrentScriptNode(Node n) {
      while (true) {
        if (n.isScript()) {
          return n;
        }
        n = n.getParent();
      }
    }
  }

  static void generateModuleWrapper(String moduleName, Node script, Node contents) {
    Node block = IR.block();

    Node moduleNameArg = IR.name(moduleName);
    Node moduleExportsArg = IR.getprop(
        IR.name(moduleName),
        IR.string("exports"));

    Node requireArg = IR.function(IR.name(""), IR.paramList(), IR.block());
    Node filenameArg = IR.string("");
    Node dirnameArg = IR.string("");

    Node callNode = IR.call(
        IR.function(
            IR.name(""),
            IR.paramList(
                IR.name("module"),
                IR.name("exports"),
                IR.name("require"),
                IR.name("__filename"),
                IR.name("__dirname")),
            block),
        moduleNameArg,
        moduleExportsArg,
        requireArg,
        filenameArg,
        dirnameArg);
    callNode.putBooleanProp(Node.FREE_CALL, true);

    Node newContents = IR.exprResult(callNode);
    newContents.copyInformationFromForTree(script);

    if (contents != null) {
      block.addChildrenToBack(contents);
    }
    script.addChildrenToBack(newContents);
  }

  static Node generateModuleDeclaration(String name) {
    Node exportsProp = IR.stringKey("exports");
    exportsProp.addChildToFront(IR.objectlit());

    Node provide = IR.exprResult(IR.call(
        IR.getprop(IR.name("goog"), IR.string("provide")),
        IR.string(name)));
    Node exportsDecl = IR.exprResult(
        IR.assign(
            IR.getprop(IR.name(name), IR.string("exports")),
            IR.objectlit()));

    return IR.script(provide, exportsDecl).removeChildren();

//    Node module = IR.var(
//        IR.name(name),
//        IR.objectlit(exportsProp));
//
//    return module;
  }

  /**
   * Callback used to replace all references to global {@code this} with a reference to the
   * {@code exports} object for the current CommonJS module.
   *
   * <p>This class is a variation of  {@link com.google.javascript.jscomp.CheckGlobalThis}, which
   * does the same traversal, but reports usages of global {@code this} as errors.
   */
  private static class ReplaceGlobalThisCallback implements NodeTraversal.Callback {

    private Node assignLhsChild = null;

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {

      if (n.isFunction()) {
        // Don't traverse functions that are constructors or have the @this
        // or @override annotation.
        JSDocInfo jsDoc = getFunctionJsDocInfo(n);
        if (jsDoc != null &&
            (jsDoc.isConstructor() ||
                jsDoc.isInterface() ||
                jsDoc.hasThisType() ||
                jsDoc.isOverride())) {
          return false;
        }

        // Don't traverse functions unless they would normally
        // be able to have a @this annotation associated with them. e.g.,
        // var a = function() { }; // or
        // function a() {} // or
        // a.x = function() {}; // or
        // var a = {x: function() {}};
        int pType = parent.getType();
        if (!(pType == Token.BLOCK ||
            pType == Token.SCRIPT ||
            pType == Token.NAME ||
            pType == Token.ASSIGN ||

            // object literal keys
            pType == Token.STRING_KEY)) {
          return false;
        }

        // Don't traverse functions that are getting lent to a prototype.
        Node gramps = parent.getParent();
        if (parent.getType() == Token.STRING_KEY
            || parent.getType() == Token.GETTER_DEF
            || parent.getType() == Token.SETTER_DEF) {
          JSDocInfo maybeLends = gramps.getJSDocInfo();
          if (maybeLends != null &&
              maybeLends.getLendsName() != null &&
              maybeLends.getLendsName().endsWith(".prototype")) {
            return false;
          }
        }
      }

      if (parent != null && parent.isAssign()) {
        Node lhs = parent.getFirstChild();

        if (n == lhs) {
          // Always traverse the left side of the assignment. To handle
          // nested assignments properly (e.g., (a = this).property = c;),
          // assignLhsChild should not be overridden.
          if (assignLhsChild == null) {
            assignLhsChild = lhs;
          }
        } else {
          // Only traverse the right side if it's not an assignment to a prototype
          // property or subproperty.
          if (n.isGetProp() || n.isGetElem()) {
            if (lhs.isGetProp() &&
                lhs.getLastChild().getString().equals("prototype")) {
              return false;
            }
            Node llhs = lhs.getFirstChild();
            if (llhs.isGetProp() &&
                llhs.getLastChild().getString().equals("prototype")) {
              return false;
            }
          }
        }
      }

      return true;    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis() && shouldReplace(n)) {
        Node exports = IR.name("exports");

        Node children = n.removeChildren();
        if (children != null) {
          exports.addChildrenToFront(children);
        }

        parent.replaceChild(n, exports);
      }

      if (n == assignLhsChild) {
        assignLhsChild = null;
      }
    }

    private boolean shouldReplace(Node n) {
      Node parent = n.getParent();
      if (assignLhsChild != null) {
        return true;  // Always replace THIS on the left side of an assign.
      }
      // Also replace THIS with a property access.
      return parent != null && (parent.isGetElem() || parent.isGetProp());
    }

    /**
     * Gets a function's JSDoc information, if it has any. Checks for a few
     * patterns (ellipses show where JSDoc would be):
     * <pre>
     * ... function() {}
     * ... x = function() {};
     * var ... x = function() {};
     * ... var x = function() {};
     * </pre>
     *
     * <p>This method copied from
     * {@link com.google.javascript.jscomp.CheckGlobalThis#getFunctionJsDocInfo(
     *     com.google.javascript.rhino.Node)}.
     */
    private JSDocInfo getFunctionJsDocInfo(Node n) {
      JSDocInfo jsDoc = n.getJSDocInfo();
      Node parent = n.getParent();
      if (jsDoc == null) {
        int parentType = parent.getType();
        if (parentType == Token.NAME || parentType == Token.ASSIGN) {
          jsDoc = parent.getJSDocInfo();
          if (jsDoc == null && parentType == Token.NAME) {
            Node gramps = parent.getParent();
            if (gramps.isVar()) {
              jsDoc = gramps.getJSDocInfo();
            }
          }
        }
      }
      return jsDoc;
    }
  }
}