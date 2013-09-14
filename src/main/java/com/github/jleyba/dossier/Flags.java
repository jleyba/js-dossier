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

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes the runtime configuration for the app.
 */
class Flags {

  @Option(
      name = "--help", aliases = "-h",
      usage = "Print this help message and exit.")
  boolean displayHelp;

  @Option(
      name = "--src", aliases = "-s",
      handler = SimplePathHandler.class,
      required = true,
      usage = "List of sources to include as input to the Closure compiler. If a source path " +
          "refers to a directory, all .js files under that directory will be included.")
  List<Path> srcs = new LinkedList<>();

  @Option(
      name = "--exclude", aliases = "-x",
      handler = SimplePathHandler.class,
      usage = "List of source files to exclude; may be specified as the path to a specific " +
          "file or directory. If a directory is specified, all of its descendants will be " +
          "excluded.")
  List<Path> excludes = new LinkedList<>();

  @Option(
      name = "--extern", aliases = "-e",
      handler = SimplePathHandler.class,
      usage = "Defines an externs file to pass to the Closure compiler.")
  List<Path> externs = new LinkedList<>();

  @Option(
      name = "--output", aliases = "-o",
      handler = OutputDirPathHandler.class,
      usage = "Path to the directory to write all generated documentation to.",
      required = true)
  Path outputDir;

  /**
   * Initializes a new runtime configuration from the given command line flags. If the givne
   * command line arguments are invalid, or the usage string is requested via the
   * {@link #displayHelp --help} flag, this method will print the usage string to stderr and
   * terminate the program.
   *
   * @param args The command line flags.
   * @return The new runtime configuration.
   */
  static Config initConfig(String[] args) {
    Flags flags = new Flags();
    CmdLineParser parser = new CmdLineParser(flags);

    boolean isConfigValid = true;
    List<String> preprocessedArgs = preprocessArgs(args);
    try {
      parser.parseArgument(preprocessedArgs);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      isConfigValid = false;
    }

    if (!isConfigValid || flags.displayHelp) {
      parser.printUsage(System.err);
      System.exit(1);
    }

    Iterable<Path> filteredRawSources = FluentIterable.from(flags.srcs)
        .filter(excluded(flags.excludes));
    ImmutableSet.Builder<Path> sources = ImmutableSet.builder();
    for (Path source : filteredRawSources) {
      if (Files.isDirectory(source)) {
        try {
          sources.addAll(Paths.expandDir(source, unhiddenJsFiles()));
        } catch (IOException e) {
          e.printStackTrace(System.err);
          System.exit(2);
        }
      } else {
        sources.add(source);
      }
    }

    return new Config(
        sources.build(),
        ImmutableSet.copyOf(flags.externs),
        flags.outputDir);
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

  public static class OutputDirPathHandler extends OptionHandler<Path> {

    private final FileSystem fileSystem = FileSystems.getDefault();

    public OutputDirPathHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Path> setter) {
      super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
      Path path = fileSystem.getPath(params.getParameter(0));
      if (Files.exists(path) && !Files.isDirectory(path)) {
        throw new CmdLineException(owner, "Path must be a directory: " + path);
      }
      setter.addValue(path);
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "PATH";
    }
  }

  public static class SimplePathHandler extends OptionHandler<Path> {
    private final FileSystem fileSystem = FileSystems.getDefault();

    public SimplePathHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Path> setter) {
      super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
      Path path = fileSystem.getPath(params.getParameter(0));
      if (!Files.exists(path)) {
        throw new CmdLineException(owner, "Path does not exist: " + path);
      }

      if (!Files.isReadable(path)) {
        throw new CmdLineException(owner, "Path is not readable: " + path);
      }

      setter.addValue(path);
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "PATH";
    }
  }
}
