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

import static com.google.common.base.Preconditions.checkState;

import com.github.jsdossier.annotations.Stderr;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.rhino.Node;

import java.io.PrintStream;
import java.nio.file.Path;

import javax.inject.Inject;

/**
 * A specialized version of the {@link com.google.javascript.jscomp.Compiler Closure Compiler} that preserves CommonJS module
 * structure so as to properly extract JSDoc info.
 */
public final class DossierCompiler extends Compiler {

  private final DossierModuleRegistry moduleRegistry;
  private boolean hasParsed = false;

  /**
   * Creates a new compiler that reports errors and warnings to an output stream.
   *
   * @param stream the output stream.
   * @param moduleRegistry the module registry to use.
   */
  @Inject
  DossierCompiler(
      @Stderr PrintStream stream,
      DossierModuleRegistry moduleRegistry) {
    super(stream);
    this.moduleRegistry = moduleRegistry;
  }

  @VisibleForTesting
  @Deprecated
  public DossierCompiler(
      PrintStream stream,
      Iterable<Path> modules) {
    this(stream, new DossierModuleRegistry(ImmutableSet.copyOf(modules)));
  }

  public DossierModuleRegistry getModuleRegistry() {
    return moduleRegistry;
  }

  @Override
  public void parse() {
    checkState(!hasParsed,
        "%s can only parse its inputs once! Create a new instance if you must re-parse",
        getClass());
    hasParsed = true;

    // First we transform the CommonJS modules. This ensures input sources are properly re-ordered
    // based on the goog.provide/require statements we generate for the modules. This is necessary
    // since the compiler does its final input ordering before invoking any custom passes
    // (otherwise, we could just process the modules as a custom pass).
    DossierProcessCommonJsModules cjs = new DossierProcessCommonJsModules(this);
    // TODO(jleyba): processCommonJsModules(cjs, getExternsInOrder());
    processCommonJsModules(cjs, getInputsById().values());

    // Now we can proceed with the normal parsing.
    super.parse();
  }

  private void processCommonJsModules(
      DossierProcessCommonJsModules compilerPass, Iterable<CompilerInput> inputs) {
    for (CompilerInput input : inputs) {
      Node root = input.getAstRoot(this);
      if (root == null) {
        continue;
      }
      compilerPass.process(null, root);
    }
  }
}
