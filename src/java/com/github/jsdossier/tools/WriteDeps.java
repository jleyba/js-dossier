package com.github.jsdossier.tools;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.DepsGenerator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    ImmutableList<SourceFile> sourceFiles = FluentIterable.from(flags.inputs)
        .transform(new Function<String, SourceFile>() {
          @Override
          public SourceFile apply(String input) {
            return SourceFile.fromFile(input);
          }
        })
        .toList();

    DepsGenerator generator = new DepsGenerator(
        depsFile,
        sourceFiles,
        DepsGenerator.InclusionStrategy.DO_NOT_DUPLICATE,
        closure.toAbsolutePath().toString(),
        new PrintStreamErrorManager(System.err));
    try (BufferedWriter writer = Files.newBufferedWriter(output, UTF_8)) {
      writer.write(generator.computeDependencyCalls());
      writer.flush();
    }
  }
}
