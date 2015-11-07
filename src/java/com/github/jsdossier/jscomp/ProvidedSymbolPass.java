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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.javascript.jscomp.NodeTraversal.traverse;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashSet;
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
  
  @Inject
  ProvidedSymbolPass(
      @Input FileSystem inputFs,
      @Modules ImmutableSet<Path> nodeModules,
      DossierCompiler compiler,
      TypeRegistry typeRegistry) {
    this.inputFs = inputFs;
    this.nodeModules = nodeModules;
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
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
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
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
          Module module = Module.builder()
              .setId(name)
              .setPath(inputFs.getPath(n.getSourceFileName()))
              .setType(type)
              .setJsDoc(findScriptJsDoc(n))
              .build();
//          traverseEs6(compiler, getScriptNode(n), new ExportedNameCollector(module));
          // TODO: collect module exports
          System.out.println(">>> Adding module " + name);
          typeRegistry.addModule(module);
        } else {
          typeRegistry.recordProvide(name);
        }
      }
    });
  }
  
  private static JsDoc findScriptJsDoc(Node n) {
    n = getScriptNode(n);
    if (n.isScript() && n.getParent() != null && n.getParent().isBlock()) {
      return JsDoc.from(n.getJSDocInfo());
    }
    return JsDoc.from(null);
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
  
  private static final class ExportedNameCollector extends NodeTraversal.AbstractShallowCallback {
    private final Module module;
    private final Set<Node> exportAssignments = new HashSet<>();

    private ExportedNameCollector(Module module) {
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
      if (n.isScript()) {
        for (Node ref : exportAssignments) {
          String rhsName = ref.getLastChild().getQualifiedName();
          if (isNullOrEmpty(rhsName)) {
            continue;
          }
          String lhsName = ref.getFirstChild().getQualifiedName();
        }
      }
    }
  }
}
