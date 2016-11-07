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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Describes the runtime configuration for the app.
 */
final class Flags {

  boolean displayHelp;
  boolean displayJsonHelp;
  boolean printConfig;
  int numThreads = Runtime.getRuntime().availableProcessors() * 2;
  Path config = null;
  JsonObject jsonConfig = new JsonObject();

  private final FileSystem fileSystem;

  private Flags(FileSystem fileSystem) {
    this.fileSystem = fileSystem;
  }

  @Option(
      name = "--help", aliases = "-h",
      usage = "Print this help message and exit.")
  private void setDisplayHelp(boolean help) {
    this.displayHelp = help;
  }

  @Option(
      name = "--help_json",
      usage = "Print detailed usage information on the JSON configuration file format, then exit")
  private void setDisplayJsonHelp(boolean help) {
    this.displayJsonHelp = help;
  }

  @Option(
      name = "--config", aliases = "-c",
      metaVar = "CONFIG",
      usage = "Path to the JSON configuration file to use. If specified, all other configuration " +
          "flags will be ignored")
  private void setConfigPath(String path) {
    config = fileSystem.getPath(path).toAbsolutePath().normalize();
    checkArgument(Files.exists(config), "Path does not exist: %s", config);
    checkArgument(Files.isReadable(config), "Path is not readable: %s", config);
  }

  @Option(
      name = "--print_config", aliases = "-p",
      usage = "Whether to print diagnostic information about the parsed JSON configuration, " +
          "including all resolved paths.")
  private void setPrintConfig(boolean print) {
    this.printConfig = print;
  }

  @Option(
      name = "--num_threads",
      usage = "The number of threads to use for rendering. Defaults to 2 times the number of " +
          "available processors")
  private void setNumThreads(int n) {
    checkArgument(n >= 1, "invalid number of flags: %s", n);
    this.numThreads = n;
  }

  @Option(
      name = "--closure_library_dir",
      metaVar = "PATH",
      usage = "Sets the path to the Closure library's root directory. When provided, Closure files" +
          " will automatically be added as sources based on the goog.require calls in --source" +
          " files. Refer to --help_json for more information")
  private void setClosureLibraryDir(String path) {
    this.jsonConfig.addProperty("closureLibraryDir", path);
  }

  @Option(
      name = "--closure_dep_file",
      metaVar = "PATH",
      depends = "--closure_library_dir",
      usage = "Defines a file to parse for goog.addDependency calls. This requires also providing" +
          " --closure_library_dir")
  private void addClosureDepFile(String path) {
    addToArray("closureDepFiles", path);
  }

  @Option(
      name = "--source",
      metaVar = "PATH",
      usage = "Defines the path to a file to extract API documentation from, or a directory of" +
          " such files")
  private void addSource(String path) {
    addToArray("sources", path);
  }

  @Option(
      name = "--module",
      metaVar = "PATH",
      usage = "Defines the path to a CommonJS module to extract API documentation from, or a" +
          " directory of such files")
  private void addModule(String path) {
    addToArray("modules", path);
  }

  @Option(
      name = "--extern",
      metaVar = "PATH",
      usage = "Defines the path to a file that provides extern definitions, or a directory of" +
          " such files.")
  private void addExtern(String path) {
    addToArray("externs", path);
  }

  @Option(
      name = "--extern_module",
      metaVar = "PATH",
      usage = "Defines the path to a file that provides extern definitions for CommonJS modules, " +
          "or a directory of such files.")
  private void addExternModule(String path) {
    addToArray("externModules", path);
  }

  @Option(
      name = "--exclude",
      metaVar = "PATH",
      usage = "Defines the path to a file or directory of files that should be excluded from" +
          " processing.")
  private void addExclude(String path) {
    addToArray("excludes", path);
  }

  @Option(
      name = "--output",
      metaVar = "PATH",
      usage = "Sets the path to the output directory, or a .zip file to generate the output in")
  private void setOutput(String path) {
    jsonConfig.addProperty("output", path);
  }

  @Option(
      name = "--readme",
      metaVar = "PATH",
      usage = "Sets the path to a markdown file to include as a readme on the main landing page")
  private void setReadme(String path) {
    jsonConfig.addProperty("readme", path);
  }

  @Option(
      name = "--strict",
      usage = "Whether to run all type checking passes before generating any documentation")
  private void setStrict(boolean strict) {
    jsonConfig.addProperty("strict", strict);
  }

  @Option(
      name = "--language",
      usage = "Which version of ECMAScript the input sources conform to." +
          " Must be one of {ES3, ES5, ES5_STRICT, ES6, ES6_STRICT};" +
          " Defaults to ES6_STRICT")
  private void setLanguage(Config.Language language) {
    jsonConfig.addProperty("language", language.name());
  }

  @Option(
      name = "--module_naming_convention",
      usage = "The module naming convention to use; refer to --help_json for more information." +
          " Must be one of {ES6, NODE}; defaults to ES6")
  private void setNamingConvention(String convention) {
    jsonConfig.addProperty("moduleNamingConvention", convention);
  }

  @Option(
      name = "--source_url_template",
      usage = "Template to use when generating links to source files; refer to --help_json for" +
          " more information.")
  private void setSourceUrlTemplate(String template) {
    jsonConfig.addProperty("sourceUrlTemplate", template);
  }

  @Option(
      name = "--module_filter",
      hidden = true,  // Expose this when it works better.
      usage = "Regular expression for modules that should be excluded from generated" +
          " documentation, even if found in the type graph")
  private void addModuleFilter(String filter) {
    addToArray("moduleFilters", filter);
  }

  @Option(
      name = "--type_filter",
      usage = "Regular expression for types that should be excluded from generated documentation," +
          " even if found in the type graph")
  private void addTypeFilter(String filter) {
    addToArray("typeFilters", filter);
  }

  @Option(
      name = "--custom_page",
      usage = "Defines a markdown file to include with the generated documentation. Each page " +
          "should be defined as $name:$path, where $name is the desired page title and $path " +
          "is the path to the actual file")
  private void addCustomPage(String spec) {
    List<String> parts = Splitter.on(':').limit(2).splitToList(spec);
    JsonObject page = new JsonObject();
    page.addProperty("name", parts.get(0));
    page.addProperty("path", parts.get(1));
    addToArray("customPages", page);
  }

  private void addToArray(String key, String value) {
    addToArray(key, new JsonPrimitive(value));
  }

  private void addToArray(String key, JsonElement element) {
    if (!jsonConfig.has(key)) {
      jsonConfig.add(key, new JsonArray());
    }
    jsonConfig.get(key).getAsJsonArray().add(element);
  }

  /**
   * Parses the given command line flags, exiting the program if there are any errors or if usage
   * information was requested with the {@link #displayHelp --help} flag.
   */
  synchronized static Flags parse(String[] args, FileSystem fileSystem) {
    final Flags flags = new Flags(fileSystem);
    CmdLineParser parser = new CmdLineParser(flags);
    parser.setUsageWidth(79);

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      if (!flags.displayHelp) {
        System.err.println(e.getMessage());
      }
      flags.displayHelp = true;
    }

    if (flags.displayHelp) {
      System.err.println("\nUsage: dossier [options] -c CONFIG");

      System.err.println("\nwhere options include:\n");
      parser.printUsage(System.err);
    }

    if (flags.displayJsonHelp) {
      System.err.println("\nThe JSON configuration file may have the following options:\n");
      System.err.println(Config.getOptionsText(false));
    }

    if (flags.displayHelp || flags.displayJsonHelp) {
      System.exit(1);
    }

    return flags;
  }
}
