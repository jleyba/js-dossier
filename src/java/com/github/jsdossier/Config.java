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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.FluentIterable.from;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.collect.Sets.intersection;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.DepsFileParser;
import com.google.javascript.jscomp.deps.DepsGenerator;
import com.google.javascript.jscomp.deps.SortedDependencies;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Describes the runtime configuration for the app.
 */
class Config {

  private final ImmutableSet<Path> srcs;
  private final ImmutableSet<Path> modules;
  private final ImmutableSet<Path> externs;
  private final ImmutableSet<Path> excludes;
  private final ImmutableSet<Pattern> typeFilters;
  private final Path srcPrefix;
  private final Path modulePrefix;
  private final Path output;
  private final boolean isZipOutput;
  private final Optional<Path> readme;
  private final ImmutableList<MarkdownPage> customPages;
  private final boolean strict;
  private final boolean useMarkdown;
  private final Language language;
  private final PrintStream outputStream;
  private final PrintStream errorStream;
  private final FileSystem fileSystem;

  /**
   * Creates a new runtime configuration.
   *
   *
   * @param srcs The list of compiler input sources.
   * @param modules The list of CommonJS compiler input sources.
   * @param externs The list of extern files for the Closure compiler.
   * @param excludes The list of excluded files.
   * @param typeFilters The list of types to filter from generated output.
   * @param output Path to the output directory.
   * @param isZipOutput Whether the output directory belongs to a zip file system.
   * @param readme Path to a markdown file to include in the main index.
   * @param customPages Custom markdown files to include in the generated documentation.
   * @param modulePrefix Prefix to strip from each module path when rendering documentation.
   * @param strict Whether to enable all type checks.
   * @param useMarkdown Whether to use markdown for comment parser.
   * @param language The JavaScript dialog sources must conform to.
   * @param outputStream The stream to use for standard output.
   * @param errorStream The stream to use for error output.
   * @throws IllegalStateException If any of source, moudle, and extern sets intersect, or if the
   *     output path is not a directory.
   */
  private Config(
      ImmutableSet<Path> srcs,
      ImmutableSet<Path> modules,
      ImmutableSet<Path> externs,
      ImmutableSet<Path> excludes,
      ImmutableSet<Pattern> typeFilters,
      boolean isZipOutput,
      Path output,
      Optional<Path> readme,
      List<MarkdownPage> customPages,
      Optional<Path> modulePrefix,
      boolean strict,
      boolean useMarkdown,
      Language language,
      PrintStream outputStream,
      PrintStream errorStream,
      FileSystem fileSystem) {
    checkArgument(!srcs.isEmpty() || !modules.isEmpty(),
        "There must be at least one input source or module");
    checkArgument(intersection(srcs, externs).isEmpty(),
        "The sources and externs inputs must be disjoint:\n  sources: %s\n  externs: %s",
        srcs, externs);
    checkArgument(intersection(srcs, modules).isEmpty(),
        "The sources and modules inputs must be disjoint:\n  sources: %s\n  modules: %s",
        srcs, modules);
    checkArgument(intersection(modules, externs).isEmpty(),
        "The sources and modules inputs must be disjoint:\n  modules: %s\n  externs: %s",
        modules, externs);
    checkArgument(!exists(output) || isDirectory(output),
        "Output path, %s, is not a directory", output);
    checkArgument(!readme.isPresent() || exists(readme.get()),
        "README path, %s, does not exist", readme.orNull());
    for (MarkdownPage page : customPages) {
      checkArgument(exists(page.getPath()),
          "For custom page \"%s\", file does not exist: %s",
          page.getName(), page.getPath());
    }

    this.srcs = srcs;
    this.modules = modules;
    this.srcPrefix = getSourcePrefixPath(fileSystem, srcs, modules);
    this.modulePrefix = getModulePreixPath(fileSystem, modulePrefix, modules);
    this.externs = externs;
    this.excludes = excludes;
    this.typeFilters = typeFilters;
    this.output = output;
    this.isZipOutput = isZipOutput;
    this.readme = readme;
    this.customPages = ImmutableList.copyOf(customPages);
    this.strict = strict;
    this.useMarkdown = useMarkdown;
    this.language = language;
    this.outputStream = outputStream;
    this.errorStream = errorStream;
    this.fileSystem = fileSystem;
  }

