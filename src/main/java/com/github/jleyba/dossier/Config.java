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
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.DepsFileParser;
import com.google.javascript.jscomp.deps.DepsGenerator;
import com.google.javascript.jscomp.deps.SortedDependencies;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Describes the runtime configuration for the app.
 */
class Config {

  private final ImmutableSet<Path> srcs;
  private final ImmutableSet<Path> externs;
  private final Path srcPrefix;
  private final Path output;

  /**
   * Creates a new runtime configuration.
   *
   * @param srcs The list of compiler input sources.
   * @param externs The list of extern files for the Closure compiler.
   * @param output Path to the output directory.
   * @throws IllegalStateException If the source and extern lists intersect, or if the output
   *     path is not a directory.
   */
  private Config(ImmutableSet<Path> srcs, ImmutableSet<Path> externs, Path output) {
    checkArgument(intersection(srcs, externs).isEmpty(),
        "The sources and externs inputs must be disjoint:\n  sources: %s\n  externs: %s",
        srcs, externs);
    checkArgument(!Files.exists(output) || Files.isDirectory(output),
        "Output path, %s, is not a directory", output);

    this.srcs = srcs;
    this.srcPrefix = Paths.getCommonPrefix(srcs);
    this.externs = externs;
    this.output = output;
  }

  /**
   * Returns the set of input sources for the compiler.
   */
  ImmutableSet<Path> getSources() {
    return srcs;
  }

  /**
   * Returns the longest common path prefix for all of the input sources.
   */
  Path getSrcPrefix() {
    return srcPrefix;
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
   * Loads a new runtime configuration from the provided command line flags.
   */
  static Config load(Flags flags) throws IOException {
    Iterable<Path> filteredRawSources = FluentIterable.from(flags.srcs)
        .filter(excluded(flags.excludes));
    ImmutableSet.Builder<Path> srcBuilder = ImmutableSet.builder();
    for (Path source : filteredRawSources) {
      if (Files.isDirectory(source)) {
        srcBuilder.addAll(Paths.expandDir(source, unhiddenJsFiles()));
      } else {
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

    return new Config(
        sources,
        ImmutableSet.copyOf(flags.externs),
        flags.outputDir);
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

  private static Predicate<Path> excluded(final List<Path> excludes) {
    return new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        for (Path exclude : excludes) {
          if (input.startsWith(exclude)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  private static DirectoryStream.Filter<Path> unhiddenJsFiles() {
    return new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path entry) throws IOException {
        return !Files.isHidden(entry)
            && (Files.isDirectory(entry) || entry.toString().endsWith(".js"));
      }
    };
  }
}
