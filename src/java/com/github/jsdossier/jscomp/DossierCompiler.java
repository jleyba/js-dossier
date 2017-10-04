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

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Stderr;
import com.github.jsdossier.jscomp.Annotations.Internal;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSModule;
import com.google.javascript.jscomp.SourceFile;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

/**
 * A specialized version of the {@link com.google.javascript.jscomp.Compiler Closure Compiler} that
 * preserves CommonJS module structure so as to properly extract JSDoc info.
 */
public final class DossierCompiler extends Compiler {

  private final ImmutableSet<Path> modulePaths;
  private final ImmutableList<DossierCompilerPass> passes;
  private final NodeLibrary nodeLibrary;

  private boolean hasParsed = false;

  @Inject
  DossierCompiler(
      @Stderr PrintStream stream,
      @Modules ImmutableSet<Path> modulePaths,
      @Internal ImmutableList<DossierCompilerPass> passes,
      NodeLibrary nodeLibrary) {
    super(stream);
    this.modulePaths = modulePaths;
    this.passes = passes;
    this.nodeLibrary = nodeLibrary;
  }

  @Override
  public <T extends SourceFile> void initModules(
      List<T> externs, List<JSModule> modules, CompilerOptions options) {
    checkArgument(modules.size() == 1, "only expecting 1 module, but got %s", modules.size());
    List<? extends SourceFile> externList = externs;
    if (!modulePaths.isEmpty()) {
      try {
        externList = concat(externs, nodeLibrary.getExternFiles());
        
        JSModule module = modules.iterator().next();
        nodeLibrary.getExternModules().forEach(module::add);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    super.initModules(externList, modules, options);
  }

  private ImmutableList<SourceFile> concat(
      Iterable<? extends SourceFile> a, Iterable<? extends SourceFile> b) {
    return ImmutableList.<SourceFile>builder().addAll(a).addAll(b).build();
  }

  @Override
  public void parseForCompilation() {
    checkState(
        !hasParsed,
        "%s can only parse its inputs once! Create a new instance if you must re-parse",
        getClass());
    hasParsed = true;

    getInputsById()
        .values()
        .stream()
        .map(input -> input.getAstRoot(this))
        .filter(Objects::nonNull)
        .forEach(
            node -> {
              for (DossierCompilerPass pass : passes) {
                pass.process(this, node);
              }
            });

    super.parseForCompilation();
  }
}
