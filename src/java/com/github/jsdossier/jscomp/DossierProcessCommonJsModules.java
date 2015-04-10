/*
 Copyright 2013-2015 Jason Leyba

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.NodeTraversal.traverse;
import static com.google.javascript.jscomp.NodeTraversal.traverseTyped;
import static com.google.javascript.rhino.IR.call;
import static com.google.javascript.rhino.IR.exprResult;
import static com.google.javascript.rhino.IR.getprop;
import static com.google.javascript.rhino.IR.name;
import static com.google.javascript.rhino.IR.string;

import com.google.common.base.Splitter;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

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
 */
class DossierProcessCommonJsModules implements CompilerPass {

  // NB: The following errors are forbid situations that complicate type checking.

  /**
   * Reported when there are multiple assignments to module.exports.
   */
  private static DiagnosticType MULTIPLE_ASSIGNMENTS_TO_MODULE_EXPORTS =
      DiagnosticType.error(
          "DOSSIER_INVALID_MODULE_EXPORTS_ASSIGNMENT",
          "Multiple assignments to module.exports are not permitted");

  private final AbstractCompiler compiler;
  private final DossierModuleRegistry moduleRegistry;

  private DossierModule currentModule;

  DossierProcessCommonJsModules(DossierCompiler compiler) {
    this.compiler = compiler;
    this.moduleRegistry = compiler.getModuleRegistry();
  }

  @Override
  public void process(Node externs, Node root) {
    traverseTyped(compiler, root, new CommonJsModuleCallback());
  }

  private void printTree(Node n) {
    StringWriter sw = new StringWriter();
    try {
      n.appendStringTree(sw);
      System.err.println(sw.toString());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Main traversal callback for processing the AST of a CommonJS module.
   */
  private class CommonJsModuleCallback implements NodeTraversal.Callback {

    private final List<Node> moduleExportRefs = new ArrayList<>();

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        checkState(currentModule == null);
        if (!moduleRegistry.hasModuleWithPath(n.getSourceFileName())) {
          return false;
        }
        currentModule = moduleRegistry.registerScriptForModule(n);

        // Process all namespace references before module.exports and exports references.
        traverseTyped(t.getCompiler(), n, new ProcessNamespaceReferences());
      }
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        visitScript(t, n);
      }

      if (n.isCall()
          && n.getChildCount() == 2
          && n.getFirstChild().matchesQualifiedName("require")
          && n.getChildAtIndex(1).isString()) {
        visitRequireCall(t, n, parent);
      }

      if (n.isGetProp()
          && "module.exports".equals(n.getQualifiedName())) {
        if (t.getScope().getVar("module") == null) {
          moduleExportRefs.add(n);
        }
      }
    }

    private void visitScript(NodeTraversal t, Node script) {
      if (currentModule == null) {
        return;
      }

      processModuleExportRefs(t);

      Node googModule = exprResult(call(
          getprop(
              name("goog"),
              string("module")),
          string(currentModule.getVarName())
      ));
      script.addChildToFront(googModule.srcrefTree(script));

      t.getInput().addProvide(currentModule.getVarName());

      traverse(t.getCompiler(), script, new TypeCleanup());

      currentModule = null;

      t.getCompiler().reportCodeChange();
    }

    private void processModuleExportRefs(NodeTraversal t) {
      Node moduleExportsAssignment = null;
      for (Node ref : moduleExportRefs) {
        if (isTopLevelAssignLhs(ref)) {
          if (moduleExportsAssignment != null) {
            t.report(ref, MULTIPLE_ASSIGNMENTS_TO_MODULE_EXPORTS);
            return;
          } else {
            moduleExportsAssignment = ref;
          }
        }
      }

      for (Node ref : moduleExportRefs) {
        ref.getParent().replaceChild(
            ref,
            name("exports").srcrefTree(ref));
      }
    }

    private boolean isTopLevelAssign(Node n) {
      return n.isAssign()
          && n.getParent().isExprResult()
          && n.getParent().getParent().isScript();
    }

    private boolean isTopLevelAssignLhs(Node n) {
      return n == n.getParent().getFirstChild()
          && isTopLevelAssign(n.getParent());
    }

