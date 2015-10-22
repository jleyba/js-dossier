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

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;

import com.github.jsdossier.annotations.Args;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Stderr;
import com.github.jsdossier.annotations.Stdout;
import com.google.inject.Provider;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Customized command line runner that invokes the compiler in order to populate the type registry
 * for the documentation step.
 */
@Singleton
public final class CallableCompiler extends CommandLineRunner implements Callable<Integer> {

  private final FileSystem inputFileSystem;
  private final Provider<DossierCompiler> compilerProvider;
  private final Provider<CompilerOptions> optionsProvider;

  @Inject
  CallableCompiler(
      @Args String[] args,
      @Stdout PrintStream stdout,
      @Stderr PrintStream stderr,
      @Input FileSystem inputFileSystem,
      Provider<DossierCompiler> compilerProvider,
      Provider<CompilerOptions> optionsProvider) {
    super(args, stdout, stderr);
    this.inputFileSystem = inputFileSystem;
    this.compilerProvider = compilerProvider;
    this.optionsProvider = optionsProvider;
  }

  @Override
  protected com.google.javascript.jscomp.Compiler createCompiler() {
    return compilerProvider.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected CompilerOptions createOptions() {
    return optionsProvider.get();
  }

  @Override
  protected List<SourceFile> createInputs(List<String> files, List<String> zips, boolean allowStdIn)
      throws IOException {
    checkArgument(zips.isEmpty(), "Reading from zip files is not supported");
    List<SourceFile> inputs = new ArrayList<>(files.size());
    for (String filename : files) {
      checkArgument(!"-".equals(filename), "Reading from stdin not supported");
      Path path = inputFileSystem.getPath(filename);
      try (InputStream inputStream = newInputStream(path)) {
        SourceFile file = SourceFile.fromInputStream(filename, inputStream, UTF_8);
        inputs.add(file);
      }
    }
    return inputs;
  }

  @Override
  public Integer call() throws IOException {
    try {
      return doRun();
    } catch (FlagUsageException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
