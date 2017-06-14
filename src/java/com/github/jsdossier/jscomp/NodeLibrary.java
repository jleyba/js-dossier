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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.inject.ImplementedBy;
import com.google.javascript.jscomp.SourceFile;
import java.io.IOException;

/**
 * Manages externs for node modules.
 */
@ImplementedBy(NodeLibraryImpl.class)
public interface NodeLibrary {

  /**
   * Returns the list of source files that define globals for this library. The files returned in
   * this list are not themselves modules. This collection will be disjoint with
   * {@link #getExternModules()}.
   */
  default ImmutableList<SourceFile> getExternFiles() throws IOException {
    return ImmutableList.of();
  }

  /**
   * Returns the collection of files that define modules in this library. This collection will be
   * disjoint with {@link #getExternFiles()}.
   */
  default ImmutableCollection<SourceFile> getExternModules() throws IOException {
    return ImmutableList.of();
  }

  /**
   * Returns whether the provided {@code id} may be used to require a module in this library.
   */
  default boolean canRequireId(String id) {
    return false;
  }

  default String normalizeRequireId(String id) {
    throw new IllegalArgumentException("not an extern module: " + id);
  }

  default String getIdFromPath(String path) {
    throw new IllegalArgumentException("not an extern module: " + path);
  }

  /**
   * Returns whether the given {@code path} references a module in this library.
   * The default implementation always returns false.
   */
  default boolean isModulePath(String path) {
    return false;
  }
}
