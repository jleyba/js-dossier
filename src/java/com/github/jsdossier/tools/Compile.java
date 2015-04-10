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

package com.github.jsdossier.tools;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.DepsFileParser;
import com.google.javascript.jscomp.deps.JsFileParser;
import com.google.javascript.jscomp.deps.SortedDependencies;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Invokes the Closure compiler.
 */
final class Compile {
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
        name = "--flag", aliases = "-f",
        usage = "An additional flag to pass to the Closure compiler")
    List<String> flags = new ArrayList<>();
  }

  public static void main(String[] args) throws Exception {
    Flags flags = new Flags();

    CmdLineParser parser = new CmdLineParser(flags);
    parser.setUsageWidth(79);
    parser.parseArgument(args);

    FileSystem fs = FileSystems.getDefault();
    final Path closure = fs.getPath(flags.closure).toAbsolutePath();

    ErrorManager errorManager = new PrintStreamErrorManager(System.err);
    JsFileParser jsFileParser = new JsFileParser(errorManager);

    List<DependencyInfo> info = new ArrayList<>(flags.inputs.size());
    for (String path : flags.inputs) {
      Path absPath = fs.getPath(path).toAbsolutePath();
      Path closureRelativePath = closure.relativize(absPath);
      info.add(jsFileParser.parseFile(
          absPath.toString(),
          closureRelativePath.toString(),
          new String(Files.readAllBytes(absPath), UTF_8)));
    }

    List<DependencyInfo> allDeps = new LinkedList<>(info);
    allDeps.addAll(
        new DepsFileParser(errorManager).parseFile(closure.resolve("deps.js").toString()));
    List<DependencyInfo> sortedDeps = new SortedDependencies<>(allDeps)
        .getSortedDependenciesOf(info);

    ImmutableSet<String> compilerFlags = FluentIterable.from(sortedDeps)
        .transform(new Function<DependencyInfo, String>() {
          @Override
          public String apply(DependencyInfo input) {
            return "--js=" + closure.resolve(input.getPathRelativeToClosureBase())
                .toAbsolutePath()
                .normalize()
                .toString();
          }
        })
        .append("--js=" + closure.resolve("base.js"))
        .append(flags.flags)
        .toSet();

    CommandLineRunner.main(compilerFlags.toArray(new String[compilerFlags.size()]));
  }
}
