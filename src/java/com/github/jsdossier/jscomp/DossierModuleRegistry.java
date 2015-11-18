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

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.jscomp.Module.Type;

import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.ES6ModuleLoader;
import com.google.javascript.rhino.Node;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

/**
 * Tracks which source files should be treated as CommonJS modules during a compiler run.
 */
public final class DossierModuleRegistry {

  private final FileSystem fileSystem;
  private final Set<String> commonJsModulePaths;

  private final Map<Node, DossierModule> scriptToModule = new HashMap<>();
  private final Map<String, DossierModule> nameToModule = new HashMap<>();

  /**
   * Creates a new registry.
   *
   * @param commonJsModulePaths the set of source files that should be treated as a CommonJS
   *     modules. All other source files will be treated as normal JS files.
   */
  @Inject
  public DossierModuleRegistry(@Modules ImmutableSet<Path> commonJsModulePaths) {
    this.commonJsModulePaths = FluentIterable.from(commonJsModulePaths)
        .transform(Functions.toStringFunction())
        .toSet();
    this.fileSystem = commonJsModulePaths.iterator().hasNext()
        ? commonJsModulePaths.iterator().next().getFileSystem()
        : FileSystems.getDefault();
  }

  FileSystem getFileSystem() {
    return fileSystem;
  }

  /**
   * Returns whether there is a module whose global variable matches the given {@code name}.
   */
  public boolean hasModuleNamed(String name) {
    return nameToModule.containsKey(name);
  }

  /**
   * Returns whether there is a known CommonJS module with the given source file {@code path}.
   */
  boolean hasModuleWithPath(String path) {
    return commonJsModulePaths.contains(path);
  }

  /**
   * Registers the main script node for a CommonJS module, returning a {@link DossierModule}
   * wrapper that may be used to track individual properties of the module.
   */
  DossierModule registerScriptForModule(Node script) {
    checkArgument(script.isScript(), "Not a script node: %s", script);
    checkArgument(hasModuleWithPath(script.getSourceFileName()),
        "Not a CommonJS module: %s", script.getSourceFileName());

    DossierModule module = scriptToModule.get(script);
    if (module == null) {
      Path path = fileSystem.getPath(script.getSourceFileName());
      module = new DossierModule(
          script,
          Module.builder()
              .setId(getId(path))
              .setPath(path)
              .setJsDoc(JsDoc.from(null))
              .setType(Type.NODE)
              .build());
      scriptToModule.put(script, module);
      nameToModule.put(module.getVarName(), module);
    } else {
      checkArgument(module.getScriptNode() == script,
          "A script node has already been registered for %s. Existing script: %s, new script: %s",
          module.getScriptNode(), script);
    }
    return module;
  }

  static String getId(Path path) {
    // NB: don't use modulePath.toUri(), because that will include the file system type.
    URI uri = URI.create(path.toString());
    return ES6ModuleLoader.toModuleName(uri);
  }

  public Iterable<DossierModule> getModules() {
    return Iterables.unmodifiableIterable(scriptToModule.values());
  }
}
