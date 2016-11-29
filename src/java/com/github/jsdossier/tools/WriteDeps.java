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

package com.github.jsdossier.tools;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.DepsGenerator;
import com.google.javascript.jscomp.deps.ModuleLoader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Writes the dependency map info needed for JavaScript testing.
 */
final class WriteDeps {
  static final class Flags {
    @Option(
        name = "--input", aliases = "-i", required = true,
        usage = "Specified the path to a file to extract dependency info from")
    List<String> inputs = new ArrayList<>();

    @Option(
        name = "--closure", aliases = "-c", required = true,
        usage = "Path to the Closure library directory")
    String closure = "";

    @Option(
        name = "--output", aliases = "-o", required = true,
        usage = "Path to the file to write")
    String output = "";
  }

  public static void main(String[] args) throws CmdLineException, IOException {
    Flags flags = new Flags();

    CmdLineParser parser = new CmdLineParser(flags);
    parser.setUsageWidth(79);
    parser.parseArgument(args);

    FileSystem fs = FileSystems.getDefault();
    Path closure = fs.getPath(flags.closure);
    Path output = fs.getPath(flags.output);

    ImmutableList<SourceFile> depsFile = ImmutableList.of(
        SourceFile.fromFile(closure.resolve("deps.js").toString()));
    List<SourceFile> sourceFiles =
        flags.inputs.stream().map(SourceFile::fromFile).collect(toList());

    PrintStreamErrorManager errorManager =  new PrintStreamErrorManager(System.err);
    DepsGenerator generator = new DepsGenerator(
        depsFile,
        sourceFiles,
        DepsGenerator.InclusionStrategy.DO_NOT_DUPLICATE,
        closure.toAbsolutePath().toString(),
        errorManager,
        ModuleLoader.EMPTY);

    String calls = generator.computeDependencyCalls();
    if (errorManager.getErrorCount() > 0) {
      errorManager.generateReport();
      return;
    }

    try (BufferedWriter writer = Files.newBufferedWriter(output, UTF_8)) {
      writer.write(calls);
      writer.flush();
    }
  }
}
