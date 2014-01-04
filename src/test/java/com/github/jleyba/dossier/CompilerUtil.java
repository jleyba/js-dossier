package com.github.jleyba.dossier;

import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.javascript.jscomp.*;
import com.google.javascript.jscomp.Compiler;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

public class CompilerUtil {

  private static final List<SourceFile> NO_EXTERNS = ImmutableList.of();

  private final Compiler compiler;
  private final CompilerOptions options;

  CompilerUtil(Config config) {
    this(new Compiler(), new Main(new String[0],
        new PrintStream(ByteStreams.nullOutputStream()),
        new PrintStream(ByteStreams.nullOutputStream()),
        config).createOptions());
  }

  public CompilerUtil(Compiler compiler, CompilerOptions options) {
    this.compiler = compiler;
    this.options = options;
  }

  Compiler getCompiler() {
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

      fail(Joiner.on("\n").join(errors));
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
}
