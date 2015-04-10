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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.NamedType;

import com.github.jsdossier.NominalType.TypeDescriptor;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class TypeRegistry {

  private static final String INTERNAL_NAMESPACE_VAR = "$jscomp";

  private final JSTypeRegistry jsTypeRegistry;

  private final Set<String> providedSymbols = new HashSet<>();
  private final Set<String> implicitNamespaces = new HashSet<>();
  private final Map<String, ModuleDescriptor> moduleDescriptors = new HashMap<>();

  private final Map<Path, JsDoc> fileOverviews = new HashMap<>();
  private final Map<String, NominalType> nominalTypes = new HashMap<>();
  private final Map<String, NominalType> moduleExports = new HashMap<>();
  private final Map<ModuleDescriptor, NominalType> moduleToExports = new IdentityHashMap<>();
  private final Set<NominalType> allTypes = new HashSet<>();
  private final Map<String, NominalType> nameToModuleTypes = new HashMap<>();
  private final Set<TypeDescriptor> allDescriptors = Sets.newIdentityHashSet();

  private final Map<String, JSType> externsByName = new HashMap<>();
  private final Set<JSType> externs = new HashSet<>();

  public TypeRegistry(JSTypeRegistry jsTypeRegistry) {
    this.jsTypeRegistry = checkNotNull(jsTypeRegistry, "null JSTypeRegistry");
  }

  public static boolean isInternalNamespaceVar(String name) {
    return name.startsWith(INTERNAL_NAMESPACE_VAR);
  }

  /**
   * Evaluates a type expression to a JSType.
   *
   * @param expression the expression to evaluate.
   * @return the evaluated type.
   */
  public JSType evaluate(JSTypeExpression expression) {
    return expression.evaluate(null, jsTypeRegistry);
  }

  /**
   * Forward declares a module, but does <em>not</em> define it as a nominal type.
   */
  public void declareModule(ModuleDescriptor module) {
    moduleDescriptors.put(module.getName(), module);
    recordGoogProvide(module.getName());
  }

  @Nullable
  public ModuleDescriptor getModuleDescriptor(String name) {
    return moduleDescriptors.get(name);
  }

  public void recordGoogProvide(String name) {
    providedSymbols.add(name);
    implicitNamespaces.add(name);
    for (int i = name.lastIndexOf('.'); i != -1; i = name.lastIndexOf('.')) {
      name = name.substring(0, i);
      implicitNamespaces.add(name);
    }
  }

  public Set<String> getProvidedSymbols() {
    return Collections.unmodifiableSet(providedSymbols);
  }

  public Set<String> getImplicitNamespaces() {
    return Collections.unmodifiableSet(implicitNamespaces);
  }

  public boolean hasNamespace(String name) {
    return implicitNamespaces.contains(name);
  }

  public void addExtern(String name, JSType type) {
    externsByName.put(name, type);
    externs.add(type);
  }

  public Set<String> getExternNames() {
    return Collections.unmodifiableSet(externsByName.keySet());
  }

  public boolean isExtern(JSType type) {
    return externs.contains(type);
  }

  public void recordFileOverview(Path path, JsDoc jsdoc) {
    fileOverviews.put(path, jsdoc);
  }

  @Nullable
  public JsDoc getFileOverview(Path path) {
    return fileOverviews.get(path);
  }

  public void addType(NominalType type) {
    if (findTypeDescriptor(type.getJsType()) == null) {
      verify(allTypes.add(type));
    }

    if (type.getJsdoc() == null || !type.getJsdoc().isTypedef()) {
      allDescriptors.add(type.getTypeDescriptor());
    }

    if (type.getModule() != null) {
      registerModuleExports(type);
    }

    if (type.isModuleExports() && type.isCommonJsModule()) {
      checkArgument(!moduleToExports.containsKey(type.getModule()),
          "Module already registerd %s", type.getModule().getName());
      moduleToExports.put(type.getModule(), type);
      moduleExports.put(type.getQualifiedName(false), type);
    } else if (!type.isCommonJsModule()) {
      nominalTypes.put(type.getQualifiedName(true), type);
    }
  }

  private void registerModuleExports(NominalType type) {
    String qualifiedName = type.getQualifiedName(true);
    nameToModuleTypes.put(qualifiedName, type);

    JSType jsType = type.getJsType();
    if (jsType.isObject() && jsType.toObjectType().hasReferenceName()) {
      String referenceName = jsType.toObjectType().getReferenceName();
      if (referenceName.startsWith(INTERNAL_NAMESPACE_VAR)) {
        nameToModuleTypes.put(referenceName, type);
      }
    }

    // If there are any known subtypes (e.g. |type| is an alias), register them for future lookup.
    registerModuleTypes(qualifiedName, type.getTypes());
  }

  private void registerModuleTypes(String baseName, Iterable<NominalType> types) {
    for (NominalType type : types) {
      String name = baseName + "." + type.getName();
      nameToModuleTypes.put(name, type);
      registerModuleTypes(name, type.getTypes());
    }
  }

  @Nullable
  public NominalType.TypeDescriptor findTypeDescriptor(JSType type) {
    for (NominalType.TypeDescriptor descriptor : allDescriptors) {
      if (typesEqual(type, descriptor.toJSType())) {
        return descriptor;
      }
    }
    return null;
  }

  @VisibleForTesting Map<String, NominalType> getNominalTypeMap() {
    return nominalTypes;
  }

  public Collection<NominalType> getNominalTypes() {
    return Collections.unmodifiableCollection(nominalTypes.values());
  }

  @Nullable
  public NominalType getNominalType(String name) {
    return nominalTypes.get(name);
  }

  @Nullable
  public NominalType getModuleType(String name) {
    return nameToModuleTypes.get(name);
  }

  public Collection<NominalType>
  getModules() {
    return Collections.unmodifiableCollection(moduleExports.values());
  }

  /**
   * Returns the first {@link NominalType} whose underlying {@link JSType} is equivalent to the
   * given type.
   */
  @Nullable
  public NominalType resolve(JSType type) {
    if (type instanceof ObjectTypeI && ((ObjectTypeI) type).getConstructor() != null) {
      type = type.toMaybeObjectType().getConstructor();
    }
    Iterator<NominalType> candidates = FluentIterable.from(allTypes)
        .filter(hasType(type))
        .iterator();
    return candidates.hasNext() ? candidates.next() : null;
  }

  private static boolean typesEqual(JSType a, JSType b) {
    if (a.equals(b)) {
      // NOTE: FunctionTypes are considered equalivalent if they have the same
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
    // Sometimes the JSCompiler will generate two version of a constructor:
    //   function(new: Foo): undefined
    //   function(new: Foo): ?
    // We consider these equivalent even though technically they are not
    // (I'm not sure how the return type of a constructor could be unknown).
    if (a.isConstructor() && b.isConstructor()) {
      a = a.toMaybeFunctionType().getInstanceType();
      b = b.toMaybeFunctionType().getInstanceType();
      return typesEqual(a, b);
    }
    return false;
  }

  private static Predicate<NominalType> hasType(final JSType type) {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(@Nullable NominalType input) {
        return input != null && typesEqual(input.getJsType(), type);
      }
    };
  }

  /**
   * Returns the type hierarchy for the given type as a stack with the type at the
   * bottom and the root ancestor at the top (Object is excluded as it is implied).
   */
  public LinkedList<JSType> getTypeHierarchy(JSType type) {
    LinkedList<JSType> stack = new LinkedList<>();
    for (; type != null; type = getBaseType(type)) {
      stack.push(type);
    }
    return stack;
  }

  @Nullable
  private JSType getBaseType(JSType type) {
    JSDocInfo info = type.getJSDocInfo();
    if (info == null) {
      return null;
    }
    JSTypeExpression baseType = info.getBaseType();
    if (baseType == null) {
      return null;
    }
    type = evaluate(baseType);
    if (type instanceof NamedType) {
      String name = ((NamedType) type).getReferenceName();
      if (nominalTypes.containsKey(name)) {
        return nominalTypes.get(name).getJsType();
      }
      if (nameToModuleTypes.containsKey(name)) {
        return nameToModuleTypes.get(name).getJsType();
      }
    }
    return type;
  }

  /**
   * Returns the interfaces implemented by the given type. If the type is itself an interface, this
   * will return its super types.
   */
  public ImmutableSet<JSType> getImplementedTypes(NominalType nominalType) {
    JSType type = nominalType.getJsType();
    ImmutableSet.Builder<JSType> builder = ImmutableSet.builder();
    if (type.isConstructor()) {
      for (JSType jsType : getTypeHierarchy(type)) {
        if (jsType.getJSDocInfo() != null) {
          for (JSTypeExpression expr : jsType.getJSDocInfo().getImplementedInterfaces()) {
            builder.add(evaluate(expr));
          }
        } else if (jsType.isInstanceType()) {
          NominalType resolved = resolve(jsType);
          if (resolved != null && resolved != nominalType) {
            builder.addAll(getImplementedTypes(resolved));
          }
        }
      }
    } else if (type.isInterface() && nominalType.getJsdoc() != null) {
      builder.addAll(getExtendedInterfaces(nominalType.getJsdoc().getInfo()));
    }
    return builder.build();
  }

  private Set<JSType> getExtendedInterfaces(JSDocInfo info) {
    Set<JSType> interfaces = new HashSet<>();
    for (JSTypeExpression expr : info.getExtendedInterfaces()) {
      JSType type = expr.evaluate(null, jsTypeRegistry);
      if (interfaces.add(type) && type.getJSDocInfo() != null) {
        interfaces.addAll(getExtendedInterfaces(type.getJSDocInfo()));
      }
    }
    return interfaces;
  }
}
