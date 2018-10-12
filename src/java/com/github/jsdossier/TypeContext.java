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

import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.Symbol;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.jscomp.Types;
import com.google.common.io.Files;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Defines a context in which type names may be defined. For instance, consider:
 *
 * <pre><code>
 *   goog.provide('foo');
 *   goog.provide('bar');
 *
 *   goog.scope(function() {
 *     let ns = foo;
 *
 *     ns.One = class {};
 *     ns.Two = class {
 *       /** @param {ns.One} a . *\
 *       method(a) {}
 *     };
 *   });
 *
 *   goog.scope(function() {
 *     let ns = bar;
 *
 *     ns.One = class {};
 *     ns.Two = class extends foo.Two {
 *       /** @param {ns.One} b . *\
 *       method2(b) {}
 *     };
 *   });
 * </code></pre>
 *
 * <p>In this example, for the context {@code foo.Two}, the type name {@code ns.One} will resolve to
 * {@code foo.One}, whereas for {@code bar.Two} it will resolve to {@code bar.One}.
 */
final class TypeContext {

  private static final char MODULE_PATH_SEPARATOR = '/';

  private final TypeRegistry typeRegistry;
  private final JSTypeRegistry jsTypeRegistry;
  private final DossierFileSystem dfs;
  private final ModuleNamingConvention moduleNamingConvention;
  private final Optional<NominalType> context;

  @Inject
  TypeContext(
      TypeRegistry typeRegistry,
      JSTypeRegistry jsTypeRegistry,
      ModuleNamingConvention moduleNamingConvention,
      DossierFileSystem dfs) {
    this(typeRegistry, jsTypeRegistry, dfs, moduleNamingConvention, Optional.empty());
  }

  private TypeContext(
      TypeRegistry typeRegistry,
      JSTypeRegistry jsTypeRegistry,
      DossierFileSystem dfs,
      ModuleNamingConvention moduleNamingConvention,
      Optional<NominalType> context) {
    this.typeRegistry = typeRegistry;
    this.jsTypeRegistry = jsTypeRegistry;
    this.dfs = dfs;
    this.moduleNamingConvention = moduleNamingConvention;
    this.context = context;
  }

  /** Creates a new context focused on the given type. */
  public TypeContext changeContext(@Nullable NominalType context) {
    return new TypeContext(
        typeRegistry, jsTypeRegistry, dfs, moduleNamingConvention, Optional.ofNullable(context));
  }

  public boolean isGlobalScope() {
    return !context.isPresent();
  }

  /** Returns the type type names or are resolved against, or null if using the global scope. */
  @Nullable
  public NominalType getContextType() {
    return context.orElse(null);
  }

  /**
   * Resolves the named type relative to this context. Will recurse into the parent context,
   * ultimately returning {@code null} if the type cannot be resolved.
   */
  @Nullable
  @CheckReturnValue
  public NominalType resolveType(String name) {
    if (name.indexOf(MODULE_PATH_SEPARATOR) != -1) {
      return resolveModuleType(name);
    }

    if (!context.isPresent()) {
      return resolveGlobalType(name);
    }

    String def = typeRegistry.resolveAlias(context.get(), name);
    if (def != null) {
      // If we resolved the name to a module's internal variable, then check if the variable is
      // defined in the global scope. If not, then the compiler decided he internal variable could
      // be inlined. If this is the case, we know dossier encountered `name` within the context
      // type's module, so we can see if the internal variable was exported from the module
      // (which would be our resolved type).
      if (Types.isModuleContentsVar(def)) {
        NominalType type = resolve(jsTypeRegistry.getGlobalType(def));
        if (type != null) {
          return type;
        }

        if (context.get().getModule().isPresent()) {
          Module module = context.get().getModule().get();
          type = resolveModuleContentVar(module, def);
          if (type != null) {
            return type;
          }
        }
      }

      name = def;
    } else {
      for (int index = name.indexOf('.'); index != -1; ) {
        String subName = name.substring(0, index);
        def = typeRegistry.resolveAlias(context.get(), subName);
        if (def != null) {
          name = def + name.substring(index);
          break;
        }

        if (index + 1 < name.length()) {
          index = name.indexOf('.', index + 1);
        } else {
          break;
        }
      }
    }

    if (context.get().getModule().isPresent()) {
      Module module = context.get().getModule().get();
      if (module.getExportedNames().containsKey(name)) {
        name = module.getExportedNames().get(name);
      }
    }

    return resolveGlobalType(name);
  }

  @Nullable
  @CheckReturnValue
  private NominalType resolveModuleContentVar(Module module, String compilerName) {
    // Step 1: map the compiler's generated name to the original source name.
    return module
        .getInternalSymbolTable()
        .getAllSymbols()
        .stream()
        .filter(s -> compilerName.equals(s.getReferencedSymbol()))
        .map(Symbol::getName)
        .findFirst()
        // Step 2: find the module's export that maps to the internal name.
        .flatMap(
            internalName ->
                module
                    .getExportedNames()
                    .entrySet()
                    .stream()
                    .filter(e -> internalName.equals(e.getValue()))
                    .map(e -> module.getId() + "." + e.getKey())
                    // Step 3: Double check it's a legit type and we didn't lose state somewhere.
                    .filter(typeRegistry::isType)
                    .map(typeRegistry::getType)
                    .findFirst())
        .orElse(null);
  }

  @Nullable
  @CheckReturnValue
  private NominalType resolveModuleType(String pathStr) {
    Path path = resolveModulePath(pathStr);

    NominalType type = resolveModuleType(path);
    if (type != null) {
      return type;
    }

    String baseName = Files.getNameWithoutExtension(path.getFileName().toString());
    int index = baseName.indexOf('.');
    if (index != -1) {
      path = path.resolveSibling(baseName.substring(0, index) + ".js");
      if (typeRegistry.isModule(path)) {
        Module module = typeRegistry.getModule(path);
        String typeName = module.getId() + baseName.substring(index);
        if (typeRegistry.isType(typeName)) {
          return typeRegistry.getType(typeName);
        }
      }
    }

    return null;
  }

  @Nullable
  private NominalType resolveModuleType(Path path) {
    if (!typeRegistry.isModule(path)
        && moduleNamingConvention == ModuleNamingConvention.NODE
        && !path.endsWith("index.js")) {
      String name = Files.getNameWithoutExtension(path.toString());
      path = path.resolveSibling(name).resolve("index.js");
    }
    if (typeRegistry.isModule(path)) {
      Module module = typeRegistry.getModule(path);
      return typeRegistry.getType(module.getId());
    }
    return null;
  }

  private Path resolveModulePath(String pathStr) {
    if (pathStr.endsWith("/") && moduleNamingConvention == ModuleNamingConvention.NODE) {
      pathStr += "index";
    }

    if (!pathStr.endsWith(".js")) {
      pathStr += ".js";
    }

    if (!context.isPresent()
        || !context.get().getModule().isPresent()
        || (!pathStr.startsWith("./") && !pathStr.startsWith("../"))) {
      return dfs.resolveModule(pathStr);
    } else {
      return context.get().getModule().get().getPath().resolveSibling(pathStr).normalize();
    }
  }

  @Nullable
  @CheckReturnValue
  private NominalType resolveGlobalType(String name) {
    if (typeRegistry.isType(name)) {
      return typeRegistry.getType(name);
    }

    NominalType type = resolve(jsTypeRegistry.getGlobalType(name));
    if (type != null) {
      return type;
    }

    return resolveModuleType(name);
  }

  @Nullable
  @CheckReturnValue
  private NominalType resolve(@Nullable JSType type) {
    if (type == null) {
      return null;
    }
    Collection<NominalType> types = typeRegistry.findTypes(type);
    if (types.isEmpty() && type.isInstanceType()) {
      types = typeRegistry.findTypes(type.toObjectType().getConstructor());
    }
    if (!types.isEmpty()) {
      return types.iterator().next();
    }
    return null;
  }
}
