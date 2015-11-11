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

package com.github.jsdossier;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NominalType2;
import com.github.jsdossier.jscomp.TypeRegistry2;
import com.github.jsdossier.proto.TypeLink;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Class responsible for generating the information necessary to render links between documented
 * types.
 */
final class LinkFactory {

  private static final String MDN_PREFIX =
      "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/";

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
          .put("Infinity", MDN_PREFIX + "Global_Objects/Infinity")
          .put("Math", MDN_PREFIX + "Global_Objects/Math")
          .put("NaN", MDN_PREFIX + "Global_Objects/NaN")
          .put("Number", MDN_PREFIX + "Global_Objects/Number")
          .put("Object", MDN_PREFIX + "Global_Objects/Object")
          .put("RangeError", MDN_PREFIX + "Global_Objects/RangeError")
          .put("ReferenceError", MDN_PREFIX + "Global_Objects/ReferenceError")
          .put("RegExp", MDN_PREFIX + "Global_Objects/RegExp")
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
          .build();

  private final DossierFileSystem dfs;
  private final TypeRegistry2 typeRegistry;
  private final JSTypeRegistry jsTypeRegistry;
  private final Optional<NominalType2> context;

  /**
   * Creates a link factory with no context: all links will be generated relative to the global
   * scope.
   */
  @Inject
  LinkFactory(
      DossierFileSystem dfs,
      TypeRegistry2 typeRegistry,
      JSTypeRegistry jsTypeRegistry) {
    this(dfs, typeRegistry, jsTypeRegistry, Optional.<NominalType2>absent());
  }

  private LinkFactory(
      DossierFileSystem dfs,
      TypeRegistry2 typeRegistry,
      JSTypeRegistry jsTypeRegistry,
      Optional<NominalType2> context) {
    this.dfs = dfs;
    this.typeRegistry = typeRegistry;
    this.jsTypeRegistry = jsTypeRegistry;
    this.context = context;
  }

  /**
   * Returns the link factory for the global scope.
   */
  public LinkFactory forGlobalScope() {
    if (context.isPresent()) {
      return new LinkFactory(dfs, typeRegistry, jsTypeRegistry);
    }
    return this;
  }

  /**
   * Returns a new factory that generates links relative to the given type.
   */
  public LinkFactory withContext(NominalType2 type) {
    return new LinkFactory(dfs, typeRegistry, jsTypeRegistry, Optional.of(type));
  }

  /**
   * Generates a link to the specified type. If this factory has a context type, the generated link
   * will be relative to the context's generated file. Otherwise, the link will be relative to the
   * output root (e.g. the "global" scope).
   */
  public TypeLink createLink(NominalType2 type) {
    Path path;
    String symbol = null;

    if (type.getJsDoc().isTypedef() || type.getJsDoc().isDefine()) {
      int index = type.getName().lastIndexOf('.');
      if (index == -1) {
        path = dfs.getGlobalsPath();
        symbol = type.getName();
      } else {
        String parentName = type.getName().substring(0, index);
        TypeLink link = createLink(typeRegistry.getType(parentName));
        String displayName = dfs.getDisplayName(type);
        return link.toBuilder()
            .setText(displayName)
            .setHref(link.getHref() + "#" + displayName)
            .build();
      }

    } else {
      path = dfs.getPath(type);
    }

    if (context.isPresent()) {
      path = dfs.getRelativePath(context.get(), path);
    } else {
      path = dfs.getOutputRoot().relativize(path);
    }

    String href = getUriPath(path);
    if (symbol != null) {
      href += "#" + symbol;
    }
    return TypeLink.newBuilder()
        .setText(dfs.getDisplayName(type))
        .setHref(href)
        .build();
  }

