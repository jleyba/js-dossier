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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;
import static java.util.stream.Collectors.toList;

import com.github.jsdossier.annotations.Externs;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Stderr;
import com.github.jsdossier.annotations.Stdout;
import com.github.jsdossier.annotations.StrictMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provider;
import com.google.javascript.jscomp.AbstractCommandLineRunner;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.FlagUsageException;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WhitelistWarningsGuard;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Customized command line runner that invokes the compiler in order to populate the type registry
 * for the documentation step.
 */
@Singleton
public final class DossierCommandLineRunner extends CommandLineRunner implements Callable<Integer> {

  private static final ImmutableSet<String> CHECKS =
      ImmutableSet.of(
          "accessControls",
          "ambiguousFunctionDecl",
          "checkRegExp",
          "checkTypes",
          "checkVars",
          "constantProperty",
          "deprecated",
          "duplicateMessage",
          "es5Strict",
          "externsValidation",
          "fileoverviewTags",
          "globalThis",
          "invalidCasts",
          "missingProperties",
          "nonStandardJsDocs",
          "strictModuleDepCheck",
          "typeInvalidation",
          "undefinedVars",
          "unknownDefines",
          "uselessCode",
          "visibility");

  private final FileSystem inputFileSystem;
  private final Provider<DossierCompiler> compilerProvider;
  private final Provider<CompilerOptions> optionsProvider;

  @Inject
  DossierCommandLineRunner(
      @StrictMode boolean strictMode,
      @Stdout PrintStream stdout,
      @Stderr PrintStream stderr,
      @Input FileSystem inputFileSystem,
      @Input ImmutableSet<Path> sources,
      @Externs ImmutableSet<Path> externs,
      Provider<DossierCompiler> compilerProvider,
      Provider<CompilerOptions> optionsProvider) {
    super(new String[0], stdout, stderr);
    this.inputFileSystem = inputFileSystem;
    this.compilerProvider = compilerProvider;
    this.optionsProvider = optionsProvider;

    getCommandLineConfig()
        .setWarningGuards(
            CHECKS
                .stream()
                .map(
                    c ->
                        new HiddenFlagEntry<>(
                            strictMode ? CheckLevel.ERROR : CheckLevel.WARNING, c))
                .collect(toList()))
        .setCodingConvention(new ClosureCodingConvention())
        .setMixedJsSources(
            sources
                .stream()
                .map(s -> new HiddenFlagEntry<>(JsSourceType.JS, s.toString()))
                .collect(toList()))
        .setExterns(externs.stream().map(Path::toString).collect(toList()));
  }

  /** Hack to break encapsulation. */
  private static final class HiddenFlagEntry<T> extends AbstractCommandLineRunner.FlagEntry<T> {
    private HiddenFlagEntry(T flag, String value) {
      super(flag, value);
    }
  }

  @Override
  protected com.google.javascript.jscomp.Compiler createCompiler() {
    return compilerProvider.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected CompilerOptions createOptions() {
    return optionsProvider.get();
  }

  @Override
  protected void addWhitelistWarningsGuard(CompilerOptions options, File whitelistFile) {
    options.addWarningsGuard(WhitelistWarningsGuard.fromFile(whitelistFile));
  }

  @Override
  protected List<SourceFile> createInputs(
      List<FlagEntry<JsSourceType>> files,
      List<JsonFileSpec> jsonFiles,
      boolean allowStdIn,
      List<JsModuleSpec> jsModuleSpecs)
      throws IOException {
    // Compiler defaults to reading from stdin if no files specified, but we don't support that.
    if (files.size() == 1 && "-".equals(files.get(0).getValue())) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<SourceFile> inputs = ImmutableList.builder();
    for (FlagEntry<JsSourceType> flagEntry : files) {
      checkArgument(
          flagEntry.getFlag() != JsSourceType.JS_ZIP,
          "Zip file inputs are not supported: %s",
          flagEntry.getValue());
      checkArgument(!"-".equals(flagEntry.getValue()), "Reading from stdin is not supported");

      Path path = inputFileSystem.getPath(flagEntry.getValue());
      try (InputStream inputStream = newInputStream(path)) {
        SourceFile file = SourceFile.fromInputStream(path.toString(), inputStream, UTF_8);
        inputs.add(file);
      }
    }
    return inputs.build();
  }

  @Override
  public Integer call() throws IOException {
    try {
      return doRun();
    } catch (FlagUsageException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
