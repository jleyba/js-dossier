package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.DossierModule;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.Property;

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
public final class NominalType {

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
  }

  private final String name;
  private final TypeDescriptor typeDescriptor;
  private final Node node;
  private final JsDoc jsdoc;
  private final DossierModule module;
  private final NominalType parent;

  private final Map<String, Object> attributes = new HashMap<>();

  NominalType(
      @Nullable NominalType parent,
      String name,
      TypeDescriptor typeDescriptor,
      Node node,
      @Nullable JsDoc jsdoc,
      @Nullable DossierModule module) {
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
    checkArgument(!isModuleExports() || parent == null,
        "Module exports object may not have a parent: %s", name);
  }

  @Override
  public String toString() {
    return String.format("NominalType(%s, %s)", name, typeDescriptor.type);
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
   * Returns the properties defined on this type.
   */
  public Collection<Property> getProperties() {
    return Collections.unmodifiableCollection(typeDescriptor.properties.values());
  }

  /**
   * Returns the named property, or null if no such property is defined on this type.
   */
  @Nullable
  public Property getProperty(String name) {
    return typeDescriptor.properties.get(name);
  }

  /**
   * Returns whether this type has a known property with the given name.
   */
  public boolean hasProperty(String name) {
    return typeDescriptor.properties.containsKey(name);
  }

  public String getQualifiedName() {
    return getQualifiedName(false);
  }

  public String getQualifiedName(boolean includeModule) {
    if (parent != null && (includeModule || !parent.isModuleExports())) {
      return parent.getQualifiedName(includeModule) + "." + name;
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
  public DossierModule getModule() {
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
    return module != null && name.equals(module.getVarName());
  }

  TypeDescriptor getTypeDescriptor() {
    return typeDescriptor;
  }
}
