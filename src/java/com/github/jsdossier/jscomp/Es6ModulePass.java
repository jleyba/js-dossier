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

import static com.github.jsdossier.jscomp.Module.Type.ES6;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.deps.ModuleNames;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Pass responsible for processing ES6 modules. */
final class Es6ModulePass implements DossierCompilerPass {

  private static final String DEFAULT = "default";

  private final TypeRegistry typeRegistry;
  private final FileSystem inputFs;

  @Inject
  Es6ModulePass(TypeRegistry typeRegistry, @Input FileSystem inputFs) {
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
  }

  @Override
  public void process(DossierCompiler compiler, Node root) {
    if (!root.isFromExterns()) {
      traverseEs6(compiler, root, new Es6ModuleTraversal());
    }
  }

  private class Es6ModuleTraversal implements NodeTraversal.Callback {

    private boolean isEs6Module;
    private Module.Builder module;

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      if (n.isScript()) {
        Path path = inputFs.getPath(n.getSourceFileName());
        module =
            Module.builder()
                .setId(ES6.newId(path))
                .setJsDoc(JsDoc.from(n.getJSDocInfo()))
                .setAliases(AliasRegion.forFile(path));

        // Start building the module. It won't be finalized unless we see an export statement.
        isEs6Module = false;
      }
      return parent == null || !parent.isFunction() || n == parent.getFirstChild();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        visitScript();
        return;
      }

      if (n.isClass() || n.isFunction() || NodeUtil.isNameDeclaration(n)) {
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

      if (parent.isModuleBody()) {
        if (n.getJSDocInfo() != null) {
          module.internalVarDocsBuilder().put(name, n.getJSDocInfo());
        }

      } else if (parent.isExport()) {
        if (parent.getBooleanProp(Node.EXPORT_DEFAULT)) {
          module.exportedNamesBuilder().put(DEFAULT, name);
        } else {
          module.exportedNamesBuilder().put(name, name);
        }
        if (parent.getJSDocInfo() != null) {
          module.exportedDocsBuilder().put(name, parent.getJSDocInfo());
        }

        if (n.getJSDocInfo() != null) {
          module.internalVarDocsBuilder().put(name, n.getJSDocInfo());
        } else if (parent.getJSDocInfo() != null) {
          module.internalVarDocsBuilder().put(name, parent.getJSDocInfo());
        }
      }
    }

    private void visitExport(Node n) {
      isEs6Module = true;

      // Case: export name;
      // Will the second child ever be non-null here?
      if (n.getFirstChild().isName() && n.getFirstChild().getNext() == null) {
        String name = n.getFirstChild().getQualifiedName();
        if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
          module.exportedNamesBuilder().put(DEFAULT, name);
        } else {
          module.exportedNamesBuilder().put(name, name);
        }
        if (n.getJSDocInfo() != null) {
          module.exportedDocsBuilder().put(name, n.getJSDocInfo());
        }
        return;
      }

      // Case: export {Name} or export {Name as Alias}
      if (n.getFirstChild().getToken() == Token.EXPORT_SPECS) {
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

          module.exportedNamesBuilder().put(second.getQualifiedName(), trueName);

          if (!first.matchesQualifiedName(DEFAULT)) {
            module.getAliases().addAlias(first.getQualifiedName(), trueName);
          }
        }
      }
    }

    private void visitScript() {
      if (isEs6Module) {
        verify(module != null);
        typeRegistry.addModule(module.build());
      }
      isEs6Module = false;
      module = null;
    }

    private void visitImport(Node n) {
      checkArgument(n.isImport());
      isEs6Module = true;

      AliasRegion aliasRegion = module.getAliases();

      Node first = n.getFirstChild();
      Node second = first.getNext();

      String def = extractModuleId(module.getId().getPath(), second.getNext());
      if (def == null) {
        return;
      }

      if (first.isName()) {
        String alias = first.getQualifiedName();
        aliasRegion.addAlias(alias, def + "." + DEFAULT);
      }

      if (second.isImportStar()) {
        String alias = second.getString();
        aliasRegion.addAlias(alias, def);

      } else if (second.isImportSpecs()) {
        for (Node spec = second.getFirstChild(); spec != null; spec = spec.getNext()) {
          String imported = spec.getFirstChild().getQualifiedName();
          String alias = spec.getLastChild().getQualifiedName();
          aliasRegion.addAlias(alias, def + "." + imported);
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
      Path module = ctx.resolveSibling(path).normalize();
      return ModuleNames.fileToModuleName(module.toString());
    }
  }
}
