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

package com.github.jsdossier.soy;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Generates JavaScript classes from Soy templates.
 */
final class SoyJsCompiler {

  private static final class Flags {
    @Option(
        name = "--help", aliases = "-h",
        usage = "Print this help message and exit.")
    private boolean help;

    @Option(
        name = "--output_dir",
        usage = "The directory to write the generated files to.",
        required =  true)
    private String outputDir;

    @Option(
        name = "--file", aliases = "-f",
        usage = "The input files to compile.",
        required = true)
    private List<String> files = new ArrayList<>();
  }

  public static void main(String[] args) throws IOException {
    Flags flags = new Flags();
    CmdLineParser parser = new CmdLineParser(flags);
    parser.setUsageWidth(79);

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      if (!flags.help) {
        System.err.println(e.getMessage());
      }
      flags.help = true;
    }

    if (flags.help) {
      System.err.println("\nUsage: SoyJsCompiler [options]");
      System.err.println("\nwhere options include:");
      parser.printUsage(System.err);
      System.exit(1);
    }

    FileSystem fs = FileSystems.getDefault();

    Path outputDir = fs.getPath(flags.outputDir).toAbsolutePath();
    checkArgument(
        !exists(outputDir) || isDirectory(outputDir), "Must be a directory: %s", outputDir);

    ImmutableMap.Builder<Path, Path> inoutBuilder = ImmutableMap.builder();
    for (String file : flags.files) {
      Path inputFile = fs.getPath(file);
      Path outputFile = outputDir.resolve(inputFile.getFileName() + ".js");
      inoutBuilder.put(inputFile, outputFile);
    }
    ImmutableMap<Path, Path> inoutMap = inoutBuilder.build();

    Injector injector = Guice.createInjector(new DossierSoyModule());
    DossierSoyTypeProvider typeProvider = injector.getInstance(DossierSoyTypeProvider.class);
    SoyFileSet.Builder filesetBuilder = injector.getInstance(SoyFileSet.Builder.class)
        .setLocalTypeRegistry(
            new SoyTypeRegistry(ImmutableSet.of((SoyTypeProvider) typeProvider)));
    for (Path inputFile : inoutMap.keySet()) {
      filesetBuilder.add(inputFile.toFile());
    }
    SoyFileSet fileSet = filesetBuilder.build();

    SoyJsSrcOptions options = new SoyJsSrcOptions();

    // These options must be disabled before enabling goog modules below.
    options.setShouldDeclareTopLevelNamespaces(false);
    options.setShouldProvideRequireSoyNamespaces(false);
    options.setShouldProvideRequireJsFunctions(false);
    options.setShouldProvideBothSoyNamespacesAndJsFunctions(false);
    options.setShouldGenerateJsdoc(true);

    options.setShouldGenerateGoogModules(true);

    Iterator<Path> outputFiles = inoutMap.values().iterator();
    for (String content : fileSet.compileToJsSrc(options, null)) {
      Path file = outputFiles.next();
      Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
  }
}
