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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Multimaps.filterKeys;

import com.github.jsdossier.annotations.Global;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Dossier's internal type registry. */
@Singleton
public final class TypeRegistry {

  private final Set<String> providedSymbols = new HashSet<>();
  private final Set<String> implicitNamespaces = new HashSet<>();

  private final Map<Module.Id, Module> modulesById = new HashMap<>();
  private final Map<Path, Module> modulesByPath = new HashMap<>();

  private final Map<Path, JSDocInfo.Visibility> defaultVisibilities = new HashMap<>();
  private final Multimap<Path, AliasRegion> aliasRegions =
      MultimapBuilder.hashKeys().linkedHashSetValues().build();
  private final Map<String, NominalType> typesByName = new HashMap<>();
  private final ListMultimap<JSType, NominalType> typesByJsType =
      Multimaps.newListMultimap(new IdentityHashMap<>(), ArrayList::new);

  private final Map<String, NominalType> resolvedModuleContentAliases = new HashMap<>();

  private final SetMultimap<NominalType, NominalType> nestedTypes =
      MultimapBuilder.hashKeys().hashSetValues().build();

  private final SetMultimap<FunctionType, ObjectType> subInterfaces =
      MultimapBuilder.hashKeys().hashSetValues().build();
  private final SetMultimap<FunctionType, ObjectType> knownImplementations =
      MultimapBuilder.hashKeys().hashSetValues().build();
  private final SetMultimap<FunctionType, ObjectType> implementedInterfaces =
      MultimapBuilder.hashKeys().linkedHashSetValues().build();
  private final SetMultimap<FunctionType, JSType> directSubtypes =
      MultimapBuilder.hashKeys().hashSetValues().build();
  private final ListMultimap<FunctionType, JSType> typeHierarchy =
      MultimapBuilder.hashKeys().arrayListValues().build();

  private final SymbolTable symbolTable;

  @Inject
  TypeRegistry(@Global SymbolTable symbolTable) {
    this.symbolTable = symbolTable;
  }

  /** Records a region of a file that defines variable aliases. */
  public void addAliasRegion(AliasRegion region) {
    aliasRegions.put(region.getPath(), region);
  }

  /** Returns the alias regions defined for the file with the given path. */
  public Collection<AliasRegion> getAliasRegions(Path path) {
    return aliasRegions.get(path);
  }

  /**
   * Iterates over all registered node and closure modules, collecting the names of internal
   * variables that are aliases for other types. This is used for fast lookup in {@link
   * #resolveAlias(NominalType, String)}.
   *
   * @param jsRegistry The JS registry to use when resolving aliases.
   */
  public void collectModuleContentAliases(JSTypeRegistry jsRegistry) {
    for (Module module : getAllModules()) {
      if (module.getId().getType() == Module.Type.ES6) {
        continue;
      }

      AliasRegion aliasRegion = module.getAliases();
      for (String alias : aliasRegion.getAliases()) {
        String name = aliasRegion.resolveAlias(alias);
        if (Types.isModuleContentsVar(alias)) {
          if (isType(name)) {
            resolvedModuleContentAliases.put(alias, getType(name));
          } else {
            JSType type = jsRegistry.getGlobalType(name);
            if (type != null) {
              Iterator<NominalType> types = getTypes(type).iterator();
              if (types.hasNext()) {
                resolvedModuleContentAliases.put(alias, types.next());
              }
            }
          }
        }
      }
    }
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
    SymbolTable table = symbolTable.findTableFor(type.getNode());
    return resolveAlias(table, key);
  }

  @Nullable
  @CheckReturnValue
  String resolveAlias(SymbolTable table, String name) {
    String resolved = resolveAlias(table, name, new HashSet<>());
    if (resolved != null && !name.equals(resolved)) {
      return resolved;
    }
    return null;
  }

