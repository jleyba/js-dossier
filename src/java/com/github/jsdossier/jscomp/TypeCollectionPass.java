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

import static com.github.jsdossier.jscomp.Types.isBuiltInFunctionProperty;
import static com.github.jsdossier.jscomp.Types.isConstructorTypeDefinition;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static java.util.Comparator.comparing;

import com.github.jsdossier.annotations.Global;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.ModuleFilter;
import com.google.common.base.Splitter;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.TypedScope;
import com.google.javascript.jscomp.TypedVar;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.NoType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.ProxyObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Compiler pass responsible for collecting the types to be documented. */
public final class TypeCollectionPass implements CompilerPass {

  private static final String INTERNAL_NAMESPACE_VAR = "$jscomp";
  private static final String MODULE_ID_PREFIX = "module$exports$";
  private static final String MODULE_CONTENTS_PREFIX = "module$contents$";
  private static final Logger log = Logger.getLogger(TypeCollectionPass.class.getName());

  private final DossierCompiler compiler;
  private final TypeRegistry typeRegistry;
  private final FileSystem inputFs;
  private final Predicate<Path> modulePathFilter;
  private final NodeLibrary nodeLibrary;
  private final SymbolTable symbolTable;

  @Inject
  TypeCollectionPass(
      DossierCompiler compiler,
      TypeRegistry typeRegistry,
      @Input FileSystem inputFs,
      @ModuleFilter Predicate<Path> modulePathFilter,
      NodeLibrary nodeLibrary,
      @Global SymbolTable symbolTable) {
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
    this.modulePathFilter = modulePathFilter;
    this.nodeLibrary = nodeLibrary;
    this.symbolTable = symbolTable;
  }

  @Override
  public void process(Node externsRoot, Node root) {
    if (compiler.getErrorCount() > 0) {
      return;
    }

    new TypeCollector(externsRoot).collectTypes(compiler.getTopScope());

    // Check for known modules that did not register as a type. These are modules that import
    // others, but have no exports of their own.
    for (Module module : typeRegistry.getAllModules()) {
      if (modulePathFilter.test(module.getPath())) {
        continue;
      }
      if (!module.isClosure() && !typeRegistry.isType(module.getId().getCompiledName())) {
        typeRegistry.addType(
            NominalType.builder()
                .setName(module.getId().getCompiledName())
                .setType(compiler.getTypeRegistry().getNativeType(JSTypeNative.VOID_TYPE))
                .setSourceFile(module.getPath())
                .setNode(module.getRoot())
                .setJsDoc(module.getJsDoc())
                .setModule(module)
                .build());
      }
    }

    typeRegistry.collectModuleContentAliases(compiler.getTypeRegistry());
  }

  private static void logfmt(String msg, Object... args) {
    if (log.isLoggable(Level.FINE)) {
      log.fine(String.format(msg, args));
    }
  }

  private static final class ExternCollector implements Visitor<Object> {
    private final Set<JSType> externs;

    private ExternCollector(Set<JSType> externs) {
      this.externs = externs;
    }

    public void crawl(JSType type) {
      type.visit(this);
      if ((type.isNominalType() && !type.isInstanceType())
          || type.isNominalConstructor()
          || type.isEnumType()) {
        externs.add(type);
      }
    }

    @Override
    public Object caseFunctionType(FunctionType type) {
      for (String name : type.getOwnPropertyNames()) {
        if (!isBuiltInFunctionProperty(type, name)) {
          crawl(type.getPropertyType(name));
        }
      }
      return null;
    }

    @Override
    public Object caseObjectType(ObjectType type) {
      if (type.isGlobalThisType()) {
        return null;
      }

      for (String name : type.getOwnPropertyNames()) {
        if (type.getPropertyType(name).isEnumElementType()) {
          continue;
        }
        crawl(type.getPropertyType(name));
      }
      return null;
    }

    @Override
    public Object caseProxyObjectType(ProxyObjectType type) {
      return null;
    }

    @Override
    public Object caseNoType(NoType type) {
      return null;
    }

    @Override
    public Object caseEnumElementType(EnumElementType type) {
      return null;
    }

    @Override
    public Object caseAllType() {
      return null;
    }

