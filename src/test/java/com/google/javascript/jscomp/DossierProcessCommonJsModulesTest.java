package com.google.javascript.jscomp;

import static com.github.jleyba.dossier.CompilerUtil.createSourceFile;
import static org.junit.Assert.assertEquals;

import com.github.jleyba.dossier.CompilerUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Tests for {@link DossierProcessCommonJsModules}.
 */
@RunWith(JUnit4.class)
public class DossierProcessCommonJsModulesTest {

  @Test
  public void doesNotModifySourceIfFileIsNotACommonJsModule() {
    CompilerUtil compiler = createCompiler();

    compiler.compile(path("foo/bar.js"), "var x = 123;");
    assertEquals("var x = 123;", compiler.toSource().trim());
  }

  @Test
  public void wrapsCommonJsModules() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "var x = 123;");
    assertEquals(
        module("dossier$$module__foo$bar", "  var x = 123;"),
        compiler.toSource().trim());
  }

  @Test
  public void replacesAllGlobalThisReferencesWithinModuleWithExportReferences() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "this.name = 'Bob';");
    assertEquals(
        module("dossier$$module__foo$bar", "  exports.name = \"Bob\";"),
        compiler.toSource().trim());
  }

  @Test
  public void sortsSingleModuleDep() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    SourceFile root = createSourceFile(path("foo/root.js"), "");
    SourceFile leaf = createSourceFile(path("foo/leaf.js"), "require('./root');");

    compiler.compile(leaf, root);  // Should reorder since leaf depends on root.

    assertEquals(
        lines(
            module("dossier$$module__foo$root"),
            module("dossier$$module__foo$leaf", "  dossier$$module__foo$root.exports;")),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesRequireStatementToDirectlyReferenceExportsObject() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    compiler.compile(
        createSourceFile(path("foo/root.js"), ""),
        createSourceFile(path("foo/leaf.js"),
            "var foo = require('./root');",
            "var bar = require('./root').bar"));

    assertEquals(
        lines(
            module("dossier$$module__foo$root"),
            module("dossier$$module__foo$leaf", lines(
                "  var foo = dossier$$module__foo$root.exports;",
                "  var bar = dossier$$module__foo$root.exports.bar;"))),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesRequireStatementToDirectlyReferenceExportsObject_compoundStatement() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    compiler.compile(
        createSourceFile(path("foo/root.js"), ""),
        createSourceFile(path("foo/leaf.js"),
            "var foo = require('./root'),",
            "    bar = require('./root').bar"));

    assertEquals(
        lines(
            module("dossier$$module__foo$root"),
            module("dossier$$module__foo$leaf",
                "  var foo = dossier$$module__foo$root.exports, " +
                    "bar = dossier$$module__foo$root.exports.bar;")),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringModulesFromASubDirectory() {
    CompilerUtil compiler = createCompiler(path("foo/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"), "require('./bar/two');"),
        createSourceFile(path("foo/bar/two.js"), ""));

    assertEquals(
        lines(
            module("dossier$$module__foo$bar$two"),
            module("dossier$$module__foo$one",
                "  dossier$$module__foo$bar$two.exports;")),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringModulesFromAParentDirectory() {
    CompilerUtil compiler = createCompiler(path("foo/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('../one');"));

    assertEquals(
        lines(
            module("dossier$$module__foo$one"),
            module("dossier$$module__foo$bar$two",
                "  dossier$$module__foo$one.exports;")),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringModulesFromAParentsSibling() {
    CompilerUtil compiler = createCompiler(
        path("foo/baz/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/baz/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('../baz/one');"));

    assertEquals(
        lines(
            module("dossier$$module__foo$baz$one"),
            module("dossier$$module__foo$bar$two",
                "  dossier$$module__foo$baz$one.exports;")),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringAbsoluteModule() {
    CompilerUtil compiler = createCompiler(
        path("/absolute/foo/baz/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("/absolute/foo/baz/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('/absolute/foo/baz/one');"));

    assertEquals(
        lines(
            module("dossier$$module__$absolute$foo$baz$one"),
            module("dossier$$module__foo$bar$two",
                "  dossier$$module__$absolute$foo$baz$one.exports;")),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringExternModule() {
  }

  private static String module(String name) {
    return module(name, Optional.<String>absent());
  }

  private static String module(String name, String contents) {
    return module(name, Optional.of(contents));
  }

  private static String module(String name, Optional<String> contents) {
    ImmutableList.Builder<String> builder = ImmutableList.<String>builder().add(
        "var " + name + " = {};",
        name + ".exports = {};",
        "(function(module, exports, require, __filename, __dirname) {");
    if (contents.isPresent()) {
      builder.add(contents.get());
    }
    builder.add(
        "})(" + name + ", " + name + ".exports, function() {",
        "}, \"\", \"\");");
    return Joiner.on("\n").join(builder.build());
  }

  private static CompilerUtil createCompiler(final Path... commonJsModules) {
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new ClosureCodingConvention());
    options.setIdeMode(true);
    options.setClosurePass(true);
    options.setPrettyPrint(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    Compiler compiler = new DossierCompiler(System.err, ImmutableSet.copyOf(commonJsModules));

    return new CompilerUtil(compiler, options);
  }

  private static String lines(String... lines) {
    return Joiner.on('\n').join(lines);
  }

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }
}
