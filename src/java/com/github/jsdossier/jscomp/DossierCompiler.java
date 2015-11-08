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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Stderr;
import com.google.common.collect.Range;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.ES6ModuleLoader;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import javax.inject.Inject;

/**
 * A specialized version of the {@link com.google.javascript.jscomp.Compiler Closure Compiler} that preserves CommonJS module
 * structure so as to properly extract JSDoc info.
 */
public final class DossierCompiler extends Compiler {

  private final DossierModuleRegistry moduleRegistry;
  private final TypeRegistry2 typeRegistry;
  private final FileSystem inputFs;
  private final AliasTransformListener aliasTransformListener;

  private boolean hasParsed = false;

  @Inject
  DossierCompiler(
      @Stderr PrintStream stream,
      DossierModuleRegistry moduleRegistry,
      TypeRegistry2 typeRegistry,
      @Input FileSystem inputFs,
      AliasTransformListener aliasTransformListener) {
    super(stream);
    this.moduleRegistry = moduleRegistry;
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
    this.aliasTransformListener = aliasTransformListener;
  }

  public DossierModuleRegistry getModuleRegistry() {
    return moduleRegistry;
  }

  @Override
  public void parse() {
    checkState(!hasParsed,
        "%s can only parse its inputs once! Create a new instance if you must re-parse",
        getClass());
    hasParsed = true;
    
    // Look for ES6 modules before we do anything else. This is necessary since the compiler
    // transpiles modules to ES5.
    processEs6Modules();

    // First we transform the CommonJS modules. This ensures input sources are properly re-ordered
    // based on the goog.provide/require statements we generate for the modules. This is necessary
    // since the compiler does its final input ordering before invoking any custom passes
    // (otherwise, we could just process the modules as a custom pass).
    DossierProcessCommonJsModules cjs = new DossierProcessCommonJsModules(
        moduleRegistry, typeRegistry, inputFs);
    // TODO(jleyba): processCommonJsModules(cjs, getExternsInOrder());
    processCommonJsModules(cjs, getInputsById().values());

    // Now we can proceed with the normal parsing.
    super.parse();
  }
  
  private void processEs6Modules() {
    for (CompilerInput input : getInputsById().values()) {
      Node root = input.getAstRoot(this);
      if (root != null) {
        processEs6Modules(root);
      }
    }
  }
  
  private void processEs6Modules(Node root) {
    traverseEs6(this, root, new NodeTraversal.AbstractShallowCallback() {
      private boolean isModule;

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (!isModule && n.isExport()) {
          isModule = true;
          Path path = inputFs.getPath(n.getSourceFileName());
          typeRegistry.addModule(Module.builder()
              .setId(ES6ModuleLoader.toModuleName(toSimpleUri(path)))
              .setPath(path)
              .setType(Module.Type.ES6)
              .setJsDoc(findScriptJsDoc(n))
              .build());

          // The compiler does not generate alias notifications when it rewrites an ES6 module,
          // so we register a region here.
          AliasRegion region = AliasRegion.builder()
              .setPath(path)
              .setRange(Range.atLeast(Position.of(0, 0)))
              .build();
          typeRegistry.addAliasRegion(region);
        }
      }
    });
  }

  /**
   * Returns a URI from the path's string representation. This is used instead of the URI from the
   * file system provider for compatibility with the compiler in testing.
   */
  private static URI toSimpleUri(Path path) {
    return URI.create(path.toString()).normalize();
  }

  private static JsDoc findScriptJsDoc(Node n) {
    while (n != null && !n.isScript()) {
      n = n.getParent();
    }
    if (n == null) {
      throw new AssertionError("traversed too far");
    }
    if (n.getParent() != null && n.getParent().isBlock()) {
      return JsDoc.from(n.getJSDocInfo());
    }
    return JsDoc.from(null);
  }

  private void processCommonJsModules(
      DossierProcessCommonJsModules compilerPass, Iterable<CompilerInput> inputs) {
    for (CompilerInput input : inputs) {
      Node root = input.getAstRoot(this);
      if (root == null) {
        continue;
      }
      compilerPass.process(this, root);
    }
  }
}
