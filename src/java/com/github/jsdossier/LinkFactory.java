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

package com.github.jsdossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.html.types.SafeUrls.sanitize;

import com.github.jsdossier.annotations.SourceUrlTemplate;
import com.github.jsdossier.annotations.TypeFilter;
import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NodeLibrary;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.Position;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.SourceLink;
import com.github.jsdossier.proto.TypeLink;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrls;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Class responsible for generating the information necessary to render links between documented
 * types.
 */
@AutoFactory(className = "LinkFactoryBuilder") // Avoid generating a LinkFactoryFactory.
final class LinkFactory {

  private static final ImmutableMap<String, NamedType> EXTERN_TYPE_REFERENCES =
      createExternReferences();

  private final DossierFileSystem dfs;
  private final TypeRegistry typeRegistry;
  private final JSTypeRegistry jsTypeRegistry;
  private final NodeLibrary nodeLibrary;
  private final ModuleNamingConvention namingConvention;
  private final Optional<NominalType> pathContext;
  private final TypeContext typeContext;
  private final Optional<String> urlTemplate;
  private final Predicate<String> typeNameFilter;
  private final boolean jsonPaths;

  /**
   * Creates a new link factory.
   *
   * @param dfs used to generate paths to documentation in the output file system.
   * @param typeRegistry used to lookup nominal types.
   * @param jsTypeRegistry used to lookup JavaScript types.
   * @param typeContext defines the context in which to resolve type names.
   * @param urlTemplate if provided, defines a template for links to source files.
   * @param pathContext the object, if any, to generate paths relative to in the output file system.
   *     If {@code null}, paths will be relative to the output root.
   */
  LinkFactory(
      @Provided DossierFileSystem dfs,
      @Provided TypeRegistry typeRegistry,
      @Provided JSTypeRegistry jsTypeRegistry,
      @Provided NodeLibrary nodeLibrary,
      @Provided ModuleNamingConvention namingConvention,
      @Provided TypeContext typeContext,
      @Provided @SourceUrlTemplate Optional<String> urlTemplate,
      @Provided @TypeFilter Predicate<String> typeNameFilter,
      @Nullable NominalType pathContext) {
    this(
        dfs,
        typeRegistry,
        jsTypeRegistry,
        nodeLibrary,
        namingConvention,
        typeContext,
        urlTemplate,
        typeNameFilter,
        pathContext,
        false);
  }

  LinkFactory(
      @Provided DossierFileSystem dfs,
      @Provided TypeRegistry typeRegistry,
      @Provided JSTypeRegistry jsTypeRegistry,
      @Provided NodeLibrary nodeLibrary,
      @Provided ModuleNamingConvention namingConvention,
      @Provided TypeContext typeContext,
      @Provided @SourceUrlTemplate Optional<String> urlTemplate,
      @Provided @TypeFilter Predicate<String> typeNameFilter,
      @Nullable NominalType pathContext,
      boolean jsonPaths) {
    this.dfs = dfs;
    this.typeRegistry = typeRegistry;
    this.jsTypeRegistry = jsTypeRegistry;
    this.nodeLibrary = nodeLibrary;
    this.namingConvention = namingConvention;
    this.pathContext = Optional.ofNullable(pathContext);
    this.typeContext = typeContext;
    this.urlTemplate = urlTemplate;
    this.typeNameFilter = typeNameFilter;
    this.jsonPaths = jsonPaths;
  }

  public TypeContext getTypeContext() {
    return typeContext;
  }

  /**
   * Creates a new link factory that resolves type names relative to the given context type. All
   * generated paths will remain relative to this factory's path context type.
   */
  public LinkFactory withTypeContext(NominalType context) {
    // NB: Can't use an overloaded constructor b/c AutoFactory tries to generate a constructor
    // for everything, even ones with private visibility.
    return new LinkFactory(
        dfs,
        typeRegistry,
        jsTypeRegistry,
        nodeLibrary,
        namingConvention,
        typeContext.changeContext(context),
        urlTemplate,
        typeNameFilter,
        pathContext.orElse(null),
        jsonPaths);
  }

  /**
   * Creates a new link factory that includes links to the JSON data file for all type references.
   */
  public LinkFactory withJsonPaths() {
    return new LinkFactory(
        dfs,
        typeRegistry,
        jsTypeRegistry,
        nodeLibrary,
        namingConvention,
        typeContext,
        urlTemplate,
        typeNameFilter,
        pathContext.orElse(null),
        true);
  }

