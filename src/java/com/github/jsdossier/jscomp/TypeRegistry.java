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

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.github.jsdossier.annotations.Input;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.ObjectType;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Dossier's internal type registry.
 */
@Singleton
public final class TypeRegistry {

  private final FileSystem inputFs;

  private final Set<String> providedSymbols = new HashSet<>();
  private final Set<String> implicitNamespaces = new HashSet<>();
  private final Map<String, Module> modulesById = new HashMap<>();
  private final Map<Path, Module> modulesByPath = new HashMap<>();
  private final Multimap<Path, AliasRegion> aliasRegions =
      MultimapBuilder.hashKeys().arrayListValues().build();
  private final Map<String, NominalType2> typesByName = new HashMap<>();
  private final ListMultimap<JSType, NominalType2> typesByJsType = Multimaps.newListMultimap(
      new IdentityHashMap<JSType, Collection<NominalType2>>(),
      new Supplier<List<NominalType2>>() {
        @Override
        public List<NominalType2> get() {
          return new ArrayList<>();
        }
      });
  
  @Inject
  TypeRegistry(@Input FileSystem inputFs) {
    this.inputFs = inputFs;
  }

  /**
   * Records a region of a file that defines variable aliases.
   */
  public void addAliasRegion(AliasRegion region) {
    aliasRegions.put(region.getPath(), region);
  }

  /**
   * Returns the alias regions defined for the file with the given path.
   */
  public Collection<AliasRegion> getAliasRegions(Path path) {
    return aliasRegions.get(path);
  }

  /**
   * Resolves an alias created by the compiler relative to the given type.
   *
   * @param type the point of reference for the alias to resolve.
   * @param key the alias to resolve.
   * @return the resolved alias, or null if none is defined.
   */
  public String resolveAlias(NominalType2 type, String key) {
    if (!aliasRegions.containsKey(type.getSourceFile())) {
      return null;
    }

    for (AliasRegion region : aliasRegions.get(type.getSourceFile())) {
      if (region.getRange().contains(type.getSourcePosition())) {
        String def = region.resolveAlias(key);
        if (def != null) {
          return def;
        }
      }
    }

    return null;
  }

  /**
   * Registers a new module.
   */
  public void addModule(Module module) {
    modulesById.put(module.getId(), module);
    modulesByPath.put(module.getPath(), module);
    if (module.getType() == Module.Type.CLOSURE) {
      recordProvide(module.getId());
    }
  }

  /**
   * Returns whether there is a module registered with the given ID.
   */
  public boolean isModule(String id) {
    return modulesById.containsKey(id);
  }

  /**
   * Returns whether the given path defines a module.
   */
  public boolean isModule(Path path) {
    return modulesByPath.containsKey(path);
  }

  /**
   * Returns the module with the given ID.
   * 
   * @throws IllegalArgumentException if there is no such module.
   */
  public Module getModule(String id) {
    checkArgument(isModule(id), "No such module: %s", id);
    return modulesById.get(id);
  }

  /**
   * Returns the module defined by the file with the given path.
   *
   * @throws IllegalArgumentException if there is no such module.
   */
  public Module getModule(Path path) {
    checkArgument(isModule(path), "Not a module: %s", path);
    return modulesByPath.get(path);
  }

  /**
   * Records a symbol declared by a "goog.provide" statement.
   */
  public void recordProvide(String symbol) {
    providedSymbols.add(symbol);
    implicitNamespaces.add(symbol);
    for (int i = symbol.lastIndexOf('.'); i != -1; i = symbol.lastIndexOf('.')) {
      symbol = symbol.substring(0, i);
      implicitNamespaces.add(symbol);
    }
  }

  @VisibleForTesting
  Set<String> getProvidedSymbols() {
    return Collections.unmodifiableSet(providedSymbols);
  }
  
  @VisibleForTesting
  Set<String> getImplicitNamespaces() {
    return Collections.unmodifiableSet(implicitNamespaces);
  }

  /**
   * Returns whether the provided symbol was declared with a "goog.provide" statement.
   * This only checks for symbols directly declared.
   */
  public boolean isProvided(String symbol) {
    return providedSymbols.contains(symbol) || implicitNamespaces.contains(symbol);
  }

  /**
   * Registers a nominal type.
   */
  public void addType(NominalType2 type) {
    checkArgument(!typesByName.containsKey(type.getName()),
        "A type with name %s has already been defined", type.getName());
    typesByName.put(type.getName(), type);
    typesByJsType.put(type.getType(), type);
  }

  /**
   * Returns whether there is a type registered with the given name.
   */
  public boolean isType(String name) {
    return typesByName.containsKey(name);
  }

  /**
   * Returns whether there is a nominal type registered with the given JSType.
   */
  public boolean isType(JSType type) {
    return typesByJsType.containsKey(type);
  }

