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

import com.github.jsdossier.annotations.SourceUrlTemplate;
import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NodeLibrary;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.Position;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.SourceLink;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.net.URI;
import java.nio.file.Path;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Class responsible for generating the information necessary to render links between documented
 * types.
 */
@AutoFactory(className = "LinkFactoryBuilder")  // Avoid generating a LinkFactoryFactory.
final class LinkFactory {

  private static final String MDN = "https://developer.mozilla.org/en-US/docs/Web/JavaScript/";
  private static final String MDN_PREFIX = MDN + "Reference/";
  private static final String CLOSURE_COMPILER_PREFIX =
      "https://github.com/google/closure-compiler/wiki/Special-types-in-the-Closure-Type-System#";

  /**
   * Maps built-in objects to a link to their definition on the Mozilla Develper Network.
   * The Closure compiler's externs do not provide links for these types in its externs.
   */
  private static final ImmutableMap<String, String> BUILTIN_TO_MDN_LINK =
      ImmutableMap.<String, String>builder()
          .put("Arguments", MDN_PREFIX + "Functions/arguments")
          .put("Array", MDN_PREFIX + "Global_Objects/Array")
          .put("Boolean", MDN_PREFIX + "Global_Objects/Boolean")
          .put("Date", MDN_PREFIX + "Global_Objects/Date")
          .put("Error", MDN_PREFIX + "Global_Objects/Error")
          .put("Function", MDN_PREFIX + "Global_Objects/Function")
          .put("Generator", MDN_PREFIX + "Global_Objects/Generaor")
          .put("IArrayLike", CLOSURE_COMPILER_PREFIX + "iarraylike")
          .put("IObject", CLOSURE_COMPILER_PREFIX + "iobject")
          .put("IThenable", CLOSURE_COMPILER_PREFIX + "ithenable")
          .put("Infinity", MDN_PREFIX + "Global_Objects/Infinity")
          .put("Iterable", MDN_PREFIX + "Global_Objects/Symbol/iterator")
          .put("Iterator", MDN + "Guide/The_Iterator_protocol")
          .put("Map", MDN_PREFIX + "Global_Objects/Map")
          .put("Math", MDN_PREFIX + "Global_Objects/Math")
          .put("NaN", MDN_PREFIX + "Global_Objects/NaN")
          .put("Number", MDN_PREFIX + "Global_Objects/Number")
          .put("Object", MDN_PREFIX + "Global_Objects/Object")
          .put("Promise", MDN_PREFIX + "Global_Objects/Promise")
          .put("RangeError", MDN_PREFIX + "Global_Objects/RangeError")
          .put("ReferenceError", MDN_PREFIX + "Global_Objects/ReferenceError")
          .put("RegExp", MDN_PREFIX + "Global_Objects/RegExp")
          .put("Set", MDN_PREFIX + "Global_Objects/Set")
          .put("Symbol", MDN_PREFIX + "Global_Objects/Symbol")
          .put("String", MDN_PREFIX + "Global_Objects/String")
          .put("SyntaxError", MDN_PREFIX + "Global_Objects/SyntaxError")
          .put("TypeError", MDN_PREFIX + "Global_Objects/TypeError")
          .put("URIError", MDN_PREFIX + "Global_Objects/URIError")
          .put("arguments", MDN_PREFIX + "Functions/arguments")
          .put("boolean", MDN_PREFIX + "Global_Objects/Boolean")
          .put("null", MDN_PREFIX + "Global_Objects/Null")
          .put("number", MDN_PREFIX + "Global_Objects/Number")
          .put("string", MDN_PREFIX + "Global_Objects/String")
          .put("undefined", MDN_PREFIX + "Global_Objects/Undefined")
          .put("WeakMap", MDN_PREFIX + "Global_Objects/WeakMap")
          .put("WeakSet", MDN_PREFIX + "Global_Objects/WeakSet")
          .build();

