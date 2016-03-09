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
package com.github.jsdossier;

import static com.github.jsdossier.Paths.normalizedAbsolutePath;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.FluentIterable.from;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.collect.Sets.intersection;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.write;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
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
import com.google.javascript.jscomp.deps.ClosureSortedDependencies;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.DepsFileParser;
import com.google.javascript.jscomp.deps.DepsGenerator;
import com.google.javascript.jscomp.deps.SortedDependencies;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Describes the runtime configuration for the app.
 */
@AutoValue
abstract class Config {

  public static Builder builder() {
    return new AutoValue_Config.Builder();
  }

  Config() {}

  /**
   * Returns the set of input sources for the compiler.
   */
  abstract ImmutableSet<Path> getSources();

  /**
   * Returns the set of CommonJS input sources for the compiler.
   */
  abstract ImmutableSet<Path> getModules();

  /**
   * Returns the longest common path prefix for all of the input sources.
   */
  abstract Path getSrcPrefix();

  /**
   * Returns the user-specified module prefix to use for input modules.
   */
  abstract Optional<Path> getModulePrefix();

  /**
   * Returns the set of extern files to use.
   */
  abstract ImmutableSet<Path> getExterns();

  /**
   * Returns the set of files to process as extern modules.
   */
  abstract ImmutableSet<Path> getExternModules();

  /**
   * Returns the files to include for type checking, but to <em>exclude</em>
   * from generated documentation.
   */
  abstract ImmutableSet<Path> getExcludes();

  /**
   * Returns the path to the output directory.
   */
  abstract Path getOutput();

  /**
   * Returns the path to the readme markdown file, if any, to include in the main index.
   */
  abstract Optional<Path> getReadme();

  /**
   * Returns the custom pages to include in the generated documentation.
   */
  abstract ImmutableSet<MarkdownPage> getCustomPages();

  /**
   * Returns whether to enable all type checks.
   */
  abstract boolean isStrict();

  /**
   * Returns the language dialect sources must conform to.
   */
  abstract Language getLanguage();

  /**
   * Returns the module naming convention to use.
   */
  abstract ModuleNamingConvention getModuleNamingConvention();

  /**
   * Returns the user provided source URL pattern.
   */
  abstract Optional<String> getSourceUrlTemplate();

  /**
   * Returns the file system used in this configuration.
   */
  abstract FileSystem getFileSystem();

  /**
   * Returns the regular expressions to use for filtering out types from
   * generated documentation.
   */
  abstract ImmutableSet<Pattern> getTypeFilters();

  /**
   * Returns the regular expressions to use for filtering out modules from
   * generated documentation.
   */
  abstract ImmutableSet<Pattern> getModuleFilters();

  /**
   * Returns this configuration object as a JSON object.
   */
  JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.add("output", new JsonPrimitive(getOutput().toString()));
    json.add("sources", toJsonArray(getSources()));
    json.add("modules", toJsonArray(getModules()));
    json.add("externs", toJsonArray(getExterns()));
    json.add("excludes", toJsonArray(getExcludes()));
    json.add("moduleFilters", toJsonArray(getModuleFilters()));
    json.add("typeFilters", toJsonArray(getTypeFilters()));
    json.add("stripModulePrefix", new JsonPrimitive(getModulePrefix().toString()));
    json.add("readme", getReadme().isPresent()
        ? new JsonPrimitive(getReadme().get().toString())
        : JsonNull.INSTANCE);
    json.addProperty("strict", isStrict());
    json.addProperty("language", getLanguage().name());

    if (getSourceUrlTemplate().isPresent()) {
      json.addProperty("sourceUrlTemplate", getSourceUrlTemplate().get());
    }