    @Override
    public Object caseBooleanType() {
      return null;
    }

    @Override
    public Object caseNoObjectType() {
      return null;
    }

    @Override
    public Object caseUnknownType() {
      return null;
    }

    @Override
    public Object caseNullType() {
      return null;
    }

    @Override
    public Object caseNamedType(NamedType type) {
      return null;
    }

    @Override
    public Object caseNumberType() {
      return null;
    }

    @Override
    public Object caseStringType() {
      return null;
    }

    @Override
    public Object caseSymbolType() {
      return null;
    }

    @Override
    public Object caseVoidType() {
      return null;
    }

    @Override
    public Object caseUnionType(UnionType type) {
      return null;
    }

    @Override
    public Object caseTemplatizedType(TemplatizedType type) {
      return null;
    }

    @Override
    public Object caseTemplateType(TemplateType templateType) {
      return null;
    }
  }

  private final class TypeCollector {

    private final Node externsRoot;
    private final Set<JSType> externTypes = new HashSet<>();

    private TypeCollector(Node externsRoot) {
      this.externsRoot = externsRoot;
    }

    private boolean isExtern(Node node) {
      while (node != null) {
        if (node == externsRoot) {
          return true;
        }
        node = node.getParent();
      }
      return false;
    }

    public void collectTypes(TypedScope topScope) {
      checkArgument(topScope.isGlobal());

      externTypes.clear();

      // Pass 1: identify which types are externs and which are user-defined.
      ExternCollector externCollector = new ExternCollector(externTypes);
      for (TypedVar var : topScope.getAllSymbols()) {
        String name = var.getName();
        if (name.contains(".")) {
          continue;
        }

        if (isExtern(var.getParentNode())) {
          if (var.getType() != null) {
            externCollector.crawl(var.getType());
          }
          continue;
        }

        Node node = var.getNameNode();
        if (node.getSourceFileName() != null
            && nodeLibrary.isModulePath(node.getSourceFileName())) {
          if (name.startsWith(MODULE_ID_PREFIX)) {
            String id = name.substring(MODULE_ID_PREFIX.length());
            if (nodeLibrary.canRequireId(id)) {
              logfmt("Recording core node module as an extern: %s", id);
              externTypes.add(node.getJSType());
              continue;
            }
          }

          Path file = inputFs.getPath(node.getSourceFileName());
          if (name.startsWith(MODULE_CONTENTS_PREFIX)) {
            logfmt("Recording extern from node extern: %s", name);
            externTypes.add(node.getJSType());
            String id = nodeLibrary.getIdFromPath(node.getSourceFileName());
            recordModuleContentsAlias(file, stripContentsPrefix(id, name), name);
            continue;
          }

          throw new AssertionError("unexpected case in " + file + " (" + name + ")");
        }
      }

      // Pass 2: process the user defined types.
      symbolTable
          .getAllSymbols()
          .stream()
          .sorted(
              comparing(Symbol::getReferencedSymbol, this::nullReferencesFirst)
                  .thenComparing(Symbol::getScope, this::globalGoogScopeThenModules)
                  .thenComparing(this::referencesBeforeAliases)
                  .thenComparing(Symbol::getName))
          .map(this::resolveGoogScopeSymbol)
          .forEach(this::processSymbol);
    }

    private void processSymbol(Symbol symbol) {
      final JSType globalThis =
          compiler.getTypeRegistry().getNativeObjectType(JSTypeNative.GLOBAL_THIS);

      if (typeRegistry.isType(symbol.getName())) {
        return;
      }

      JSType type = null;
      boolean isFromCompilerRegistry = false;

      if (symbol.getReferencedSymbol() != null
          && typeRegistry.isType(symbol.getReferencedSymbol())) {
        type = typeRegistry.getType(symbol.getReferencedSymbol()).getType();
      }

      if (type == null) {
        type = compiler.getTypeRegistry().getGlobalType(symbol.getName());
        isFromCompilerRegistry = type != null;
      }

      if (type == null) {
        type = globalThis;
        for (String part : Splitter.on('.').split(symbol.getName())) {
          type = type.findPropertyType(part);
          if (type == null) {
            return;
          }
        }
      }

      if (type != null) {
        if (type.isInstanceType()
            && (isFromCompilerRegistry
                || shouldConvertToConstructor(type)
                || shouldConvertToConstructor(symbol))) {
          type = type.toObjectType().getConstructor();
        } else if (type.isEnumElementType()) {
          type = type.toMaybeEnumElementType().getEnumType();
        }
      }

      if (type == null) {
        log.warning("skipping " + symbol.getName() + "; no type");
        return;
      } else if (globalThis.equals(type)) {
        log.warning("skipping " + symbol.getName() + "; references global this");
        return;
      } else if (type.isOrdinaryFunction() && !typeRegistry.isProvided(symbol.getName())) {
        log.warning("skipping " + symbol.getName() + "; is ordinary function");
        return;
      }
      collectTypes(symbol.getName(), type, symbol.getNode(), symbol.getJSDocInfo());
    }

