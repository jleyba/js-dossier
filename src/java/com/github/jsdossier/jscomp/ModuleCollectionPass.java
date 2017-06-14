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

import static com.github.jsdossier.jscomp.Module.Type.CLOSURE;
import static com.github.jsdossier.jscomp.Module.Type.NODE;
import static com.github.jsdossier.jscomp.Nodes.isCall;
import static com.github.jsdossier.jscomp.Nodes.isModuleBody;
import static com.github.jsdossier.jscomp.Nodes.isTopLevelAssign;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;
import static com.google.javascript.jscomp.NodeUtil.isNameDeclaration;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Process scripts containing a module body.
 */
final class ModuleCollectionPass implements CompilerPass {

  private static final Logger log = Logger.getLogger(ModuleCollectionPass.class.getName());

  private final DossierCompiler compiler;
  private final FileSystem inputFs;
  private final ImmutableSet<Path> nodeModules;
  private final TypeRegistry typeRegistry;
  private final NodeLibrary nodeLibrary;

  @Inject
  ModuleCollectionPass(
      DossierCompiler compiler,
      @Input FileSystem inputFs,
      @Modules ImmutableSet<Path> nodeModules,
      TypeRegistry typeRegistry,
      NodeLibrary nodeLibrary) {
    this.compiler = compiler;
    this.inputFs = inputFs;
    this.nodeModules = nodeModules;
    this.typeRegistry = typeRegistry;
    this.nodeLibrary = nodeLibrary;
  }

