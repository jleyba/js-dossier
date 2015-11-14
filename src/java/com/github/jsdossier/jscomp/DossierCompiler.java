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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Stderr;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.ES6ModuleLoader;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * A specialized version of the {@link com.google.javascript.jscomp.Compiler Closure Compiler} that preserves CommonJS module
 * structure so as to properly extract JSDoc info.
 */
public final class DossierCompiler extends Compiler {

  private final DossierModuleRegistry moduleRegistry;
  private final TypeRegistry2 typeRegistry;
  private final FileSystem inputFs;

  private boolean hasParsed = false;

  @Inject
  DossierCompiler(
      @Stderr PrintStream stream,
      DossierModuleRegistry moduleRegistry,
      TypeRegistry2 typeRegistry,
      @Input FileSystem inputFs) {
    super(stream);
    this.moduleRegistry = moduleRegistry;
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
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
    traverseEs6(this, root, new Es6ModuleTraversal());
  }

  /**
   * Returns a URI from the path's string representation. This is used instead of the URI from the
   * file system provider for compatibility with the compiler in testing.
   */
  private static URI toSimpleUri(Path path) {
    return URI.create(path.toString()).normalize();
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

  private void printTree(Node n) {
    StringWriter sw = new StringWriter();
    try {
      n.appendStringTree(sw);
      System.err.println(sw.toString());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  private class Es6ModuleTraversal extends NodeTraversal.AbstractShallowCallback {

    private final Set<Node> imports = new HashSet<>();
    private final Map<String, String> exportedNames = new HashMap<>();
    private final Map<String, JSDocInfo> internalDocs = new HashMap<>();
    private Module.Builder module;

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
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
      
      if (n.isExport()) {
        visitExport(n);
        return;
      }

      if (n.isImport()) {
        imports.add(n);
      }
    }
    
    private void visitExport(Node n) {
      if (module == null) {
        Path path = inputFs.getPath(n.getSourceFileName());
        System.out.println("Found ES6 module: " + path);
        module = Module.builder()
            .setId(ES6ModuleLoader.toModuleName(toSimpleUri(path)))
            .setPath(path)
            .setType(Module.Type.ES6);

        // The compiler does not generate alias notifications when it rewrites an ES6 module,
        // so we register a region here.
        AliasRegion region = AliasRegion.builder()
            .setPath(path)
            .setRange(Range.atLeast(Position.of(0, 0)))
            .build();
        typeRegistry.addAliasRegion(region);
      }
      
      // Will the second child ever be non-null here?  export default foo;
      if (n.getFirstChild().isName() && n.getFirstChild().getNext() == null) {
        String name = n.getFirstChild().getQualifiedName();
        exportedNames.put(name, name);
        return;
      }

      if (n.getFirstChild().getType() == Token.EXPORT_SPECS) {
        Node exportSpecs = n.getFirstChild();

        String context = "";
        if (exportSpecs.getNext() != null && exportSpecs.getNext().isString()) {
          Path path = inputFs.getPath(n.getSourceFileName());
          context = extractModuleId(path, exportSpecs.getNext());
          if (context == null) {
            return;
          }
          context += ".";
        }

        for (Node spec = exportSpecs.getFirstChild(); spec != null; spec = spec.getNext()) {
          Node first = spec.getFirstChild();
          checkArgument(first.isName());
          
          Node second = firstNonNull(first.getNext(), first);
          
          exportedNames.put(second.getQualifiedName(), context + first.getQualifiedName());
        }
      }
    }
    
    private void visitScript(Node script) {
      if (module == null) {
        return;
      }

      module.setJsDoc(JsDoc.from(script.getJSDocInfo()))
          .setExportedNames(ImmutableMap.copyOf(exportedNames))
          .setInternalVarDocs(ImmutableMap.copyOf(internalDocs));

      typeRegistry.addModule(module.build());
      exportedNames.clear();
      internalDocs.clear();
      module = null;

      for (Node imp : imports) {
        processImportStatement(imp);
      }
      imports.clear();
    }

    private void processImportStatement(Node n) {
      checkArgument(n.isImport());

      Path path = inputFs.getPath(n.getSourceFileName());
      AliasRegion aliasRegion = getFirst(typeRegistry.getAliasRegions(path), null);
      if (aliasRegion == null) {
        throw new AssertionError();
      }
      
      Node first = n.getFirstChild();
      Node second = first.getNext();
      
      String def = extractModuleId(path, second.getNext());
      if (def == null) {
        return;
      }

      if (first.isEmpty() && second.getType() == Token.IMPORT_STAR) {
        String alias = second.getString();
        aliasRegion.addAlias(alias, def);

      } else if (first.isName()) {
        String alias = first.getQualifiedName();
        aliasRegion.addAlias(alias, def + "." + alias);

      } else if (first.isEmpty() && second.getType() == Token.IMPORT_SPECS) {
        for (Node spec = second.getFirstChild(); spec != null; spec = spec.getNext()) {
          String imported = spec.getFirstChild().getQualifiedName();
          String alias = spec.getLastChild().getQualifiedName();
          
          // TODO: not sure if this is the best way to handle defaults.
          if ("default".equals(imported)) {
            aliasRegion.addAlias(alias, def);
          } else {
            aliasRegion.addAlias(alias, def + "." + imported);
          }
        }
      }
    }
    
    @Nullable
    @CheckReturnValue
    private String extractModuleId(Path context, Node pathNode) {
      checkArgument(pathNode.isString());
      
      String path = pathNode.getString();
      // TODO: support non-relative imports?
      if (!path.startsWith("./") && !path.startsWith("../")) {
        return null;
      }
      return guessModuleId(context, path);
    }

    private String guessModuleId(Path ctx, String path) {
      Path module = ctx.resolveSibling(path);
      return ES6ModuleLoader.toModuleName(toSimpleUri(module));
    }
  }
}
