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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
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

  @VisibleForTesting
  static String nameToId(String name) {
    // See transformations in
    // com.google.javascript.jscomp.ClosureRewriteModule
    return "module$exports$" + name.replace('.', '$');
  }

  private String toContentsVar(Module.Builder module, String varName) {
    return "module$contents$"
        + module.getId().substring("module$exports$".length())
        + "_" + varName;
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

  private static boolean isCall(Node n, String name) {
    return n.isCall()
        && n.getFirstChild().matchesQualifiedName(name)
        && n.getSecondChild().isString();
  }

  @Override
  public void process(Node ignored, Node root) {
    final Map<Node, Module.Builder> blockToModule = new HashMap<>();
    final Set<String> googModules = new HashSet<>();
    final Set<String> moduleFiles = new HashSet<>();

    traverseEs6(compiler, root, new AbstractShallowCallback() {
      private Module.Builder module;

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (isCall(n, "goog.module") && module == null) {
          verify(n.getSecondChild().isString());
          googModules.add(n.getSecondChild().getString());
          moduleFiles.add(n.getSourceFileName());

          Path path = inputFs.getPath(n.getSourceFileName());
          Module.Type type = nodeModules.contains(path) ? Module.Type.NODE : Module.Type.CLOSURE;
          String name = n.getSecondChild().getString();

          module = Module.builder()
              .setId(nameToId(name))
              .setOriginalName(type == Module.Type.CLOSURE ? name : n.getSourceFileName())
              .setPath(path)
              .setAliases(AliasRegion.forFile(path))
              .setType(type);
        }

        if (n.isCall()
            && "goog.module.declareLegacyNamespace".equals(n.getFirstChild().getQualifiedName())
            && module != null
            && module.getType() == Module.Type.CLOSURE) {
          module.setId(module.getOriginalName());
          module.setHasLegacyNamespace(true);
        }

        if (n.isModuleBody() && module != null) {
          blockToModule.put(n, module);
          module = null;
        }
      }
    });

    traverseEs6(compiler, root, new NodeTraversal.AbstractShallowCallback() {

      private final Set<Node> exportAssignments = new HashSet<>();
      private final Map<String, JSDocInfo> internalDocs = new HashMap<>();
      private final Map<String, String> localAliases = new HashMap<>();
      private final Map<String, String> internalAliases = new HashMap<>();

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (isTopLevelAssign(n)) {
          String name = n.getFirstChild().getQualifiedName();
          if (!isNullOrEmpty(name) && ("exports".equals(name) || name.startsWith("exports."))) {
            exportAssignments.add(n);
          }
        }

        if (n.isModuleBody()) {
          visitModuleBody(n);
          return;
        }

        if (n.isClass() && isModuleBlock(parent)) {
          String name = n.getFirstChild().getQualifiedName();
          if (n.getJSDocInfo() != null) {
            internalDocs.put(name, n.getJSDocInfo());
          }
        }

        if (n.isFunction() && isModuleBlock(parent)) {
          String name = n.getFirstChild().getQualifiedName();
          if (n.getJSDocInfo() != null) {
            internalDocs.put(name, n.getJSDocInfo());
          }
        }

        if (NodeUtil.isNameDeclaration(n) && isModuleBlock(parent)) {
          visitDeclaration(n);
        }

        if (isCall(n, "goog.provide")) {
          typeRegistry.recordProvide(n.getSecondChild().getString());
        }
      }

      private void visitDeclaration(Node decl) {
        // Don't do anything special if we're not in a module file.
        if (!moduleFiles.contains(decl.getSourceFileName())) {
          return;
        }

        Node lhs = decl.getFirstChild();
        Node rhs = null;
        if (lhs.isName()) {
          rhs = lhs.getFirstChild();

          // While we're here, record the JSDocs for the lhs.
          if (decl.getJSDocInfo() != null) {
            internalDocs.put(lhs.getString(), decl.getJSDocInfo());
          }
        } else if (lhs.isDestructuringLhs() && lhs.getFirstChild().isObjectPattern()) {
          rhs = lhs.getSecondChild();
        }

        if (rhs == null) {
          return;
        }

        String rhsName = getQualifiedName(rhs);
        if (isNullOrEmpty(rhsName)) {
          return;
        }

        List<String> lhsNames = getLhsNames(lhs);
        if (lhsNames.size() == 1) {
          if (Types.isInternalVar(lhsNames.get(0))) {
            internalAliases.put(lhsNames.get(0), rhsName);
          } else {
            localAliases.put(lhsNames.get(0), rhsName);
          }
        } else {
          // Destructuring case:
          //    var {One, Two, Three} = goog.require('foo');
          for (String name : lhsNames) {
            localAliases.put(name, rhsName + "." + name);
          }
        }
      }

      @Nullable
      private String getQualifiedName(Node n) {
        if (isCall(n, "goog.require")) {
          String name = n.getSecondChild().getString();
          if (googModules.contains(name)) {
            name = nameToId(name);
          }
          return name;
        }

        if (n.isName() || n.isString()) {
          String name = n.getString();
          if (internalAliases.containsKey(name)) {
            name = internalAliases.get(name);
          }
          return name;
        }

        if (n.isGetProp()) {
          String lhs = getQualifiedName(n.getFirstChild());
          String rhs = getQualifiedName(n.getSecondChild());
          return lhs + "." + rhs;
        }

        return null;
      }

      private List<String> getLhsNames(Node lhs) {
        if (lhs.isName()) {
          return ImmutableList.of(lhs.getQualifiedName());
        } else if (lhs.isDestructuringLhs() && lhs.getFirstChild().isObjectPattern()) {
          Node obj = lhs.getFirstChild();
          List<String> names = new ArrayList<>();
          for (Node key = obj.getFirstChild(); key != null; key = key.getNext()) {
            if (key.isStringKey()) {
              names.add(key.getString());
            }
          }
          return names;
        }
        return ImmutableList.of();
      }

      private void visitModuleBody(Node body) {
        checkArgument(body.isModuleBody(), "must be a module body node: %s", body);
        checkArgument(body.getParent().isScript(),
            "parent is not a script node: %s", body.getParent());

        Module.Builder module = blockToModule.get(body);
        if (module == null) {
          exportAssignments.clear();
          internalDocs.clear();
          localAliases.clear();
          return;
        }

        Map<String, String> exportedNames = new HashMap<>();
        for (Node ref : exportAssignments) {
          if (ref.getLastChild().isObjectLit()) {
            if (ref.getFirstChild().isGetProp()) {
              continue;  // Ignore: exports.NAME = {...}
            }

            Map<String, String> names = extractObjectLitNames(ref.getLastChild());
            exportedNames.putAll(names);
            continue;
          }

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

        module.setJsDoc(JsDoc.from(body.getParent().getJSDocInfo()))
            .setExportedNames(ImmutableMap.copyOf(exportedNames))
            .setInternalVarDocs(ImmutableMap.copyOf(internalDocs));

        for (Map.Entry<String, String> alias : localAliases.entrySet()) {
          module.getAliases().addAlias(alias.getKey(), alias.getValue());
          module.getAliases().addAlias(
              toContentsVar(module, alias.getKey()),
              alias.getValue());
        }

        Module m = module.build();
        typeRegistry.addAliasRegion(m.getAliases());
        if (!nodeLibrary.isModuleId(m.getId())) {
          typeRegistry.addModule(m);
        }
        exportAssignments.clear();
        internalDocs.clear();
        localAliases.clear();
        blockToModule.remove(body);
      }

      private boolean isModuleBlock(Node n) {
        return n != null && blockToModule.containsKey(n);
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

  private static boolean isTopLevelAssign(Node n) {
    return n.isAssign()
        && n.getParent().isExprResult()
        && n.getGrandparent().isModuleBody();
  }
}