  /**
   * Creates a link to a specific property on a type.
   */
  public TypeLink createLink(NominalType2 type, String property) {
    TypeLink link = createLink(type);
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
            .setText(link.getText() + "#" + property)
            .setHref(link.getHref() + "#" + property)
            .build();
      }
    }
    
    if (type.getType().toObjectType().getPropertyType(property).isEnumElementType()) {
      return link.toBuilder()
          .setText(link.getText() + "." + property)
          .setHref(link.getHref() + "#" + property)
          .build();
    }

    if (!type.getType().toObjectType().getPropertyNames().contains(property)) {
      return link.toBuilder()
          .setText(link.getText() + "." + property)
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
        .setText(link.getText() + "." + property)
        .setHref(link.getHref() + "#" + id)
        .build();
  }

  /**
   * Generates a link to the given symbol, relative to this factory's context type. If the symbol
   * does not resolve to a type, this method will return a link with no path.
   */
  public TypeLink createLink(String symbol) {
    // Trim down the target symbol to something that may be indexed.
    int index = symbol.indexOf('(');
    if (index != -1) {
      symbol = symbol.substring(0, index);
    }

    if (symbol.startsWith("#")) {
      return context.isPresent()
          ? createLink(context.get(), symbol)
          : TypeLink.newBuilder().setText(symbol).build();

    } else if (symbol.endsWith("#")) {
      symbol = symbol.substring(0, symbol.length() - 1);

    } else if (symbol.endsWith(".prototype")) {
      symbol = symbol.substring(0, symbol.length() - ".prototype".length());
    }

    TypeRef ref = TypeRef.from(symbol);
    if (ref.type.isEmpty() && !context.isPresent()) {
      return TypeLink.newBuilder()
          .setText(symbol)
          .build();
    }

    String typeName = ref.type;
    String property = ref.property;
    NominalType2 type;
    if (typeName.isEmpty()) {
      type = context.get();

    } else {
      if (context.isPresent()) {
        typeName = resolveAlias(typeName);
      }
      type = resolveType(typeName);
    }

    // Link might be a qualified path to a property.
    if (type == null && property.isEmpty()) {
      index = typeName.lastIndexOf('.');
      if (index != -1 && index != typeName.length() - 1) {
        property = typeName.substring(index + 1);
        typeName = typeName.substring(0, index);
        type = resolveType(typeName);
      }
    }
 
    if (type != null) {
      return property.isEmpty() ? createLink(type) : createLink(type, property);
    }

    TypeLink link = resolveExternLink(ref.type);
    if (link == null && ref.property.isEmpty()
        && (index = ref.type.indexOf('.')) != -1) {
      link = resolveExternLink(ref.type.substring(0, index));
    }

    if (link != null) {
      return link.toBuilder().setText(symbol).build();
    }

    return TypeLink.newBuilder()
        .setText(symbol)
        .build();
  }
  
  @Nullable
  @CheckReturnValue
  private NominalType2 resolveType(String symbol) {
    if (typeRegistry.isType(symbol)) {
      return typeRegistry.getType(symbol);
    }

    Path modulePath = resolveModulePath(symbol);
    if (typeRegistry.isModule(modulePath)) {
      Module module = typeRegistry.getModule(modulePath);
      verify(typeRegistry.isType(module.getId()));
      return typeRegistry.getType(module.getId());
    }

    return null;
  }
  
  private String resolveAlias(String symbol) {
    verify(context.isPresent());

    NominalType2 ctx = context.get();
    for (int index = symbol.indexOf('.'); index != -1;) {
      String subName = symbol.substring(0, index);
      NominalType2 def = resolveAlias(ctx, subName);
      if (def != null) {
        return def.getName() + symbol.substring(index);
      }

      if (index + 1 < symbol.length()) {
        index = symbol.indexOf('.', index + 1);
      } else {
        break;
      }
    }
    
    NominalType2 def = resolveAlias(ctx, symbol);
    if (def != null) {
      return def.getName();
    }
    return symbol;
  }

  @Nullable
  @CheckReturnValue
  private NominalType2 resolveAlias(NominalType2 context, String alias) {
    String def = typeRegistry.resolveAlias(context, alias);
    if (def != null) {
      alias = def;
    }
    if (typeRegistry.isType(alias)) {
      return typeRegistry.getType(alias);
    }
    JSType type = jsTypeRegistry.getType(alias);
    if (type != null) {
      if (type.isInstanceType()) {
        type = type.toObjectType().getConstructor();
      }
      List<NominalType2> types = typeRegistry.getTypes(type);
      if (!types.isEmpty()) {
        return types.get(0);
      }
    }
    return null;
  }
  
  private Path resolveModulePath(String symbol) {
    if (context.isPresent() && context.get().getModule().isPresent()) {
      Path currentPath = context.get().getModule().get().getPath();
      return currentPath.resolveSibling(symbol + ".js").normalize();
    }
    return dfs.getModulePrefix().resolve(symbol + ".js");
  }

  @Nullable
  @CheckReturnValue
  private TypeLink resolveExternLink(String name) {
    if (BUILTIN_TO_MDN_LINK.containsKey(name)) {
      return TypeLink.newBuilder()
          .setText(name)
          .setHref(BUILTIN_TO_MDN_LINK.get(name))
          .build();
    }
    return null;
  }
  
  private String getUriPath(Path path) {
    return URI.create(path.toString()).toString();
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
