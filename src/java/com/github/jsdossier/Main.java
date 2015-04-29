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
import static com.google.common.collect.Iterables.transform;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.jscomp.SourceFile;

import com.github.jsdossier.jscomp.DossierCompiler;
import org.joda.time.Instant;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Main extends CommandLineRunner {

  private final Config config;

  private TypeRegistry typeRegistry;

  @VisibleForTesting
  Main(String[] args, PrintStream out, PrintStream err, Config config) {
    super(args, out, err);
    this.config = config;
  }

  @Override
  protected com.google.javascript.jscomp.Compiler createCompiler() {
    return new DossierCompiler(getErrorPrintStream(), config.getModules());
  }

  @Override
  @SuppressWarnings("unchecked")
  protected CompilerOptions createOptions() {
    AbstractCompiler compiler = getCompiler();
    if (!(compiler instanceof DossierCompiler)) {
      throw new AssertionError();
    }
    typeRegistry = new TypeRegistry(compiler.getTypeRegistry());
    return createOptions(config.getFileSystem(), typeRegistry, (DossierCompiler) compiler);
  }

  @VisibleForTesting
  static CompilerOptions createOptions(
      FileSystem fileSystem,
      TypeRegistry typeRegistry,
      DossierCompiler compiler) {
    CompilerOptions options = new CompilerOptions();

    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    // IDE mode must be enabled or all of the jsdoc info will be stripped from the AST.
    options.setIdeMode(true);

    // For easier debugging.
    options.setPrettyPrint(true);

    ProvidedSymbolsCollectionPass providedNamespacesPass =
        new ProvidedSymbolsCollectionPass(compiler, typeRegistry, fileSystem);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, providedNamespacesPass);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS,
        new DocPass(compiler, typeRegistry, fileSystem));

    return options;
  }

  @Override
  protected List<SourceFile> createInputs(List<String> files, boolean allowStdIn)
      throws IOException {
    List<SourceFile> inputs = new ArrayList<>(files.size());
    for (String filename : files) {
      checkArgument(!"-".equals(filename), "Reading from stdin not supported");
      Path path = config.getFileSystem().getPath(filename);
      try (InputStream inputStream = newInputStream(path)) {
        SourceFile file = SourceFile.fromInputStream(filename, inputStream, UTF_8);
        inputs.add(file);
      }
    }
    return inputs;
  }

  private void runCompiler() {
    if (!shouldRunCompiler()) {
      System.exit(-1);
    }

    Instant start = Instant.now();
    config.getOutputStream().println("Generating documentation...");

    int result = 0;
    try {
      result = doRun();
    } catch (FlagUsageException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
    } catch (Throwable t) {
      t.printStackTrace(System.err);
      System.exit(-2);
    }

    if (result != 0) {
      System.exit(result);
    }

    DocWriter writer = new DocWriter(
        config.getOutput(),
        Iterables.concat(config.getSources(), config.getModules()),
        config.getSrcPrefix(),
        config.getReadme(),
        config.getCustomPages(),
        typeRegistry,
        config.getTypeFilter(),
        new Linker(
            config.getOutput(),
            config.getSrcPrefix(),
            config.getModulePrefix(),
            config.getTypeFilter(),
            typeRegistry),
        new CommentParser(config.useMarkdown()));
    try {
      writer.generateDocs();
      if (config.isZipOutput()) {
        config.getOutput().getFileSystem().close();
      }
    } catch (IOException e) {
      e.printStackTrace(System.err);
      System.exit(-3);
    }

    Instant stop = Instant.now();
    String output = new PeriodFormatterBuilder()
        .appendHours().appendSuffix("h")  // I hope not...
        .appendSeparator(" ")
        .appendMinutes().appendSuffix("m")
        .appendSeparator(" ")
        .appendSecondsWithOptionalMillis().appendSuffix("s")
        .toFormatter()
        .print(new Period(start, stop));

    config.getOutputStream().println("Finished in " + output);
  }

  private static Function<Path, String> toFlag(final String flagPrefix) {
    return new Function<Path, String>() {
      @Override
      public String apply(Path input) {
        return flagPrefix + input;
      }
    };
  }

  private static final List<String> STANDARD_FLAGS = ImmutableList.of(
      "--jscomp_warning=accessControls",
      "--jscomp_warning=ambiguousFunctionDecl",
      "--jscomp_warning=checkRegExp",
      "--jscomp_warning=checkTypes",
      "--jscomp_warning=checkVars",
      "--jscomp_warning=constantProperty",
      "--jscomp_warning=deprecated",
      "--jscomp_warning=duplicateMessage",
      "--jscomp_warning=es5Strict",
      "--jscomp_warning=externsValidation",
      "--jscomp_warning=fileoverviewTags",
      "--jscomp_warning=globalThis",
      "--jscomp_warning=invalidCasts",
      "--jscomp_warning=missingProperties",
      "--jscomp_warning=nonStandardJsDocs",
      "--jscomp_warning=strictModuleDepCheck",
      "--jscomp_warning=typeInvalidation",
      "--jscomp_warning=undefinedVars",
      "--jscomp_warning=unknownDefines",
      "--jscomp_warning=uselessCode",
      "--jscomp_warning=visibility",
      "--third_party=false");

  @VisibleForTesting static void run(String[] args, FileSystem fileSystem) {
    Flags flags = Flags.parse(args, fileSystem);
    Config config = null;
    try (InputStream stream = newInputStream(flags.config)) {
      config = Config.load(stream, fileSystem);
    } catch (IOException e) {
      e.printStackTrace(System.err);
      System.exit(-1);
    }

    if (flags.printConfig) {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      String header = " Configuration  ";
      int len = header.length();
      String pad = Strings.repeat("=", len / 2);

      System.err.println(pad + header + pad);
      System.err.println(gson.toJson(config.toJson()));
      System.err.println(Strings.repeat("=", 79));
      System.exit(1);
    }

    Iterable<String> standardFlags = STANDARD_FLAGS;
    if (config.isStrict()) {
      standardFlags = transform(standardFlags, new Function<String, String>() {
        @Override
        public String apply(String input) {
          return input.replace("--jscomp_warning", "--jscomp_error");
        }
      });
    }

    ImmutableList<String> compilerFlags = ImmutableList.<String>builder()
        .addAll(transform(config.getSources(), toFlag("--js=")))
        .addAll(transform(config.getModules(), toFlag("--js=")))
        .addAll(transform(config.getExterns(), toFlag("--externs=")))
        .add("--language_in=" + config.getLanguage().getName())
        .addAll(standardFlags)
        .build();

    PrintStream nullStream = new PrintStream(ByteStreams.nullOutputStream());
    args = compilerFlags.toArray(new String[compilerFlags.size()]);

    Logger log = Logger.getLogger(Main.class.getPackage().getName());
    log.setLevel(Level.WARNING);
    log.addHandler(new Handler() {
      @Override
      public void publish(LogRecord record) {
        System.err.printf(
            "[%s][%s] %s\n",
            record.getLevel(),
            record.getLoggerName(),
            record.getMessage());
      }

      @Override public void flush() {}
      @Override public void close() {}
    });

    Main main = new Main(args, nullStream, System.err, config);
    main.runCompiler();
  }

  public static void main(String[] args) {
    run(args, FileSystems.getDefault());

    // Explicitly exit. Soy bootstraps itself with Guice, whose finalers cause the JVM to
    // take >30 seconds to shutdown.
    // TODO: track down the exact cause of shutdown delay and fix it.
    System.exit(0);
  }
}
