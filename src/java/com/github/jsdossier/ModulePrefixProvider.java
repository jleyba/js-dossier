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

import static com.github.jsdossier.Paths.getCommonPrefix;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.file.Files.isDirectory;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.TypeRegistry2;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import java.nio.file.FileSystem;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Computes the common ancestor for all module inputs.
 */
final class ModulePrefixProvider implements Provider<Path>  {

  private final FileSystem inputFs;
  private final TypeRegistry2 typeRegistry;
  private final Optional<Path> userSuppliedPrefix;

  @Inject
  ModulePrefixProvider(
      @Input FileSystem inputFs,
      TypeRegistry2 typeRegistry,
      @ModulePrefix Optional<Path> userSuppliedPrefix) {
    this.inputFs = inputFs;
    this.typeRegistry = typeRegistry;
    this.userSuppliedPrefix = userSuppliedPrefix;
  }

  @Override
  public Path get() {
    ImmutableSet<Path> modules = getModulePaths();

    Path path;
    if (userSuppliedPrefix.isPresent()) {
      path = userSuppliedPrefix.get();
      checkArgument(isDirectory(path), "Module prefix must be a directory: %s", path);
      for (Path module : modules) {
        checkArgument(module.startsWith(path),
            "Module prefix <%s> is not an ancestor of module %s", path, module);
      }
    } else {
      path = getCommonPrefix(inputFs.getPath("").toAbsolutePath(), modules);
      if (modules.contains(path) && path.getParent() != null) {
        path = path.getParent();
      }
    }

    // Always display at least one parent directory, if possible.
    for (Path module : modules) {
      if (path.equals(module.getParent())) {
        return firstNonNull(path.getParent(), path);
      }
    }

    return path;
  }
  
  private ImmutableSet<Path> getModulePaths() {
    return FluentIterable.from(typeRegistry.getAllModules())
        .filter(new Predicate<Module>() {
          @Override
          public boolean apply(Module module) {
            return module.getType() != Module.Type.CLOSURE;
          }
        })
        .transform(new Function<Module, Path>() {
          @Override
          public Path apply(Module module) {
            return module.getPath();
          }
        })
        .toSet();
  }
}