    JsonArray pages = new JsonArray();
    for (MarkdownPage page : getCustomPages()) {
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
   * Returns whether the type with the given type should be excluded from documentation.
   */
  boolean isFilteredType(String name) {
    for (Pattern filter : getTypeFilters()) {
      if (filter.matcher(name).matches()) {
        return true;
      }
    }
    int index = name.lastIndexOf('.');
    return index != -1 && isFilteredType(name.substring(0, index));
  }

  /**
   * Returns whether the given path should be excluded from documentation.
   */
  boolean isFilteredModule(Path path) {
    for (Pattern filter : getModuleFilters()) {
      if (filter.matcher(path.toAbsolutePath().normalize().toString()).matches()) {
        return true;
      }
    }
    return false;
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract ImmutableSet<Path> getExterns();
    abstract Builder setExterns(ImmutableSet<Path> paths);
    abstract Builder setExternModules(ImmutableSet<Path> paths);
    abstract Builder setExcludes(ImmutableSet<Path> paths);

    abstract ImmutableSet<Path> getModules();
    abstract Builder setModules(ImmutableSet<Path> paths);

    abstract ImmutableSet<Path> getSources();
    abstract Builder setSources(ImmutableSet<Path> paths);

    abstract Path getOutput();
    abstract Builder setOutput(Path path);

    abstract FileSystem getFileSystem();
    abstract Builder setFileSystem(FileSystem fs);

    abstract Optional<Path> getReadme();
    abstract Builder setReadme(Optional<Path> path);

    abstract ImmutableSet<MarkdownPage> getCustomPages();
    abstract Builder setCustomPages(ImmutableSet<MarkdownPage> pages);

    abstract Builder setStrict(boolean strict);
    abstract Builder setLanguage(Language lang);
    abstract Builder setModuleNamingConvention(ModuleNamingConvention convention);
    abstract Builder setSourceUrlTemplate(Optional<String> template);
    abstract Builder setSrcPrefix(Path path);
    abstract Builder setModulePrefix(Optional<Path> path);
    abstract Builder setTypeFilters(ImmutableSet<Pattern> filters);
    abstract Builder setModuleFilters(ImmutableSet<Pattern> filters);
    abstract Config autoBuild();

    public Config build() {
      checkArgument(!getSources().isEmpty() || !getModules().isEmpty(),
          "There must be at least one input source or module");
      checkArgument(intersection(getSources(), getExterns()).isEmpty(),
          "The sources and externs inputs must be disjoint:\n  sources: %s\n  externs: %s",
          getSources(), getExterns());
      checkArgument(intersection(getSources(), getModules()).isEmpty(),
          "The sources and modules inputs must be disjoint:\n  sources: %s\n  modules: %s",
          getSources(), getModules());
      checkArgument(intersection(getModules(), getExterns()).isEmpty(),
          "The sources and modules inputs must be disjoint:\n  modules: %s\n  externs: %s",
          getModules(), getExterns());
      checkArgument(!exists(getOutput()) || isDirectory(getOutput()) || isZipFile(getOutput()),
          "Output path, %s, is neither a directory nor a zip file", getOutput());
      checkArgument(!getReadme().isPresent() || exists(getReadme().get()),
          "README path, %s, does not exist", getReadme().orNull());
      for (MarkdownPage page : getCustomPages()) {
        checkArgument(exists(page.getPath()),
            "For custom page \"%s\", file does not exist: %s",
            page.getName(), page.getPath());
      }

      return setSrcPrefix(getSourcePrefixPath(getFileSystem(), getSources(), getModules()))
          .autoBuild();
    }
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

  private static boolean isZipFile(Path path) {
    return path.toString().endsWith(".zip");
  }

  /**
   * Loads a new runtime configuration from the provided input stream.
   */
  static Config load(InputStream stream, FileSystem fileSystem) {
    Spec spec = Spec.fromJson(stream, fileSystem);
    Path output = spec.getOutput();
    if (exists(output)) {
      checkArgument(
          isDirectory(output) || isZipFile(output),
          "Output path must be a directory or zip file: %s", output);
    }

    ImmutableSet<Path> excludes = resolve(spec.getExcludes());

    @SuppressWarnings("unchecked")
    Predicate<Path> filter = Predicates.and(
        notExcluded(excludes),
        notHidden());

    Iterable<Path> filteredSources = from(resolve(spec.getSources())).filter(filter);
    Iterable<Path> filteredModules = from(resolve(spec.getModules())).filter(filter);

    if (spec.getClosureLibraryDir().isPresent()) {
      ImmutableSet<Path> depsFiles = ImmutableSet.<Path>builder()
          .add(spec.getClosureLibraryDir().get().resolve("deps.js"))
          .addAll(resolve(spec.getClosureDepFiles()))
          .build();

      try {
        filteredSources = processClosureSources(
            filteredSources, depsFiles, spec.getClosureLibraryDir().get());
      } catch (IOException | SortedDependencies.CircularDependencyException e) {
        throw new RuntimeException(e);
      }
    }

    return builder()
        .setSources(ImmutableSet.copyOf(filteredSources))
        .setModules(ImmutableSet.copyOf(filteredModules))
        .setExterns(ImmutableSet.copyOf(resolve(spec.getExterns())))
        .setExternModules(ImmutableSet.copyOf(resolve(spec.getExternModules())))
        .setExcludes(excludes)
        .setTypeFilters(spec.getTypeFilters())
        .setModuleFilters(spec.getModuleFilters())
        .setOutput(output)
        .setReadme(spec.getReadme())
        .setCustomPages(spec.getCustomPages())
        .setModulePrefix(spec.getModulePrefix())
        .setStrict(spec.isStrict())
        .setLanguage(spec.getLanguage())
        .setFileSystem(fileSystem)
        .setModuleNamingConvention(spec.getModuleNamingConvention())
        .setSourceUrlTemplate(checkSourceUrlTemplate(spec))
        .build();
  }

  private static Optional<String> checkSourceUrlTemplate(Spec spec) {
    if (spec.getSourceUrlTemplate().isPresent()) {
      String template = spec.getSourceUrlTemplate().get();
      checkArgument(template.startsWith("http://") || template.startsWith("https://"),
          "Invalid URL template: must be a http or https URL: %s", template);
      checkArgument(template.contains("${path}"),
          "Invalid URL template: must contain '${path}' and (optionally) '${line}': %s",
          template);
    }
    return spec.getSourceUrlTemplate();
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

    List<DependencyInfo> sortedDeps = new ClosureSortedDependencies<>(allDeps)
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

  static final class PathSpec {
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

    Iterable<Description> descriptions =
        FluentIterable.from(ImmutableList.copyOf(Spec.class.getDeclaredMethods()))
            .transform(new Function<Method, Description>() {
              @Nullable
              @Override
              public Description apply(@Nullable Method input) {
                return input == null ? null : input.getAnnotation(Description.class);
              }
            })
            .filter(Predicates.notNull())
            .toSortedList(new Comparator<Description>() {
              @Override
              public int compare(Description a, Description b) {
                return a.name().compareTo(b.name());
              }
            });

    for (Description description : descriptions) {
      String str = " * `" + description.name() + "` " + description.desc().trim();
      boolean isFirst = true;
      for (String line : Splitter.on('\n').split(str)) {
        if (isFirst) {
          printLine(pw, line);
          isFirst = false;
        } else {
          printLine(pw, "   " + line);
        }
      }
      pw.println();
    }
    pw.println();
    return sw.toString();
  }

  private static void printLine(PrintWriter pw, String line) {
    if (line.length() <= 79) {
      pw.println(line.replaceAll("\\s+$", ""));
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
  @Target(ElementType.METHOD)
  @interface Description {
    String name();
    String desc();
  }

  @AutoValue
  public abstract static class Spec {
    public static Builder builder() {
      return new AutoValue_Config_Spec.Builder()
          .setClosureLibraryDir(Optional.<Path>absent())
          .setClosureDepFiles(ImmutableSet.<PathSpec>of())
          .setSources(ImmutableSet.<PathSpec>of())
          .setModules(ImmutableSet.<PathSpec>of())
          .setModulePrefix(Optional.<Path>absent())
          .setModuleNamingConvention(ModuleNamingConvention.ES6)
          .setExcludes(ImmutableSet.<PathSpec>of())
          .setExterns(ImmutableSet.<PathSpec>of())
          .setExternModules(ImmutableSet.<PathSpec>of())
          .setModuleFilters(ImmutableSet.<Pattern>of())
          .setTypeFilters(ImmutableSet.<Pattern>of())
          .setReadme(Optional.<Path>absent())
          .setCustomPages(ImmutableSet.<MarkdownPage>of())
          .setLanguage(Language.ES6_STRICT)
          .setStrict(false)
          .setSourceUrlTemplate(Optional.<String>absent());
    }

    public static Spec fromJson(InputStream stream, FileSystem fileSystem) {
      Path pwd = normalizedAbsolutePath(fileSystem, "");

      Gson gson = new GsonBuilder()
          .registerTypeAdapter(Spec.class, new SpecMarshaller())
          .registerTypeAdapter(Path.class, new PathDeserializer(fileSystem))
          .registerTypeAdapter(PathSpec.class, new PathSpecDeserializer(pwd))
          .registerTypeAdapter(Pattern.class, new PatternDeserializer())
          .registerTypeAdapter(
              new TypeToken<ImmutableSet<Pattern>>(){}.getType(),
              new ImmutableSetDeserializer<>(Pattern.class))
          .registerTypeAdapter(
              new TypeToken<ImmutableSet<MarkdownPage>>(){}.getType(),
              new ImmutableSetDeserializer<>(MarkdownPage.class))
          .registerTypeAdapter(
              new TypeToken<ImmutableSet<Path>>(){}.getType(),
              new ImmutableSetDeserializer<>(Path.class))
          .registerTypeAdapter(
              new TypeToken<ImmutableSet<PathSpec>>(){}.getType(),
              new ImmutableSetDeserializer<>(PathSpec.class))
          .registerTypeAdapter(
              new TypeToken<Optional<Path>>() {}.getType(),
              new OptionalDeserializer<>(Path.class))
          .registerTypeAdapter(
              new TypeToken<Optional<String>>(){}.getType(),
              new OptionalDeserializer<>(String.class))
          .create();

      return gson.fromJson(
          new InputStreamReader(stream, StandardCharsets.UTF_8), Spec.class);
    }

    @Description(
        name = "output",
        desc = "Path to the directory to write all generated documentation to. This field is" +
            " required.")
    abstract Path getOutput();

    @Description(
        name = "closureLibraryDir",
        desc = "Path to the base directory of the Closure library (which must contain base.js" +
        " and deps.js). When this option is specified, Closure's deps.js and all of the files" +
        " specified by `closureDepsFile` will be parsed for calls to `goog.addDependency`. The" +
        " resulting map will be used to automatically expand the set of `sources` any time a" +
        " symbol is goog.require'd with the ile that goog.provides that symbol, along with all" +
        " of its transitive dependencies.\n" +
        "\n" +
        " For example, suppose you have one source file, `foo.js`:\n" +
        "\n" +
        "```js\n" +
        "goog.require('goog.array');\n" +
        "// ...\n" +
        "```\n" +
        "\n" +
        " and your configuration includes:\n" +
        "\n" +
        "```json\n" +
        "\"sources\": [\"foo.js\"],\n" +
        "\"closureLibraryDir\": \"closure/goog\"\n" +
        "```\n" +
        "\n" +
        " due to the dependencies of goog.array declared in closure/goog/deps.js, this is" +
        " equivalent to the following configuration:\n" +
        "\n" +
        "```json\n" +
        "\"sources\": [\n" +
        "    \"closure/goog/base.js\",\n" +
        "    \"closure/goog/debug/error.js\",\n" +
        "    \"closure/goog/string/string.js\",\n" +
        "    \"closure/goog/asserts/asserts.js\",\n" +
        "    \"closure/goog/array/array.js\",\n" +
        "    \"foo.js\"\n" +
        "]\n" +
        "```\n" +
        "\n" +
        " Notice specifying `closureLibraryDir` instructs Dossier to sort the input files so a" +
        " a file that goog.provides symbol X comes before any file that goog.requires X.")
    abstract Optional<Path> getClosureLibraryDir();

    @Description(
        name = "closureDepFiles",
        desc =
        "Path to a file to parse for calls to `goog.addDependency`. This option " +
        "requires also setting `closureLibraryDir`.")
    abstract ImmutableSet<PathSpec> getClosureDepFiles();

    @Description(
        name = "sources",
        desc =
        "A list of .js files to extract API documentation from. If a glob pattern " +
        "is specified, every .js file under the current working directory matching that pattern" +
        " will be included. Specifying the path to a directory, `foo`, is the same as using " +
        "the glob pattern `foo/**.js`. The set of paths specified by this option *must* be " +
        "disjoint from those specified by `modules`.")
    abstract ImmutableSet<PathSpec> getSources();

    @Description(
        name = "modules",
        desc =
        "A list of .js files to extract API documentation from. Each file will be " +
        "processed as a CommonJS module, with only its exported API included in the generated" +
        " output. If a glob pattern is specified, every .js file under the current directory " +
        "matching that pattern will be included. Specifying the path to a directory, `foo`, is" +
        " the same as the glob pattern `foo/**.js`. The set of paths specified by this option " +
        "*mut* be disjoint from those specified by `sources`.")
    abstract ImmutableSet<PathSpec> getModules();

    @Description(
        name = "modulePrefix",
        desc =
        "A prefix to strip from every module's path when generating documentation." +
        " The specified path must be a directory that is an ancestor of every file specified " +
        "in `modules`. Note: if this option is omitted, the closest common ancestor for all " +
        "module files will be selected as the default.")
    abstract Optional<Path> getModulePrefix();

    @Description(
        name = "moduleNamingConvention",
        desc =
        "The module naming convention to use. If set to `NODE`, modules with a basename" +
        " of index.js will use the name of the parent directory" +
        " (e.g. \"foo/bar/index.js\" -> \"foo/bar/\"). Must be one of {ES6, NODE}; defaults to ES6")
    abstract ModuleNamingConvention getModuleNamingConvention();

    @Description(
        name = "excludes",
        desc =
        "A list of .js files to exclude from processing. If a directory is specified," +
        " all of the .js files under that directory will be excluded. A glob pattern may also" +
        " be specified to exclude all of the paths under the current working directory that " +
        "match  the provided pattern.")
    abstract ImmutableSet<PathSpec> getExcludes();

    @Description(
        name = "externs",
        desc =
        "A list of .js files to include as an extern file for the Closure compiler. " +
        "These  files are used to satisfy references to external types, but are excluded when " +
        "generating  API documentation.")
    abstract ImmutableSet<PathSpec> getExterns();

    @Description(
        name = "externModules",
        desc =
        "A list of .js files to include as CommonJS extern module definitions. Each module may be" +
            " required in source by the file's base name, excluding the extension. For example," +
            " 'extern/libfoo.js' would provide the extern definition for the import" +
            " `require('libfoo');`")
    abstract ImmutableSet<PathSpec> getExternModules();

    @Description(
        name = "moduleFilters",
        desc =
        "List of regular expressions for modules that should be excluded from generated "
        + "documentation, even if found in the type graph. The provided expressions will be "
        + "to the _absolute_ path of the source file for each module.")
    abstract ImmutableSet<Pattern> getModuleFilters();

    @Description(
        name = "typeFilters",
        desc =
        "List of regular expressions for types that should be excluded from generated " +
        "documentation, even if found in the type graph.")
    abstract ImmutableSet<Pattern> getTypeFilters();

    @Description(
        name = "readme",
        desc =
        "Path to a README file to include as the main landing page for the generated " +
        "documentation. This file should use markdown syntax.")
    abstract Optional<Path> getReadme();

    @Description(
        name = "customPages",
        desc =
        "List of additional files to include in the generated documentation. Each page " +
        "is defined as a {name: string, path: string} object, where the name is what's " +
        "displayed in the navigation menu, and `path` is the path to the markdown file to use. " +
        "Files will be included in the order listed, after the standard navigation items.")
    abstract ImmutableSet<MarkdownPage> getCustomPages();

    @Description(
        name = "language",
        desc =
        "Specifies which version of ECMAScript the input sources conform to. Defaults " +
        "to ES6_STRICT. Must be one of {ES3, ES5, ES5_STRICT, ES6, ES6_STRICT}")
    abstract Language getLanguage();

    @Description(
        name = "strict",
        desc = "Whether to run with all type checking flags enabled.")
    abstract boolean isStrict();

    @Description(
        name = "sourceUrlTemplate",
        desc =
        "Specifies a template from which to generate a HTTP(S) links to source files. Within this" +
            " template, the `${path}` and `${line}` tokens will be replaced with the linked" +
            " type's source file path and line number, respectively. Source paths will be" +
            " relative to the closest common ancestor of all input files.\n" +
            "\n" +
            " If this option is not specified, a rendered copy of each input file will be" +
            " included in the generated output.")
    abstract Optional<String> getSourceUrlTemplate();

    abstract Builder toBuilder();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setOutput(Path path);
      abstract Builder setClosureLibraryDir(Optional<Path> dir);
      abstract Builder setClosureDepFiles(ImmutableSet<PathSpec> files);
      abstract Builder setSources(ImmutableSet<PathSpec> sources);
      abstract Builder setModules(ImmutableSet<PathSpec> modules);
      abstract Builder setModulePrefix(Optional<Path> prefix);
      abstract Builder setModuleNamingConvention(ModuleNamingConvention convention);
      abstract Builder setExcludes(ImmutableSet<PathSpec> excludes);
      abstract Builder setExterns(ImmutableSet<PathSpec> externs);
      abstract Builder setExternModules(ImmutableSet<PathSpec> externs);
      abstract Builder setModuleFilters(ImmutableSet<Pattern> filters);
      abstract Builder setTypeFilters(ImmutableSet<Pattern> filters);
      abstract Builder setReadme(Optional<Path> path);
      abstract Builder setCustomPages(ImmutableSet<MarkdownPage> pages);
      abstract Builder setLanguage(Language language);
      abstract Builder setStrict(boolean strict);
      abstract Builder setSourceUrlTemplate(Optional<String> template);
      abstract Spec build();
    }
  }

  enum Language {
    ES3("ECMASCRIPT3"),
    ES5("ECMASCRIPT5"),
    ES5_STRICT("ECMASCRIPT5_STRICT"),
    ES6("ECMASCRIPT6"),
    ES6_STRICT("ECMASCRIPT6_STRICT"),
    ;

    private final String fullName;

    Language(String fullName) {
      this.fullName = fullName;
    }

    public String getName() {
      return fullName;
    }
  }

  private static class SpecMarshaller implements JsonDeserializer<Spec> {

    @Override
    public Spec deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      Spec.Builder builder = Spec.builder();

      JsonObject jsonObj = json.getAsJsonObject();
      for (Method getter : Spec.class.getDeclaredMethods()) {
        Description desc = getter.getAnnotation(Description.class);
        if (desc == null || !jsonObj.has(desc.name())) {
          continue;
        }

        Type genericType = getter.getGenericReturnType();
        Object value = context.deserialize(jsonObj.get(desc.name()), genericType);

        String setterName;
        if (getter.getName().startsWith("get")) {
          setterName = "set" + getter.getName().substring("get".length());
        } else {
          verify(getter.getName().startsWith("is"));
          setterName = "set" + getter.getName().substring("is".length());
        }

        Class<? extends Spec.Builder> clazz = builder.getClass();
        try {
          Class<?> basicType;
          if (genericType instanceof Class) {
            basicType = (Class<?>) genericType;
          } else if (genericType instanceof ParameterizedType) {
            basicType = (Class<?>) ((ParameterizedType) genericType).getRawType();
          } else {
            throw new AssertionError();
          }
          Method setterMethod = clazz.getMethod(setterName, basicType);
          setterMethod.invoke(builder, basicType.cast(value));
        } catch (NoSuchMethodException
            | InvocationTargetException
            | IllegalAccessException
            | RuntimeException e) {
          throw new JsonParseException(e);
        }
      }

      return builder.build();
    }
  }

  private static class ImmutableSetDeserializer<T> implements JsonDeserializer<ImmutableSet<T>> {
    private final Class<T> componentType;

    private ImmutableSetDeserializer(Class<T> componentType) {
      this.componentType = componentType;
    }

    @Override
    public ImmutableSet<T> deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      if (json.isJsonNull()) {
        return ImmutableSet.of();
      }
      List<T> items = new ArrayList<>();
      JsonArray array = json.getAsJsonArray();
      for (int i = 0; i < array.size(); i++) {
        JsonElement element = array.get(i);
        if (element.isJsonNull()) {
          if (i == array.size() - 1) {
            break;
          }
          throw new JsonParseException("null element in array at index " + i);
        }
        T item = context.deserialize(array.get(i), componentType);
        items.add(item);
      }
      return ImmutableSet.copyOf(items);
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
      return normalizedAbsolutePath(fileSystem, jsonElement.getAsString());
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

  public static void main(String[] args) throws IOException {
    URL headUrl = Resources.getResource(Config.class, "resources/ReadmeHead.md");
    URL tailUrl = Resources.getResource(Config.class, "resources/ReadmeTail.md");
    String output = Resources.toString(headUrl, UTF_8)
        + getOptionsText(true)
        + Resources.toString(tailUrl, UTF_8);

    if (args.length > 0) {
      Path path = FileSystems.getDefault().getPath(args[0]);
      write(path, output.getBytes(UTF_8));
    } else {
      System.err.println(output);
    }
  }
}