  /** Creates a link to a specific line in a rendered source file. */
  public SourceLink createSourceLink(Path path, Position position) {
    if (urlTemplate.isPresent()) {
      path = dfs.getSourceRelativePath(path);
    } else {
      path = dfs.getPath(path);
      if (pathContext.isPresent()) {
        path = dfs.getRelativePath(pathContext.get(), path);
      }
    }

    String pathStr = getUriPath(path);
    SourceLink.Builder link =
        SourceLink.newBuilder()
            .setPath(SafeUrls.toProto(sanitize(pathStr)))
            .setLine(position.getLine());
    if (urlTemplate.isPresent()) {
      String url =
          urlTemplate
              .get()
              .replaceAll("%path%", pathStr)
              .replaceAll("%line%", String.valueOf(position.getLine()));

      link.setUrl(SafeUrls.toProto(sanitize(url)));
    }
    return link.build();
  }

  /**
   * Creates a link to the rendered source file for the given node, relative to this factory's
   * current context.
   */
  public SourceLink createSourceLink(Node node) {
    if (node == null || node.isFromExterns()) {
      return SourceLink.newBuilder().setPath(SafeUrls.toProto(SafeUrls.fromConstant(""))).build();
    }
    Path sourcePath = dfs.getSourcePath(node);
    return createSourceLink(sourcePath, Position.of(node.getLineno(), 0));
  }

  private static TypeLink.Builder appendFragment(TypeLink original, String fragment) {
    SafeUrl url = SafeUrls.fromProto(original.getHref());
    url = sanitize(url.getSafeUrlString() + "#" + fragment);
    return original.toBuilder().setHref(SafeUrls.toProto(url));
  }

  /**
   * Generates a link to the specified type. If this factory has a context type, the generated link
   * will be relative to the context's generated file. Otherwise, the link will be relative to the
   * output root (e.g. the "global" scope).
   */
  public NamedType createTypeReference(final NominalType type) {
    Path path;
    Path jsonPath;
    String symbol = null;

    if (type.getJsDoc().isTypedef() || type.getJsDoc().isDefine()) {
      int index = type.getName().lastIndexOf('.');
      if (index == -1) {
        path = dfs.getGlobalsPath();
        jsonPath = dfs.getGlobalsJson();
        symbol = type.getName();
      } else {
        String parentName = type.getName().substring(0, index);
        NamedType parentType = createTypeReference(typeRegistry.getType(parentName));
        String displayName = dfs.getDisplayName(type);
        return parentType
            .toBuilder()
            .setName(displayName)
            .setLink(appendFragment(parentType.getLink(), displayName))
            .build();
      }

    } else {
      path = dfs.getPath(type);
      jsonPath = dfs.getJsonPath(type);
    }

    if (pathContext.isPresent()) {
      path = dfs.getRelativePath(pathContext.get(), path);
      if (jsonPath != null) {
        jsonPath = dfs.getRelativePath(pathContext.get(), jsonPath);
      }
    } else {
      path = dfs.getRelativePath(path);
      if (jsonPath != null) {
        jsonPath = dfs.getRelativePath(jsonPath);
      }
    }

    String href = getUriPath(path);
    if (symbol != null) {
      href += "#" + symbol;
    }

    String displayName = dfs.getDisplayName(type);
    String qualifiedName = dfs.getQualifiedDisplayName(type);

    NamedType.Builder builder = NamedType.newBuilder().setName(displayName);
    if (!displayName.equals(qualifiedName)) {
      builder.setQualifiedName(qualifiedName);
    }
    builder.getLinkBuilder().setHref(SafeUrls.toProto(sanitize(href)));
    if (jsonPath != null && jsonPaths) {
      builder.getLinkBuilder().setJson(getUriPath(jsonPath));
    }
    return builder.build();
  }

