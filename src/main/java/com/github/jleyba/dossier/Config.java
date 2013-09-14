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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.intersection;

import com.google.common.collect.ImmutableSet;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Describes the runtime configuration for the app.
 */
class Config {

  private final ImmutableSet<Path> srcs;
  private final ImmutableSet<Path> externs;
  private final Path output;

  /**
   * Creates a new runtime configuration.
   *
   * @param srcs The list of compiler input sources.
   * @param externs The list of extern files for the Closure compiler.
   * @param output Path to the output directory.
   * @throws IllegalStateException If the source and extern lists intersect, or if the output
   *     path is not a directory.
   */
  Config(ImmutableSet<Path> srcs, ImmutableSet<Path> externs, Path output) {
    checkArgument(intersection(srcs, externs).isEmpty(),
        "The sources and externs inputs must be disjoint:\n  sources: %s\n  externs: %s",
        srcs, externs);
    checkArgument(!Files.exists(output) || Files.isDirectory(output),
        "Output path, %s, is not a directory", output);

    this.srcs = srcs;
    this.externs = externs;
    this.output = output;
  }

  ImmutableSet<Path> getSources() {
    return srcs;
  }

  ImmutableSet<Path> getExterns() {
    return externs;
  }

  Path getOutput() {
    return output;
  }
}
