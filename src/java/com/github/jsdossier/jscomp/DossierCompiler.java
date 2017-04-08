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

import static com.google.common.base.Preconditions.checkState;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Stderr;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * A specialized version of the {@link com.google.javascript.jscomp.Compiler Closure Compiler} that
 * preserves CommonJS module structure so as to properly extract JSDoc info.
 */
public final class DossierCompiler extends Compiler {

  private final TypeRegistry typeRegistry;
  private final FileSystem inputFs;
  private final ImmutableSet<Path> modulePaths;
  private final Es6ModulePassFactory modulePassFactory;
  private final FileVisibilityPassFactory fileVisibilityPassFactory;
  private final NodeLibrary nodeLibrary;

  private boolean hasParsed = false;

  @Inject
  DossierCompiler(
      @Stderr PrintStream stream,
      TypeRegistry typeRegistry,
      @Input FileSystem inputFs,
      @Modules ImmutableSet<Path> modulePaths,
      Es6ModulePassFactory modulePassFactory,
      FileVisibilityPassFactory fileVisibilityPassFactory,
      NodeLibrary nodeLibrary) {
    super(stream);
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
    this.modulePaths = modulePaths;
    this.modulePassFactory = modulePassFactory;
    this.fileVisibilityPassFactory = fileVisibilityPassFactory;
    this.nodeLibrary = nodeLibrary;
  }

  @Override
  public <T1 extends SourceFile, T2 extends SourceFile> Result compile(
      List<T1> externs, List<T2> inputs, CompilerOptions options) {
    List<? extends SourceFile> externList = externs;
    List<? extends SourceFile> inputList = inputs;
    if (!modulePaths.isEmpty()) {
      try {
        externList = concat(externs, nodeLibrary.getExternFiles());
        inputList = concat(inputs, nodeLibrary.getExternModules());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return super.compile(externList, inputList, options);
  }

  private ImmutableList<SourceFile> concat(
      Iterable<? extends SourceFile> a, Iterable<? extends SourceFile> b) {
    return ImmutableList.<SourceFile>builder().addAll(a).addAll(b).build();
  }

  @Override
  public void parse() {
    checkState(
        !hasParsed,
        "%s can only parse its inputs once! Create a new instance if you must re-parse",
        getClass());
    hasParsed = true;

    collectFileVisibilities();

    // Look for ES6 modules before we do anything else. This is necessary since the compiler
    // transpiles modules to ES5.
    processEs6Modules();

    // First we transform the CommonJS modules. This ensures input sources are properly re-ordered
    // based on the goog.provide/require statements we generate for the modules. This is necessary
    // since the compiler does its final input ordering before invoking any custom passes
    // (otherwise, we could just process the modules as a custom pass).
    if (!modulePaths.isEmpty()) {
      processCommonJsModules();
    }

    // Now we can proceed with the normal parsing.
    super.parse();
  }

  private void collectFileVisibilities() {
    CompilerPass pass = fileVisibilityPassFactory.create(this);
    for (CompilerInput input : getInputsById().values()) {
      Node root = input.getAstRoot(this);
      if (root != null) {
        pass.process(null, root);
      }
    }
  }

  private void processEs6Modules() {
    Es6ModulePass modulePass = modulePassFactory.create(this);
    for (CompilerInput input : getInputsById().values()) {
      Node root = input.getAstRoot(this);
      if (root != null) {
        modulePass.process(null, root);
      }
    }
  }

  private void processCommonJsModules() {
    List<Node> roots = new ArrayList<>();
    for (CompilerInput input : getInputsById().values()) {
      if (input.isExtern()) {
        continue;
      }

      Node root = input.getAstRoot(this);
      if (root == null) {
        continue;
      }
      roots.add(root);
    }

    NodeModulePass processor = new NodeModulePass(typeRegistry, inputFs, modulePaths, nodeLibrary);
    processor.process(this, roots);
  }
}
