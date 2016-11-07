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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.ModuleFilter;
import com.github.jsdossier.annotations.TypeFilter;
import com.github.jsdossier.jscomp.Module.Type;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
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
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.PrototypeObjectType;
import com.google.javascript.rhino.jstype.ProxyObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Compiler pass responsible for collecting the types to be documented.
 */
public final class TypeCollectionPass implements CompilerPass {

  private static final String INTERNAL_NAMESPACE_VAR = "$jscomp";
  private static final String MODULE_ID_PREFIX = "module$exports$";
  private static final String MODULE_CONTENTS_PREFIX = "module$contents$";
  private static final Logger log = Logger.getLogger(TypeCollectionPass.class.getName());

  private final DossierCompiler compiler;
  private final TypeRegistry typeRegistry;
  private final FileSystem inputFs;
  private final Predicate<String> typeNameFilter;
  private final Predicate<Path> modulePathFilter;
  private final NodeLibrary nodeLibrary;

  @Inject
  TypeCollectionPass(
      DossierCompiler compiler,
      TypeRegistry typeRegistry,
      @Input FileSystem inputFs,
      @ModuleFilter Predicate<Path> modulePathFilter,
      @TypeFilter Predicate<String> typeNameFilter,
      NodeLibrary nodeLibrary) {
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
    this.modulePathFilter = modulePathFilter;
    this.typeNameFilter = typeNameFilter;
    this.nodeLibrary = nodeLibrary;
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
      if (modulePathFilter.apply(module.getPath())) {
        continue;
      }
      if (module.getType() != Module.Type.CLOSURE && !typeRegistry.isType(module.getId())) {
        typeRegistry.addType(
            NominalType.builder()
                .setName(module.getId())
                .setType(compiler.getTypeRegistry().getNativeType(JSTypeNative.VOID_TYPE))
                .setSourceFile(module.getPath())
                .setSourcePosition(Position.of(0, 0))
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

  private final class ExternCollector implements Visitor<Object> {
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

    @Override public Object caseProxyObjectType(ProxyObjectType type) { return null; }
    @Override public Object caseNoType(NoType type) { return null; }
    @Override public Object caseEnumElementType(EnumElementType type) { return null; }
    @Override public Object caseAllType() { return null; }
    @Override public Object caseBooleanType() { return null; }
    @Override public Object caseNoObjectType() { return null; }
    @Override public Object caseUnknownType() { return null; }
    @Override public Object caseNullType() { return null; }
    @Override public Object caseNamedType(NamedType type) { return null; }
    @Override public Object caseNumberType() { return null; }
    @Override public Object caseStringType() { return null; }
    @Override public Object caseVoidType() { return null; }
    @Override public Object caseUnionType(UnionType type) { return null; }
    @Override public Object caseTemplatizedType(TemplatizedType type) { return null; }
    @Override public Object caseTemplateType(TemplateType templateType) { return null; }
  }

  private class TypeCollector implements Visitor<Void> {

    private final Node externsRoot;
    private final Set<JSType> externTypes = new HashSet<>();

    private final Deque<NominalType> types = new ArrayDeque<>();

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
      types.clear();

      // Pass 1: identify which types are externs and which are user-defined.
      ExternCollector externCollector = new ExternCollector(externTypes);
      Set<TypedVar> userTypes = new LinkedHashSet<>();
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
            if (nodeLibrary.isModuleId(id)) {
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
        userTypes.add(var);
      }

      // Pass 2: process the user defined types.
      for (TypedVar var : userTypes) {
        String name = var.getName();
        JSType type = var.getType();
        Node node = var.getNameNode();
        JSDocInfo info = var.getJSDocInfo();

        if (type == null) {
          if (info  != null && info.getTypedefType() != null) {
            type = compiler.getTypeRegistry().getNativeType(JSTypeNative.NO_TYPE);
          } else {
            continue;
          }
        }

        if (name.startsWith(INTERNAL_NAMESPACE_VAR)) {
          logfmt("Skipping internal compiler namespace %s", name);
          continue;
        } else if (node == null) {
          logfmt("Skipping type without a source node: %s", name);
          continue;
        } else if (node.getJSType() == null) {
          logfmt("Unable to determine type for %s; skipping", name);
          continue;
        } else if (node.getJSType().isGlobalThisType()) {
          logfmt("Skipping global this: %s", name);
          continue;
        } else if (type.isUnknownType()) {
          logfmt("Skipping unknown type: %s", name);
          continue;
        } else if (isExternAlias(type, info)) {
          logfmt("Skipping extern alias: %s", name);
          continue;
        } else if (node.getStaticSourceFile() == null) {
          logfmt("Skipping type from phantom node: %s", name);
          continue;
        }

        Path file = inputFs.getPath(node.getSourceFileName());
        if (nodeLibrary.isModulePath(node.getSourceFileName())) {
          if (name.startsWith(MODULE_ID_PREFIX)) {
            String id = name.substring(MODULE_ID_PREFIX.length());
            if (nodeLibrary.isModuleId(id)) {
              logfmt("Recording core node module as an extern: %s", id);
              externTypes.add(node.getJSType());
              continue;
            }
          }

          if (name.startsWith(MODULE_CONTENTS_PREFIX)) {
            logfmt("Recording extern from node extern: %s", name);
            externTypes.add(node.getJSType());
            String id = nodeLibrary.getIdFromPath(node.getSourceFileName());
            recordModuleContentsAlias(file, stripContentsPrefix(id, name), name);
            continue;
          }

          throw new AssertionError("unexpected case in " + file + " (" + name + ")");
        }

        if (typeRegistry.isModule(file) && name.startsWith(MODULE_CONTENTS_PREFIX)) {
          Module module = typeRegistry.getModule(file);
          if (module.getType() != Type.ES6) {
            String id = module.getId();
            if (id.startsWith(MODULE_ID_PREFIX)) {
              id = id.substring(MODULE_ID_PREFIX.length());
            }
            recordModuleContentsAlias(file, stripContentsPrefix(id, name), name);
            logfmt("Skipping module internal alias: %s", name);
            continue;
          }
        }

        if (info == null || isNullOrEmpty(info.getOriginalCommentString())) {
          info = node.getJSType().getJSDocInfo();
        }

        if (isPrimitive(node.getJSType())
            && (info == null || (info.getTypedefType() == null && !info.isDefine()))) {
          logfmt("Skipping primitive type assigned to %s: %s", name, node.getJSType());
          continue;
        }

        // TODO: globals (functions, typedefs, compiler constants) should be documented together.
        if (info != null && info.isDefine()) {
          logfmt("Skipping global compiler constant: %s", name);
          continue;
        }

        Path path = inputFs.getPath(node.getSourceFileName());
        Optional<Module> module = getModule(name, node);
        if (module.isPresent() && modulePathFilter.apply(module.get().getPath())) {
          continue;
        }

        NominalType nominalType = NominalType.builder()
            .setName(name)
            .setType(type)
            .setJsDoc(info)
            .setSourceFile(path)
            .setSourcePosition(Position.of(node.getLineno(), node.getCharno()))
            .setModule(module)
            .build();

        recordType(nominalType);
      }
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
        if (externTypes.contains(type)) {
          return true;
        }
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
        return Optional.absent();
      }

      Module module = typeRegistry.getModule(path);
      if (module.getHasLegacyNamespace()) {
        verify(module.getType() == Type.CLOSURE,
            "legacy namespace on non-closure module: %s", module.getOriginalName());
        verify(module.getOriginalName().equals(module.getId()));
        return Optional.of(module);
      }

      if (typeRegistry.isImplicitNamespace(typeName)) {
        verify(!typeName.equals(module.getId()));
        return Optional.absent();
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
          if (module.getType() == Module.Type.ES6) {
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
          && !typeRegistry.isImplicitNamespace(type.getName())) {
        logfmt("Ignoring undeclared namespace %s", type.getName());
        System.out.printf("Ignoring undeclared namespace %s\n", type.getName());
        return;
      }

      if (!typeRegistry.getTypes(type.getType()).isEmpty()) {
        logfmt("Found type alias: %s", type.getName());
        addType(type);
        return;
      }

      if (addType(type)) {
        types.push(type);
        jsType.visit(this);
        types.pop();
      }
    }

    /**
     * Registers a type, unless it is excluded by the name filter.
     *
     * @param type the type to add.
     * @return whether the type was registered.
     */
    private boolean addType(NominalType type) {
      if (typeNameFilter.apply(type.getName())) {
        return false;
      }
      typeRegistry.addType(type);
      return true;
    }

    private boolean shouldUsePropertyTypeDocs(NominalType parent, Property property) {
      JSDocInfo info = property.getJSDocInfo();
      return (info == null && !isTheObjectType(property.getType()))
          || (parent.isModuleExports()
              && parent.getModule().get().getType() != Module.Type.ES6
              && (info == null || isNullOrEmpty(info.getOriginalCommentString())));
    }

    private void crawlProperty(Property property) {
      checkState(!types.isEmpty());
      if (property.getType().isInstanceType()) {
        return;
      }

      NominalType parent = types.peek();

      Node node = property.getNode();
      JSDocInfo info = property.getJSDocInfo();
      if (shouldUsePropertyTypeDocs(parent, property)) {
        info = property.getType().getJSDocInfo();
      }
      JsDoc jsdoc = JsDoc.from(info);

      if (jsdoc.isTypedef()) {
        String name = parent.getName() + "." + property.getName();

        Optional<Module> module = getModule(name, node);
        if (module.isPresent() && modulePathFilter.apply(module.get().getPath())) {
          return;
        }

        addType(NominalType.builder()
            .setName(name)
            .setModule(module)
            .setJsDoc(jsdoc)
            .setType(property.getType())
            .setSourceFile(inputFs.getPath(node.getSourceFileName()))
            .setSourcePosition(Position.of(node.getLineno(), node.getCharno()))
            .build());
        return;
      }

      JSType propertyType = property.getType();
      if (typeRegistry.isModule(propertyType)) {
        return;
      }

      if (propertyType.isInstanceType() && jsdoc.isConstructor()) {
        JSType ctor = ((PrototypeObjectType) propertyType).getConstructor();
        if (ctor != null && parent.getType().equals(ctor)) {
          propertyType = ctor;
        }
      }

      String name = parent.getName() + "." + property.getName();
      Optional<Module> module = getModule(name, node);
      if (module.isPresent() && modulePathFilter.apply(module.get().getPath())) {
        return;
      }

      NominalType nt = NominalType.builder()
          .setName(name)
          .setModule(module)
          .setJsDoc(jsdoc)
          .setType(propertyType)
          .setSourceFile(inputFs.getPath(node.getSourceFileName()))
          .setSourcePosition(Position.of(node.getLineno(), node.getCharno()))
          .build();

      if (propertyType.isConstructor()) {
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
        if (parent.isModuleExports() || isConstructorTypeDefinition(propertyType, jsdoc)) {
          recordType(nt);
        }
      } else if (propertyType.isInterface() || propertyType.isEnumType()) {
        recordType(nt);

      } else if (!propertyType.isInstanceType()
          && propertyType instanceof PrototypeObjectType
          && (typeRegistry.isProvided(parent.getName() + "." + property.getName())
              || typeRegistry.isImplicitNamespace(parent.getName() + "." + property.getName())
              || !typeRegistry.getTypes(propertyType).isEmpty())) {
        recordType(nt);
      }
    }

    @Override
    public Void caseFunctionType(FunctionType type) {
      for (String name : type.getOwnPropertyNames()) {
        if (!"apply".equals(name)
            && !"bind".equals(name)
            && !"call".equals(name)
            && !"prototype".equals(name)) {
          crawlProperty(type.getOwnSlot(name));
        }
      }
      return null;
    }

    @Override
    public Void caseObjectType(ObjectType type) {
      if (type.isGlobalThisType()) {
        return null;
      }
      for (String name : type.getOwnPropertyNames()) {
        if (!"prototype".equals(name)) {
          crawlProperty(type.getOwnSlot(name));
        }
      }
      return null;
    }

    @Override public Void caseNoType(NoType type) { return null; }
    @Override public Void caseEnumElementType(EnumElementType type) { return null; }
    @Override public Void caseAllType() { return null; }
    @Override public Void caseBooleanType() { return null; }
    @Override public Void caseNoObjectType() { return null; }
    @Override public Void caseUnknownType() { return null; }
    @Override public Void caseNullType() { return null; }
    @Override public Void caseNamedType(NamedType type) { return null; }
    @Override public Void caseProxyObjectType(ProxyObjectType type) { return null; }
    @Override public Void caseNumberType() { return null; }
    @Override public Void caseStringType() { return null; }
    @Override public Void caseVoidType() { return null; }
    @Override public Void caseUnionType(UnionType type) { return null; }
    @Override public Void caseTemplatizedType(TemplatizedType type) { return null; }
    @Override public Void caseTemplateType(TemplateType templateType) { return null; }
  }

  private static boolean isTheObjectType(JSType type) {
    if (!type.isInstanceType()) {
      return false;
    }
    ObjectType obj = type.toObjectType();
    return obj.getConstructor().isNativeObjectType()
        && "Object".equals(obj.getConstructor().getReferenceName());
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