  private final DossierFileSystem dfs;
  private final TypeRegistry typeRegistry;
  private final JSTypeRegistry jsTypeRegistry;
  private final NodeLibrary nodeLibrary;
  private final ModuleNamingConvention namingConvention;
  private final Optional<NominalType> pathContext;
  private final TypeContext typeContext;
  private final Optional<String> urlTemplate;

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
      @Nullable NominalType pathContext) {
    this.dfs = dfs;
    this.typeRegistry = typeRegistry;
    this.jsTypeRegistry = jsTypeRegistry;
    this.nodeLibrary = nodeLibrary;
    this.namingConvention = namingConvention;
    this.pathContext = Optional.fromNullable(pathContext);
    this.typeContext = typeContext;
    this.urlTemplate = urlTemplate;
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
        dfs, typeRegistry, jsTypeRegistry, nodeLibrary, namingConvention,
        typeContext.changeContext(context), urlTemplate,
        pathContext.orNull());
  }

  /**
   * Creates a link to a specific line in a rendered source file.
   */
  public SourceLink createLink(Path path, Position position) {
    if (urlTemplate.isPresent()) {
      path = dfs.getSourceRelativePath(path);
    } else {
      path = dfs.getPath(path);
      if (pathContext.isPresent()) {
        path = dfs.getRelativePath(pathContext.get(), path);
      }
    }

    String pathStr = getUriPath(path);
    SourceLink.Builder link = SourceLink.newBuilder()
        .setPath(pathStr)
        .setLine(position.getLine());
    if (urlTemplate.isPresent()) {
      String url = urlTemplate.get()
          .replaceAll("%path%", pathStr)
          .replaceAll("%line%", String.valueOf(position.getLine()));
      link.setUri(url);
    }
    return link.build();
  }

  /**
   * Creates a link to the rendered source file for the given node, relative to this factory's
   * current context.
   */
  public SourceLink createLink(Node node) {
    if (node == null || node.isFromExterns()) {
      return SourceLink.newBuilder().setPath("").build();
    }
    Path sourcePath = dfs.getSourcePath(node);
    return createLink(sourcePath, Position.of(node.getLineno(), 0));
  }

  /**
   * Generates a link to the specified type. If this factory has a context type, the generated link
   * will be relative to the context's generated file. Otherwise, the link will be relative to the
   * output root (e.g. the "global" scope).
   */
  public NamedType createLink(final NominalType type) {
    Path path;
    String symbol = null;

    if (type.getJsDoc().isTypedef() || type.getJsDoc().isDefine()) {
      int index = type.getName().lastIndexOf('.');
      if (index == -1) {
        path = dfs.getGlobalsPath();
        symbol = type.getName();
      } else {
        String parentName = type.getName().substring(0, index);
        NamedType link = createLink(typeRegistry.getType(parentName));
        String displayName = dfs.getDisplayName(type);
        return link.toBuilder()
            .setName(displayName)
            .setHref(link.getHref() + "#" + displayName)
            .build();
      }

    } else {
      path = dfs.getPath(type);
    }

    if (pathContext.isPresent()) {
      path = dfs.getRelativePath(pathContext.get(), path);
    } else {
      path = dfs.getRelativePath(path);
    }

    String href = getUriPath(path);
    if (symbol != null) {
      href += "#" + symbol;
    }

    String displayName = dfs.getDisplayName(type);
    String qualifiedName = dfs.getQualifiedDisplayName(type);

    NamedType.Builder builder = NamedType.newBuilder()
        .setHref(href)
        .setName(displayName);
    if (!displayName.equals(qualifiedName)) {
      builder.setQualifiedName(qualifiedName);
    }
    return builder.build();
  }

  /**
   * Creates a link to a specific property on a type.
   */
  public NamedType createLink(NominalType type, String property) {
    NamedType link = createLink(type);
    checkState(!link.getHref().isEmpty(), "Failed to build link for %s", type.getName());

    boolean checkPrototype = false;
    if (property.startsWith("#")) {
      checkPrototype = true;
      property = property.substring(1);
    }

    if (property.isEmpty()) {
      return link;
    }

    if (checkPrototype
        && (type.getType().isConstructor() || type.getType().isInterface())) {
      ObjectType instanceType = ((FunctionType) type.getType()).getInstanceType();
      if (instanceType.getPropertyNames().contains(property)) {
        return link.toBuilder()
            .setName(link.getName() + "#" + property)
            .setHref(link.getHref() + "#" + property)
            .build();
      }
    }

    if (type.isModuleExports()) {
      String exportedType = type.getName() + "." + property;
      if (typeRegistry.isType(exportedType)) {
        return createLink(typeRegistry.getType(exportedType));
      }
    }

    if (type.getType().toObjectType().getPropertyType(property).isEnumElementType()) {
      return link.toBuilder()
          .setName(link.getName() + "." + property)
          .setHref(link.getHref() + "#" + property)
          .build();
    }

    JSDocInfo propertyDocs = type.getType().toObjectType().getOwnPropertyJSDocInfo(property);
    if (propertyDocs != null && propertyDocs.isDefine()) {
      String name = dfs.getQualifiedDisplayName(type) + "." + property;
      return link.toBuilder()
          .setName(name)
          .setHref(link.getHref() + "#" + name)
          .build();
    }

    if (!type.getType().toObjectType().getPropertyNames().contains(property)) {
      return link.toBuilder()
          .setName(link.getName() + "." + property)
          .build();
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
    return link.toBuilder()
        .setName(link.getName() + "." + property)
        .setHref(link.getHref() + "#" + id)
        .build();
  }

  /**
   * Generates a link to the given symbol, relative to this factory's context type. If the symbol
   * does not resolve to a type, this method will return a link with no path.
   */
  public NamedType createLink(String symbol) {
    // Trim down the target symbol to something that may be indexed.
    int index = symbol.indexOf('(');
    if (index != -1) {
      symbol = symbol.substring(0, index);
    }

    if (symbol.startsWith("#")) {
      return pathContext.isPresent()
          ? createLink(pathContext.get(), symbol)
          : NamedType.newBuilder().setName(symbol).build();

    } else if (symbol.endsWith("#")) {
      symbol = symbol.substring(0, symbol.length() - 1);

    } else if (symbol.endsWith(".prototype")) {
      symbol = symbol.substring(0, symbol.length() - ".prototype".length());
    }

    TypeRef ref = TypeRef.from(symbol);
    if (ref.type.isEmpty() && typeContext.isGlobalScope()) {
      return NamedType.newBuilder()
          .setName(symbol)
          .build();
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
    if (type == null && property.isEmpty()
        && typeContext.getContextType() != null
        && typeContext.getContextType().getModule().isPresent()
        && typeContext.getContextType().getModule().get().getType() == Module.Type.ES6) {
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
      return property.isEmpty() ? createLink(type) : createLink(type, property);
    }

    NamedType link = createExternModuleLink(ref.type);
    if (link != null) {
      if (!ref.property.isEmpty()) {
        link = link.toBuilder()
            .setName(link.getName() + "." + ref.property)
            .build();
      }
      return link;
    }

    link = createNativeExternLink(ref.type);
    if (link == null && ref.property.isEmpty()
        && (index = ref.type.indexOf('.')) != -1) {
      link = createNativeExternLink(ref.type.substring(0, index));
    }

    if (link != null) {
      return link.toBuilder().setName(symbol).build();
    }

    return NamedType.newBuilder()
        .setName(symbol)
        .build();
  }

  @Nullable
  @CheckReturnValue
  private NamedType maybeCreateExportedPropertyLink(NominalType type, String property) {
    checkArgument(type.isModuleExports());
    if (type.getType().toObjectType().hasOwnProperty(property)) {
      return createLink(type, property);
    }
    Module module = type.getModule().get();
    String exportedName =
        Iterables.getFirst(module.getExportedNames().asMultimap().inverse().get(property), null);
    if (exportedName == null) {
      return null;
    }
    verify(type.getType().toObjectType().hasOwnProperty(exportedName));
    return createLink(type, exportedName);
  }

  @Nullable
  @CheckReturnValue
  public NamedType createExternModuleLink(String name) {
    if (Module.Type.NODE.isModuleId(name)) {
      final String externId = Module.Type.NODE.stripModulePrefix(name);

      if (nodeLibrary.isModuleId(externId)) {
        return NamedType.newBuilder().setName(externId).build();
      }

      int index = externId.indexOf('.');
      if (index != -1 && nodeLibrary.isModuleId(externId.substring(0, index))) {
        return NamedType.newBuilder().setName(externId).build();
      }
    }
    return null;
  }

  /**
   * Creates a link to one of the JS built-in types defined in externs.
   */
  @Nullable
  @CheckReturnValue
  public NamedType createNativeExternLink(String name) {
    if (BUILTIN_TO_MDN_LINK.containsKey(name)) {
      return NamedType.newBuilder()
          .setName(name)
          .setHref(BUILTIN_TO_MDN_LINK.get(name))
          .setExtern(true)
          .build();
    }
    return null;
  }

  private String getUriPath(Path path) {
    return URI.create(path.normalize().toString()).toString();
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
