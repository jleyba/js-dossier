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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.StaticTypedScope;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Describes a named type to be documented.
 */
public final class NominalType implements StaticTypedScope<JSType> {

  static class TypeDescriptor {
    private final JSType type;
    private final Map<String, NominalType> types = new HashMap<>();
    private final Map<String, Property> properties = new HashMap<>();
    private final Set<NominalType> aliases = new HashSet<>();

    public TypeDescriptor(JSType type) {
      this.type = checkNotNull(type, "null JSType");
    }

    @Override public boolean equals(Object o) {
      if (o instanceof TypeDescriptor) {
        TypeDescriptor that = (TypeDescriptor) o;
        return this.type.equals(that.type);
      }
      return false;
    }
    @Override public int hashCode() { return type.hashCode(); }
    @Override public String toString() { return type.toString(); }

    public JSType toJSType() {
      return type;
    }

    public Set<NominalType> getAliases() {
      return Collections.unmodifiableSet(aliases);
    }
  }

  private final String name;
  private final TypeDescriptor typeDescriptor;
  private final Node node;
  private final JsDoc jsdoc;
  private final ModuleDescriptor module;
  private final NominalType parent;

  private final Map<String, Object> attributes = new HashMap<>();

  NominalType(
      @Nullable NominalType parent,
      String name,
      TypeDescriptor typeDescriptor,
      Node node,
      @Nullable JsDoc jsdoc,
      @Nullable ModuleDescriptor module) {
    this.name = name;
    this.typeDescriptor = typeDescriptor;
    this.node = node;
    this.jsdoc = jsdoc;
    this.parent = parent;
    this.module = module;

    typeDescriptor.aliases.add(this);

    if (parent != null) {
      parent.typeDescriptor.types.put(name, this);
    }
    checkArgument(
        !isCommonJsModule() || !isModuleExports() || parent == null,
        "CommonJS module export objects may not have a parent: %s", name);
  }

  @Override
  public String toString() {
    return String.format("NominalType(%s, %s)", name, typeDescriptor.type);
  }

  @Override
  public Node getRootNode() {
    return node;
  }

  @Override
  public StaticTypedScope<JSType> getParentScope() {
    return null;
  }

  @Override
  public Property getSlot(String name) {
    return getOwnSlot(name);
  }

  @Override
  public Property getOwnSlot(String name) {
    return typeDescriptor.properties.get(name);
  }

  @Override
  public JSType getTypeOfThis() {
    return typeDescriptor.type;
  }

  /**
   * Returns the object this type is defined as a property on, or null if this is type is defined
   * in the global scope.
   */
  @Nullable
  public NominalType getParent() {
    return parent;
  }

  /**
   * Returns the types defined as properties on this type.
   */
  public Collection<NominalType> getTypes() {
    return Collections.unmodifiableCollection(typeDescriptor.types.values());
  }

  /**
   * Returns a named type defined as a property on this type.
   *
   * @param name the unqualified type name (e.g. "bar", not "foo.bar").
   * @return the nominal type, if it exists.
   */
  @Nullable
  public NominalType getType(String name) {
    return typeDescriptor.types.get(name);
  }

  /**
   * Returns the properties defined on this type.
   */
  public Collection<Property> getProperties() {
    return Collections.unmodifiableCollection(typeDescriptor.properties.values());
  }

  public String getQualifiedName() {
    return getQualifiedName(!isCommonJsModule());
  }

  /**
   * @param includeNamespace Whether to include the parent namespace/module's name in computed
   *     name.
   * @return This type's qualified name, either relative to global scope or its parent namespace.
   */
  public String getQualifiedName(boolean includeNamespace) {
    if (parent != null
        && (includeNamespace || !(parent.isNamespace() || parent.isModuleExports()))) {
      return parent.getQualifiedName(includeNamespace) + "." + name;
    }
    return name;
  }

  public String getName() {
    return name;
  }

  public JSType getJsType() {
    return typeDescriptor.type;
  }

  public Node getNode() {
    return node;
  }

  @Nullable
  public JsDoc getJsdoc() {
    return jsdoc;
  }

  @Nullable
  public ModuleDescriptor getModule() {
    if (module != null) {
      return module;
    }
    if (parent != null) {
      return parent.getModule();
    }
    return null;
  }

  public void addProperty(Property property) {
    this.typeDescriptor.properties.put(property.getName(), property);
  }

  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  @Nullable
  public <T> T getAttribute(String key, Class<T> type) {
    Object value = attributes.get(key);
    checkArgument(value == null || type.isInstance(type),
        "Attribute %s is a %s, not a %s", key,
        value == null ? null : value.getClass().getName(),
        type.getName());
    return type.cast(value);
  }

  public boolean isModuleExports() {
    return module != null && getQualifiedName(true).equals(module.getName());
  }

  public boolean isCommonJsModule() {
    return getModule() != null && getModule().isCommonJsModule();
  }

  public boolean isNamespace() {
    return !typeDescriptor.type.isConstructor()
        && !typeDescriptor.type.isInterface()
        && !typeDescriptor.type.isEnumType();
  }

  TypeDescriptor getTypeDescriptor() {
    return typeDescriptor;
  }
}
