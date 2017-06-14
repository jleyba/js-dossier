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

package com.github.jsdossier.testing;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.exists;

import com.github.jsdossier.jscomp.DossierCompiler;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.AbstractCommandLineRunner;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.Environment;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.jstype.JSType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;

public class CompilerUtil {

  private static final List<SourceFile> NO_EXTERNS = ImmutableList.of();

  private static final ImmutableListMultimap<Environment, SourceFile> BUILTIN_EXTERN_CACHE =
      loadBuiltinExterns();

  private final DossierCompiler compiler;
  private final TypeRegistry typeRegistry;
  private final CompilerOptions options;

  @Inject
  public CompilerUtil(
      DossierCompiler compiler, TypeRegistry typeRegistry, CompilerOptions options) {
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
    this.options = options;
  }

  public DossierCompiler getCompiler() {
    return compiler;
  }

  public CompilerOptions getOptions() {
    return options;
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
    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .addAll(getBuiltInExterns(options.getEnvironment()))
            .build();

    Result result = compiler.compile(externs, inputs, options);
    assertCompiled(result);

    typeRegistry.computeTypeRelationships(compiler.getTopScope(), compiler.getTypeRegistry());
  }

  public JSType evaluate(JSTypeExpression expression) {
    return expression.evaluate(compiler.getTopScope(), compiler.getTypeRegistry());
  }

  private static ImmutableListMultimap<Environment, SourceFile> loadBuiltinExterns() {
    ImmutableListMultimap.Builder<Environment, SourceFile> externs =
        ImmutableListMultimap.builder();
    for (Environment environment : Environment.values()) {
      try {
        externs.putAll(environment, AbstractCommandLineRunner.getBuiltinExterns(environment));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return externs.build();
  }

  private static ImmutableList<SourceFile> getBuiltInExterns(Environment environment) {
    return BUILTIN_EXTERN_CACHE.get(environment);
  }

  private  void assertCompiled(Result result) {
    if (result.errors.length > 0 || result.warnings.length > 0) {
      List<String> errors = Lists.newLinkedList();
      errors.add("Failed to compile!");
      errors.add("Compiler errors:");
      appendErrors(errors, result.errors);

      errors.add("Compiler warnings");
      appendErrors(errors, result.warnings);
      
      if (result.errors.length == 0) {
        System.out.println(toSource());
      }

      throw new CompileFailureException(Joiner.on("\n").join(errors));
    }
  }

  private static void appendErrors(List<String> list, JSError[] errors) {
    for (JSError error : errors) {
      list.add(String.format("%s %s:%d", error.description, error.sourceName, error.lineNumber));
    }
  }

  public static SourceFile createSourceFile(Path path, String... lines) {
    if (!exists(path)) {
      try {
        if (path.getParent() != null) {
          createDirectories(path.getParent());
        }
        createFile(path);
      } catch (IOException e) {
        throw new AssertionError("unexpected IO error", e);
      }
    }
    return SourceFile.fromCode(path.toString(), Joiner.on("\n").join(lines));
  }

  public static class CompileFailureException extends RuntimeException {

    public CompileFailureException(String message) {
      super(message);
    }
  }
}
