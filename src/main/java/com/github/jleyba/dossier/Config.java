package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;

import com.google.common.base.Predicate;
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Describes the runtime configuration for the app.
 */
class Config {

  @Option(
      name = "--help", aliases = "-h",
      usage = "Print this help message and exit.")
  boolean displayHelp;

  @Option(
      name = "--closure_root", aliases = "-c",
      handler = ClosureDirPathHandler.class,
      usage = "Path to the Closure library's root directory. This directory must contain the " +
          "base.js and deps.js files. This option is short hand for --src PATH --doc_exclude PATH")
  Path closureRoot;

  @Option(
      name = "--src", aliases = "-s",
      handler = ExpandoPathHandler.class,
      required = true,
      usage = "List of sources to include as input to the Closure compiler. If a source path " +
          "refers to a directory, all .js files under that directory will be included. If " +
          "multiple source patterns are specified, they must all be under a common root.")
  List<Path> srcs = new LinkedList<>();

  @Option(
      name = "--exclude", aliases = "-e",
      handler = ExpandoPathHandler.class,
      usage = "List of source files to exclude; may be specified as the path to a specific " +
          "file or directory. If a directory is specified, all of its descendants will be " +
          "excluded")
  List<Path> excludes = new LinkedList<>();

  @Option(
      name = "--doc_exclude",
      handler = ExpandoPathHandler.class,
      usage = "List of files to include as sources to the Closure compiler, but should be " +
          "excluded when generating documentation. If a directory is specified, all of its " +
          "descendant .js files will be excluded.")
  List<Path> excludeDocs = new LinkedList<>();

  @Option(
      name = "--extern",
      handler = SimplePathHandler.class,
      usage = "Defines an externs file to pass to the Closure compiler.")
  List<Path> externs = new LinkedList<>();

  @Option(
      name = "--output", aliases = "-o",
      handler = OutputDirPathHandler.class,
      usage = "Path to the directory to write all generated documentation to.",
      required = true)
  Path outputDir;

  Iterable<Path> filteredSrcs() throws IOException {
    Iterable<Path> allSrcs = srcs;
    if (closureRoot != null) {
      allSrcs = concat(allSrcs, expandDir(closureRoot));
    }
    return filter(allSrcs, new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        return !excludes.contains(input);
      }
    });
  }

  Iterable<Path> filteredDocSrcs() {
    return filter(srcs, new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        return !excludes.contains(input) && !excludeDocs.contains(input);
      }
    });
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

  public static class ClosureDirPathHandler extends OptionHandler<Path> {

    private final FileSystem fileSystem = FileSystems.getDefault();

    public ClosureDirPathHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Path> setter) {
      super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
      Path path = fileSystem.getPath(params.getParameter(0));
      if (!Files.isReadable(path) || !Files.isDirectory(path)) {
        throw new CmdLineException(owner, "File must be a directory: " + path);
      }
      if (!Files.exists(path.resolve("base.js"))) {
        throw new CmdLineException(
            owner, "Path must reference the Closure library's root directory: " + path);
      }
      setter.addValue(path);
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "DIR";
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

      if (Files.isDirectory(path)) {
        throw new CmdLineException(owner, "Path is a directory: " + path);
      }

      setter.addValue(path);
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "PATH";
    }
  }

  public static class ExpandoPathHandler extends OptionHandler<Path> {
    private final FileSystem fileSystem = FileSystems.getDefault();

    public ExpandoPathHandler(
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

      if (Files.isDirectory(path)) {
        try {
          for (Path p : expandDir(path)) {
            setter.addValue(p);
          }
        } catch (IOException e) {
          throw new CmdLineException(owner, e);
        }
      } else {
        setter.addValue(path);
      }

      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "PATH";
    }
  }

  static List<Path> expandDir(Path dir) throws IOException {
    checkArgument(Files.isDirectory(dir));
    List<Path> paths = new LinkedList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, new JsFiles())) {
      for (Path path : stream) {
        if (Files.isDirectory(path)) {
          paths.addAll(expandDir(path));
        } else {
          paths.add(path);
        }
      }
    }
    return paths;
  }

  private static class JsFiles implements DirectoryStream.Filter<Path> {

    @Override
    public boolean accept(Path entry) throws IOException {
      return Files.isDirectory(entry) || entry.toString().endsWith(".js");
    }
  }

}
