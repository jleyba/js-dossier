package com.github.jleyba.dossier;

import com.google.common.collect.Iterables;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Maintains a registry of documented types.
 */
class DocRegistry {

  private final Map<String, Descriptor> externs = new HashMap<>();
  private final Map<String, Descriptor> types = new HashMap<>();

  void addExtern(Descriptor descriptor) {
    externs.put(descriptor.getFullName(), descriptor);
  }

  boolean isExtern(String name) {
    return externs.containsKey(name);
  }

  @Nullable
  Descriptor getExtern(String name) {
    return externs.get(name);
  }

  void addType(Descriptor descriptor) {
    types.put(descriptor.getFullName(), descriptor);
  }

  @Nullable
  Descriptor getType(String name) {
    return types.get(name);
  }

  boolean isKnownType(String name) {
    return types.containsKey(name) || isExtern(name);
  }

  Iterable<Descriptor> getTypes() {
    return Iterables.unmodifiableIterable(types.values());
  }
}