  /**
   * Returns the nominal type with the given name.
   * 
   * @throws IllegalArgumentException if there is no such type.
   */
  public NominalType2 getType(String name) {
    checkArgument(isType(name), "no such type: %s", name);
    return typesByName.get(name);
  }

  /**
   * Returns all nominal types that have the given JSType.
   */
  public List<NominalType2> getTypes(JSType type) {
    return Collections.unmodifiableList(typesByJsType.get(type));
  }

  /**
   * Returns all registered types.
   */
  public Collection<NominalType2> getAllTypes() {
    return Collections.unmodifiableCollection(typesByName.values());
  }
  
  @Nullable
  @CheckReturnValue
  public NominalType2 resolveType(String name) {
    if (typesByName.containsKey(name)) {
      return typesByName.get(name);
    }
    return null;
  }
  
  public List<JSType> getTypeHierarchy(JSType type, JSTypeRegistry jsRegistry) {
    List<JSType> stack = new ArrayList<>();
    for (; type != null; type = getBaseType(type, jsRegistry)) {
      JSType toAdd = type;
      if (toAdd.isConstructor()) {
        toAdd = ((FunctionType) toAdd).getInstanceType();
      }
      stack.add(toAdd);
    }
    return stack;
  }

  @Nullable
  private JSType getBaseType(JSType type, JSTypeRegistry jsRegistry) {
    JSDocInfo info = type.getJSDocInfo();
    if (info == null) {
      return null;
    }
    JSTypeExpression baseType = info.getBaseType();
    if (baseType == null) {
      return null;
    }
    type = baseType.evaluate(null, jsRegistry);
    if (type instanceof NamedType) {
      String name = ((NamedType) type).getReferenceName();
      if (typesByName.containsKey(name)) {
        return typesByName.get(name).getType();
      }
      throw new AssertionError("Need to resolve " + name);
    }
    return type;
  }

  /**
   * Returns the interfaces directly implemented by the given type.
   */
  public ImmutableSet<JSType> getDeclaredInterfaces(JSType jsType, JSTypeRegistry jsRegistry) {
    Iterable<JSTypeExpression> interfaces;
    if (jsType.isConstructor() && jsType.getJSDocInfo() != null) {
      interfaces = jsType.getJSDocInfo().getImplementedInterfaces();

    } else if (jsType.isInterface() && jsType.getJSDocInfo() != null) {
      interfaces = jsType.getJSDocInfo().getExtendedInterfaces();

    } else if (jsType.isInstanceType()) {
      JSType ctorType = ((ObjectType) jsType).getConstructor();
      return ctorType == null
          ? ImmutableSet.<JSType>of() : getDeclaredInterfaces(ctorType, jsRegistry
      );

    } else {
      return ImmutableSet.of();
    }

    ImmutableSet.Builder<JSType> builder = ImmutableSet.builder();
    for (JSTypeExpression expr : interfaces) {
      builder.add(expr.evaluate(null, jsRegistry));
    }
    return builder.build();
  }

  /**
   * Returns the interfaces implemented by the given type. If the type is itself an interface, this
   * will return the interfaces it extends.
   */
  public ImmutableSet<JSType> getImplementedTypes(
      NominalType2 nominalType, JSTypeRegistry jsRegistry) {
    JSType type = nominalType.getType();
    ImmutableSet.Builder<JSType> builder = ImmutableSet.builder();
    if (type.isConstructor()) {
      for (JSType jsType : getTypeHierarchy(type, jsRegistry)) {
        if (jsType.getJSDocInfo() != null) {
          for (JSTypeExpression expr : jsType.getJSDocInfo().getImplementedInterfaces()) {
            JSType exprType = expr.evaluate(null, jsRegistry);
            if (exprType.getJSDocInfo() != null) {
              builder.addAll(getExtendedInterfaces(exprType.getJSDocInfo(), jsRegistry));
            }
            builder.add(exprType);
          }

        } else if (jsType.isInstanceType()) {
          Collection<NominalType2> types = getTypes(jsType);
          if (!types.isEmpty()) {
            NominalType2 nt = types.iterator().next();
            if (nt != nominalType) {
              builder.addAll(getImplementedTypes(nt, jsRegistry));
            }
          }
        }
      }
    } else if (type.isInterface()) {
      builder.addAll(getExtendedInterfaces(nominalType.getJsDoc().getInfo(), jsRegistry));
    }
    return builder.build();
  }
  
  private Set<JSType> getExtendedInterfaces(JSDocInfo jsdoc, JSTypeRegistry jsRegistry) {
    Set<JSType> interfaces = new HashSet<>();
    for (JSTypeExpression expr : jsdoc.getExtendedInterfaces()) {
      JSType type = expr.evaluate(null, jsRegistry);
      if (interfaces.add(type) && type.getJSDocInfo() != null) {
        interfaces.addAll(getExtendedInterfaces(type.getJSDocInfo(), jsRegistry));
      }
    }
    return interfaces;
  }
}
