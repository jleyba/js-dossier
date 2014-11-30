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
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Maintains a registry of documented types.
 */
class DocRegistry {

  /**
   * Map of source path to the fileoverview string for that source.
   */
  private final Map<Path, String> fileOverviews = new HashMap<>();

  /**
   * Map of qualified extern name to the descriptor for that type.
   */
  private final Map<String, Descriptor> externs = new HashMap<>();

  /**
   * Map of qualified names to the descriptor for that type.
   */
  private final Map<String, Descriptor> types = new HashMap<>();

  private final Map<JSType, Descriptor> declaredTypes = new HashMap<>();

  /**
   * Map of qualified module name to the descriptor for that module. Here, the qualified name is
   * taken from the managed name when merging the descriptor into the global scope and <i>not</i>
   * the name that other CommonJS modules would use to reference the module.
   *
   * @see com.google.javascript.jscomp.DossierModule
   */
  private final Map<String, ModuleDescriptor> modules = new HashMap<>();

  private final JSTypeRegistry typeRegistry;

  DocRegistry(JSTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
  }

  JSType evaluate(JSTypeExpression expression) {
    return expression.evaluate(null, typeRegistry);
  }

  void addFileOverview(Path path, @Nullable String overview) {
    fileOverviews.put(checkNotNull(path, "null path"), Strings.nullToEmpty(overview));
  }

  @Nullable
  String getFileOverview(Path path) {
    return fileOverviews.get(path);
  }

  void addExtern(Descriptor descriptor) {
    externs.put(descriptor.getFullName(), descriptor);
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

  boolean isExtern(Descriptor descriptor) {
    return externs.containsValue(descriptor);
  }

  boolean isExtern(JSType type) {
    return externs.containsKey(getDisplayName(type));
  }

  @Nullable
  Descriptor getExtern(String name) {
    return externs.get(name);
  }

  void addType(Descriptor descriptor) {
    types.put(descriptor.getFullName(), descriptor);
    if (descriptor.getType() != null) {
      declaredTypes.put(descriptor.getType(), descriptor);
    }
  }

  @Nullable
  Descriptor getType(JSType type) {
    String name = getDisplayName(type);
    if (types.containsKey(name)) {
      return types.get(name);
    }
    return declaredTypes.get(type);
  }

  @Nullable
  Descriptor getType(String type) {
    return types.get(type);
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
    return isKnownType(name);
  }

  Iterable<Descriptor> getTypes() {
    return Iterables.unmodifiableIterable(types.values());
  }

  void addModule(ModuleDescriptor module) {
    types.put(module.getDescriptor().getFullName(), module.getDescriptor());
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
    typeName = typeName.replaceAll("\\.?<.*>$", "");
    typeName = typeName.replace("#", ".prototype.");
    if (typeName.endsWith(".prototype")) {
      typeName = typeName.substring(0, typeName.length() - ".prototype".length());
    }

    if (typeName.endsWith(".")) {
      typeName = typeName.substring(0, typeName.length() - 1);
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
        } else if (parent.isModuleExports()) {
          return resolveModuleDescriptor(parent, name);
        } else {
          return findProperty(parent.getProperties(), name);
        }
      }
    }

    return null;
  }

  @Nullable
  private Descriptor resolveModuleDescriptor(Descriptor moduleExports, String typeName) {
    checkArgument(moduleExports.isModuleExports());

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

  static String getDisplayName(JSType type) {
    if (isNullOrEmpty(type.getDisplayName())) {
      return type.toString();
    }
    return type.getDisplayName();
  }
}