  @Nullable
  @CheckReturnValue
  private String resolveAlias(SymbolTable table, String name, Set<String> seen) {
    if (!seen.add(name)) {
      return null;
    }

    Symbol symbol = table.getSlot(name);
    if (symbol == null) {
      int index = name.indexOf('.');
      if (index > 0) {
        String base = name.substring(0, index);
        String resolvedBase = resolveAlias(table, base, seen);
        if (resolvedBase != null) {
          return resolvedBase + "." + name.substring(index + 1);
        }
      }
      return null;
    }

    String reference = symbol.getReferencedSymbol();
    if (reference == null) {
      return symbol.getName();
    }

    // If the name is an exported property from a module, it may reference a value
    // that's internal to the module. If this is the case, we stop and use the
    // qualified name of the export.
    // If the reference is exported by a module, we can resolve directly.
    int index = name.indexOf('.');
    if (index > 0) {
      String base = name.substring(0, index);
      if (symbolTable.getModuleById(base) != null) {
        return name;
      }
    }

    return resolveAlias(table, symbol.getReferencedSymbol(), seen);
  }

  /** Registers a new module. */
  public void addModule(Module module) {
    if (module.getId().getType() == Module.Type.CLOSURE && module.getHasLegacyNamespace()) {
      recordImplicitProvide(module.getId().getOriginalName());
    }
    modulesById.put(module.getId(), module);
    modulesByPath.put(module.getId().getPath(), module);
    addAliasRegion(module.getAliases());
  }

  /** Returns whether there is a module registered with the given ID. */
  public boolean isModule(String id) {
    return findModule(id).isPresent();
  }

  private Optional<Map.Entry<Module.Id, Module>> findModule(String id) {
    return modulesById
        .entrySet()
        .stream()
        .filter(e -> e.getKey().getCompiledName().equals(id))
        .findFirst();
  }

  /** Returns whether the given path defines a module. */
  public boolean isModule(Path path) {
    return modulesByPath.containsKey(path);
  }

  /** Returns whether the given type is registered as a module's exports. */
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
    return findModule(id)
        .orElseThrow(() -> new IllegalArgumentException("no such module: " + id))
        .getValue();
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

  /** Returns all registered modules. */
  public Collection<Module> getAllModules() {
    return Collections.unmodifiableCollection(modulesById.values());
  }

  /** Records a symbol declared by a "goog.provide" statement. */
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

  /** Returns whether the provided symbol was declared with a "goog.provide" statement. */
  public boolean isProvided(String symbol) {
    return providedSymbols.contains(symbol);
  }

  /**
   * Returns whether a symbol identifies a namespace implicitly created by a "goog.provide" or
   * "goog.module" statement. For example, {@code goog.module('foo.bar.baz')} implicitly creates the
   * "foo" and "foo.bar" namespaces.
   */
  public boolean isImplicitNamespace(String symbol) {
    return !providedSymbols.contains(symbol) && implicitNamespaces.contains(symbol);
  }

