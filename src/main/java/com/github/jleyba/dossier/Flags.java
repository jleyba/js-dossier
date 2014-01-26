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

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Describes the runtime configuration for the app.
 */
class Flags {

  @Option(
      name = "--help", aliases = "-h",
      usage = "Print this help message and exit.")
  boolean displayHelp;

  @Option(
      name = "--config", aliases = "-c",
      handler = SimplePathHandler.class,
      required = true,
      usage = "Path to the JSON configuration file to use.")
  Path config = null;

  private Flags() {}

  /**
   * Parses the given command line flags, exiting the program if there are any errors or if usage
   * information was requested with the {@link #displayHelp --help} flag.
   */
  static Flags parse(String[] args) {
    Flags flags = new Flags();
    CmdLineParser parser = new CmdLineParser(flags);
    parser.setUsageWidth(79);

    boolean isConfigValid = true;
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      isConfigValid = false;
    }

    if (!isConfigValid || flags.displayHelp) {
      parser.printUsage(System.err);
      System.exit(1);
    }

    return flags;
  }

  private static Path getPath(String path) {
    return FileSystems.getDefault()
        .getPath(path)
        .toAbsolutePath()
        .normalize();
  }

  public static class SimplePathHandler extends OptionHandler<Path> {
    public SimplePathHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Path> setter) {
      super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
      Path path = getPath(params.getParameter(0));
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