    private Symbol resolveGoogScopeSymbol(final Symbol s) {
      SymbolTable table = s.getScope();
      if (table == null || !table.isGoogScope()) {
        return s;
      }

      // In a goog.scope() block, if this symbol is a reference, find the global symbol it is
      // a reference to. Otherwise, the containing type must be a resolvable reference.
      if (s.getReferencedSymbol() == null) {
        int index = s.getName().lastIndexOf('.');
        if (index == -1) {
          return s; // Abort and try to recover later.
        }

        String name = typeRegistry.resolveAlias(table, s.getName().substring(0, index));
        if (name == null) {
          return s; // Abort and try to recover later.
        }
      }

      Symbol current = s;
      while (current.getReferencedSymbol() != null) {
        Symbol ref = table.getSlot(current.getReferencedSymbol());
        if (ref == null) {
          break;
        }
        current = ref;
      }
      if (current.getReferencedSymbol() == null) {
        logfmt("resolved %s to %s", s, current);
        return current;
      }
      logfmt("resolved %s to synthetic %s", s, current.getReferencedSymbol());
      return s.toBuilder().setName(current.getReferencedSymbol()).setReferencedSymbol(null).build();
    }

    private int globalGoogScopeThenModules(SymbolTable a, SymbolTable b) {
      return Comparator.<SymbolTable>comparingInt(
              st -> {
                if (st.getParentScope() == null) {
                  return 0;
                } else if (st.isGoogScope()) {
                  return 1;
                } else {
                  return 2;
                }
              })
          .compare(a, b);
    }

    private int nullReferencesFirst(@Nullable String a, @Nullable String b) {
      if (a == null) {
        return b == null ? 0 : -1;
      } else if (b == null) {
        return 1;
      } else {
        return 0;
      }
    }

    private int referencesBeforeAliases(Symbol a, Symbol b) {
      if (a.getName().equals(b.getReferencedSymbol())) {
        return -1;
      } else if (b.getName().equals(a.getReferencedSymbol())) {
        return 1;
      } else {
        return 0;
      }
    }

    private boolean shouldConvertToConstructor(JSType type) {
      JSDocInfo info = type.getJSDocInfo();
      return info != null && (info.isInterface() || info.isConstructor());
    }

    private boolean shouldConvertToConstructor(Symbol s) {
      Node n = s.getNode();
      return n != null && (n.isClass() || (n.getJSType() != null && n.getJSType().isConstructor()));
    }

