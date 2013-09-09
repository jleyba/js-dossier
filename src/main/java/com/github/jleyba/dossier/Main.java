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

import static com.google.common.collect.Lists.newLinkedList;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends CommandLineRunner {

  private static final List<String> STANDARD_FLAGS = ImmutableList.of(
      "--jscomp_error=accessControls",
      "--jscomp_error=ambiguousFunctionDecl",
      "--jscomp_error=checkRegExp",
      "--jscomp_error=checkTypes",
      "--jscomp_error=checkVars",
      "--jscomp_error=constantProperty",
//      "--jscomp_error=deprecated",
      "--jscomp_error=duplicateMessage",
      "--jscomp_error=es5Strict",
      "--jscomp_error=externsValidation",
      "--jscomp_error=fileoverviewTags",
      "--jscomp_error=globalThis",
      "--jscomp_error=invalidCasts",
      "--jscomp_error=missingProperties",
      "--jscomp_error=nonStandardJsDocs",
      "--jscomp_error=strictModuleDepCheck",
      "--jscomp_error=typeInvalidation",
      "--jscomp_error=undefinedVars",
      "--jscomp_error=unknownDefines",
      "--jscomp_error=uselessCode",
      "--jscomp_error=visibility",
      "--third_party=false");

  private final Config config;

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
        new DocPass(config, getCompiler()));

    options.setCustomPasses(customPasses);
    return options;
  }

  private static List<String> preprocessArgs(String[] args) {
    Pattern argPattern = Pattern.compile("(--[a-zA-Z_]+)=(.*)");
    Pattern quotesPattern = Pattern.compile("^['\"](.*)['\"]$");
    List<String> processedArgs = Lists.newArrayList();
    for (String arg : args) {
      Matcher matcher = argPattern.matcher(arg);
      if (matcher.matches()) {
        processedArgs.add(matcher.group(1));

        String value = matcher.group(2);
        Matcher quotesMatcher = quotesPattern.matcher(value);
        if (quotesMatcher.matches()) {
          processedArgs.add(quotesMatcher.group(1));
        } else {
          processedArgs.add(value);
        }
      } else {
        processedArgs.add(arg);
      }
    }

    return processedArgs;
  }

  public static void main(String[] args) {
    final Config config = new Config();
    CmdLineParser parser = new CmdLineParser(config);

    boolean isConfigValid = true;
    List<String> preprocessedArgs = preprocessArgs(args);
    try {
      parser.parseArgument(preprocessedArgs);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      isConfigValid = false;
    }

    if (!isConfigValid || config.displayHelp) {
      parser.printUsage(System.err);
      System.exit(1);
    }

    // Remove duplicates.
    Set<Path> srcFiles = null;
    try {
      srcFiles = ImmutableSet.copyOf(config.filteredSrcs());
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }

    List<String> compilerFlags = Lists.newArrayListWithExpectedSize(
        STANDARD_FLAGS.size() + srcFiles.size());
    compilerFlags.addAll(STANDARD_FLAGS);
    for (Path path : srcFiles) {
      compilerFlags.add("--js=" + path);
    }

    PrintStream nullStream = new PrintStream(new OutputStream() {
      @Override
      public void write(int i) throws IOException {
      }
    });
    args = compilerFlags.toArray(new String[compilerFlags.size()]);

    Main main = new Main(args, nullStream, nullStream, config);
    if (main.shouldRunCompiler()) {
      main.run();
    } else {
      System.exit(-1);
    }
  }
}
