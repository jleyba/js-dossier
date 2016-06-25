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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Multimaps.filterKeys;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.jstype.JSType;

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
import javax.inject.Singleton;

/**
 * Dossier's internal type registry.
 */
@Singleton
public final class TypeRegistry {

  private final Set<String> providedSymbols = new HashSet<>();
  private final Set<String> implicitNamespaces = new HashSet<>();
  private final Map<String, Module> modulesById = new HashMap<>();
  private final Map<Path, Module> modulesByPath = new HashMap<>();
  private final Map<Path, JSDocInfo.Visibility> defaultVisibilities = new HashMap<>();
  private final Multimap<Path, AliasRegion> aliasRegions =
      MultimapBuilder.hashKeys().linkedHashSetValues().build();
  private final Map<String, NominalType> typesByName = new HashMap<>();
  private final ListMultimap<JSType, NominalType> typesByJsType = Multimaps.newListMultimap(
      new IdentityHashMap<JSType, Collection<NominalType>>(),
      new Supplier<List<NominalType>>() {
        @Override
        public List<NominalType> get() {
          return new ArrayList<>();
        }
      });

  private final SetMultimap<NominalType, NominalType> nestedTypes =
      MultimapBuilder.hashKeys().hashSetValues().build();

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
  @Nullable
  @CheckReturnValue
  public String resolveAlias(NominalType type, String key) {
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
    if (module.getType() == Module.Type.CLOSURE && module.getHasLegacyNamespace()) {
      recordImplicitProvide(module.getOriginalName());
    }
    modulesById.put(module.getId(), module);
    modulesByPath.put(module.getPath(), module);
    addAliasRegion(module.getAliases());
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
   * Returns whether the given type is registered as a module's exports.
   */
  public boolean isModule(JSType type) {
    for (NominalType ntype : getTypes(type)) {
      if (ntype.isModuleExports()) {
        return true;
      }
    }
    return false;
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
   * Returns all registered modules.
   */
  public Collection<Module> getAllModules() {
    return Collections.unmodifiableCollection(modulesById.values());
  }

  /**
   * Records a symbol declared by a "goog.provide" statement.
   */
  public void recordProvide(String symbol) {
    providedSymbols.add(symbol);
    recordImplicitProvide(symbol);
  }

  private void recordImplicitProvide(String symbol) {
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
   */
  public boolean isProvided(String symbol) {
    return providedSymbols.contains(symbol) || implicitNamespaces.contains(symbol);
  }

  /**
   * Returns whether a symbol identifies a namespace implicitly created by a "goog.provide" or
   * "goog.module" statement. For example, {@code goog.module('foo.bar.baz')} implicitly creates
   * the "foo" and "foo.bar" namespaces.
   */
  public boolean isImplicitNamespace(String symbol) {
    return !providedSymbols.contains(symbol) && implicitNamespaces.contains(symbol);
  }

  /**
   * Registers a nominal type.
   */
  public void addType(NominalType type) {
    checkArgument(!typesByName.containsKey(type.getName()),
        "A type with name %s has already been defined", type.getName());
    typesByName.put(type.getName(), type);
    typesByJsType.put(type.getType(), type);

    int index = type.getName().lastIndexOf('.');
    if (index != -1) {
      String parentName = type.getName().substring(0, index);
      if (isType(parentName)) {
        nestedTypes.put(getType(parentName), type);
      }
    }
  }

  /**
   * Returns whether there is a type registered with the given name.
   */
  public boolean isType(String name) {
    return typesByName.containsKey(name);
  }

  /**
   * Returns the nominal type with the given name.
   *
   * @throws IllegalArgumentException if there is no such type.
   */
  public NominalType getType(String name) {
    checkArgument(isType(name), "no such type: %s", name);
    return typesByName.get(name);
  }

  /**
   * Returns all nominal types that have the given JSType.
   */
  public List<NominalType> getTypes(JSType type) {
    return Collections.unmodifiableList(typesByJsType.get(type));
  }

  /**
   * Finds all nominal types whose underlying JSType is <em>equivalent</em> to the given type. This
   * stands in contrast to {@link #getTypes(JSType)}, which returns the nominal types with the
   * exact JSType.
   */
  public Collection<NominalType> findTypes(final JSType type) {
    Predicate<JSType> predicate = new Predicate<JSType>() {
      @Override
      public boolean apply(JSType input) {
        return typesEqual(type, input);
      }
    };
    Multimap<JSType, NominalType> filtered = filterKeys(typesByJsType, predicate);
    return Collections.unmodifiableCollection(filtered.values());
  }

  private static boolean typesEqual(JSType a, JSType b) {
    if (a.equals(b)) {
      // NOTE: FunctionTypes are considered equal if they have the same
      // signature. This works for type checking, but we are looking for unique
      // nominal types - so fallback on a strict identity check. This relies
      // on insight gained from a comment in JSType#checkEquivalenceHelper:
      //
      // Relies on the fact that for the base {@link JSType}, only one
      // instance of each sub-type will ever be created in a given registry, so
      // there is no need to verify members. If the object pointers are not
      // identical, then the type member must be different.
      if (a.isFunctionType()) {
        verify(b.isFunctionType());
        return a == b;
      }
      return true;
    }
    // We consider the following two versions of a constructor to be equivalent,
    // even though the compiler does not:
    //   function(new: Foo): undefined
    //   function(new: Foo): ?
    if (a.isConstructor() && b.isConstructor()
        && a.toMaybeFunctionType() != null
        && b.toMaybeFunctionType() != null) {
      a = a.toMaybeFunctionType().getInstanceType();
      b = b.toMaybeFunctionType().getInstanceType();
      if (a != null && b != null) {
        return typesEqual(a, b);
      }
    }
    return false;
  }

  /**
   * Returns all registered types.
   */
  public Collection<NominalType> getAllTypes() {
    return Collections.unmodifiableCollection(typesByName.values());
  }

  /**
   * Returns all types nested under another.
   */
  public Set<NominalType> getNestedTypes(NominalType type) {
    return Collections.unmodifiableSet(nestedTypes.get(type));
  }

  /**
   * Sets the default visibility for the given source file.
   */
  public void setDefaultVisibility(Path path, Visibility visibility) {
    defaultVisibilities.put(
        checkNotNull(path, "null path"),
        checkNotNull(visibility, "null visibility"));
  }

  /**
   * Returns the effective visibility for the given type.
   */
  public Visibility getDefaultVisibility(Path path) {
    if (defaultVisibilities.containsKey(path)) {
      return defaultVisibilities.get(path);
    }
    return Visibility.PUBLIC;
  }

  /**
   * Returns the effective visibility for the given type.
   */
  public Visibility getVisibility(NominalType type) {
    JsDoc docs = type.getJsDoc();
    Visibility visibility = docs.getVisibility();

    if (visibility == Visibility.INHERITED
        && defaultVisibilities.containsKey(type.getSourceFile())) {
      visibility = defaultVisibilities.get(type.getSourceFile());
    }

    if (visibility == Visibility.INHERITED) {
      visibility = Visibility.PUBLIC;
    }
    return visibility;
  }
}
