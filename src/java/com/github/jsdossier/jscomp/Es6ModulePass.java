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
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.ES6ModuleLoader;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Pass responsible for processing ES6 modules.
 */
@AutoFactory
final class Es6ModulePass implements CompilerPass {

  private static final Logger log = Logger.getLogger(Es6ModulePass.class.getName());

  private static final String DEFAULT = "default";

  private final DossierCompiler compiler;
  private final TypeRegistry typeRegistry;
  private final FileSystem inputFs;
  private final ImmutableSet<Path> declaredModules;

  Es6ModulePass(
      @Provided TypeRegistry typeRegistry,
      @Provided @Input FileSystem inputFs,
      @Provided @Modules ImmutableSet<Path> declaredModules,
      DossierCompiler compiler) {
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
    this.declaredModules = declaredModules;
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    traverseEs6(compiler, root, new Es6ModuleTraversal());
  }

  /**
   * Returns a URI from the path's string representation. This is used instead of the URI from the
   * file system provider for compatibility with the compiler in testing.
   */
  private static URI toSimpleUri(Path path) {
    if (path.isAbsolute()) {
      path = path.getRoot().relativize(path);
    }
    return URI.create(path.toString().replace('\\', '/')).normalize();
  }

  @SuppressWarnings("unused")
  private static void printTree(Node n) {
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
    private final Map<String, JSDocInfo> exportedDocs = new HashMap<>();
    private final Map<String, String> exportedNames = new HashMap<>();
    private final Map<String, JSDocInfo> internalDocs = new HashMap<>();
    private JsDoc moduleDocs;
    private Module.Builder module;

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        visitScript(n);
        return;
      }

      if (n.isClass()) {
        visitInternalNameDefinition(n, parent);
      }

      if (n.isFunction()) {
        visitInternalNameDefinition(n, parent);
      }

      if ((n.isConst() || n.isLet() || n.isVar())) {
        visitInternalNameDefinition(n, parent);
      }

      if (n.isExport()) {
        visitExport(n);
      }

      if (n.isImport()) {
        visitImport(n);
      }
    }

    private void visitInternalNameDefinition(Node n, Node parent) {
      String name = n.getFirstChild().getQualifiedName();
      if (isNullOrEmpty(name)) {
        return;
      }

      if (parent.isScript()) {
        if (n.getJSDocInfo() != null) {
          internalDocs.put(name, n.getJSDocInfo());
        }

      } else if (parent.isExport()) {
        if (parent.getBooleanProp(Node.EXPORT_DEFAULT)) {
          exportedNames.put(DEFAULT, name);
        } else {
          exportedNames.put(name, name);
        }
        if (parent.getJSDocInfo() != null) {
          exportedDocs.put(name, parent.getJSDocInfo());
        }

        if (n.getJSDocInfo() != null) {
          internalDocs.put(name, n.getJSDocInfo());
        } else if (parent.getJSDocInfo() != null) {
          internalDocs.put(name, parent.getJSDocInfo());
        }
      }
    }

    private void visitExport(Node n) {
      if (module == null) {
        initModule(n);
        module.setType(Module.Type.ES6);
      }

      // Case: export name;
      // Will the second child ever be non-null here?
      if (n.getFirstChild().isName() && n.getFirstChild().getNext() == null) {
        String name = n.getFirstChild().getQualifiedName();
        if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
          exportedNames.put(DEFAULT, name);
        } else {
          exportedNames.put(name, name);
        }
        if (n.getJSDocInfo() != null) {
          exportedDocs.put(name, n.getJSDocInfo());
        }
        return;
      }

      // Case: export {Name} or export {Name as Alias}
      if (n.getFirstChild().getType() == Token.EXPORT_SPECS) {
        Path path = inputFs.getPath(n.getSourceFileName());
        Node exportSpecs = n.getFirstChild();

        String context = "";
        if (n.getLastChild().isString()) {
          context = extractModuleId(path, exportSpecs.getNext());
          if (context == null) {
            return;
          }
          context += ".";
        }

        for (Node spec = exportSpecs.getFirstChild(); spec != null; spec = spec.getNext()) {
          Node first = spec.getFirstChild();
          checkArgument(first.isName());
          String trueName = context + first.getQualifiedName();

          Node second = firstNonNull(first.getNext(), first);

          exportedNames.put(second.getQualifiedName(), trueName);

          if (!DEFAULT.equals(first.getQualifiedName())) {
            getAliasRegion(path).addAlias(first.getQualifiedName(), trueName);
          }
        }
      }
    }

    private void visitImport(Node n) {
      imports.add(n);
      if (module == null
          && declaredModules.contains(inputFs.getPath(n.getSourceFileName()))) {
        initModule(n);
      }
    }

    private void initModule(Node n) {
      if (module != null) {
        return;
      }

      Path path = inputFs.getPath(n.getSourceFileName());
      URI uri = toSimpleUri(path);
      log.fine(String.format("Found ES6 module: %s (%s)", path, uri));

      module = Module.builder()
          .setId(ES6ModuleLoader.toModuleName(uri))
          .setPath(path)
          .setType(Module.Type.ES6);

      if (n.getJSDocInfo() != null) {
        moduleDocs = JsDoc.from(n.getJSDocInfo());
        module.setJsDoc(moduleDocs);
      }

      // The compiler does not generate alias notifications when it rewrites an ES6 module,
      // so we register a region here.
      AliasRegion region = AliasRegion.builder()
          .setPath(path)
          .setRange(Range.atLeast(Position.of(0, 0)))
          .build();
      typeRegistry.addAliasRegion(region);
    }

    private void visitScript(Node script) {
      if (module == null) {
        return;
      }

      if (moduleDocs == null) {
        module.setJsDoc(JsDoc.from(script.getJSDocInfo()));
      }

      module
          .setExportedDocs(ImmutableMap.copyOf(exportedDocs))
          .setExportedNames(ImmutableMap.copyOf(exportedNames))
          .setInternalVarDocs(ImmutableMap.copyOf(internalDocs));

      typeRegistry.addModule(module.build());
      exportedDocs.clear();
      exportedNames.clear();
      internalDocs.clear();
      module = null;
      moduleDocs = null;

      for (Node imp : imports) {
        processImportStatement(imp);
      }
      imports.clear();
    }

    private void processImportStatement(Node n) {
      checkArgument(n.isImport());

      Path path = inputFs.getPath(n.getSourceFileName());
      AliasRegion aliasRegion = getAliasRegion(path);

      Node first = n.getFirstChild();
      Node second = first.getNext();

      String def = extractModuleId(path, second.getNext());
      if (def == null) {
        return;
      }

      if (first.isName()) {
        String alias = first.getQualifiedName();
        aliasRegion.addAlias(alias, def + "." + DEFAULT);
      }

      if (second.getType() == Token.IMPORT_STAR) {
        String alias = second.getString();
        aliasRegion.addAlias(alias, def);

      } else if (second.getType() == Token.IMPORT_SPECS) {
        for (Node spec = second.getFirstChild(); spec != null; spec = spec.getNext()) {
          String imported = spec.getFirstChild().getQualifiedName();
          String alias = spec.getLastChild().getQualifiedName();
          aliasRegion.addAlias(alias, def + "." + imported);
        }
      }
    }

    private AliasRegion getAliasRegion(Path path) {
      AliasRegion aliasRegion = getFirst(typeRegistry.getAliasRegions(path), null);
      if (aliasRegion == null) {
        throw new AssertionError();
      }
      return aliasRegion;
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
