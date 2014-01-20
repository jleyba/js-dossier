// Copyright 2013 Jason Leyba
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.jstype.JSType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Maintains a registry of documented types.
 */
class DocRegistry {

  private final Map<Path, String> fileOverviews = new HashMap<>();
  private final Map<String, Descriptor> externs = new HashMap<>();
  private final Map<String, Descriptor> types = new HashMap<>();
  private final Map<JSType, Descriptor> jsTypeToDescriptor = new HashMap<>();
  private final Map<String, ModuleDescriptor> modules = new HashMap<>();

  void addFileOverview(Path path, @Nullable String overview) {
    fileOverviews.put(checkNotNull(path, "null path"), Strings.nullToEmpty(overview));
  }

  @Nullable
  String getFileOverview(Path path) {
    return fileOverviews.get(path);
  }

  void addExtern(Descriptor descriptor) {
    externs.put(descriptor.getFullName(), descriptor);
    jsTypeToDescriptor.put(descriptor.getType(), descriptor);
  }

  boolean isExtern(String name) {
    while (!externs.containsKey(name)) {
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
      } else {
        break;
      }
    }
    return externs.containsKey(name);
  }

  @Nullable
  Descriptor getExtern(String name) {
    return externs.get(name);
  }

  void addType(Descriptor descriptor) {
    types.put(descriptor.getFullName(), descriptor);
    jsTypeToDescriptor.put(descriptor.getType(), descriptor);
  }

  @Nullable
  Descriptor getType(JSType type) {
    return jsTypeToDescriptor.get(type);
  }

  boolean isKnownType(String name) {
    if (types.containsKey(name)
        || modules.containsKey(name)
        || isExtern(name)) {
      return true;
    }

    if (name.endsWith(".exports")) {
      name = name.substring(0, name.length() - ".exports".length());
      return modules.containsKey(name);
    }

    for (ModuleDescriptor module : modules.values()) {
      if (module.exportsProperty(name)) {
        return true;
      }
    }

    return false;
  }

  boolean isDocumentedType(Descriptor descriptor) {
    String name = descriptor.getFullName();
    while (!isKnownType(name)) {
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
      } else {
        break;
      }
    }
    return isKnownType(name) && !isExtern(name);
  }

  Iterable<Descriptor> getTypes() {
    return Iterables.unmodifiableIterable(types.values());
  }

  void addModule(ModuleDescriptor module) {
    modules.put(module.getName(), module);
  }

  Iterable<ModuleDescriptor> getModules() {
    return Iterables.unmodifiableIterable(modules.values());
  }

  /**
   * Searches for the descriptor with the given name.
   *
   * @param typeName The qualified typename to search for.
   * @return The resolved descriptor, or {@code null}.
   */
  @Nullable
  Descriptor resolve(String typeName) {
    return resolve(typeName, null);
  }

  /**
   * Resolves the given type, first against the exported API of the specified module, and
   * then against all global types.
   *
   * @param typeName The qualified typename to search for.
   * @param relativeTo If non-null, will attempt to resolve the given type name against the
   *     module's exported API before checking the global scope.
   * @return The resolved descriptor, or {@code null}.
   */
  @Nullable
  Descriptor resolve(String typeName, @Nullable ModuleDescriptor relativeTo) {
    typeName = typeName.replace("#", ".prototype.");
    if (typeName.endsWith(".prototype")) {
      typeName = typeName.substring(0, typeName.length() - ".prototype".length());
    }

    if (relativeTo != null) {
      Descriptor descriptor = relativeTo.getExportedProperty(typeName);
      if (descriptor != null) {
        return descriptor;
      }
    }

    if (externs.containsKey(typeName)) {
      return externs.get(typeName);
    }

    if (types.containsKey(typeName)) {
      return types.get(typeName);
    }

    if (modules.containsKey(typeName)) {
      return modules.get(typeName).getDescriptor();
    }

    int index = typeName.lastIndexOf('.');
    if (index != -1 && index + 1 < typeName.length()) {
      String parentName = typeName.substring(0, index);
      String name = typeName.substring(index + 1);
      Descriptor parent = resolve(parentName, relativeTo);
      if (parent != null) {
        if (parentName.endsWith(".prototype")) {
          return findProperty(parent.getInstanceProperties(), name);
        } else if (isModuleExports(parent)) {
          return resolveModuleDescriptor(parent, name);
        } else {
          return findProperty(parent.getProperties(), name);
        }
      }
    }

    return null;
  }

  private boolean isModuleExports(Descriptor descriptor) {
    if (descriptor.getFullName().endsWith(".exports")) {
      Optional<ModuleDescriptor> module = descriptor.getModule();
      return module.isPresent() && descriptor == module.get().getDescriptor();
    }
    return false;
  }

  @Nullable
  private Descriptor resolveModuleDescriptor(Descriptor moduleExports, String typeName) {
    checkArgument(moduleExports.getModule().isPresent()
        && moduleExports == moduleExports.getModule().get().getDescriptor());

    // Reference to the module as a namespace.
    if ("exports".equals(typeName)) {
      return moduleExports;
    }

    ModuleDescriptor module = moduleExports.getModule().get();
    return findProperty(module.getExportedProperties(), typeName);
  }

  @Nullable
  private Descriptor findProperty(Iterable<Descriptor> descriptors, String name) {
    for (Descriptor descriptor : descriptors) {
      if (name.equals(descriptor.getSimpleName())) {
        return descriptor;
      }
    }
    return null;
  }
}
