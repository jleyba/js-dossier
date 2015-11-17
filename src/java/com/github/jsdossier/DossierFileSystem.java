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

import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NominalType2;
import com.github.jsdossier.jscomp.TypeRegistry2;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;

import java.nio.file.Path;

import javax.inject.Inject;

/**
 * Class responsible for generating the paths for all rendered items.
 */
@DocumentationScoped
final class DossierFileSystem {
  
  private static final String MODULE_DIR = "module";
  private static final String SOURCE_DIR = "source";

  private final Path outputRoot;
  private final Path modulePrefix;
  private final Path sourcePrefix;
  private final ImmutableSet<Path> allModules;
  private final TypeRegistry2 typeRegistry;
  private final ModuleNamingConvention namingConvention;

  @Inject
  DossierFileSystem(
      @Output Path outputRoot,
      @SourcePrefix Path sourcePrefix,
      @ModulePrefix Path modulePrefix,
      @Modules ImmutableSet<Path> allModules,
      TypeRegistry2 typeRegistry,
      ModuleNamingConvention namingConvention) {
    this.outputRoot = outputRoot;
    this.modulePrefix = modulePrefix;
    this.sourcePrefix = sourcePrefix;
    this.allModules = allModules;
    this.typeRegistry = typeRegistry;
    this.namingConvention = namingConvention;
  }

  /**
   * Returns the prefix path trimmed from all module inputs before computing their output path.
   */
  public Path getModulePrefix() {
    return modulePrefix;
  }

  /**
   * Returns the root output path.
   */
  public Path getOutputRoot() {
    return outputRoot;
  }

  /**
   * Returns the path to the global types index.
   */
  public Path getGlobalsPath() {
    return outputRoot.resolve(".globals.html");
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
    if (module == null || module.getType() == ModuleType.CLOSURE) {
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
   * Returns the path to the generated documentation for the given {@code type}.
   */
  public Path getPath(NominalType2 type) {
    if (type.isModuleExports() && type.getModule().get().getType() != Module.Type.CLOSURE) {
      return getPath(type.getModule().get());
    }
    
    Module module = type.getModule().orNull();
    if (module == null || module.getType() == Module.Type.CLOSURE) {
      return outputRoot.resolve(type.getName() + ".html");
    }

    Path path = getPath(module);
    String base = module.getId() + ".";
    String name = type.getName().substring(base.length());
    String exports =
        stripExtension(path).getFileName().toString() + "_exports_" + name + ".html";
    return path.resolveSibling(exports);
  }

  /**
   * Returns the path to the generated documentation for the given {@code module}.
   */
  public Path getPath(ModuleDescriptor module) {
    Path path = stripExtension(modulePrefix.relativize(module.getPath()));
    return outputRoot.resolve(MODULE_DIR).resolve(path + ".html");
  }

  /**
   * Returns the path to the generated documentation for the given {@code module}.
   */
  public Path getPath(Module module) {
    Path path = stripExtension(modulePrefix.relativize(module.getPath()));
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
   * Returns the fully-qualified display name for the given type. For types exported by a module,
   * this is the display name of the module <em>and</em> the type's display name. For types defined
   * in the global scope or off a namespace in the global scope, the qualified display name is the
   * same as the normal {@linkplain #getDisplayName(NominalType2) display name}.
   */
  public String getQualifiedDisplayName(NominalType2 type) {
    String name = getDisplayName(type);
    if (type.getModule().isPresent()) {
      return getDisplayName(type.getModule().get()) + "." + name;
    }
    return name;
  }

  /**
   * Returns the display name for the given type.
   */
  public String getDisplayName(NominalType2 type) {
    if (type.getModule().isPresent()) {
      Module module = type.getModule().get();
      if (type.getName().equals(module.getId())) {
        return getDisplayName(module);
      }
      return type.getName().substring(module.getId().length() + 1);
    }
    return type.getName();
  }

  /**
   * Returns the display name for the given module.
   */
  public String getDisplayName(ModuleDescriptor module) {
    if (module.getType() == ModuleType.CLOSURE) {
      return module.getName();
    }

    Path path = stripExtension(module.getPath());

    if (namingConvention == ModuleNamingConvention.NODE
        && path.endsWith("index") && path.getParent() != null) {
      path = path.getParent();
      
      Path other = path.resolveSibling(path.getFileName() + ".js");
      if (allModules.contains(other)) {
        return toCanonicalString(modulePrefix.relativize(path)) + "/";
      }
    }
    return toCanonicalString(modulePrefix.relativize(path));
  }

  /**
   * Returns the display name for the given module.
   */
  public String getDisplayName(Module module) {
    if (module.getType() == Module.Type.CLOSURE) {
      return module.getId();
    }

    Path path = stripExtension(module.getPath());

    if (namingConvention == ModuleNamingConvention.NODE
        && path.endsWith("index")
        && path.getParent() != null) {
      path = path.getParent();

      Path other = path.resolveSibling(path.getFileName() + ".js");
      if (typeRegistry.isModule(other)) {
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
   * Computes the relative path from the generated documentation for {@code type} to the specified
   * {@code file}.
   */
  public Path getRelativePath(NominalType2 type, Path file) {
    checkArgument(
        file.getFileSystem() == outputRoot.getFileSystem() && file.startsWith(outputRoot),
        "The target file does not belong to the output file system: %s", file);
    return Paths.getRelativePath(getPath(type), file);
  }

  /**
   * Returns the relative path between two generated files.
   */
  public Path getRelativePath(NominalType2 from, NominalType2 to) {
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
