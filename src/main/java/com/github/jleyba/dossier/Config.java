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
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.intersection;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.DepsFileParser;
import com.google.javascript.jscomp.deps.DepsGenerator;
import com.google.javascript.jscomp.deps.SortedDependencies;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Describes the runtime configuration for the app.
 */
class Config {

  private final ImmutableSet<Path> srcs;
  private final ImmutableSet<Path> modules;
  private final ImmutableSet<Path> externs;
  private final Path srcPrefix;
  private final Path modulePrefix;
  private final Path output;
  private final Optional<Path> license;
  private final Optional<Path> readme;
  private final PrintStream outputStream;
  private final PrintStream errorStream;

  /**
   * Creates a new runtime configuration.
   *
   *
   * @param srcs The list of compiler input sources.
   * @param modules The list of CommonJS compiler input sources.
   * @param externs The list of extern files for the Closure compiler.
   * @param output Path to the output directory.
   * @param license Path to a license file to include with the generated documentation.
   * @param readme Path to a markdown file to include in the main index.
   * @param modulePrefix Prefix to strip from each module path when rendering documentation.
   * @param outputStream The stream to use for standard output.
   * @param errorStream The stream to use for error output.
   * @throws IllegalStateException If any of source, moudle, and extern sets intersect, or if the
   *     output path is not a directory.
   */
  private Config(
      ImmutableSet<Path> srcs, ImmutableSet<Path> modules, ImmutableSet<Path> externs, Path output,
      Optional<Path> license, Optional<Path> readme, Optional<Path> modulePrefix,
      PrintStream outputStream, PrintStream errorStream) {
    checkArgument(intersection(srcs, externs).isEmpty(),
        "The sources and externs inputs must be disjoint:\n  sources: %s\n  externs: %s",
        srcs, externs);
    checkArgument(intersection(srcs, modules).isEmpty(),
        "The sources and modules inputs must be disjoint:\n  sources: %s\n  modules: %s",
        srcs, modules);
    checkArgument(intersection(modules, externs).isEmpty(),
        "The sources and modules inputs must be disjoint:\n  modules: %s\n  externs: %s",
        modules, externs);
    checkArgument(!Files.exists(output) || Files.isDirectory(output),
        "Output path, %s, is not a directory", output);
    checkArgument(!license.isPresent() || Files.exists(license.get()),
        "LICENSE path, %s, does not exist", license.orNull());
    checkArgument(!readme.isPresent() || Files.exists(readme.get()),
        "README path, %s, does not exist", readme.orNull());

    this.srcs = srcs;
    this.modules = modules;
    this.srcPrefix = getSourcePrefixPath(srcs, modules);
    this.modulePrefix = getModulePreixPath(modulePrefix, modules);
    this.externs = externs;
    this.output = output;
    this.license = license;
    this.readme = readme;
    this.outputStream = outputStream;
    this.errorStream = errorStream;
  }

  /**
   * Returns the set of input sources for the compiler.
   */
  ImmutableSet<Path> getSources() {
    return srcs;
  }

  /**
   * Returns the set of CommonJS input sources for the compiler.
   */
  ImmutableSet<Path> getModules() {
    return modules;
  }

  /**
   * Returns the longest common path prefix for all of the input sources.
   */
  Path getSrcPrefix() {
    return srcPrefix;
  }

  /**
   * Returns the common path prefix for all of the input modules.
   */
  Path getModulePrefix() {
    return modulePrefix;
  }

  /**
   * Returns the set of extern files to use.
   */
  ImmutableSet<Path> getExterns() {
    return externs;
  }

  /**
   * Returns the path to the output directory.
   */
  Path getOutput() {
    return output;
  }

  /**
   * Returns the path to a license file to include with the generated documentation.
   */
  Optional<Path> getLicense() {
    return license;
  }

  /**
   * Returns the path to the readme markdown file, if any, to include in the main index.
   */
  Optional<Path> getReadme() {
    return readme;
  }

  /**
   * Returns the stream to use as stdout.
   */
  PrintStream getOutputStream() {
    return outputStream;
  }

  /**
   * Returns the stream to use as stderr.
   */
  PrintStream getErrorStream() {
    return errorStream;
  }

  private static Path getSourcePrefixPath(ImmutableSet<Path> sources, ImmutableSet<Path> modules) {
    Path prefix = Paths.getCommonPrefix(Iterables.concat(sources, modules));
    if (sources.contains(prefix) || modules.contains(prefix)) {
      prefix = prefix.getParent();
    }
    return prefix;
  }

  private static Path getModulePreixPath(
      Optional<Path> userSupplierPath, ImmutableSet<Path> modules) {
    Path path;
    if (userSupplierPath.isPresent()) {
      path = userSupplierPath.get();
      checkArgument(Files.isDirectory(path), "Module prefix must be a directory: %s", path);
      for (Path module : modules) {
        checkArgument(module.startsWith(path),
            "Module prefix <%s> is not an ancestor of module %s", path, module);
      }
    } else {
      path = Paths.getCommonPrefix(modules);
      if (modules.contains(path) && path.getParent() != null) {
        path = path.getParent();
      }
    }

    // Always display at least one parent directory, if possible.
    for (Path module : modules) {
      if (path.equals(module.getParent())) {
        return Objects.firstNonNull(path.getParent(), path);
      }
    }

    return path;
  }

