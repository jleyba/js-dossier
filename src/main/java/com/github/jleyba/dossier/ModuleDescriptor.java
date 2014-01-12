package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.DossierModule;
import com.google.javascript.jscomp.Scope;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Descriptor for a CommonJS module.
 */
class ModuleDescriptor {

  private final Descriptor descriptor;
  private final DossierModule module;

  /**
   * Descriptors for this module's exported API.
   */
  private final List<Descriptor> exportedProperties = new LinkedList<>();

  /**
   * Type definitions defined within the module but not on an exported property.
   */
  private final List<Descriptor> internalTypeDefs = new LinkedList<>();

  private final Map<String, Object> attributes = new HashMap<>();

  /**
   * Cretes a new module descriptor.
   *
   * @param descriptor the descriptor for the module's exported API.
   * @param module a reference to the module itelf.
   */
  ModuleDescriptor(Descriptor descriptor, DossierModule module) {
    this.descriptor = descriptor;
    this.module = module;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ModuleDescriptor) {
      ModuleDescriptor that = (ModuleDescriptor) o;
      return this.descriptor.equals(that.descriptor)
          && this.module.equals(that.module);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptor, module);
  }

  public Iterable<Scope.Var> getInternalVars() {
    return module.getInternalVars();
  }

  public Descriptor getDescriptor() {
    return descriptor;
  }

  public Path getPath() {
    return module.getModulePath();
  }

  public String getSource() {
    return module.getScriptNode().getSourceFileName();
  }

  @Nullable
  public JsDoc getJsDoc() {
    return module.getScriptNode().getJSDocInfo() == null ? null
        : new JsDoc(module.getScriptNode().getJSDocInfo());
  }

  public void addExportedProperty(Descriptor descriptor) {
    descriptor.setModule(this);
    exportedProperties.add(descriptor);
  }

  Iterable<Descriptor> getExportedProperties() {
    return Iterables.unmodifiableIterable(exportedProperties);
  }

  void addTypedef(Descriptor descriptor) {
    descriptor.setModule(this);
    internalTypeDefs.add(descriptor);
  }

  Iterable<Descriptor> getInternalTypeDefs() {
    return Iterables.unmodifiableIterable(internalTypeDefs);
  }

  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> T getAttribute(String key) {
    return (T) attributes.get(key);
  }
}
