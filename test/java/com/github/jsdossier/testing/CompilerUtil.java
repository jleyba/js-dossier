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

package com.github.jsdossier.testing;

import com.github.jsdossier.jscomp.CallableCompiler;
import com.github.jsdossier.jscomp.DossierCompiler;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.AbstractCommandLineRunner;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.inject.Inject;

public class CompilerUtil {

  private static final List<SourceFile> NO_EXTERNS = ImmutableList.of();

  private final DossierCompiler compiler;
  private final CompilerOptions options;

  @Inject
  public CompilerUtil(DossierCompiler compiler, CompilerOptions options) {
    this.compiler = compiler;
    this.options = options;
  }

  public DossierCompiler getCompiler() {
    return compiler;
  }

  public String toSource() {
    return compiler.toSource();
  }

  void printTree(Appendable appendable) throws IOException {
    compiler.getRoot().appendStringTree(appendable);
  }

  public void compile(Path path, String... lines) {
    compile(createSourceFile(path, lines));
  }

  public void compile(SourceFile... sourceFiles) {
    compile(NO_EXTERNS, Lists.newArrayList(sourceFiles));
  }

  public void compile(List<SourceFile> externs, List<SourceFile> inputs) {
    if (options.getNewTypeInference()) {
      try {
        externs = ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .addAll(AbstractCommandLineRunner.getBuiltinExterns(options))
            .build();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    Result result = compiler.compile(externs, inputs, options);
    assertCompiled(result);
  }

  private static void assertCompiled(Result result) {
    if (result.errors.length > 0 || result.warnings.length > 0) {
      List<String> errors = Lists.newLinkedList();
      errors.add("Failed to compile!");
      errors.add("Compiler errors:");
      appendErrors(errors, result.errors);

      errors.add("Compiler warnings");
      appendErrors(errors, result.warnings);

      throw new CompileFailureException(Joiner.on("\n").join(errors));
    }
  }

  private static void appendErrors(List<String> list, JSError[] errors) {
    for (JSError error : errors) {
      list.add(String.format("%s %s:%d",
          error.description,
          error.sourceName,
          error.lineNumber));
    }
  }

  public static SourceFile createSourceFile(Path path, String... lines) {
    return SourceFile.fromCode(path.toString(), Joiner.on("\n").join(lines));
  }

  public static class CompileFailureException extends RuntimeException {

    public CompileFailureException(String message) {
      super(message);
    }
  }
}