    private void visitRequireCall(NodeTraversal t, Node require, Node parent) {
      Path currentFile = moduleRegistry.getFileSystem().getPath(t.getSourceName());

      String modulePath = require.getChildAtIndex(1).getString();
      String moduleName;

      if (modulePath.startsWith(".") || modulePath.startsWith("/")) {
        Path moduleFile = currentFile.getParent().resolve(modulePath).normalize();
        moduleName = DossierModule.guessModuleName(moduleFile);
      } else {
        // TODO: allow users to provide extern module definitions.
        moduleName = DossierModule.externModuleName(modulePath);
      }

      // Only register the require statement on this module if it occurs at the global
      // scope. Assume other require statements are not declared at the global scope to
      // avoid create a circular dependency. While node can handle these, by returning
      // a partial definition of the required module, the cycle would be an error for
      // the compiler. For more information on how Node handles cycles, see:
      //     http://www.nodejs.org/api/modules.html#modules_cycles
      if (t.getScope().isGlobal()) {
        t.getInput().addRequire(moduleName);
      }

      parent.replaceChild(require, name(moduleName).srcrefTree(require));
      compiler.reportCodeChange();
    }
  }

  private class ProcessNamespaceReferences extends NodeTraversal.AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isAssign()) {
        processAssignment(t, n);
      }

      // Handle simple property references: foo.bar;
      if (n.isExprResult()
          && n.getFirstChild().isGetProp()
          && n.getFirstChild() == n.getLastChild()) {
        processPropertyDeclaration(t, n);
      }

      // Handle obj.__defineGetter__(...);
      if (n.isExprResult()
          && n.getFirstChild().isCast()
          && n.getFirstChild().getFirstChild().isCall()
          && n.getFirstChild().getFirstChild().getFirstChild().isGetProp()) {
        String name = n.getFirstChild().getFirstChild().getFirstChild().getQualifiedName();
        if (name.endsWith(".__defineGetter__")) {
          procesPropertyGetter(t, n);
        }
      }
    }

    /**
     * Modifies any assignments whose type is declared as a reference to another namespace to be a
     * direct reference to the indicated namespace object. This is required since the Closure
     * Compiler's type system does not fully support namespace type references yet.
     *
     * TODO(jleyba): Remove this when Closure supports namespace type references.
     */
    private void processAssignment(NodeTraversal t, Node node) {
      JSType type = getNamespaceType(t, node);
      if (type == null) {
        return;
      }

      String namespace = type.getDisplayName().substring(0, type.getDisplayName().length() - 1);
      if (t.getScope().isGlobal()) {
        t.getInput().addRequire(namespace);
      }
      node.setJSDocInfo(null);

      Node prop = buildProp(namespace);
      prop.copyInformationFromForTree(node.getLastChild());
      node.replaceChild(node.getLastChild(), prop);
    }

    /**
     * Modifies any property declarations whose declared type is a namespace to be a direct
     * reference to the indicated namespace object.
     */
    private void processPropertyDeclaration(NodeTraversal t, Node node) {
      Node getProp = node.getFirstChild();
      JSType type = getNamespaceType(t, getProp);
      if (type == null) {
        return;
      }

      String namespace = type.getDisplayName().substring(0, type.getDisplayName().length() - 1);
      if (t.getScope().isGlobal()) {
        t.getInput().addRequire(namespace);
      }
      getProp.setJSDocInfo(null);

      node.removeChildren();
      Node prop = IR.assign(getProp, buildProp(namespace));
      prop.copyInformationFromForTree(getProp);
      node.addChildrenToFront(prop);
    }

    /**
     * Modifies any property getters whose declared type is a namespace to be a direct
     * reference to the indicated namespace object.
     */
    private void procesPropertyGetter(NodeTraversal t, Node node) {
      JSType type = getNamespaceType(t, node.getFirstChild());
      if (type == null) {
        return;
      }

      Node call = node.getFirstChild().getFirstChild();
      Node getProp = call.getFirstChild();
      Node name = getProp.getNext();
      checkArgument(name.isString());

      String namespace = type.getDisplayName().substring(0, type.getDisplayName().length() - 1);
      if (t.getScope().isGlobal()) {
        t.getInput().addRequire(namespace);
      }

      node.removeChildren();
      getProp = getProp.cloneTree();
      getProp.replaceChild(getProp.getLastChild(), name.cloneNode());
      Node prop = IR.assign(getProp.cloneTree(), buildProp(namespace));
      prop.copyInformationFromForTree(getProp);
      node.addChildrenToFront(prop);
    }

    @Nullable
    private JSType getNamespaceType(NodeTraversal t, Node node) {
      JSDocInfo info = node.getJSDocInfo();
      if (info == null || info.getType() == null) {
        return null;
      }

      JSType type = info.getType().evaluate(t.getTypedScope(), t.getCompiler().getTypeRegistry());
      if (type == null || type.getDisplayName() == null) {
        return null;
      }

      if (type.getDisplayName().endsWith(".")) {
        return type;
      }

      return null;
    }
  }

  private class TypeCleanup extends NodeTraversal.AbstractPostOrderCallback {

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
      if (typeNode.isString()) {
        typeNode.putProp(Node.ORIGINALNAME_PROP, typeNode.getString());

        if (typeNode.getString().startsWith("exports.")) {
          String newName = currentModule.getVarName() +
              typeNode.getString().substring("exports".length());
          typeNode.setString(newName);
        }
      }

      for (Node child = typeNode.getFirstChild(); child != null; child = child.getNext()) {
        fixTypeNode(child);
      }
    }
  }

  private static Node buildProp(String namespace) {
    Iterator<String> names = Splitter.on('.')
        .omitEmptyStrings()
        .split(namespace)
        .iterator();
    checkArgument(names.hasNext());

    Node current = name(names.next());
    while (names.hasNext()) {
      current = getprop(current, string(names.next()));
    }

    return current;
  }
}