  /** Creates a link to a specific property on a type. */
  public NamedType createTypeReference(NominalType type, String property) {
    NamedType typeRef = createTypeReference(type);
    checkState(
        !SafeUrls.fromProto(typeRef.getLink().getHref()).getSafeUrlString().isEmpty(),
        "Failed to build link for %s",
        type.getName());

    boolean checkPrototype = false;
    if (property.startsWith("#")) {
      checkPrototype = true;
      property = property.substring(1);
    }

    if (property.isEmpty()) {
      return typeRef;
    }

    if (checkPrototype && (type.getType().isInstanceType() || type.getType().isConstructor() || type.getType().isInterface())) {
      ObjectType instanceType = type.getType().isInstanceType()
          ? type.getType().toObjectType()
          : ((FunctionType) type.getType()).getInstanceType();
      if (instanceType.getPropertyNames().contains(property)) {
        return typeRef
            .toBuilder()
            .setName(typeRef.getName() + "#" + property)
            .setLink(appendFragment(typeRef.getLink(), property))
            .build();
      }
    }

    if (type.isModuleExports()) {
      String exportedType = type.getName() + "." + property;
      if (typeRegistry.isType(exportedType)) {
        return createTypeReference(typeRegistry.getType(exportedType));
      }
    }

    if (type.getType().toObjectType().getPropertyType(property).isEnumElementType()) {
      return typeRef
          .toBuilder()
          .setName(typeRef.getName() + "." + property)
          .setLink(appendFragment(typeRef.getLink(), property))
          .build();
    }

    JSDocInfo propertyDocs = type.getType().toObjectType().getOwnPropertyJSDocInfo(property);
    if (propertyDocs != null && propertyDocs.isDefine()) {
      String name = dfs.getQualifiedDisplayName(type) + "." + property;
      return typeRef
          .toBuilder()
          .setName(name)
          .setLink(appendFragment(typeRef.getLink(), name))
          .build();
    }

    if (!type.getType().toObjectType().getPropertyNames().contains(property)) {
      return typeRef.toBuilder().setName(typeRef.getName() + "." + property).build();
    }

    String id = property;
    if (type.getType().isConstructor()
        || type.getType().isInterface()
        || type.getType().isEnumType()) {
      String name = type.getName();
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(index + 1);
      }
      id = name + "." + id;
    }

