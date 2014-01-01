package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.github.jleyba.dossier.Descriptor;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    NodeTraversal.traverse(compiler, root, new CommonJsModuleCallback());
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
  private class CommonJsModuleCallback extends NodeTraversal.AbstractPostOrderCallback {

    /**
     * List of require calls found within a module. This list will be translated to a list
     * of goog.require calls when the module's SCRIPT node is visited.
     */
    private final List<Node> requiredModules = new LinkedList<>();

    private final Map<String, String> renamedVars = new HashMap<>();
    private final Map<String, String> aliasedVars = new HashMap<>();

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

      if (n.isName() && "module".equals(n.getQualifiedName())
          && (parent.isExprResult() || parent.isGetProp())) {
        visitModule(t, n);
      }

      if (n.isName() && "exports".equals(n.getQualifiedName())
          && (parent.isExprResult() || parent.isGetProp())) {
        visitExports(t, n);
      }

      if (n.isName() && ("__filename".equals(n.getQualifiedName())
          || "__dirname".equals(n.getQualifiedName()))) {
        visitFilenameOrDirnameFreeVariables(t, n);
      }
    }

    private void visitScript(NodeTraversal t, Node script) {
      if (!commonJsModules.contains(script.getSourceFileName())) {
        return;
      }

      String moduleName = getModuleName(t);

      t.getInput().addProvide(moduleName);

      // Node generate our module wrapper.
      Node wrappedContents = script.removeChildren();

      generateModuleDeclaration(script, moduleName, requiredModules);
      requiredModules.clear();

      if (wrappedContents != null) {
        script.addChildrenToBack(wrappedContents);
      }

      NodeTraversal.traverse(
          t.getCompiler(), script, new SuffixVarsCallback(renamedVars, moduleName));
      NodeTraversal.traverse(t.getCompiler(), script, new TypeCleanup(renamedVars, aliasedVars));
      renamedVars.clear();
      aliasedVars.clear();

      t.getCompiler().reportCodeChange();
    }

    private void visitModule(NodeTraversal t, Node node) {
      checkArgument(node.isName());

      String moduleName = getModuleName(t);
      changeName(node, moduleName);
    }

    private void visitExports(NodeTraversal t, Node node) {
      checkArgument(node.isName());

      String moduleName = getModuleName(t);
      Node moduleExports = IR.getprop(IR.name(moduleName), IR.string("exports"));
      moduleExports.copyInformationFromForTree(node);

      node.getParent().replaceChild(node, moduleExports);
    }

    private void visitFilenameOrDirnameFreeVariables(NodeTraversal t, Node node) {
      checkArgument(node.isName());

      String moduleName = getModuleName(t);
      Node propAccessor = IR.getprop(IR.name(moduleName), IR.string(node.getString()));
      propAccessor.copyInformationFromForTree(node);

      node.getParent().replaceChild(node, propAccessor);
    }

    private void changeName(Node node, String newName) {
      checkArgument(node.isName());
      node.putProp(Node.ORIGINALNAME_PROP, node.getString());
      node.setString(newName);
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

      while (parent.isGetProp()) {
        parent = parent.getParent();
      }
      if (parent.isName()) {
        aliasedVars.put(parent.getString(), parent.getFirstChild().getQualifiedName());
      } else if (parent.isAssign()) {
        aliasedVars.put(parent.getFirstChild().getQualifiedName(),
            parent.getFirstChild().getNext().getQualifiedName());
      }

      requiredModules.add(IR.exprResult(
          IR.call(IR.getprop(IR.name("goog"), IR.string("require")),
              IR.string(moduleName))).copyInformationFromForTree(require));

      compiler.reportCodeChange();
    }

    private String getModuleName(NodeTraversal t) {
      FileSystem fileSystem = FileSystems.getDefault();
      Path modulePath = fileSystem.getPath(t.getSourceName().replaceAll("\\.js$", ""));
      return Descriptor.getModuleName(modulePath);
    }
  }

  /**
   * Traverses a node tree and appends a suffix to all global variable names.
   */
  private static class SuffixVarsCallback extends NodeTraversal.AbstractPostOrderCallback {

    private final Map<String, String> renamedVars;
    private final String suffix;

    private SuffixVarsCallback(Map<String, String> renamedVars, String suffix) {
      this.renamedVars = renamedVars;
      this.suffix = suffix;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        Scope.Var var = t.getScope().getVar(name);
        if (var != null && var.isGlobal()) {
          n.putProp(Node.ORIGINALNAME_PROP, name);
          n.setString(name + "$$_" + suffix);
          renamedVars.put(name, n.getString());
        }
      }
    }
  }

  private static class TypeCleanup extends NodeTraversal.AbstractPostOrderCallback {

    private final Map<String, String> renamedVars;
    private final Map<String, String> aliasedVars;

    private TypeCleanup(Map<String, String> renamedVars, Map<String, String> aliasedVars) {
      this.renamedVars = renamedVars;
      this.aliasedVars = aliasedVars;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        for (Node node : info.getTypeNodes()) {
          fixTypeNode(node);
        }
      }
    }

    private void fixTypeNode(Node typeNode) {
      if (typeNode.isString() && !fixAliasedType(typeNode)) {
        String name = typeNode.getString();
        int endIndex = name.indexOf('.');
        if (endIndex == -1) {
          endIndex = name.length();
        }

        String baseName = name.substring(0, endIndex);
        if (renamedVars.containsKey(baseName)) {
          typeNode.setString(
              renamedVars.get(baseName) + typeNode.getString().substring(endIndex));
        }
      }

      for (Node child = typeNode.getFirstChild(); child != null; child = child.getNext()) {
        fixTypeNode(child);
      }
    }

    private boolean fixAliasedType(Node typeNode) {
      String name = typeNode.getString();
      int endIndex = -1;
      while (endIndex != name.length()) {
        endIndex = name.indexOf('.', endIndex + 1);
        if (endIndex == -1) {
          endIndex = name.length();
        }

        String baseName = name.substring(0, endIndex);
        if (aliasedVars.containsKey(baseName)) {
          typeNode.setString(aliasedVars.get(baseName) + typeNode.getString().substring(endIndex));
          return true;
        }
      }
      return false;
    }
  }

  private static void generateModuleDeclaration(
      Node script, String name, List<Node> requiredModules) {
    checkArgument(script.isScript());

    Node provide = IR.exprResult(IR.call(
        IR.getprop(IR.name("goog"), IR.string("provide")),
        IR.string(name)));
    provide.copyInformationFromForTree(script);
    script.addChildrenToFront(provide);

    List<Node> children = Lists.newArrayList(
        // Define __filename and __dirname free variables off our module object to satisfy
        // the type checker.
        propAssign(name, "__filename", IR.string("")),
        propAssign(name, "__dirname", IR.string("")),
        // Module.filename from Node
        propAssign(name, "filename", IR.string("")),
        // The exported API object literal.
        propAssign(name, "exports", IR.objectlit()));
    for (Node child : children) {
      child.copyInformationFromForTree(script);
      script.addChildAfter(child, provide);
    }

    for (Node require : requiredModules) {
      // Source information for each require node is set when the require() call
      // is encountered. Even though we are placing the require call at the top of
      // the script tree, we do not adjust the source location information.
      script.addChildAfter(require, provide);
    }
  }

  private static Node propAssign(String parentName, String propName, Node propValue) {
    return IR.exprResult(
        IR.assign(
            IR.getprop(IR.name(parentName), IR.string(propName)),
            propValue));
  }
}