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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.jstype.JSType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Maintains a registry of documented types.
 */
class DocRegistry {

  private final Map<Path, String> fileOverviews = new HashMap<>();
  private final Map<String, Descriptor> externs = new HashMap<>();
  private final Map<String, Descriptor> types = new HashMap<>();
  private final Map<JSType, Descriptor> jsTypeToDescriptor = new HashMap<>();
  private final Set<ModuleDescriptor> modules = new HashSet<>();

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
  Descriptor getType(String name) {
    return types.get(name);
  }

  @Nullable
  Descriptor getType(JSType type) {
    return jsTypeToDescriptor.get(type);
  }

  boolean isKnownType(String name) {
    return types.containsKey(name) || isExtern(name);
  }

  Iterable<Descriptor> getTypes() {
    return Iterables.unmodifiableIterable(types.values());
  }

  void addModule(ModuleDescriptor module) {
    modules.add(module);
  }

  Iterable<ModuleDescriptor> getModules() {
    return Iterables.unmodifiableIterable(modules);
  }
}
