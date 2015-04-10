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

package com.github.jsdossier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;

import com.github.jsdossier.jscomp.DossierCompiler;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.nio.file.FileSystem;
import java.util.HashSet;
import java.util.Set;

/**
 * A compiler pass that collects all of the provided symbols in the input sources.
 */
class ProvidedSymbolsCollectionPass implements CompilerPass {

  private final TypeRegistry typeRegistry;
  private final DossierCompiler compiler;
  private final FileSystem fileSystem;

  ProvidedSymbolsCollectionPass(
      DossierCompiler compiler,
      TypeRegistry typeRegistry,
      FileSystem fileSystem) {
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
    this.fileSystem = fileSystem;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new NodeTraversal.AbstractShallowCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        CodingConvention convention = compiler.getCodingConvention();
        if (n.isCall()) {
          String name = convention.extractClassNameIfProvide(n, parent);
          if (!isNullOrEmpty(name)) {
            if (convention.extractIsModuleFile(n, parent)) {
              ModuleDescriptor module = new ModuleDescriptor(
                  name,
                  fileSystem.getPath(n.getSourceFileName()),
                  compiler.getModuleRegistry().hasModuleNamed(name));
              NodeTraversal.traverse(
                  compiler, getScriptNode(n), new ClosureModuleNameCollector(module));
              typeRegistry.declareModule(module);

            } else {
              typeRegistry.recordGoogProvide(name);
            }
          }
        }
      }
    });
  }

  private static Node getScriptNode(Node n) {
    while (n != null && !n.isScript()) {
      n = n.getParent();
    }
    return checkNotNull(n);
  }

  private static boolean isTopLevelAssign(Node n) {
    return n.isAssign()
        && n.getParent().isExprResult()
        && n.getParent().getParent().isScript();
  }

  private static class ClosureModuleNameCollector extends NodeTraversal.AbstractShallowCallback {
    private final ModuleDescriptor module;
    private final Set<Node> exportAssignments = new HashSet<>();

    private ClosureModuleNameCollector(ModuleDescriptor module) {
      this.module = module;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (isTopLevelAssign(n)) {
        String name = n.getFirstChild().getQualifiedName();
        if (!isNullOrEmpty(name) && ("exports".equals(name) || name.startsWith("exports."))) {
          exportAssignments.add(n);
        }
      }

      // Handle a function declaration within the module's main body.
      if (n.isFunction() && parent.isScript()) {
        verify(n.getFirstChild().isName());
        String name = n.getFirstChild().getString();
        JSDocInfo docs = n.getJSDocInfo();
        if (!"__missing_name__".equals(name) && docs != null) {
          module.addInternalVarDocs(name, docs);
        }
      }

      if (n.isVar() && parent.isScript()) {
        verify(n.getFirstChild().isName());
        String name = n.getFirstChild().getString();
        if (n.getJSDocInfo() != null) {
          module.addInternalVarDocs(name, n.getJSDocInfo());
        }
      }

      if (n.isScript()) {
        for (Node ref : exportAssignments) {
          String rhsName = ref.getLastChild().getQualifiedName();
          if (isNullOrEmpty(rhsName)) {
            continue;
          }
          String lhsName = ref.getFirstChild().getQualifiedName();
          if (isNullOrEmpty(lhsName)) {
            continue;
          }
          lhsName = module.getName() + lhsName.substring("exports".length());
          module.addExportedName(rhsName, lhsName);
        }
      }
    }
  }
}