  /**
   * Returns the set of input sources for the compiler.
   */
  ImmutableSet<Path> getSources() {
    return srcs;
  }

  /**
   * Returns the set of CommonJS input sources for the compiler.
   */
  ImmutableSet<Path> getModules() {
    return modules;
  }

  /**
   * Returns the longest common path prefix for all of the input sources.
   */
  Path getSrcPrefix() {
    return srcPrefix;
  }

  /**
   * Returns the common path prefix for all of the input modules.
   */
  Path getModulePrefix() {
    return modulePrefix;
  }

  /**
   * Returns the set of extern files to use.
   */
  ImmutableSet<Path> getExterns() {
    return externs;
  }

  /**
   * Returns the set of types that should be filtered from generated output.
   */
  Predicate<NominalType> getTypeFilter() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(NominalType input) {
        return isFilteredType(input);
      }
    };
  }

  /**
   * Returns the path to the output directory.
   */
  Path getOutput() {
    return output;
  }

  /**
   * Returns whethre the output directory belongs to a zip file system.
   */
  boolean isZipOutput() {
    return isZipOutput;
  }

  /**
   * Returns the path to the readme markdown file, if any, to include in the main index.
   */
  Optional<Path> getReadme() {
    return readme;
  }

  /**
   * Returns the custom pages to include in the generated documentation.
   */
  ImmutableList<MarkdownPage> getCustomPages() {
    return customPages;
  }

  /**
   * Returns whether to enable all type checks.
   */
  boolean isStrict() {
    return strict;
  }

  /**
   * Returns whether to parse comments as markdown.
   */
  boolean useMarkdown() {
    return useMarkdown;
  }

  /**
   * Returns the language dialect sources must conform to.
   */
  Language getLanguage() {
    return language;
  }

  /**
   * Returns the stream to use as stdout.
   */
  PrintStream getOutputStream() {
    return outputStream;
  }

  /**
   * Returns the stream to use as stderr.
   */
  PrintStream getErrorStream() {
    return errorStream;
  }

  /**
   * Returns the file system used in this configuration.
   */
  FileSystem getFileSystem() {
    return fileSystem;
  }

  /**
   * Returns this configuration object as a JSON object.
   */
  JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.add("output", new JsonPrimitive(output.toString()));
    json.add("sources", toJsonArray(srcs));
    json.add("modules", toJsonArray(modules));
    json.add("externs", toJsonArray(externs));
    json.add("excludes", toJsonArray(excludes));
    json.add("typeFilters", toJsonArray(typeFilters));
    json.add("stripModulePrefix", new JsonPrimitive(modulePrefix.toString()));
    json.add("readme", readme.isPresent()
        ? new JsonPrimitive(readme.get().toString())
        : JsonNull.INSTANCE);
    json.addProperty("strict", strict);
    json.addProperty("useMarkdown", useMarkdown);
    json.addProperty("language", language.name());

    JsonArray pages = new JsonArray();
    for (MarkdownPage page : customPages) {
      pages.add(page.toJson());
    }
    json.add("customPages", pages);

    return json;
  }

  private JsonArray toJsonArray(Iterable<?> items) {
    JsonArray array = new JsonArray();
    for (Object i : items) {
      array.add(new JsonPrimitive(i.toString()));
    }
    return array;
  }

  /**
   * Returns whether the given type is a filtered type.
   */
  boolean isFilteredType(NominalType input) {
    String qualifiedName = input.getQualifiedName(true);
    for (Pattern filter : typeFilters) {
      if (filter.matcher(qualifiedName).matches()) {
        return true;
      }
    }
    NominalType parent = input.getParent();
    return parent != null && isFilteredType(parent);
  }

  private static Path getSourcePrefixPath(
      FileSystem fileSystem, ImmutableSet<Path> sources, ImmutableSet<Path> modules) {
    Path prefix = Paths.getCommonPrefix(fileSystem.getPath("").toAbsolutePath(),
        Iterables.concat(sources, modules));
    if (sources.contains(prefix) || modules.contains(prefix)) {
      prefix = prefix.getParent();
    }
    return prefix;
  }

  private static Path getModulePreixPath(
      FileSystem fileSystem, Optional<Path> userSupplierPath, ImmutableSet<Path> modules) {
    Path path;
    if (userSupplierPath.isPresent()) {
      path = userSupplierPath.get();
      checkArgument(isDirectory(path), "Module prefix must be a directory: %s", path);
      for (Path module : modules) {
        checkArgument(module.startsWith(path),
            "Module prefix <%s> is not an ancestor of module %s", path, module);
      }
    } else {
      path = Paths.getCommonPrefix(fileSystem.getPath("").toAbsolutePath(), modules);
      if (modules.contains(path) && path.getParent() != null) {
        path = path.getParent();
      }
    }

    // Always display at least one parent directory, if possible.
    for (Path module : modules) {
      if (path.equals(module.getParent())) {
        return firstNonNull(path.getParent(), path);
      }
    }

    return path;
  }

  private static boolean isZipFile(Path path) {
    return path.toString().endsWith(".zip");
  }

  /**
   * Loads a new runtime configuration from the provided input stream.
   */
  static Config load(InputStream stream, FileSystem fileSystem) {
    ConfigSpec spec = ConfigSpec.load(stream, fileSystem);
    checkArgument(spec.output != null, "Output not specified");
    Path output = spec.output;
    boolean isZip = isZipFile(output);
    if (exists(output)) {
      checkArgument(
          isDirectory(output) || isZipFile(output),
          "Output path must be a directory or zip file: %s", output);
    }

    if (isZip) {
      FileSystem fs;
      try {
        if (output.getFileSystem() == FileSystems.getDefault()) {
          URI uri = URI.create("jar:file:" + output.toAbsolutePath());
          fs = FileSystems.newFileSystem(uri,
              ImmutableMap.of("create", "true", "encoding", UTF_8.displayName()));
        } else {
          fs = output.getFileSystem().provider().newFileSystem(output,
              ImmutableMap.of("create", "true", "encoding", UTF_8.displayName()));
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      output = fs.getPath("/");
    }

    ImmutableSet<Path> excludes = resolve(spec.excludes);

    @SuppressWarnings("unchecked")
    Predicate<Path> filter = Predicates.and(
        notExcluded(excludes),
        notHidden());

    Iterable<Path> filteredSources = from(resolve(spec.sources)).filter(filter);
    Iterable<Path> filteredModules = from(resolve(spec.modules)).filter(filter);

    if (spec.closureLibraryDir.isPresent()) {
      ImmutableSet<Path> depsFiles = ImmutableSet.<Path>builder()
          .add(spec.closureLibraryDir.get().resolve("deps.js"))
          .addAll(spec.closureDepsFile)
          .build();

      try {
        filteredSources = processClosureSources(
            filteredSources, depsFiles, spec.closureLibraryDir.get());
      } catch (IOException | SortedDependencies.CircularDependencyException e) {
        throw new RuntimeException(e);
      }
    }

    return new Config(
        ImmutableSet.copyOf(filteredSources),
        ImmutableSet.copyOf(filteredModules),
        ImmutableSet.copyOf(resolve(spec.externs)),
        excludes,
        ImmutableSet.copyOf(spec.typeFilters),
        isZip,
        output,
        spec.readme,
        spec.customPages,
        spec.stripModulePrefix,
        spec.strict,
        spec.useMarkdown,
        spec.language,
        System.out,
        System.err,
        fileSystem);
  }

  private static ImmutableSet<Path> resolve(Iterable<PathSpec> specs) {
    Iterable<List<Path>> paths = from(specs)
        .filter(notNull())
        .transform(new Function<PathSpec, List<Path>>() {
          @Override
          public List<Path> apply(PathSpec input) {
            try {
              return input.resolve();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
    return ImmutableSet.copyOf(Iterables.concat(paths));
  }

  private static ImmutableSet<Path> processClosureSources(
      Iterable<Path> sources, ImmutableSet<Path> deps,
      Path closureBase) throws SortedDependencies.CircularDependencyException, IOException {

    Collection<SourceFile> depsFiles = newLinkedList(transform(deps, toSourceFile()));
    Collection<SourceFile> sourceFiles = newLinkedList(transform(sources, toSourceFile()));

    ErrorManager errorManager = new PrintStreamErrorManager(System.err);

    DepsGenerator generator = new DepsGenerator(
        depsFiles,
        sourceFiles,
        DepsGenerator.InclusionStrategy.ALWAYS,
        closureBase.toAbsolutePath().toString(),
        errorManager);

    String rawDeps = generator.computeDependencyCalls();
    errorManager.generateReport();
    if (rawDeps == null) {
      throw new RuntimeException("Encountered Closure dependency conflicts");
    }

    List<DependencyInfo> allDeps = new DepsFileParser(errorManager)
        .parseFile("*generated-deps*", rawDeps);

    List<DependencyInfo> sourceDeps =
        from(allDeps)
        .filter(isInSources(sources, closureBase))
        .toList();

    List<DependencyInfo> sortedDeps = new SortedDependencies<>(allDeps)
        .getDependenciesOf(sourceDeps, true);

    return ImmutableSet.<Path>builder()
        // Always include Closure's base.js first.
        .add(closureBase.resolve("base.js"))
        .addAll(transform(sortedDeps, toPath(closureBase)))
        .build();
  }

  private static Predicate<DependencyInfo> isInSources(
      final Iterable<Path> sources, Path closureBaseDir) {
    final Function<DependencyInfo, Path> pathTransform = toPath(closureBaseDir);
    final ImmutableSet<Path> sourcesSet = FluentIterable.from(sources)
        .transform(toAbsolutePath())
        .toSet();
    return new Predicate<DependencyInfo>() {
      @Override
      public boolean apply(DependencyInfo input) {
        return sourcesSet.contains(pathTransform.apply(input));
      }
    };
  }

  private static Function<Path, Path> toAbsolutePath() {
    return new Function<Path, Path>() {
      @Override
      public Path apply(Path input) {
        return input.toAbsolutePath();
      }
    };
  }

  private static Function<DependencyInfo, Path> toPath(final Path closureBaseDir) {
    return new Function<DependencyInfo, Path>() {
      @Override
      public Path apply(DependencyInfo input) {
        return closureBaseDir.resolve(input.getPathRelativeToClosureBase())
            .normalize()
            .toAbsolutePath();
      }
    };
  }

  private static Function<Path, SourceFile> toSourceFile() {
    return new Function<Path, SourceFile>() {
      @Override
      public SourceFile apply(Path input) {
        return SourceFile.fromFile(input.toAbsolutePath().toFile());
      }
    };
  }

  private static Predicate<Path> notExcluded(final Iterable<Path> excludes) {
    return new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        for (Path exclude : excludes) {
          if (input.equals(exclude)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  private static Predicate<Path> notHidden() {
    return new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        try {
          return !Files.isHidden(input);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @VisibleForTesting
  static class PathSpec {
    private final Path baseDir;
    private final String spec;

    PathSpec(Path baseDir, String spec) {
      this.baseDir = baseDir;
      this.spec = spec;
    }

    List<Path> resolve() throws IOException {
      Path path = baseDir.resolve(spec).normalize();
      if (isDirectory(path)) {
        return collectFiles(path, "**.js");
      }

      if (exists(path)) {
        return ImmutableList.of(path);
      }

      return collectFiles(firstNonNull(path.getParent(), baseDir), path.getFileName().toString());
    }

    List<Path> collectFiles(final Path baseDir, String glob) throws IOException {
      final PathMatcher matcher = baseDir.getFileSystem().getPathMatcher("glob:" + glob);
      final ImmutableList.Builder<Path> files = ImmutableList.builder();
      Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
          if (matcher.matches(baseDir.relativize(file))) {
            files.add(file);
          }
          return FileVisitResult.CONTINUE;
        }
      });
      return files.build();
    }
  }

  static String getOptionsText(boolean includeHeader) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    if (includeHeader) {
      pw.println("__Configuration Options__");
      pw.println();
    }

    for (Field field : ConfigSpec.class.getDeclaredFields()) {
      Description description = field.getAnnotation(Description.class);
      if (description != null) {
        String str = String.format(" * `%s` ", field.getName()) + description.value().trim();
        boolean isFirst = true;
        for (String line : Splitter.on("\n").split(str)) {
          if (isFirst) {
            printLine(pw, line);
            isFirst = false;
          } else {
            printLine(pw, "   " + line);
          }
        }
      }
      pw.println();
    }
    return sw.toString();
  }

  private static void printLine(PrintWriter pw, String line) {
    if (line.length() <= 79) {
      pw.println(line);
    } else {
      int index = 79;
      while (line.charAt(index) != ' ') {
        index -= 1;
      }
      while (line.charAt(index) == '.'
          && index + 1 < line.length()
          && line.charAt(index + 1) != ' ') {
        index -= 1;
      }
      pw.println(line.substring(0, index));
      printLine(pw, "   " + line.substring(index));
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  private @interface Description {
    String value();
  }

  private static class ConfigSpec {

    @Description("Path to the directory to write all generated documentation to. This field is" +
        " required.")
    private final Path output = null;

    @Description("Path to the base directory of the Closure library (which must contain base.js" +
        " and depsjs). When this option is specified, Closure's deps.js and all of the files" +
        " specified by `closureDepsFile` will be parsed for calls to `goog.addDependency`. The" +
        " resulting map will be used to automatically expand the set of `sources` any time a" +
        " symbol is goog.require'd with the ile that goog.provides that symbol, along with all" +
        " of its transitive dependencies.\n" +
        "\n" +
        " For example, suppose you have one source file, `foo.js`:\n" +
        "\n" +
        "     goog.require('goog.array');\n" +
        "     // ...\n" +
        "\n" +
        " and your configuration includes:\n" +
        "\n" +
        "     \"sources\": [\"foo.js\"],\n" +
        "     \"closureLibraryDir\": \"closure/goog\"\n" +
        "\n" +
        " due to the dependencies of goog.array declared in closure/goog/deps.js, this is" +
        " equivalent to the following configuration:\n" +
        "\n" +
        "     \"sources\": [\n" +
        "         \"closure/goog/base.js\",\n" +
        "         \"closure/goog/debug/error.js\",\n" +
        "         \"closure/goog/string/string.js\",\n" +
        "         \"closure/goog/asserts/asserts.js\",\n" +
        "         \"closure/goog/array/array.js\",\n" +
        "         \"foo.js\"\n" +
        "     ]\n" +
        "\n" +
        " Notice specifying `closureLibraryDir` instructs Dossier to sort the input files so a" +
        " a file that goog.provides symbol X comes before any file that goog.requires X.")
    private final Optional<Path> closureLibraryDir = Optional.absent();

    @Description("Path to a file to parse for calls to `goog.addDependency`. This option " +
        "requires also setting `closureLibraryDir`.")
    private final List<Path> closureDepsFile = ImmutableList.of();

    @Description("A list of .js files to extract API documentation from. If a glob pattern " +
        "is specified, every .js file under the current working directory matching that pattern" +
        " will be included. Specifying the path to a directory, `foo`, is the same as using " +
        "the glob pattern `foo/**.js`. The set of paths specified by this option *must* be " +
        "disjoint from those specified by `modules`.")
    private final List<PathSpec> sources = ImmutableList.of();

    @Description("A list of .js files to extract API documentation from. Each file will be " +
        "processed as a CommonJS module, with only its exported API included in the generated" +
        " output. If a glob pattern is specified, every .js file under the current directory " +
        "matching that pattern will be included. Specifying the path to a directory, `foo`, is" +
        " the same as the glob pattern `foo/**.js`. The set of paths specified by this option " +
        "*mut* be disjoint from those specified by `sources`.")
    private final List<PathSpec> modules = ImmutableList.of();

    @Description("A prefix to strip from every module's path when generating documentation." +
        " The specified path must be a directory that is an ancestor of every file specified " +
        "in `modules`. Note: if this option is omitted, the closest common ancestor for all " +
        "module files will be selected as the default.")
    private final Optional<Path> stripModulePrefix = Optional.absent();

    @Description("A list of .js files to exclude from processing. If a directory is specified," +
        " all of the .js files under that directory will be excluded. A glob pattern may also" +
        " be specified to exclude all of the paths under the current working directory that " +
        "match  the provided pattern.")
    private final List<PathSpec> excludes = ImmutableList.of();

    @Description("A list of .js files to include as an extern file for the Closure compiler. " +
        "These  files are used to satisfy references to external types, but are excluded when " +
        "generating  API documentation.")
    private final List<PathSpec> externs = ImmutableList.of();

    @Description("List of regular expressions for types that should be excluded from generated " +
        "documentation, even if found in the type graph.")
    private final List<Pattern> typeFilters = ImmutableList.of();

    @Description("Path to a README file to include as the main landing page for the generated " +
        "documentation. This file should use markdown syntax.")
    private final Optional<Path> readme = Optional.absent();

    @Description("List of additional files to include in the generated documentation. Each page " +
        "is defined as a {name: string, path: string} object, where the name is what's " +
        "displayed in the navigation menu, and `path` is the path to the markdown file to use. " +
        "Files will be included in the order listed, after the standard navigation items.")
    private final List<MarkdownPage> customPages = ImmutableList.of();

    @Description("Whether to run with all type checking flags enabled.")
    private final boolean strict = false;

    @Description("Whether to parse all comments as markdown. The `readme` and `customPages` will " +
        "always be parsed as markdown.")
    private final boolean useMarkdown = true;

    @Description("Specifies which version of ECMAScript the input sources conform to. Defaults " +
        "to ES5. Must be one of {ES3, ES5, ES5_STRICT}")
    private final Language language = Language.ES5;

    static ConfigSpec load(InputStream stream, FileSystem fileSystem) {
      Path pwd = fileSystem.getPath("").toAbsolutePath().normalize();
      Gson gson = new GsonBuilder()
          .registerTypeAdapter(Path.class, new PathDeserializer(fileSystem))
          .registerTypeAdapter(PathSpec.class, new PathSpecDeserializer(pwd))
          .registerTypeAdapter(Pattern.class, new PatternDeserializer())
          .registerTypeAdapter(
              new TypeToken<Optional<Path>>(){}.getType(),
              new OptionalDeserializer<>(Path.class))
          .create();

      return gson.fromJson(
          new InputStreamReader(stream, StandardCharsets.UTF_8),
          ConfigSpec.class);
    }
  }

  static enum Language {
    ES3("ECMASCRIPT3"),
    ES5("ECMASCRIPT5"),
    ES5_STRICT("ECMASCRIPT5_STRICT");

    private final String fullName;

    Language(String fullName) {
      this.fullName = fullName;
    }

    public String getName() {
      return fullName;
    }
  }

  private static class OptionalDeserializer<T> implements JsonDeserializer<Optional<T>> {

    private final Class<T> componentType;

    private OptionalDeserializer(Class<T> componentType) {
      this.componentType = componentType;
    }

    @Override
    public Optional<T> deserialize(
        JsonElement jsonElement, Type type, JsonDeserializationContext context)
        throws JsonParseException {
      if (jsonElement.isJsonNull()) {
        return Optional.absent();
      }
      T value = context.deserialize(jsonElement, componentType);
      return Optional.fromNullable(value);
    }
  }

  private static class PathDeserializer implements JsonDeserializer<Path> {

    private final FileSystem fileSystem;

    public PathDeserializer(FileSystem fileSystem) {
      this.fileSystem = fileSystem;
    }

    @Override
    public Path deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context)
        throws JsonParseException {
      return fileSystem.getPath(jsonElement.getAsString())
          .toAbsolutePath()
          .normalize();
    }
  }

  private static class PathSpecDeserializer implements JsonDeserializer<PathSpec> {

    private final Path baseDir;

    public PathSpecDeserializer(Path baseDir) {
      this.baseDir = baseDir;
    }

    @Override
    public PathSpec deserialize(
        JsonElement jsonElement, Type type, JsonDeserializationContext context)
        throws JsonParseException {
      return new PathSpec(baseDir, jsonElement.getAsString());
    }
  }

  private static class PatternDeserializer implements JsonDeserializer<Pattern> {

    @Override
    public Pattern deserialize(
        JsonElement jsonElement, Type type, JsonDeserializationContext context)
        throws JsonParseException {
      return Pattern.compile(jsonElement.getAsString());
    }
  }

  public static void main(String[] args) {
    System.err.println(getOptionsText(true));
  }
}
