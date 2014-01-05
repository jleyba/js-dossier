package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Processes all files flagged as CommonJS modules by renaming all variables so they may be
 * safely inserted into the global scope to be processed along with all other files. This pass
 * will also generate {@code goog.provide} statements for each module and replace all calls to
 * {@code require} with a direct reference to the required module's {@code exports}
 * object.
 *
 * <p>For instance, suppose we had two modules, foo.js and bar.js:
 * <pre><code>
 *   // foo.js
 *   exports.sayHi = function() {
 *     console.log('hello, world!');
 *   };
 *   exports.sayBye = function() {
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
 *   var dossier$$module__foo = {exports: {}};
 *   dossier$$module__foo.exports.sayHi = function() {
 *     console.log('hello, world!');
 *   };
 *   dossier$$module__foo.exports.sayBye = function() {
 *     console.log('hello, world!');
 *   };
 *
 *   var dossier$$module__bar = {exports: {}};
 *   var foo$$__dossier$$module__bar = dossier$$module__foo.exports;
 *   foo$$__dossier$$module__bar.sayHi();
 * </code></pre>
 *
 * <p>Note this pass generates stub values for Node.js' {@code require}, {@code __dirname} and
 * {@code __filename} to avoid type-checking errors from the compiler.
 */
class DossierProcessCommonJsModules implements CompilerPass {

  private final AbstractCompiler compiler;
  private final DossierModuleRegistry moduleRegistry;

  private DossierModule currentModule;

  DossierProcessCommonJsModules(DossierCompiler compiler) {
    this.compiler = compiler;
    this.moduleRegistry = compiler.getModuleRegistry();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new CommonJsModuleCallback());
  }

  /**
   * Main traversal callback for processing the AST of a CommonJS module.
   */
  private class CommonJsModuleCallback implements NodeTraversal.Callback {

    private final Map<String, String> renamedVars = new HashMap<>();

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        checkState(currentModule == null);
        if (moduleRegistry.hasModuleWithPath(n.getSourceFileName())) {
          currentModule = moduleRegistry.registerScriptForModule(n);
        }
      }
      return true;
    }

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
        visitModule(n);
      }

      if (n.isName() && "exports".equals(n.getQualifiedName())
          && (parent.isExprResult() || parent.isGetProp())) {
        visitExports(n);
      }

      if (n.isName() && ("__filename".equals(n.getQualifiedName())
          || "__dirname".equals(n.getQualifiedName()))) {
        visitFilenameOrDirnameFreeVariables(n);
      }
    }

    private void visitScript(NodeTraversal t, Node script) {
      if (currentModule == null) {
        return;
      }

      t.getInput().addProvide(currentModule.getVarName());

      Node moduleDecl = generateModuleDeclaration(script, currentModule.getVarName());

      NodeTraversal.traverse(t.getCompiler(), script, new SuffixVarsCallback(
          renamedVars, moduleDecl));
      NodeTraversal.traverse(t.getCompiler(), script, new TypeCleanup(renamedVars));

      renamedVars.clear();
      currentModule = null;

      t.getCompiler().reportCodeChange();
    }

    private void visitModule(Node node) {
      checkArgument(node.isName());

      String moduleName = currentModule.getVarName();
      changeName(node, moduleName);
    }

    private void visitExports(Node node) {
      checkArgument(node.isName());

      String moduleName = currentModule.getVarName();
      Node moduleExports = IR.getprop(IR.name(moduleName), IR.string("exports"));
      moduleExports.copyInformationFromForTree(node);

      node.getParent().replaceChild(node, moduleExports);
    }

    private void visitFilenameOrDirnameFreeVariables(Node node) {
      checkArgument(node.isName());

      String moduleName = currentModule.getVarName();
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

      Path currentFile = fileSystem.getPath(t.getSourceName());

      String modulePath = require.getChildAtIndex(1).getString();
      Path moduleFile = currentFile.getParent().resolve(modulePath).normalize();
      String moduleName = DossierModule.guessModuleName(moduleFile);

      t.getInput().addRequire(moduleName);

      Node moduleRef = IR.getprop(IR.name(moduleName), IR.string("exports")).srcrefTree(require);
      parent.replaceChild(require, moduleRef);

      while (parent.isGetProp()) {
        parent = parent.getParent();
      }
      if (parent.isName()) {
        currentModule.defineAlias(
            parent.getString(),
            parent.getFirstChild().getQualifiedName());
      } else if (parent.isAssign()) {
        currentModule.defineAlias(
            parent.getFirstChild().getQualifiedName(),
            parent.getFirstChild().getNext().getQualifiedName());
      }

      compiler.reportCodeChange();
    }
  }

  /**
   * Traverses a node tree and appends a suffix to all global variable names.
   */
  private class SuffixVarsCallback extends NodeTraversal.AbstractPostOrderCallback {

    private final Map<String, String> renamedVars;
    private final Node moduleDecl;

    private SuffixVarsCallback(Map<String, String> renamedVars, Node moduleDecl) {
      this.renamedVars = renamedVars;
      this.moduleDecl = moduleDecl;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        Scope.Var var = t.getScope().getVar(name);

        if (var != null && var.isGlobal()
            && !moduleDecl.equals(var.getNameNode().getParent())
            && currentModule.registerInternalVar(var)) {
          Node nameNode = var.getNameNode();
          n.putProp(Node.ORIGINALNAME_PROP, nameNode.getProp(Node.ORIGINALNAME_PROP));
          n.setString(nameNode.getString());
          renamedVars.put(name, n.getString());
        }
      }
    }
  }

  private class TypeCleanup extends NodeTraversal.AbstractPostOrderCallback {

    private final Map<String, String> renamedVars;

    private TypeCleanup(Map<String, String> renamedVars) {
      this.renamedVars = renamedVars;
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
        if (currentModule.hasAlias(baseName)) {
          typeNode.setString(
              currentModule.getAlias(baseName) + typeNode.getString().substring(endIndex));
          return true;
        }
      }
      return false;
    }
  }

  private static Node generateModuleDeclaration(Node script, String name) {
    checkArgument(script.isScript());

    Node decl = IR.var(
        IR.name(name),
        IR.objectlit(
            // Define __filename and __dirname free variables off our module object to satisfy
            // the type checker.
            IR.propdef(IR.stringKey("__filename"), IR.string("")),
            IR.propdef(IR.stringKey("__dirname"), IR.string("")),
            // module.filename from Node
            IR.propdef(IR.stringKey("filename"), IR.string("")),
            // The exported API object literal.
            IR.propdef(IR.stringKey("exports"), IR.objectlit())
        ));
    decl.copyInformationFromForTree(script);
    script.addChildrenToFront(decl);

    return decl;
  }
}