    private void collectTypes(String name, JSType type, Node node, @Nullable JSDocInfo info) {
      if (name.startsWith(INTERNAL_NAMESPACE_VAR)) {
        logfmt("Skipping internal compiler namespace %s", name);
        return;
      } else if (node == null) {
        logfmt("Skipping type without a source node: %s", name);
        return;
      } else if (type.isGlobalThisType()) {
        logfmt("Skipping global this: %s", name);
        return;
      } else if (type.isUnknownType()) {
        logfmt("Skipping unknown type: %s", name);
        return;
      } else if (isExternAlias(type, info)) {
        logfmt("Skipping extern alias: %s", name);
        return;
      } else if (node.getStaticSourceFile() == null) {
        logfmt("Skipping type from phantom node: %s", name);
        return;
      }

      Path file = inputFs.getPath(node.getSourceFileName());
      if (nodeLibrary.isModulePath(node.getSourceFileName())) {
        if (name.startsWith(MODULE_ID_PREFIX)) {
          String id = name.substring(MODULE_ID_PREFIX.length());
          if (nodeLibrary.canRequireId(id)) {
            logfmt("Recording core node module as an extern: %s", id);
            externTypes.add(node.getJSType());
            return;
          }
        }

        if (name.startsWith(MODULE_CONTENTS_PREFIX)) {
          logfmt("Recording extern from node extern: %s", name);
          externTypes.add(node.getJSType());
          String id = nodeLibrary.getIdFromPath(node.getSourceFileName());
          recordModuleContentsAlias(file, stripContentsPrefix(id, name), name);
          return;
        }

        throw new AssertionError("unexpected case in " + file + " (" + name + ")");
      }

      if (typeRegistry.isModule(file) && name.startsWith(MODULE_CONTENTS_PREFIX)) {
        Module module = typeRegistry.getModule(file);
        if (!module.isEs6()) {
          String id = module.getId().getCompiledName();
          if (id.startsWith(MODULE_ID_PREFIX)) {
            id = id.substring(MODULE_ID_PREFIX.length());
          }
          recordModuleContentsAlias(file, stripContentsPrefix(id, name), name);
          logfmt("Skipping module internal alias: %s", name);
          return;
        }
      }

      if (info == null || isNullOrEmpty(info.getOriginalCommentString())) {
        info = type.getJSDocInfo();
      }

      if (isPrimitive(type)
          && (info == null || (info.getTypedefType() == null && !info.isDefine()))) {
        logfmt("Skipping primitive type assigned to %s: %s", name, node.getJSType());
        return;
      }

      // TODO: globals (functions, typedefs, compiler constants) should be documented together.
      if (info != null && info.isDefine()) {
        logfmt("Skipping global compiler constant: %s", name);
        return;
      }

      Path path = inputFs.getPath(node.getSourceFileName());
      Optional<Module> module = getModule(name, node);
      if (module.isPresent() && modulePathFilter.test(module.get().getPath())) {
        return;
      }

      NominalType nominalType =
          NominalType.builder()
              .setName(name)
              .setType(type)
              .setJsDoc(info)
              .setSourceFile(path)
              .setNode(node)
              .setModule(module)
              .build();

      // If jsdoc is present and says this is not a constructor, we've found a
      // constructor reference, which should not be documented as a unique nominal type:
      //     /** @type {function(new: Foo)} */ var x;
      //     /** @private {function(new: Foo)} */ var x;
      //
      // We do not check jsdoc.isConstructor() since the Closure compiler may create a stub
      // JSDocInfo entry as part of one of its passes, i.e. rewriting a goog.module and an
      // exported property is an internal class:
      //     goog.module('foo');
      //     /** @constructor */
      //     function Internal() {}
      //     exports.Public = Internal;
      //
      // The exception to the rule is if this property is exporting a constructor as part of
      // a CommonJS module's public API - then we document the symbol as a type.
      if (!type.isConstructor()
          || nominalType.isModuleExports()
          || isExportedName(nominalType)
          || isConstructorTypeDefinition(type, nominalType.getJsDoc())) {
        recordType(nominalType);
      }
    }

    /** Returns whether {@code type} is directly exported by a module. */
    private boolean isExportedName(NominalType type) {
      if (type.getModule().isPresent()) {
        String idPrefix = type.getModule().get().getId() + ".";
        String name = type.getName();
        if (name.startsWith(idPrefix)) {
          return name.indexOf('.', idPrefix.length()) == -1;
        }
      }
      return false;
    }

    private boolean isExternAlias(JSType type, JSDocInfo info) {
      if (externTypes.contains(type)) {
        return true;
      }

      // If something is typed as {function(new: Foo)}, it will actually be represented as
      // {function(new: Foo): ?}, which is different than the real constructor. We can get the real
      // constructor, however, with a little type manipulation.
      if (type.isConstructor() && type.toMaybeFunctionType() != null) {
        JSType ctorType = type.toMaybeFunctionType().getInstanceType().getConstructor();
        if (externTypes.contains(ctorType)) {
          return true;
        }
      }

      if (info != null && info.getTypedefType() != null) {
        JSTypeExpression expression = info.getTypedefType();
        type = Types.evaluate(expression, compiler.getTopScope(), compiler.getTypeRegistry());
        return externTypes.contains(type);
      }

      return false;
    }

