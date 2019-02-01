/*
Copyright 2013-2018 Jason Leyba

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

import static com.github.jsdossier.jscomp.Nodes.getGoogScopeBlock;
import static com.github.jsdossier.jscomp.Nodes.isCall;
import static com.github.jsdossier.jscomp.Nodes.isExprResult;
import static com.github.jsdossier.jscomp.Nodes.isGoogProvideCall;
import static com.github.jsdossier.jscomp.Nodes.isGoogRequireCall;
import static com.github.jsdossier.jscomp.Nodes.isGoogScopeCall;
import static com.github.jsdossier.jscomp.Nodes.isGoogSetTestOnly;
import static com.github.jsdossier.jscomp.Nodes.isModuleBody;
import static com.github.jsdossier.jscomp.Nodes.isName;
import static com.github.jsdossier.jscomp.Nodes.isObjectLit;
import static com.github.jsdossier.jscomp.Nodes.isRequireCall;
import static com.github.jsdossier.jscomp.Nodes.isString;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static java.nio.file.Files.isDirectory;

import com.github.jsdossier.annotations.Global;
import com.github.jsdossier.annotations.IncludeTestOnly;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;

final class BuildSymbolTablePass implements DossierCompilerPass {

  private static final Logger log = Logger.getLogger(BuildSymbolTablePass.class.getName());

  private final FileSystem fs;
  private final SymbolTable globalSymbolTable;
  private final ImmutableSet<Path> modulePaths;
  private final NodeLibrary nodeLibrary;
  private final TypeRegistry typeRegistry;
  private final boolean includeTestOnly;

  @Nullable private Node export;

  @Inject
  BuildSymbolTablePass(
      @Input FileSystem fs,
      @Modules ImmutableSet<Path> modulePaths,
      @Global SymbolTable globalSymbolTable,
      TypeRegistry typeRegistry,
      NodeLibrary nodeLibrary,
      @IncludeTestOnly boolean includeTestOnly) {
    this.fs = fs;
    this.modulePaths = modulePaths;
    this.globalSymbolTable = globalSymbolTable;
    this.nodeLibrary = nodeLibrary;
    this.typeRegistry = typeRegistry;
    this.includeTestOnly = includeTestOnly;
  }

  @Override
  public void process(DossierCompiler compiler, Node root) {
    if (root.isFromExterns() || nodeLibrary.isModulePath(root.getSourceFileName())) {
      return;
    }
    checkArgument(root.isScript(), "process called with non-script node: %s", root);

    if (!includeTestOnly && scanForTestOnly(root)) {
      log.fine("Skipping test only file: " + root.getSourceFileName());
      return;
    }

    Module.Builder module = scanModule(root);
    SymbolTable table = module == null ? globalSymbolTable : module.getInternalSymbolTable();
    scan(module, table, root);

    if (module != null) {
      Module m = module.build();
      globalSymbolTable.add(m);
      typeRegistry.addModule(m);
    }
  }

  private boolean scanForTestOnly(Node n) {
    switch (n.getToken()) {
      case SCRIPT:
      case MODULE_BODY:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if (scanForTestOnly(child)) {
            return true;
          }
        }
        return false;

      case EXPR_RESULT:
        return isGoogSetTestOnly(n.getFirstChild());

      default:
        return false;
    }
  }

  @Nullable
  private Module.Builder scanModule(Node root) {
    final Path path = fs.getPath(root.getSourceFileName());
    if (isModuleBody(root.getFirstChild())) {
      Module.Builder builder = createModule(path, Module.Type.ES6, root);
      for (Node node = root.getFirstFirstChild(); node != null; node = node.getNext()) {
        if (isGoogModuleCall(node)) {
          assert node.getFirstChild() != null; // Implied by if above.
          checkState(
              node.getFirstChild().getSecondChild() != null, "invalid goog.module call: %s", node);

          String id = node.getFirstChild().getSecondChild().getString();
          builder.setId(Module.Type.CLOSURE.newId(id, path));
        }

        if (isGoogModuleDeclareLegacyNamespaceCall(node)
            // Paranoia checks
            && Module.Type.CLOSURE.equals(builder.getType())) {
          builder.setHasLegacyNamespace(true).setId(builder.getId().toLegacyId());
        }
      }
      return builder;
    } else if (modulePaths.contains(path) || nodeLibrary.isModulePath(root.getSourceFileName())) {
      return createModule(path, Module.Type.NODE, root);
    } else {
      return null;
    }
  }

  private Module.Builder createModule(Path path, Module.Type type, Node script) {
    checkArgument(script.isScript(), "not a script node: %s", script);
    return Module.builder()
        .setId(type.newId(path))
        .setJsDoc(JsDoc.from(script.getJSDocInfo())) // TODO: don't wrap.
        .setAliases(AliasRegion.forFile(path)) // TODO: remove
        .setRoot(script)
        .setInternalSymbolTable(globalSymbolTable.newChildTable(script));
  }

  private void scan(@Nullable Module.Builder module, SymbolTable table, Node n) {
    switch (n.getToken()) {
      case SCRIPT:
      case MODULE_BODY:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          scan(module, table, child);
        }
        break;

      case CLASS:
      case FUNCTION:
        visitDeclaration(module, table, n, nullToEmpty(getQualifiedName(n.getFirstChild())), null);
        break;

      case VAR:
      case LET:
      case CONST:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          visitVarDeclaration(module, table, child);
        }
        break;

      case EXPR_RESULT:
        if (n.getFirstChild() != null) {
          Node child = n.getFirstChild();
          if (isGoogProvideCall(child)) {
            String name = checkNotNull(child.getSecondChild()).getString();
            table.add(
                Symbol.builder(fs, n)
                    .setName(name)
                    .setGoogProvide(true)
                    .setGoogProvideOnly(true)
                    .build());

          } else if (isGoogScopeCall(child)) {
            checkState(module == null, "goog.scope encountered in a module: %s", child);
            checkState(
                table == globalSymbolTable, "expected current table to be global symbol table");
            SymbolTable googScopeTable = table.newGoogScopeTable(child);

            Node block = getGoogScopeBlock(child);
            for (Node blockChild = block.getFirstChild();
                blockChild != null;
                blockChild = blockChild.getNext()) {
              scan(null, googScopeTable, blockChild);
            }

          } else if (child.isAssign()) {
            visitAssignment(module, table, child);

          } else if (child.isGetProp()
              && isName(child.getFirstChild())
              && child.getJSDocInfo() != null
              && child.getJSDocInfo().getTypedefType() != null) {
            visitDeclaration(module, table, child, child.getQualifiedName(), null);
          }
        }
        break;

      case EXPORT:
        checkState(export == null);
        export = n;
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          scan(module, table, child);
        }
        export = null;
        break;

      case EXPORT_SPECS:
        checkState(export != null);
        checkState(module != null, "module should not be null!");

        Module.Id fromModuleId = null;
        if (n.getParent() != null
            && n.getParent().isExport() // Expected
            && isString(n.getParent().getLastChild())) {
          fromModuleId = es6ModuleId(n.getParent().getLastChild());
        }

        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          checkState(child.isExportSpec(), "expected EXPORT_SPEC: %s", child);
          visitExportSpec(module, table, child, fromModuleId);
        }
        break;

      case NAME:
        checkState(export != null);
        visitDeclaration(module, table, n, n.getQualifiedName(), null);
        break;

      case IMPORT:
        checkState(module != null);
        visitImport(table, n);
        break;

      default:
        break;
    }
  }

  private void visitAssignment(
      @Nullable Module.Builder module, final SymbolTable table, final Node n) {
    checkArgument(n.isAssign(), "not an assignment node: %s", n);

    String lhsName = getQualifiedName(n.getFirstChild());
    if (lhsName == null || shouldIgnoreName(lhsName)) {
      return;
    }

    if (module != null
        && module.isNode()
        && ("module.exports".equals(lhsName) || lhsName.startsWith("module.exports."))) {
      lhsName = lhsName.substring("module.".length());
    }

    String rhsName = getQualifiedName(n.getSecondChild());
    visitDeclaration(module, table, n, lhsName, rhsName);

    if (module != null
        && (module.isNode() || module.isClosure())
        && "exports".equals(lhsName)
        && isObjectLit(n.getSecondChild())) {

      for (Node key = n.getSecondChild().getFirstChild();
          key != null && key.isStringKey();
          key = key.getNext()) {
        String referenceName = null;
        if (key.getFirstChild() != null
            && (key.getFirstChild().isName()
                || (key.getFirstChild().isGetProp() && isName(key.getFirstFirstChild())))) {
          referenceName = key.getFirstChild().getQualifiedName();
        }

        while (referenceName != null) {
          Symbol ref = table.getSlot(referenceName);
          if (ref == null || ref.getReferencedSymbol() == null) {
            break;
          }
          referenceName = ref.getReferencedSymbol();
        }

        globalSymbolTable.add(
            Symbol.builder(fs, key)
                .setName(module.getId() + "." + key.getString())
                .setReferencedSymbol(referenceName)
                .setJSDocInfo(key.getJSDocInfo())
                .build());
      }
    }
  }

  private void visitDeclaration(
      @Nullable Module.Builder module,
      final SymbolTable table,
      final Node n,
      String name,
      @Nullable String referencedName) {
    checkState(module == null || table == module.getInternalSymbolTable());
    checkArgument(
        referencedName == null || !referencedName.isEmpty(),
        "referenceName must be null or a non-empty string");

    Symbol.Builder baseSymbol =
        Symbol.builder(fs, n).setName(name).setReferencedSymbol(referencedName);

    if (export != null) {
      checkState(module != null && module.isEs6());

      if (name.isEmpty() && export.getBooleanProp(Node.EXPORT_DEFAULT)) {
        globalSymbolTable.add(Symbol.builder(fs, n).setName(module.getId() + ".default").build());
        if (referencedName != null) {
          module.exportedNamesBuilder().put("default", referencedName);
        }
        return;

      } else {
        String compiledInternalName = module.getId().getContentsVar(name);
        String qualifiedName =
            module.getId() + "." + (export.getBooleanProp(Node.EXPORT_DEFAULT) ? "default" : name);

        baseSymbol.setReferencedSymbol(compiledInternalName);
        module
            .exportedNamesBuilder()
            .put(
                export.getBooleanProp(Node.EXPORT_DEFAULT) ? "default" : baseSymbol.getName(),
                firstNonNull(referencedName, baseSymbol.getName()));

        globalSymbolTable.add(
            Symbol.builder(fs, n)
                .setName(compiledInternalName)
                .setReferencedSymbol(referencedName)
                .build());
        globalSymbolTable.add(
            Symbol.builder(fs, n)
                .setName(qualifiedName)
                .setReferencedSymbol(compiledInternalName)
                .build());
      }

    } else if (module != null) {
      if (!module.isEs6() && ("exports".equals(name) || name.startsWith("exports."))) {
        String globalName =
            "exports".equals(name)
                ? module.getId().toString()
                : module.getId() + name.substring("exports".length());

        if (name.startsWith("exports.") && referencedName != null) {
          String exportedName = name.substring("exports.".length());
          module.exportedNamesBuilder().put(exportedName, referencedName);
        }

        baseSymbol.setReferencedSymbol(globalName);
        globalSymbolTable.add(
            Symbol.builder(fs, n).setName(globalName).setReferencedSymbol(referencedName).build());

      } else {
        String internalName = module.getId().getContentsVar(name);
        baseSymbol.setReferencedSymbol(internalName);
        globalSymbolTable.add(
            Symbol.builder(fs, n)
                .setName(internalName)
                .setReferencedSymbol(referencedName)
                .build());
      }

    } else if (table == globalSymbolTable && isGoogProvideDefinedName(table, name)) {
      table.replace(baseSymbol.build());
      return;

    } else if (table.isGoogScope()) {
      visitGoogScopeDeclaration(table, baseSymbol);
      return;
    }

    table.add(baseSymbol.build());
  }

  private void visitGoogScopeDeclaration(SymbolTable table, Symbol.Builder symbol) {
    if (symbol.getReferencedSymbol() != null) {
      table.add(symbol.build());
      return;
    }

    // If the declaration is not a reference to something else (i.e. the RHS is not another name),
    // then it must implicitly declare a global variable. Figure out what that variable is and
    // record the alias in the goog scope table, and the implied global in the global table.
    String name = symbol.getName();
    String parent = resolveGlobalParent(table, name);
    if (parent != null) {
      String reference = parent + "." + baseName(name);

      // If there is no hidden declaration, just record the symbol in the global table.
      if (reference.equals(name)) {
        globalSymbolTable.add(symbol.build());
        return;
      }

      globalSymbolTable.add(symbol.setName(reference).build());
      symbol.setName(name).setReferencedSymbol(reference);
    }

    table.add(symbol.build());
  }

  private static String baseName(String name) {
    int index = name.lastIndexOf('.');
    return index == -1 ? name : name.substring(index + 1);
  }

  @Nullable
  @CheckReturnValue
  private static String resolveGlobalParent(SymbolTable table, String name) {
    checkArgument(table.isGoogScope(), "not a goog.scope symbol table");
    int index = name.lastIndexOf('.');
    if (index == -1) {
      return null;
    }
    Symbol parent = table.getSlot(name.substring(0, index));
    while (parent != null) {
      if (parent.getReferencedSymbol() == null) {
        return parent.getName();
      }
      Symbol gp = table.getSlot(parent.getReferencedSymbol());
      if (gp == null) {
        return parent.getReferencedSymbol();
      }
      parent = gp;
    }
    return null;
  }

  private static boolean isGoogProvideDefinedName(SymbolTable table, String name) {
    Symbol s = table.getOwnSlot(name);
    return s != null && s.isGoogProvide();
  }

  private void visitVarDeclaration(@Nullable Module.Builder module, SymbolTable table, Node lhs) {
    if (lhs.isName()) {
      String name = lhs.getQualifiedName();

      Node rhs = lhs.getFirstChild();
      String rhsName;
      if (module != null && module.isClosure() && isGoogRequireCall(rhs)) {
        assert rhs.getSecondChild() != null;
        String id = rhs.getSecondChild().getString();
        rhsName = Module.Type.CLOSURE.newId(id, module.getPath()).toString();
      } else if (module != null && module.isNode() && isRequireCall(rhs)) {
        assert rhs.getSecondChild() != null;
        rhsName = resolveNodeModulePath(module, rhs.getSecondChild().getString());
      } else {
        rhsName = getQualifiedName(rhs);
      }
      visitDeclaration(module, table, lhs, name, rhsName);

    } else if (lhs.isDestructuringLhs() && lhs.getFirstChild() != null) {
      switch (lhs.getFirstChild().getToken()) {
        case ARRAY_PATTERN:
          for (Node item = lhs.getFirstFirstChild();
              item != null && item.isName();
              item = item.getNext()) {
            visitDeclaration(module, table, item, item.getQualifiedName(), null);
          }
          break;

        case OBJECT_PATTERN:
          String baseName;
          if (module != null && module.isClosure() && isGoogRequireCall(lhs.getSecondChild())) {
            assert lhs.getSecondChild().getSecondChild() != null;
            String id = lhs.getSecondChild().getSecondChild().getString();
            baseName = Module.Type.CLOSURE.newId(id, module.getPath()).toString();
          } else if (module != null && module.isNode() && isRequireCall(lhs.getSecondChild())) {
            assert lhs.getSecondChild().getSecondChild() != null;
            baseName =
                resolveNodeModulePath(module, lhs.getSecondChild().getSecondChild().getString());
          } else {
            baseName = getQualifiedName(lhs.getSecondChild());
          }

          if (baseName != null) {
            baseName += ".";
          }

          for (Node key = lhs.getFirstFirstChild();
              key != null && key.isStringKey();
              key = key.getNext()) {
            String name = key.getString();
            visitDeclaration(module, table, key, name, baseName == null ? null : baseName + name);
          }
          break;

        default:
          break; // Don't know how to handle this case.
      }
    }
  }

  @Nullable
  private String resolveNodeModulePath(Module.Builder currentModule, String rawPath) {
    if (rawPath.isEmpty()) {
      return null; // TODO(jleyba): report this as a compiler error.
    }

    if (rawPath.startsWith(".") || rawPath.startsWith("/")) {
      Path path = currentModule.getPath().resolveSibling(rawPath).normalize();
      if (rawPath.endsWith("/")
          || (isDirectory(path)
              && !rawPath.endsWith(".js")
              && !Files.exists(path.resolveSibling(path.getFileName() + ".js")))) {
        path = path.resolve("index.js");
      }
      return Module.Type.NODE.newId(path).toString();

    } else if (nodeLibrary.canRequireId(rawPath)) {
      return nodeLibrary.normalizeRequireId(rawPath);
    }

    return null;
  }

  @Nullable
  private static String getQualifiedName(@Nullable Node node) {
    if (node != null && (node.isName() || node.isGetProp())) {
      return node.getQualifiedName();
    }
    return null;
  }

  private void visitExportSpec(
      Module.Builder module, SymbolTable table, Node spec, @Nullable Module.Id fromModuleId) {
    checkState(module != null && module.isEs6() && module.getInternalSymbolTable() == table);
    checkState(spec.getFirstChild() != null && spec.getFirstChild().isName());
    checkState(spec.getSecondChild() != null && spec.getSecondChild().isName());

    String exportedName =
        checkNotNull(spec.getSecondChild().getQualifiedName(), spec.getSecondChild());
    String internalName =
        checkNotNull(spec.getFirstChild().getQualifiedName(), spec.getFirstChild());

    String referenceName;
    if (fromModuleId == null) {
      module.exportedNamesBuilder().put(exportedName, internalName);
      referenceName = module.getId().getContentsVar(internalName);
    } else {
      referenceName = fromModuleId + "." + internalName;
      module.exportedNamesBuilder().put(exportedName, referenceName);
    }

    String qualifiedExportName = module.getId() + "." + exportedName;
    globalSymbolTable.add(
        Symbol.builder(fs, spec)
            .setName(qualifiedExportName)
            .setReferencedSymbol(referenceName)
            .build());
  }

  private void visitImport(SymbolTable table, Node importNode) {
    checkState(
        isString(importNode.getLastChild()),
        "expect last to be a string: %s",
        importNode.getLastChild());
    Module.Id fromModuleId = es6ModuleId(importNode.getLastChild());

    if (isName(importNode.getFirstChild())) {
      table.add(
          Symbol.builder(fs, importNode)
              .setName(importNode.getFirstChild().getQualifiedName())
              .setReferencedSymbol(fromModuleId + ".default")
              .build());
    }

    if (importNode.getSecondChild() != null && importNode.getSecondChild().isImportStar()) {
      table.add(
          Symbol.builder(fs, importNode)
              .setName(importNode.getSecondChild().getString())
              .setReferencedSymbol(fromModuleId.toString())
              .build());
    }

    if (importNode.getSecondChild() != null && importNode.getSecondChild().isImportSpecs()) {
      for (Node spec = importNode.getSecondChild().getFirstChild();
          spec != null;
          spec = spec.getNext()) {
        checkState(spec.getFirstChild() != null);
        checkState(spec.getSecondChild() != null);
        table.add(
            Symbol.builder(fs, importNode)
                .setName(spec.getSecondChild().getQualifiedName())
                .setReferencedSymbol(fromModuleId + "." + spec.getFirstChild().getQualifiedName())
                .build());
      }
    }
  }

  private Module.Id es6ModuleId(Node string) {
    checkArgument(string.isString());
    Path path =
        fs.getPath(string.getSourceFileName()).resolveSibling(string.getString()).normalize();
    return Module.Type.ES6.newId(path);
  }

  private static boolean shouldIgnoreName(@Nullable String name) {
    return isNullOrEmpty(name) || name.endsWith(".prototype") || name.contains(".prototype.");
  }

  private static boolean isGoogModuleCall(Node n) {
    return isExprResult(n) && isCall(n.getFirstChild(), "goog.module");
  }

  private static boolean isGoogModuleDeclareLegacyNamespaceCall(Node n) {
    return isExprResult(n) && isCall(n.getFirstChild(), "goog.module.declareLegacyNamespace");
  }
}
