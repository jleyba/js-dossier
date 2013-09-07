package com.github.jleyba.dossier;

import com.google.common.collect.ImmutableList;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

class Options {

  @Nullable
  @Option(
      name = "closure_root",
      usage = "Path to the Closure library's root directory. This directory must contain the " +
          "base.js and deps.js files.")
  Path closureRoot;

  @Option(
      name = "srcs",
      usage = "List of sources to include as input to the Closure compiler. If a source path " +
          "refers to a directory, all .js files under that directory will be included.")
  List<Path> srcs = ImmutableList.of();

  @Option(
      name = "doc_exclusions",
      usage = "List of patterns for sources that should be included to satify the Closure " +
          "compiler, but should be excluded from document generation.")
  List<Pattern> docExlusionPatterns = ImmutableList.of();

  @interface Option {

    String name();

    /**
     * The usage string to display for this option.
     */
    String usage() default "";
  }
}