  /** Registers a nominal type. */
  public void addType(NominalType type) {
    checkArgument(
        !typesByName.containsKey(type.getName()),
        "A type with name %s has already been defined",
        type.getName());
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

  /** Returns whether there is a type registered with the given name. */
  public boolean isType(String name) {
    return typesByName.containsKey(name) || resolvedModuleContentAliases.containsKey(name);
  }

  /** Returns all of type names in the registry. */
  public Set<String> getTypeNames() {
    return Collections.unmodifiableSet(typesByName.keySet());
  }

  /** Returns the nominal type representing the exports object for the specified module. */
  public NominalType getType(Module.Id id) {
    return getType(id.getCompiledName());
  }

  /**
   * Returns the nominal type with the given name.
   *
   * @throws IllegalArgumentException if there is no such type.
   */
  public NominalType getType(String name) {
    checkArgument(isType(name), "no such type: %s", name);
    if (typesByName.containsKey(name)) {
      return typesByName.get(name);
    } else if (resolvedModuleContentAliases.containsKey(name)) {
      return resolvedModuleContentAliases.get(name);
    } else {
      throw new AssertionError();
    }
  }

  /** Returns all nominal types that have the given JSType. */
  public List<NominalType> getTypes(JSType type) {
    return Collections.unmodifiableList(typesByJsType.get(type));
  }

  /**
   * Finds all nominal types whose underlying JSType is <em>equivalent</em> to the given type. This
   * stands in contrast to {@link #getTypes(JSType)}, which returns the nominal types with the exact
   * JSType.
   */
  public Collection<NominalType> findTypes(final JSType type) {
    Multimap<JSType, NominalType> filtered =
        filterKeys(typesByJsType, input -> typesEqual(type, input));
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
        @SuppressWarnings("ReferenceEquality") // Yes, we want to check for reference equality.
        final boolean tmp = a == b;
        return tmp;
      }
      return true;
    }
    // We consider the following two versions of a constructor to be equivalent,
    // even though the compiler does not:
    //   function(new: Foo): undefined
    //   function(new: Foo): ?
    if (a.isConstructor()
        && b.isConstructor()
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

  /** Returns all registered types. */
  public Collection<NominalType> getAllTypes() {
    return Collections.unmodifiableCollection(typesByName.values());
  }

  /** Returns all types nested under another. */
  public Set<NominalType> getNestedTypes(NominalType type) {
    return Collections.unmodifiableSet(nestedTypes.get(type));
  }

  /** Sets the default visibility for the given source file. */
  public void setDefaultVisibility(Path path, Visibility visibility) {
    defaultVisibilities.put(
        checkNotNull(path, "null path"), checkNotNull(visibility, "null visibility"));
  }

  /** Returns the effective visibility for the given type. */
  public Visibility getDefaultVisibility(Path path) {
    if (defaultVisibilities.containsKey(path)) {
      return defaultVisibilities.get(path);
    }
    return Visibility.PUBLIC;
  }

  /** Returns the effective visibility for the given type. */
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

  /** Returns all known implementations of the given interface. */
  public ImmutableSet<ObjectType> getKnownImplementations(FunctionType type) {
    return ImmutableSet.copyOf(knownImplementations.get(type));
  }

  /** Returns all known sub-interfaces of the given interface. */
  public ImmutableSet<ObjectType> getSubInterfaces(FunctionType type) {
    return ImmutableSet.copyOf(subInterfaces.get(type));
  }

  /**
   * Returns all known direct subtypes for the given type. An empty set will be returned if the type
   * is not a constructor.
   */
  public ImmutableSet<JSType> getDirectSubTypes(FunctionType type) {
    return ImmutableSet.copyOf(directSubtypes.get(type));
  }

  /**
   * Returns the interfaces implemented by the given type. If the type is itself an interface, the
   * return set will include the interfaces it extends.
   *
   * <p>Note the returned set contains instances of {@link ObjectType} instead of {@link
   * NominalType} as each type may be an external type.
   */
  public ImmutableSet<ObjectType> getImplementedInterfaces(JSType type) {
    if (type.toMaybeFunctionType() == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.copyOf(implementedInterfaces.get(type.toMaybeFunctionType()));
  }

  /**
   * Returns the hierarchy for the given type, starting from the type itself and going up to, but
   * not including, the native Object type (Object is excluded as it is implied for all JS types).
   */
  public ImmutableList<JSType> getTypeHierarchy(JSType type) {
    if (type.toMaybeFunctionType() == null) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(typeHierarchy.get(type.toMaybeFunctionType()));
  }

  /**
   * Recomputes the type hierarchy relationships for all nominal types in this registry using the
   * given global scope and JS registry.
   */
  public void computeTypeRelationships(
      StaticTypedScope globalScope, JSTypeRegistry jsRegistry) {
    checkArgument(globalScope.getParentScope() == null, "not a global scope");

    knownImplementations.clear();
    subInterfaces.clear();
    directSubtypes.clear();
    implementedInterfaces.clear();

    Set<FunctionType> processed = new HashSet<>();
    for (NominalType nominalType : typesByName.values()) {
      JSType jsType = nominalType.getType();
      if (!jsType.isConstructor() && !jsType.isInterface()) {
        continue;
      }

      FunctionType ctor = jsType.toMaybeFunctionType();
      if (ctor == null || !processed.add(ctor)) {
        continue;
      }

      if (ctor.isInterface()) {
        scanExtendedInterfaces(new HashSet<>(), ctor);

      } else {
        scanImplementedInterfaces(ctor);
        computeTypeHiearchy(ctor, globalScope, jsRegistry);
      }
    }
  }

  private void computeTypeHiearchy(
      final FunctionType ctor, StaticTypedScope globalScope, JSTypeRegistry jsRegistry) {
    checkArgument(ctor.isConstructor());

    List<JSType> types = new ArrayList<>();
    FunctionType currentCtor = ctor;
    JSType currentInstance = getInstanceType(jsRegistry, ctor);
    while (currentInstance != null
        && currentCtor != null
        && currentCtor.getSuperClassConstructor() != null) {
      types.add(currentInstance);

      JSType superInstance =
          getSuperInstance(
              currentInstance.toMaybeObjectType(), currentCtor, globalScope, jsRegistry);
      if (superInstance == null || superInstance.toMaybeObjectType() == null) {
        break;
      }

      FunctionType superCtor = superInstance.toMaybeObjectType().getConstructor();
      if (superCtor != null) {
        directSubtypes.put(superCtor, getInstanceType(jsRegistry, currentCtor));
      }
      currentInstance = superInstance;
      currentCtor = superCtor;
    }
    typeHierarchy.putAll(ctor, types);
  }

  private JSType getInstanceType(final JSTypeRegistry jsRegistry, FunctionType ctor) {
    ObjectType instance = ctor.getInstanceType();
    if (ctor.getJSDocInfo() != null && !ctor.getJSDocInfo().getTemplateTypeNames().isEmpty()) {
      ImmutableList<JSType> templateTypes =
          ctor.getJSDocInfo()
              .getTemplateTypeNames()
              .stream()
              .map(jsRegistry::createTemplateType)
              .collect(toImmutableList());
      instance = jsRegistry.createTemplatizedType(instance, templateTypes);
    }
    return instance;
  }

  private JSType getSuperInstance(
      ObjectType instance,
      FunctionType ctor,
      StaticTypedScope globalScope,
      JSTypeRegistry jsRegistry) {
    JSType superInstance;
    if (ctor.getJSDocInfo() != null && ctor.getJSDocInfo().getBaseType() != null) {
      jsRegistry.setTemplateTypeNames(instance.getTemplateTypeMap().getTemplateKeys());

      JSTypeExpression baseTypeExpression = ctor.getJSDocInfo().getBaseType();
      superInstance = Types.evaluate(baseTypeExpression, globalScope, jsRegistry);
      jsRegistry.clearTemplateTypeNames();

      // The type expression will resolve to a named type if it is an aliased reference to
      // a module's exported type. Compensate by checking dossier's type registry, which
      // tracks exported types by their exported name (whereas the compiler tracks them by
      // their initially declared name from within the module).
      if (superInstance.isNamedType()
          && isType(superInstance.toMaybeNamedType().getReferenceName())) {
        superInstance = getType(superInstance.toMaybeNamedType().getReferenceName()).getType();
        if (superInstance.isConstructor() || superInstance.isInterface()) {
          superInstance = superInstance.toMaybeFunctionType().getTypeOfThis();
        }
      }

    } else {
      FunctionType superCtor = ctor.getSuperClassConstructor();
      if (superCtor == null) {
        return null;
      }
      superInstance = superCtor.getTypeOfThis();
    }
    return superInstance;
  }

  private void scanImplementedInterfaces(FunctionType ctor) {
    checkArgument(ctor.isConstructor());

    Set<FunctionType> seen = new HashSet<>();
    for (ObjectType iface : ctor.getAllImplementedInterfaces()) {
      if (iface.isUnknownType()
          || iface.getConstructor() == null
          || !iface.getConstructor().isInterface()) {
        continue;
      }

      implementedInterfaces.put(ctor, iface);
      knownImplementations.put(iface.getConstructor(), ctor.getInstanceType());

      scanExtendedInterfaces(seen, iface.getConstructor());

      for (ObjectType superInterface : implementedInterfaces.get(iface.getConstructor())) {
        implementedInterfaces.put(ctor, superInterface);
        knownImplementations.put(superInterface.getConstructor(), ctor.getInstanceType());
      }
    }
  }

  private void scanExtendedInterfaces(Set<FunctionType> seenCtors, FunctionType type) {
    checkArgument(type.isInterface());

    for (ObjectType iface : type.getExtendedInterfaces()) {
      if (iface.isUnknownType()) {
        continue;
      }

      checkState(
          iface.getConstructor().isInterface(), "unexpected type: %s", iface.getConstructor());

      if (seenCtors.add(iface.getConstructor())) {
        scanExtendedInterfaces(seenCtors, iface.getConstructor());

        implementedInterfaces.put(type, iface);
        subInterfaces.put(iface.getConstructor(), type.getInstanceType());
        for (ObjectType superInterface : implementedInterfaces.get(iface.getConstructor())) {
          implementedInterfaces.put(type, superInterface);
          subInterfaces.put(superInterface.getConstructor(), type.getInstanceType());
        }
      }
    }
  }
}
