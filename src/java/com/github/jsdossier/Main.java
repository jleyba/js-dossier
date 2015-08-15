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
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.transform;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;

import com.github.jsdossier.jscomp.DossierCompiler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
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

final class Main {
  private Main() {}

  private static final String INDEX_FILE_NAME = "index.html";

  private static class Runner extends CommandLineRunner {
    private final Config config;
    private TypeRegistry typeRegistry;

    private Runner(String[] args, PrintStream out, PrintStream err, Config config) {
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
      return Main.createOptions(config.getFileSystem(), typeRegistry, (DossierCompiler) compiler);
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

    @Override
    public int doRun() throws IOException {
      try {
        return super.doRun();
      } catch (FlagUsageException e) {
        // FlagUsageException has protected visibility, so have to wrap for rethrow.
        throw new IllegalArgumentException(e);
      }
    }
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

  private static void print(Config config) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String header = " Configuration  ";
    int len = header.length();
    String pad = Strings.repeat("=", len / 2);

    System.err.println(pad + header + pad);
    System.err.println(gson.toJson(config.toJson()));
    System.err.println(Strings.repeat("=", 79));
  }

  private static String[] getCompilerFlags(Config config) {
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
    return compilerFlags.toArray(new String[compilerFlags.size()]);
  }

  private static void configureLogging() {
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
  }

  private static Predicate<NominalType> isNotTypedef() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(NominalType input) {
        return !input.isTypedef();
      }
    };
  }

  private static Predicate<NominalType> isNonEmptyNamespace() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(NominalType input) {
        return !input.isEmptyNamespace();
      }
    };
  }

  @VisibleForTesting
  static int run(String[] args, FileSystem fileSystem) throws IOException {
    Flags flags = Flags.parse(args, fileSystem);
    Config config;
    try (InputStream stream = newInputStream(flags.config)) {
      config = Config.load(stream, fileSystem);
    }

    if (flags.printConfig) {
      print(config);
      return 1;
    }
    configureLogging();

    Runner runner = new Runner(
        getCompilerFlags(config),
        new PrintStream(ByteStreams.nullOutputStream()),
        System.err,
        config);

    if (!runner.shouldRunCompiler()) {
      return -1;
    }

    Instant start = Instant.now();
    System.out.println("Generating documentation...");

    int result = runner.doRun();
    if (result != 0) {
      return result;
    }

    Linker linker = new Linker(
        config.getOutput(),
        config.getSrcPrefix(),
        config.getModulePrefix(),
        config.getTypeFilter(),
        runner.typeRegistry);

    ImmutableList<NominalType> types = FluentIterable
        .from(runner.typeRegistry.getNominalTypes())
        .filter(not(config.getTypeFilter()))
        .filter(isNonEmptyNamespace())
        .filter(isNotTypedef())
        .toSortedList(new QualifiedNameComparator());

    ImmutableList<NominalType> modules = FluentIterable
        .from(runner.typeRegistry.getModules())
        .toSortedList(new DisplayNameComparator(linker));

    NavIndexFactory index = NavIndexFactory.create(
        config.getOutput().resolve(INDEX_FILE_NAME),
        !modules.isEmpty(),
        !types.isEmpty(),
        config.getCustomPages());

    DocWriter writer = new DocWriter(
        config.getOutput(),
        Iterables.concat(config.getSources(), config.getModules()),
        types,
        modules,
        config.getSrcPrefix(),
        config.getReadme(),
        config.getCustomPages(),
        runner.typeRegistry,
        config.getTypeFilter(),
        linker,
        new CommentParser(),
        index,
        new DefaultDocTemplate());

    writer.generateDocs();
    if (config.isZipOutput()) {
      config.getOutput().getFileSystem().close();
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

    System.out.println("Finished in " + output);
    return 0;
  }

  public static void main(String[] args) {
    int exitCode;
    try {
      exitCode = run(args, FileSystems.getDefault());
    } catch (IOException e) {
      e.printStackTrace(System.err);
      exitCode = 2;
    }
    System.exit(exitCode);
  }
}
