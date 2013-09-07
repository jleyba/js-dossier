package com.github.jleyba.dossier;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends CommandLineRunner {

  private static final String DOC_DIR_PROPERTY = "jsdoc.rootdir";

  private static final List<String> STANDARD_FLAGS = ImmutableList.of(
      "--jscomp_error=accessControls",
      "--jscomp_error=ambiguousFunctionDecl",
      "--jscomp_error=checkRegExp",
      "--jscomp_error=checkTypes",
      "--jscomp_error=checkVars",
      "--jscomp_error=constantProperty",
      "--jscomp_error=deprecated",
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
      "--formatting=PRETTY_PRINT",
      "--third_party=false");

  private static class Flags {
    @Option(name = "--help", usage = "Displays this message")
    private boolean displayHelp = false;

    @Option(name = "--manage_closure_dependencies",
        usage = "Automatically sort dependencies so that a file that "
            + "goog.provides symbol X will always come before a file "
            + "that goog.requires symbol X. Files that provide symbols that "
            + "are never required will be included in documentation processing, "
            + "however, the in which they will be processed is undefined.")
    private boolean managedClosureDependencies = false;

    @Option(name = "--js",
        handler = PathOptionHandler.class,
        usage = "A JavaScript file to generate documentation for. "
            + "You may specify multiple files.")
    private List<Path> js = Lists.newArrayList();

    @Option(name = "--dir",
        handler = DirOptionHandler.class,
        usage = "A directory to scan for JavaScript files to generate documentation "
            + "for. If --manage_closure_dependencies is not specified, files "
            + "will be processed in the order discovered.")
    private List<Path> dir = Lists.newArrayList();

    @Option(name = "--exclude",
        handler = PathOptionHandler.class,
        usage = "A directory or JavaScript file to exclude when generating documentation. "
            + "This option is ignored if --dir is not specified.")
    private List<Path> exclude = Lists.newArrayList();

    @Option(name = "--output_dir",
        handler = PathOptionHandler.class,
        usage = "Where to write the generated documentation.",
        required = true)
    private Path outputDir = null;
  }

  private final Flags flags;
  private final Set<Path> files;

  private Main(String[] args, PrintStream out, PrintStream err, Flags flags, Set<Path> files) {
    super(args, out, err);
    this.flags = flags;
    this.files = files;
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
            return Lists.newLinkedList();
          }
        });

    if (null != flags.outputDir) {
      customPasses.put(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS,
          new DocPass(flags.outputDir, getCompiler(), files));
    }

    options.setCustomPasses(customPasses);
    return options;
  }

  public static class PathOptionHandler extends OptionHandler<Path> {
    private final FileSystem fileSystem = FileSystems.getDefault();

    public PathOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Path> setter) {
      super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
      Path path = fileSystem.getPath(params.getParameter(0));
      setter.addValue(path);
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "PATH";
    }
  }

  public static class DirOptionHandler extends OptionHandler<Path> {

    private final FileSystem fileSystem = FileSystems.getDefault();

    public DirOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Path> setter) {
      super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
      Path path = fileSystem.getPath(params.getParameter(0));
      if (!Files.isReadable(path) || !Files.isDirectory(path)) {
        throw new CmdLineException(owner, "File must be a directory: " + path);
      }
      setter.addValue(path);
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "DIR";
    }
  }

  private static List<Path> findFiles(List<Path> directories) throws IOException {
    List<Path> allFiles = new LinkedList<>();
    for (Path directory : directories) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.js")) {
        for (Path path : stream) {
          allFiles.add(path);
        }
      }
    }
    return allFiles;
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

    List<Path> files;
    try {
      files = findFiles(flags.dir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    files.addAll(flags.js);

    // Remove duplicates.
    Set<Path> allFiles = Sets.newLinkedHashSet();
    allFiles.addAll(files);

    List<String> compilerFlags = Lists.newArrayListWithExpectedSize(
        STANDARD_FLAGS.size() + allFiles.size());
    compilerFlags.addAll(STANDARD_FLAGS);
    for (Path path : allFiles) {
      compilerFlags.add("--js=" + path);
    }

    PrintStream nullStream = new PrintStream(new OutputStream() {
      @Override
      public void write(int i) throws IOException {
      }
    });
    args = compilerFlags.toArray(new String[compilerFlags.size()]);

    Main main = new Main(args, nullStream, System.err, flags, allFiles);
    if (main.shouldRunCompiler()) {
      main.run();
    } else {
      System.exit(-1);
    }
  }
}