  @Override
  public void process(Node externs, Node root) {
    final Map<String, Module.Builder> googModules = new HashMap<>();
    final Map<String, Module.Builder> modulesByFile = new HashMap<>();
    final Set<String> legacyGoogModuleFileNames = new HashSet<>();

    traverseEs6(
        compiler,
        root,
        new NodeTraversal.AbstractShallowCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (isCall(n, "goog.module")
                && n.getSecondChild() != null
                && n.getSecondChild().isString()) {
              String name = n.getSecondChild().getString();
              Path path = inputFs.getPath(n.getSourceFileName());
              Module.Id id = (nodeModules.contains(path) ? NODE : CLOSURE).newId(name, path);
              boolean isLegacy = legacyGoogModuleFileNames.contains(n.getSourceFileName());

              Module.Builder module =
                  Module.builder()
                      .setId(isLegacy ? id.toLegacyId() : id)
                      .setAliases(AliasRegion.forFile(path))
                      .setHasLegacyNamespace(isLegacy);
              modulesByFile.put(n.getSourceFileName(), module);
              googModules.put(name, module);
            }

            if (isCall(n, "goog.module.declareLegacyNamespace")) {
              legacyGoogModuleFileNames.add(n.getSourceFileName());
              Module.Builder module = modulesByFile.get(n.getSourceFileName());
              if (module != null) {
                module.setId(module.getId().toLegacyId()).setHasLegacyNamespace(true);
              }
            }
          }
        });

    traverseEs6(
        compiler,
        root,
        new NodeTraversal.Callback() {

          Module.Builder module;

          @Override
          public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
            if (n.isModuleBody()) {
              module = modulesByFile.get(n.getSourceFileName());
            }
            return parent == null || !parent.isFunction() || n == parent.getFirstChild();
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (module == null) {
              return;
            }

            if (isTopLevelAssign(n)) {
              String name = n.getFirstChild().getQualifiedName();
              if (!isNullOrEmpty(name) && ("exports".equals(name) || name.startsWith("exports."))) {
                visitExportAssignment(n);
              }
            }

            if (n.isClass() && isModuleBody(parent)) {
              String name = n.getFirstChild().getQualifiedName();
              module.getAliases().addAlias(name, module.getId().getContentsVar(name));
              if (n.getJSDocInfo() != null) {
                module.internalVarDocsBuilder().put(name, n.getJSDocInfo());
              }
            }

            if (n.isFunction() && isModuleBody(parent)) {
              String name = n.getFirstChild().getQualifiedName();
              if (n.getJSDocInfo() != null) {
                module.internalVarDocsBuilder().put(name, n.getJSDocInfo());
              }
            }

            if (isNameDeclaration(n) && isModuleBody(parent)) {
              visitDeclaration(n);
            }

            if (n.isModuleBody()) {
              visitModuleBody(n);
            }
          }

          private void visitExportAssignment(Node node) {
            if (node.getLastChild().isObjectLit()) {
              if (node.getFirstChild().isGetProp()) {
                return; // Ignore: exports.NAME = {...}
              }

              Map<String, String> names = extractObjectLitNames(node.getLastChild());
              module.exportedNamesBuilder().putAll(names);
              return;
            }

            String rhsName = node.getLastChild().getQualifiedName();
            if (isNullOrEmpty(rhsName)) {
              return;
            }

            String lhsName = node.getFirstChild().getQualifiedName();
            if (isNullOrEmpty(lhsName)) {
              return;
            }

            int trim = "exports".length();
            lhsName = lhsName.substring(trim);
            if (lhsName.startsWith(".")) {
              lhsName = lhsName.substring(1);
            }
            if (isNullOrEmpty(lhsName)) {
              return;
            }

            module.exportedNamesBuilder().put(lhsName, rhsName);
            if (node.getJSDocInfo() != null) {
              module.exportedDocsBuilder().put(lhsName, node.getJSDocInfo());
            }
          }

          private void visitModuleBody(Node node) {
            module.setJsDoc(JsDoc.from(node.getParent().getJSDocInfo()));

            Module m = module.build();
            typeRegistry.addAliasRegion(m.getAliases());
            if (!nodeLibrary.canRequireId(m.getId().getCompiledName())) {
              typeRegistry.addModule(m);
            }

            module = null;
          }

          private void visitDeclaration(Node decl) {
            Node lhs = decl.getFirstChild();
            Node rhs;

            if (lhs.isName()) {
              rhs = lhs.getFirstChild();

              // While we're here, record the JSDocs for the lhs.
              if (decl.getJSDocInfo() != null) {
                module.internalVarDocsBuilder().put(lhs.getString(), decl.getJSDocInfo());
              }
            } else if (lhs.isDestructuringLhs() && lhs.getFirstChild().isObjectPattern()) {
              rhs = lhs.getSecondChild();
            } else {
              return;
            }

            // Special case: var X = function() {}.
            if (lhs.isName() && rhs != null && rhs.isFunction()) {
              module.getAliases().addAlias(
                  lhs.getQualifiedName(),
                  module.getId().getContentsVar(lhs.getQualifiedName()));
              return;
            }

            String rhsName = getQualifiedName(rhs);
            if (isNullOrEmpty(rhsName)) {
              return;
            }

            List<String> lhsNames = getNames(lhs);
            if (lhsNames.size() == 1) {
              recordAlias(lhsNames.get(0), rhsName);
            } else {
              // Destructuring case:
              //    var {One, Two, Three} = goog.require('foo');
              for (String name : lhsNames) {
                recordAlias(name, rhsName + "." + name);
              }
            }
          }

          private void recordAlias(String alias, String name) {
            if (Types.isInternalVar(name)) {
              verify(module.getType() == NODE,
                  "encountered internal var %s for module %s of type %s",
                  name, module.getId(), module.getType());
              String originalModuleName = Types.extractOriginalModuleName(name);
              String compiledName =
                  NODE.newId(originalModuleName, module.getId().getPath()).getCompiledName();
              int index = name.indexOf('.');
              if (index == -1) {
                name = compiledName;
              } else {
                name = compiledName + name.substring(index);
              }
            }

            module.getAliases().addAlias(alias, name);
            module.getAliases().addAlias(module.getId().getContentsVar(alias), alias);
          }

          private List<String> getNames(Node node) {
            if (node.isName()) {
              return ImmutableList.of(node.getQualifiedName());
            }

            if (node.isDestructuringLhs() && node.getFirstChild().isObjectPattern()) {
              ImmutableList.Builder<String> names = ImmutableList.builder();
              for (Node key = node.getFirstFirstChild(); key != null; key = key.getNext()) {
                if (key.isStringKey()) {
                  names.add(key.getString());
                }
              }
              return names.build();
            }

            return ImmutableList.of();
          }

          @Nullable
          private String getQualifiedName(@Nullable Node n) {
            if (n == null) {
              return null;
            }

            if (isCall(n, "goog.require")) {
              if (n.getSecondChild() == null || !n.getSecondChild().isString()) {
                return null;
              }
              String name = n.getSecondChild().getString();
              return googModules.containsKey(name)
                  ? googModules.get(name).getId().getCompiledName()
                  : name;
            }

            if (n.isName() || n.isString()) {
              return n.getString();
            }

            if (n.isGetProp()) {
              String lhs = getQualifiedName(n.getFirstChild());
              String rhs = getQualifiedName(n.getSecondChild());
              return lhs + "." + rhs;
            }

            return null;
          }
        });
  }

  private static Map<String, String> extractObjectLitNames(Node node) {
    checkArgument(node.isObjectLit());

    Map<String, String> names = new HashMap<>();
    for (Node key = node.getFirstChild(); key != null; key = key.getNext()) {
      String lhsName = key.getString();
      if (isNullOrEmpty(lhsName)) {
        continue;
      }

      Node rhsNode = key.getFirstChild();
      String rhsName = rhsNode == null ? lhsName : rhsNode.getQualifiedName();
      if (isNullOrEmpty(rhsName)) {
        continue;
      }

      names.put(lhsName, rhsName);
    }

    return names;
  }
}
