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

package com.github.jsdossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.Files.getNameWithoutExtension;

import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.Resources;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
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
  private final TypeRegistry typeRegistry;
  private final ModuleNamingConvention namingConvention;

  @Inject
  DossierFileSystem(
      @Output Path outputRoot,
      @SourcePrefix Path sourcePrefix,
      @ModulePrefix Path modulePrefix,
      TypeRegistry typeRegistry,
      ModuleNamingConvention namingConvention) {
    this.outputRoot = outputRoot;
    this.modulePrefix = modulePrefix;
    this.sourcePrefix = sourcePrefix;
    this.typeRegistry = typeRegistry;
    this.namingConvention = namingConvention;
  }

  /**
   * Resolves the given path against the common ancestor directory for all input modules. This
   * method does not check if the path is an actual module in the {@link TypeRegistry}.
   */
  public Path resolveModule(String path) {
    return modulePrefix.resolve(path).normalize();
  }

  /**
   * Returns the path on the input file system for the script containing the given node.
   */
  public Path getSourcePath(Node node) {
    return sourcePrefix.getFileSystem().getPath(node.getSourceFileName());
  }

  /**
   * Returns the path to the global types index.
   */
  public Path getGlobalsPath() {
    return outputRoot.resolve(".globals.html");
  }

  /**
   * Returns the request path resolved against the output directory.
   *
   * @throws IllegalArgumentException if the requested path is not under the output root.
   */
  public Path getPath(String path) {
    Path p = outputRoot.resolve(path).normalize();
    checkArgument(p.startsWith(outputRoot),
        "The requested path is not under the output root: %s", path);
    return p;
  }

  /**
   * Returns the path to the given file once copied to the output directory.
   */
  public Path getPath(TemplateFile file) {
    return outputRoot.resolve(file.getName());
  }

  /**
   * Returns the path of the given source file relative to the common input source directory.
   *
   * @throws IllegalArgumentException if the given file is not under the common source directory.
   */
  public Path getSourceRelativePath(Path sourceFile) {
    if (sourcePrefix.isAbsolute()) {
      sourceFile = sourceFile.toAbsolutePath();
    }
    checkArgument(sourceFile.startsWith(sourcePrefix),
        "The requested path is not a recognized source file: %s", sourceFile);
    return sourcePrefix.relativize(sourceFile.normalize());
  }

  /**
   * Returns the path of the generated documentation for the given source file.
   *
   * @throws IllegalArgumentException if the given file is not under the common source directory.
   */
  public Path getPath(Path sourceFile) {
    Path path = getSourceRelativePath(sourceFile)
        .resolveSibling(sourceFile.getFileName() + ".src.html");
    return outputRoot.resolve(SOURCE_DIR).resolve(path.toString());
  }

  /**
   * Returns the path of the generated documentation for the given node's source file.
   */
  public Path getPath(Node node) {
    return getPath(getSourcePath(node));
  }

  /**
   * Returns the path to the generated documentation for the given {@code type}.
   */
  public Path getPath(NominalType type) {
    if (type.isModuleExports() && type.getModule().get().getType() != Module.Type.CLOSURE) {
      return getPath(type.getModule().get());
    }

    Module module = type.getModule().orNull();
    if (module == null || module.getType() == Module.Type.CLOSURE) {
      return outputRoot.resolve(type.getName() + ".html");
    }

    Path path = getPath(module);
    String name = getDisplayName(type);
    String exports =
        stripExtension(path).getFileName().toString() + "_exports_" + name + ".html";
    return path.resolveSibling(exports);
  }

  /**
   * Returns the path to the generated documentation for the given {@code module}.
   */
  public Path getPath(Module module) {
    Path path = stripExtension(modulePrefix.relativize(module.getPath()));
    return outputRoot.resolve(MODULE_DIR).resolve(path + ".html");
  }

  /**
   * Returns the fully-qualified display name for the given type. For types exported by a module,
   * this is the display name of the module <em>and</em> the type's display name. For types defined
   * in the global scope or off a namespace in the global scope, the qualified display name is the
   * same as the normal {@linkplain #getDisplayName(NominalType) display name}.
   */
  public String getQualifiedDisplayName(NominalType type) {
    String name = getDisplayName(type);
    if (type.getModule().isPresent() && !type.isModuleExports()) {
      return getDisplayName(type.getModule().get()) + "." + name;
    }
    return name;
  }

  /**
   * Returns the display name for the given type.
   */
  public String getDisplayName(NominalType type) {
    if (type.getModule().isPresent()) {
      Module module = type.getModule().get();
      if (type.isModuleExports()) {
        return getDisplayName(module);
      }
      try {
        return type.getName().substring(module.getId().length() + 1);
      } catch (RuntimeException e) {
        throw new RuntimeException("For " + type.getName() + "\n   " + module.getId(), e);
      }
    }
    return type.getName();
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
   * Returns the relative path between two generated files.
   */
  public Path getRelativePath(NominalType from, NominalType to) {
    return getRelativePath(from, getPath(to));
  }

  /**
   * Returns the paths in the resource set.
   *
   * @param outputPath path of the generated file the paths in the resource set should be relative
   *     to.
   * @param template the template to pull resources from.
   * @return the resource set for the generated file.
   */
  public Resources getResources(final Path outputPath, DocTemplate template) {
    Function<TemplateFile, String> toOutputPath = new Function<TemplateFile, String>() {
      @Override
      public String apply(TemplateFile file) {
        Path toFile = getPath(file);
        return Paths.getRelativePath(outputPath, toFile).toString();
      }
    };
    Path typesJs = outputRoot.resolve("types.js");
    return Resources.newBuilder()
        .addAllCss(FluentIterable.from(template.getCss()).transform(toOutputPath))
        .addAllHeadScript(FluentIterable.from(template.getHeadJs()).transform(toOutputPath))
        .addTailScript(Paths.getRelativePath(outputPath, typesJs).toString())
        .addAllTailScript(FluentIterable.from(template.getTailJs()).transform(toOutputPath))
        .build();
  }

  private static String toCanonicalString(Path path) {
    return path.toString().replace(path.getFileSystem().getSeparator(), "/");
  }

  private static Path stripExtension(Path path) {
    String name = path.getFileName().toString();
    return path.resolveSibling(getNameWithoutExtension(name));
  }
}