    private void recordModuleContentsAlias(Path file, String alias, String name) {
      AliasRegion aliases = getModuleAliasRegion(file);
      String existing = aliases.resolveAlias(alias);
      if (existing == null) {
        aliases.addAlias(alias, name);
      }
    }

    private String getModuleContentsPrefix(String moduleId) {
      return MODULE_CONTENTS_PREFIX + moduleId.replace('.', '$') + "_";
    }

    private String stripContentsPrefix(String moduleId, String name) {
      String prefix = getModuleContentsPrefix(moduleId);
      checkArgument(name.startsWith(prefix));
      return name.substring(prefix.length());
    }

    private AliasRegion getModuleAliasRegion(Path file) {
      Collection<AliasRegion> regions = typeRegistry.getAliasRegions(file);
      if (regions.isEmpty()) {
        typeRegistry.addAliasRegion(AliasRegion.forFile(file));
        regions = typeRegistry.getAliasRegions(file);
      }
      return regions.iterator().next();
    }

    private Optional<Module> getModule(String typeName, Node node) {
      Path path = inputFs.getPath(node.getSourceFileName());
      if (!typeRegistry.isModule(path)) {
        return Optional.empty();
      }

      Module module = typeRegistry.getModule(path);
      if (module.getHasLegacyNamespace()) {
        verify(
            module.isClosure(),
            "legacy namespace on non-closure module: %s",
            module.getOriginalName());
        verify(
            module.getOriginalName().equals(module.getId().getCompiledName()),
            "expect legacy module to use original name for compiled name: %s != %s",
            module.getId().getCompiledName(),
            module.getOriginalName());
        return Optional.of(module);
      }

      if (typeRegistry.isImplicitNamespace(typeName)) {
        verify(!typeName.equals(module.getId().getCompiledName()));
        return Optional.empty();
      }
      return Optional.of(module);
    }

    private void recordType(NominalType type) {
      if (externTypes.contains(type.getType()) && !type.getJsDoc().isTypedef()) {
        logfmt("Skipping extern alias: %s", type.getName());
        return;
      }

      if (type.getName().contains("$$")) {
        int index = type.getName().indexOf("$$");
        String id = type.getName().substring(index + 2);
        if (typeRegistry.isModule(id)) {
          Module module = typeRegistry.getModule(id);
          if (module.isEs6()) {
            for (AliasRegion region : typeRegistry.getAliasRegions(type.getSourceFile())) {
              if (region.getRange().contains(type.getSourcePosition())) {
                String alias = type.getName().substring(0, index);
                region.addAlias(alias, type.getName());
              }
            }
          }
          logfmt("Skipping module alias: %s", type.getName());
          return;
        }
      }

      JSType jsType = type.getType();
      if (!type.getModule().isPresent()
          && !jsType.isConstructor()
          && !jsType.isInterface()
          && !jsType.isEnumType()
          && !type.getJsDoc().isTypedef()
          && !type.getJsDoc().isDefine()
          && !typeRegistry.isProvided(type.getName())
          && !typeRegistry.isImplicitNamespace(type.getName())
          && symbolTable.getSlot(type.getName()) == null) {
        logfmt("Ignoring undeclared namespace %s", type.getName());
        return;
      }

      if (!typeRegistry.getTypes(type.getType()).isEmpty()) {
        logfmt(
            "Found type alias: %s = %s",
            type.getName(), typeRegistry.getTypes(type.getType()).iterator().next().getName());
      }
      typeRegistry.addType(type);
    }
  }

  private static boolean isPrimitive(JSType type) {
    return !type.isEnumElementType()
        && (type.isBooleanValueType()
            || type.isBooleanObjectType()
            || type.isNumber()
            || type.isNumberValueType()
            || type.isNumberObjectType()
            || type.isString()
            || type.isStringObjectType()
            || type.isStringValueType()
            || type.isVoidType()
            || type.isArrayType());
  }
}
