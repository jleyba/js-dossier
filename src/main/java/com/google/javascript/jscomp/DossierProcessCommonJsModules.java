package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

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

    /**
     * List of require calls found within a module. This list will be translated to a list
     * of goog.require calls when the module's SCRIPT node is visited.
     */
    private final List<Node> requiredModules = new LinkedList<>();

    private final Map<String, String> renamedVars = new HashMap<>();
    private final Map<String, String> aliasedVars = new HashMap<>();

    private DossierModule currentModule;

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

      // Node generate our module wrapper.
      Node wrappedContents = script.removeChildren();

      generateModuleDeclaration(script, currentModule.getVarName(), requiredModules);
      requiredModules.clear();

      if (wrappedContents != null) {
        script.addChildrenToBack(wrappedContents);
      }

      NodeTraversal.traverse(
          t.getCompiler(), script, new SuffixVarsCallback(renamedVars, currentModule));
      NodeTraversal.traverse(t.getCompiler(), script, new TypeCleanup(renamedVars, aliasedVars));
      renamedVars.clear();
      aliasedVars.clear();

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
  }

  /**
   * Traverses a node tree and appends a suffix to all global variable names.
   */
  private class SuffixVarsCallback extends NodeTraversal.AbstractPostOrderCallback {

    private final Map<String, String> renamedVars;
    private final DossierModule currentModule;

    private SuffixVarsCallback(Map<String, String> renamedVars, DossierModule currentModule) {
      this.renamedVars = renamedVars;
      this.currentModule = currentModule;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        Scope.Var var = t.getScope().getVar(name);

        if (var != null && var.isGlobal() && currentModule.registerInternalVar(var)) {
          Node nameNode = var.getNameNode();
          n.putProp(Node.ORIGINALNAME_PROP, nameNode.getProp(Node.ORIGINALNAME_PROP));
          n.setString(nameNode.getString());
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

    Node nameNode = IR.name(name);
    nameNode.setIsSyntheticBlock(true);
    nameNode.addChildToFront(IR.objectlit());
    nameNode.copyInformationFromForTree(script);

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