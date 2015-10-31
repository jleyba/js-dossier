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
import static com.google.common.io.Files.getNameWithoutExtension;

import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.SourcePrefix;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;

import javax.inject.Inject;
import java.nio.file.Path;

/**
 * Class responsible for generating the paths for all rendered items.
 */
final class DossierFileSystem {
  
  private static final String MODULE_DIR = "module";
  private static final String SOURCE_DIR = "source";

  private final Path outputRoot;
  private final Path modulePrefix;
  private final Path sourcePrefix;
  private final ImmutableSet<Path> allModules;

  @Inject
  DossierFileSystem(
      @Output Path outputRoot,
      @SourcePrefix Path sourcePrefix,
      @ModulePrefix Path modulePrefix,
      @Modules ImmutableSet<Path> allModules) {
    this.outputRoot = outputRoot;
    this.modulePrefix = modulePrefix;
    this.sourcePrefix = sourcePrefix;
    this.allModules = allModules;
  }

  /**
   * Returns the path to the given file once copied to the output directory.
   */
  public Path getPath(TemplateFile file) {
    return outputRoot.resolve(file.getName());
  }

  /**
   * Returns the path of the generated documentation for the given source file.
   */
  public Path getPath(Path sourceFile) {
    Path path = sourcePrefix
        .relativize(sourceFile.toAbsolutePath().normalize())
        .resolveSibling(sourceFile.getFileName() + ".src.html");
    return outputRoot.resolve(SOURCE_DIR).resolve(path.toString());
  }

  /**
   * Returns the path of the generated documentation for the given node's source file.
   */
  public Path getPath(Node node) {
    return getPath(sourcePrefix.getFileSystem().getPath(node.getSourceFileName()));
  }

  /**
   * Returns the path to the generated documentation for the given {@code type}.
   */
  public Path getPath(NominalType type) {
    if (type.isModuleExports() && type.isCommonJsModule()) {
      return getPath(type.getModule());
    }

    ModuleDescriptor module = type.getModule();
    if (module == null || !module.isCommonJsModule()) {
      return outputRoot.resolve(type.getQualifiedName() + ".html");
    }

    Path path = getPath(module);
    String base = module.getName() + ".";
    String name = type.getQualifiedName(true);
    if (name.startsWith(base)) {
      name = name.substring(base.length());
    }
    
    String exports =
        stripExtension(path).getFileName().toString() + "_exports_" + name + ".html";
    return path.resolveSibling(exports);
  }

  /**
   * Returns the path to the generated documentation for the given {@code module}.
   */
  public Path getPath(ModuleDescriptor module) {
    // NB: Ideally, we'd use the module's relative path under MODULE_DIR, but soy templates won't
    // let us generate links with a "/../" in the href. Interestingly, it will permit "../" in a
    // <a href>, and it _will_ permit "/../" in the href of a <link>.
    String path = stripExtension(modulePrefix.relativize(module.getPath()))
        .toString()
        .replace(modulePrefix.getFileSystem().getSeparator(), "_");
    return outputRoot.resolve(MODULE_DIR).resolve(path + ".html");
  }

  /**
   * Returns the display name for the given type.
   */
  public String getDisplayName(NominalType type) {
    if (!type.isModuleExports() || !type.isCommonJsModule()) {
      return type.getQualifiedName();
    }
    return getDisplayName(type.getModule());
  }

  /**
   * Returns the display name for the given module.
   */
  public String getDisplayName(ModuleDescriptor module) {
    if (!module.isCommonJsModule()) {
      return module.getName();
    }

    Path path = stripExtension(module.getPath());
    
    if (path.endsWith("index") && path.getParent() != null) {
      path = path.getParent();
      
      Path other = path.resolveSibling(path.getFileName() + ".js");
      if (allModules.contains(other)) {
        return toCanonicalString(modulePrefix.relativize(path)) + "/";
      }
    }
    return toCanonicalString(modulePrefix.relativize(path));
  }

  /**
   * Returns the path to the given {@code file}, relative to the output root.
   */
  public Path getRelativePath(Path file) {
    return outputRoot.relativize(file);
  }

  /**
   * Computes the relative path from the generated documentation for {@code type} to the specified
   * {@code file}.
   */
  public Path getRelativePath(NominalType type, Path file) {
    checkArgument(
        file.getFileSystem() == outputRoot.getFileSystem() && file.startsWith(outputRoot),
        "The target file does not belong to the output file system: %s", file);
    return Paths.getRelativePath(getPath(type), file);
  }

  /**
   * Returns the relative path between two generated files.
   */
  public Path getRelativePath(NominalType from, NominalType to) {
    return getRelativePath(from, getPath(to));
  }
  
  private static String toCanonicalString(Path path) {
    return path.toString().replace(path.getFileSystem().getSeparator(), "/");
  }

  private static Path stripExtension(Path path) {
    String name = path.getFileName().toString();
    return path.resolveSibling(getNameWithoutExtension(name));
  }
}