  /**
   * Loads a new runtime configuration from the provided command line flags.
   */
  static Config load(Flags flags) throws IOException {
    Predicate<Path> notExcluded = notExcluded(flags.excludes);
    Iterable<Path> filteredRawSources = FluentIterable.from(flags.srcs)
        .filter(notExcluded);
    Iterable<Path> filteredRawModules = FluentIterable.from(flags.modules)
        .filter(notExcluded);

    Predicate<Path> regexFilter = excludePatterns(flags.filter);

    ImmutableSet.Builder<Path> srcBuilder = ImmutableSet.builder();
    for (Path source : filteredRawSources) {
      if (Files.isDirectory(source)) {
        srcBuilder.addAll(FluentIterable
            .from(Paths.expandDir(source, unhiddenJsFilesAnd(notExcluded)))
            .filter(regexFilter));
      } else if (regexFilter.apply(source)) {
        srcBuilder.add(source);
      }
    }

    ImmutableSet<Path> sources = srcBuilder.build();

    if (flags.closureLibraryDir.isPresent()) {
      ImmutableSet<Path> depsFiles = ImmutableSet.<Path>builder()
          .add(flags.closureLibraryDir.get().resolve("deps.js"))
          .addAll(flags.closureDepsFile)
          .build();

      try {
        sources = processClosureSources(sources, depsFiles, flags.closureLibraryDir.get());
      } catch (SortedDependencies.CircularDependencyException e) {
        throw new RuntimeException(e);
      }
    }

    ImmutableSet.Builder<Path> modules = ImmutableSet.builder();
    for (Path module : filteredRawModules) {
      if (Files.isDirectory(module)) {
        modules.addAll(FluentIterable
            .from(Paths.expandDir(module, unhiddenJsFilesAnd(notExcluded)))
            .filter(regexFilter));
      } else if (regexFilter.apply(module)) {
        modules.add(module);
      }
    }

    return new Config(
        sources,
        modules.build(),
        ImmutableSet.copyOf(flags.externs),
        flags.outputDir,
        Optional.fromNullable(flags.license),
        Optional.fromNullable(flags.readme),
        Optional.fromNullable(flags.stripModulePrefix),
        System.out,
        System.err);
  }

  private static ImmutableSet<Path> processClosureSources(
      ImmutableSet<Path> sources, ImmutableSet<Path> deps,
      Path closureBase) throws SortedDependencies.CircularDependencyException, IOException {

    Collection<SourceFile> depsFiles = Lists.newLinkedList(
        transform(deps, toSourceFile()));
    Collection<SourceFile> sourceFiles = Lists.newLinkedList(
        transform(sources, toSourceFile()));

    ErrorManager errorManager = new PrintStreamErrorManager(System.err);

    DepsGenerator generator = new DepsGenerator(
        depsFiles,
        sourceFiles,
        DepsGenerator.InclusionStrategy.ALWAYS,
        closureBase.toAbsolutePath().toString(),
        errorManager);

    String rawDeps = generator.computeDependencyCalls();
    errorManager.generateReport();
    if (rawDeps == null) {
      throw new RuntimeException("Encountered Closure dependency conflicts");
    }

    List<DependencyInfo> allDeps = new DepsFileParser(errorManager)
        .parseFile("*generated-deps*", rawDeps);

    List<DependencyInfo> sourceDeps = FluentIterable
        .from(allDeps)
        .filter(isInSources(sources, closureBase))
        .toList();

    List<DependencyInfo> sortedDeps = new SortedDependencies<>(allDeps)
        .getDependenciesOf(sourceDeps, true);

    return ImmutableSet.<Path>builder()
        // Always include Closure's base.js first.
        .add(closureBase.resolve("base.js"))
        .addAll(transform(sortedDeps, toPaths(closureBase)))
        .build();
  }

  private static Predicate<DependencyInfo> isInSources(
      final ImmutableSet<Path> sources, Path closureBaseDir) {
    final Function<DependencyInfo, Path> pathTransform = toPaths(closureBaseDir);
    return new Predicate<DependencyInfo>() {
      @Override
      public boolean apply(DependencyInfo input) {
        return sources.contains(pathTransform.apply(input));
      }
    };
  }

  private static Function<DependencyInfo, Path> toPaths(final Path closureBaseDir) {
    return new Function<DependencyInfo, Path>() {
      @Override
      public Path apply(DependencyInfo input) {
        return closureBaseDir.resolve(input.getPathRelativeToClosureBase())
            .normalize()
            .toAbsolutePath();
      }
    };
  }

  private static Function<Path, SourceFile> toSourceFile() {
    return new Function<Path, SourceFile>() {
      @Override
      public SourceFile apply(Path input) {
        return SourceFile.fromFile(input.toAbsolutePath().toFile());
      }
    };
  }

  private static Predicate<Path> excludePatterns(final List<Pattern> exclusionPatterns) {
    return new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        for (Pattern pattern : exclusionPatterns) {
          if (pattern.matcher(input.toString()).matches()) {
            return false;
          }
        }
        return true;
      }
    };
  }

  private static Predicate<Path> notExcluded(final List<Path> excludes) {
    return new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        for (Path exclude : excludes) {
          if (input.equals(exclude)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  private static DirectoryStream.Filter<Path> unhiddenJsFilesAnd(
      final Predicate<Path> notExcluded) {
    return new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path entry) throws IOException {
        return notExcluded.apply(entry)
            && !Files.isHidden(entry)
            && (Files.isDirectory(entry) || entry.toString().endsWith(".js"));
      }
    };
  }
}
