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

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newLinkedList;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.ByteStreams;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CustomPassExecutionTime;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class Main extends CommandLineRunner {

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

  private final Config config;
  private final DocRegistry docRegistry = new DocRegistry();

  private Main(String[] args, PrintStream out, PrintStream err, Config config) {
    super(args, out, err);
    this.config = config;
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = new CompilerOptions();

    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    // IDE mode must be enabled or all of the jsdoc info will be stripped from the AST.
    options.setIdeMode(true);

    Multimap<CustomPassExecutionTime, CompilerPass> customPasses;
    customPasses = Multimaps.newListMultimap(
        Maps.<CustomPassExecutionTime, Collection<CompilerPass>>newHashMap(),
        new Supplier<List<CompilerPass>>() {
          @Override
          public List<CompilerPass> get() {
            return newLinkedList();
          }
        });

    customPasses.put(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS,
        new DocPass(getCompiler(), docRegistry));

    options.setCustomPasses(customPasses);
    return options;
  }

  private void runCompiler() {
    if (!shouldRunCompiler()) {
      System.exit(-1);
    }

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

    LinkResolver linkResolver = new LinkResolver(config.getOutput(), docRegistry);
    DocWriter writer = new DocWriterFactory(linkResolver).createDocWriter(config, docRegistry);
    try {
      writer.generateDocs(getCompiler().getTypeRegistry());
    } catch (IOException e) {
      e.printStackTrace(System.err);
      System.exit(-3);
    }
  }

  private static Function<Path, String> toFlag(final String flagPrefix) {
    return new Function<Path, String>() {
      @Override
      public String apply(Path input) {
        return flagPrefix + input;
      }
    };
  }

  public static void main(String[] args) {
    Flags flags = Flags.parse(args);
    Config config = null;
    try {
      config = Config.load(flags);
    } catch (IOException e) {
      e.printStackTrace(System.err);
      System.exit(-1);
    }

    ImmutableList<String> compilerFlags = ImmutableList.<String>builder()
        .addAll(transform(config.getSources(), toFlag("--js=")))
        .addAll(transform(config.getExterns(), toFlag("--extern=")))
        .addAll(STANDARD_FLAGS)
        .build();

    PrintStream nullStream = new PrintStream(ByteStreams.nullOutputStream());
    args = compilerFlags.toArray(new String[compilerFlags.size()]);

    Main main = new Main(args, nullStream, System.err, config);
    main.runCompiler();
  }
}