    String fullName = typeRef.getName() + "." + property;
    if (typeNameFilter.test(fullName)) {
      return NamedType.newBuilder().setName(fullName).build();
    }
    return typeRef
        .toBuilder()
        .setName(fullName)
        .setLink(appendFragment(typeRef.getLink(), id))
        .build();
  }

  /**
   * Generates a link to the given symbol, relative to this factory's context type. If the symbol
   * does not resolve to a type, this method will return a link with no path.
   */
  public NamedType resolveTypeReference(String symbol) {
    NamedType type = resolveTypeReferenceInteranl(symbol);
    if (typeNameFilter.test(type.getQualifiedName()) || typeNameFilter.test(type.getName())) {
      type = type.toBuilder().clearLink().build();
    }
    return type;
  }

  private NamedType resolveTypeReferenceInteranl(String symbol) {
    // Trim down the target symbol to something that may be indexed.
    int index = symbol.indexOf('(');
    if (index != -1) {
      symbol = symbol.substring(0, index);
    }

    if (symbol.startsWith("#")) {
      return pathContext.isPresent()
          ? createTypeReference(pathContext.get(), symbol)
          : NamedType.newBuilder().setName(symbol).build();

    } else if (symbol.endsWith("#")) {
      symbol = symbol.substring(0, symbol.length() - 1);

    } else if (symbol.endsWith(".prototype")) {
      symbol = symbol.substring(0, symbol.length() - ".prototype".length());
    }

    TypeRef ref = TypeRef.from(symbol);
    if (ref.type.isEmpty() && typeContext.isGlobalScope()) {
      return NamedType.newBuilder().setName(symbol).build();
    }

    String typeName = ref.type;
    String property = ref.property;
    NominalType type;
    if (typeName.isEmpty()) {
      type = typeContext.getContextType();

    } else {
      type = typeContext.resolveType(typeName);
    }

    // Link might be an unqualified reference to a property exported by a ES6 module.
    if (type == null
        && property.isEmpty()
        && typeContext.getContextType() != null
        && typeContext.getContextType().getModule().isPresent()
        && typeContext.getContextType().getModule().get().isEs6()) {
      Module module = typeContext.getContextType().getModule().get();
      NamedType link =
          maybeCreateExportedPropertyLink(typeRegistry.getType(module.getId()), typeName);
      if (link != null) {
        return link;
      }
    }

    // Link might be a qualified path to a property.
    if (type == null && property.isEmpty()) {
      index = typeName.lastIndexOf('.');
      if (index != -1 && index != typeName.length() - 1) {
        property = typeName.substring(index + 1);
        typeName = typeName.substring(0, index);
        type = typeContext.resolveType(typeName);
      }
    }

    if (type != null) {
      return property.isEmpty() ? createTypeReference(type) : createTypeReference(type, property);
    }

    NamedType link = resolveExternModuleReference(ref.type);
    if (link != null) {
      if (!ref.property.isEmpty()) {
        link = link.toBuilder().setName(link.getName() + "." + ref.property).build();
      }
      return link;
    }

    link = createNativeExternLink(ref.type);
    if (link == null && ref.property.isEmpty() && (index = ref.type.indexOf('.')) != -1) {
      link = createNativeExternLink(ref.type.substring(0, index));
    }

    if (link != null) {
      return link.toBuilder().setName(symbol).build();
    }

    return NamedType.newBuilder().setName(symbol).build();
  }

  @Nullable
  @CheckReturnValue
  private NamedType maybeCreateExportedPropertyLink(NominalType type, String property) {
    checkArgument(type.isModuleExports());
    if (type.getType().toObjectType().hasOwnProperty(property)) {
      return createTypeReference(type, property);
    }
    @SuppressWarnings("ConstantConditions") // type.isModuleExports() implies module is present.
    Module module = type.getModule().get();
    String exportedName =
        Iterables.getFirst(module.getExportedNames().asMultimap().inverse().get(property), null);
    if (exportedName == null) {
      return null;
    }
    verify(type.getType().toObjectType().hasOwnProperty(exportedName));
    return createTypeReference(type, exportedName);
  }

  @Nullable
  @CheckReturnValue
  public NamedType resolveExternModuleReference(String name) {
    if (Module.Type.NODE.isModuleId(name)) {
      final String externId = Module.Type.NODE.stripModulePrefix(name);

      if (nodeLibrary.canRequireId(externId)) {
        return NamedType.newBuilder().setName(externId).build();
      }

      int index = externId.indexOf('.');
      if (index != -1 && nodeLibrary.canRequireId(externId.substring(0, index))) {
        return NamedType.newBuilder().setName(externId).build();
      }
    }
    return null;
  }

  /** Creates a link to one of the JS built-in types defined in externs. */
  @Nullable
  @CheckReturnValue
  public NamedType createNativeExternLink(String name) {
    return EXTERN_TYPE_REFERENCES.get(name);
  }

  private String getUriPath(Path path) {
    return URI.create(path.normalize().toString()).toString();
  }

  private static ImmutableMap<String, NamedType> createExternReferences() {
    ImmutableMap.Builder<String, NamedType> map = ImmutableMap.builder();
    addExternReference(map, "Arguments");
    addExternReference(map, "Array");
    addExternReference(map, "Boolean");
    addExternReference(map, "Date");
    addExternReference(map, "Error");
    addExternReference(map, "Function");
    addExternReference(map, "Generator");
    addExternReference(map, "IArrayLike");
    addExternReference(map, "IObject");
    addExternReference(map, "IThenable");
    addExternReference(map, "Infinity");
    addExternReference(map, "Iterable");
    addExternReference(map, "Iterator");
    addExternReference(map, "Map");
    addExternReference(map, "Math");
    addExternReference(map, "NaN");
    addExternReference(map, "Number");
    addExternReference(map, "Object");
    addExternReference(map, "Promise");
    addExternReference(map, "RangeError");
    addExternReference(map, "ReferenceError");
    addExternReference(map, "RegExp");
    addExternReference(map, "Set");
    addExternReference(map, "Symbol");
    addExternReference(map, "String");
    addExternReference(map, "SyntaxError");
    addExternReference(map, "TypeError");
    addExternReference(map, "URIError");
    addExternReference(map, "arguments");
    addExternReference(map, "boolean");
    addExternReference(map, "null");
    addExternReference(map, "number");
    addExternReference(map, "string");
    addExternReference(map, "undefined");
    addExternReference(map, "WeakMap");
    addExternReference(map, "WeakSet");
    return map.build();
  }

  private static void addExternReference(ImmutableMap.Builder<String, NamedType> map, String name) {
    map.put(name, createExternReference(name));
  }

  private static NamedType createExternReference(String name) {
    return NamedType.newBuilder().setName(name).setExtern(true).build();
  }

  private static class TypeRef {
    private final String type;
    private final String property;

    private TypeRef(String type, String property) {
      this.type = type;
      this.property = property;
    }

    public static TypeRef from(String typeName) {
      String property = "";
      int index;
      if ((index = typeName.indexOf('#')) != -1) {
        property = typeName.substring(index);
        typeName = typeName.substring(0, index);

      } else if ((index = typeName.indexOf(".prototype.")) != -1) {
        property = "#" + typeName.substring(index + ".prototype.".length());
        typeName = typeName.substring(0, index);
      }
      return new TypeRef(typeName, property);
    }
  }
}
