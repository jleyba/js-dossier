/*
 Copyright 2013-2016 Jason Leyba

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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

/**
 * Compiler pass used to identify symbols declared with "goog.provide" statements. This pass
 * ignores the externs tree.
 */
public final class ProvidedSymbolPass implements CompilerPass {

  private final FileSystem inputFs;
  private final ImmutableSet<Path> nodeModules;
  private final DossierCompiler compiler;
  private final TypeRegistry typeRegistry;
  private final NodeLibrary nodeLibrary;

  @Inject
  ProvidedSymbolPass(
      @Input FileSystem inputFs,
      @Modules ImmutableSet<Path> nodeModules,
      DossierCompiler compiler,
      TypeRegistry typeRegistry,
      NodeLibrary nodeLibrary) {
    this.inputFs = inputFs;
    this.nodeModules = nodeModules;
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
    this.nodeLibrary = nodeLibrary;
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

  @Override
  public void process(Node ignored, Node root) {
    traverseEs6(compiler, root, new NodeTraversal.AbstractShallowCallback() {

      private final Set<Node> exportAssignments = new HashSet<>();
      private final Map<String, JSDocInfo> internalDocs = new HashMap<>();
      private Module.Builder module;

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (isTopLevelAssign(n)) {
          String name = n.getFirstChild().getQualifiedName();
          if (!isNullOrEmpty(name) && ("exports".equals(name) || name.startsWith("exports."))) {
            exportAssignments.add(n);
          }
        }

        if (n.isScript()) {
          visitScript(n);
          return;
        }

        if (n.isClass() && parent.isScript()) {
          String name = n.getFirstChild().getQualifiedName();
          if (n.getJSDocInfo() != null) {
            internalDocs.put(name, n.getJSDocInfo());
          }
        }

        if (n.isFunction() && parent.isScript()) {
          String name = n.getFirstChild().getQualifiedName();
          if (n.getJSDocInfo() != null) {
            internalDocs.put(name, n.getJSDocInfo());
          }
        }

        if ((n.isConst() || n.isLet() || n.isVar()) && parent.isScript()) {
          String name = n.getFirstChild().getQualifiedName();
          if (n.getJSDocInfo() != null) {
            internalDocs.put(name, n.getJSDocInfo());
          }
        }

        if (!n.isCall()) {
          return;
        }

        CodingConvention convention = compiler.getCodingConvention();

        String name = convention.extractClassNameIfProvide(n, parent);
        if (isNullOrEmpty(name)) {
          return;
        }

        if (convention.extractIsModuleFile(n, parent)) {
          Path path = inputFs.getPath(n.getSourceFileName());
          Module.Type type = nodeModules.contains(path) ? Module.Type.NODE : Module.Type.CLOSURE;
          module = Module.builder()
              .setId(name)
              .setPath(inputFs.getPath(n.getSourceFileName()))
              .setType(type);
        } else {
          typeRegistry.recordProvide(name);
        }
      }

      private void visitScript(Node script) {
        if (module == null) {
          return;
        }

        Map<String, String> exportedNames = new HashMap<>();
        for (Node ref : exportAssignments) {
          String rhsName = ref.getLastChild().getQualifiedName();
          if (isNullOrEmpty(rhsName)) {
            continue;
          }

          String lhsName = ref.getFirstChild().getQualifiedName();
          if (isNullOrEmpty(lhsName)) {
            continue;
          }

          int trim = "exports".length();
          lhsName = lhsName.substring(trim);
          if (lhsName.startsWith(".")) {
            lhsName = lhsName.substring(1);
          }
          if (isNullOrEmpty(lhsName)) {
            continue;
          }

          exportedNames.put(lhsName, rhsName);
        }

        module.setJsDoc(JsDoc.from(script.getJSDocInfo()))
            .setExportedNames(ImmutableMap.copyOf(exportedNames))
            .setInternalVarDocs(ImmutableMap.copyOf(internalDocs));

        if (!nodeLibrary.isModuleId(module.getId())) {
          typeRegistry.addModule(module.build());
        }
        exportAssignments.clear();
        internalDocs.clear();
        module = null;
      }
    });
  }

  private static boolean isTopLevelAssign(Node n) {
    return n.isAssign()
        && n.getParent().isExprResult()
        && n.getParent().getParent().isScript();
  }
}
