package com.github.jleyba.dossier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DossierCompiler;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Tests for {@link JsDoc}.
 */
@RunWith(JUnit4.class)
public class JsDocTest {

  private DocRegistry docRegistry;
  private CompilerUtil util;

  @Before
  public void setUp() {
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.<Path>of());
    docRegistry = new DocRegistry(compiler.getTypeRegistry());
    CompilerOptions options = Main.createOptions(Jimfs.newFileSystem(), compiler, docRegistry);

    util = new CompilerUtil(compiler, options);
  }

  @Test
  public void returnsEmptyBlockCommentIfAnnotationsOnly() {
    JsDoc doc = getClassJsDoc("/** @constructor */function Foo(){}");
    assertEquals("", doc.getBlockComment());
  }

  @Test
  public void canExtractBlockComment_singleLine() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * Hello, world!",
        " * @constructor",
        " */",
        "function Foo(){}");
    assertEquals("Hello, world!", doc.getBlockComment());
  }

  @Test
  public void canExtractBlockComment_multiLine() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * Hello, world!",
        " * Goodbye, world!",
        " * @constructor",
        " */",
        "function Foo(){}");
    assertEquals(
        "Hello, world!\n Goodbye, world!",
        doc.getBlockComment());
  }

  @Test
  public void blockCommentRequiresAnnotationsToBeOnOwnLine() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * Hello, world! @not_an_annotation",
        " * Goodbye, world!",
        " * @constructor",
        " */",
        "function Foo(){}");
    assertEquals(
        "Hello, world! @not_an_annotation\n Goodbye, world!",
        doc.getBlockComment());
  }

  @Test
  public void parsesParamDescriptions() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * Hello, world! @not_an_annotation",
        " * Goodbye, world!",
        " * @param {string} a is for apples.",
        " * @param {string} b is for",
        " *     bananas.",
        " * @param {string} c this comment",
        " *     span multiple",
        " *     lines.",
        " * @constructor",
        " */",
        "function Foo(a, b, c){}");
    JSDocInfo rawInfo = doc.getInfo();
    Iterator<ArgDescriptor> parameters = doc.getParameters().iterator();

    ArgDescriptor param = parameters.next();
    assertEquals("a", param.getName());
    assertEquals("is for apples.", param.getDescription());
    assertSameRootNode(rawInfo.getParameterType("a"), param.getType());

    param = parameters.next();
    assertEquals("b", param.getName());
    assertEquals("is for\n     bananas.", param.getDescription());
    assertSameRootNode(rawInfo.getParameterType("b"), param.getType());

    param = parameters.next();
    assertEquals("c", param.getName());
    assertEquals("this comment\n     span multiple\n     lines.",
        param.getDescription());
    assertSameRootNode(rawInfo.getParameterType("c"), param.getType());

    assertFalse(parameters.hasNext());
  }

  @Test
  public void parsesDeprecationReason_singleLine() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * @deprecated Use something else.",
        " * @constructor",
        " */",
        "function Foo(){}");
    assertTrue(doc.isDeprecated());
    assertEquals("Use something else.", doc.getDeprecationReason());
  }

  @Test
  public void parsesDeprecationReason_twoLines() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * @deprecated Use something",
        " *     else.",
        " * @constructor",
        " */",
        "function Foo(){}");
    assertTrue(doc.isDeprecated());
    assertEquals("Use something\n     else.", doc.getDeprecationReason());
  }

  @Test
  public void parsesDeprecationReason_manyLines() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * @deprecated Use",
        " *     something",
        " *     else.",
        " * @constructor",
        " */",
        "function Foo(){}");
    assertTrue(doc.isDeprecated());
    assertEquals("Use\n     something\n     else.", doc.getDeprecationReason());
  }

  @Test
  public void parsesReturnDescription_oneLineA() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "var Foo = function() {};",
        "/** @return nothing. */",
        "Foo.prototype.bar = function() { return ''; };");
    Descriptor foo = Iterables.getOnlyElement(docRegistry.getTypes());
    Descriptor bar = Iterables.getOnlyElement(foo.getInstanceProperties());
    assertEquals("Foo.prototype.bar", bar.getFullName());
    assertTrue(bar.isFunction());
    assertEquals("nothing.", bar.getJsDoc().getReturnDescription());
  }

  @Test
  public void parsesReturnDescription_oneLineB() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "var Foo = function() {};",
        "/**",
        " * @return nothing.",
        " */",
        "Foo.bar = function() { return ''; };");
    Descriptor foo = Iterables.getOnlyElement(docRegistry.getTypes());
    Descriptor bar = Iterables.getOnlyElement(foo.getProperties());
    assertEquals("Foo.bar", bar.getFullName());
    assertTrue(bar.isFunction());
    assertEquals("nothing.", bar.getJsDoc().getReturnDescription());
  }

  @Test
  public void parsesReturnDescription_twoLines() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "var Foo = function() {};",
        "/**",
        " * @return nothing over",
        " *     two lines.",
        " */",
        "Foo.bar = function() { return ''; };");
    Descriptor foo = Iterables.getOnlyElement(docRegistry.getTypes());
    Descriptor bar = Iterables.getOnlyElement(foo.getProperties());
    assertEquals("Foo.bar", bar.getFullName());
    assertTrue(bar.isFunction());
    assertEquals("nothing over\n     two lines.", bar.getJsDoc().getReturnDescription());
  }

  @Test
  public void parsesReturnDescription_manyLines() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "var Foo = function() {};",
        "/**",
        " * @return nothing over",
        " *     many",
        " *     lines.",
        " */",
        "Foo.bar = function() { return ''; };");
    Descriptor foo = Iterables.getOnlyElement(docRegistry.getTypes());
    Descriptor bar = Iterables.getOnlyElement(foo.getProperties());
    assertEquals("Foo.bar", bar.getFullName());
    assertTrue(bar.isFunction());
    assertEquals("nothing over\n     many\n     lines.", bar.getJsDoc().getReturnDescription());
  }

  @Test
  public void parsesSeeTags_singleLineComment() {
    util.compile(path("foo/bar.js"),
        "/**",
        " * @constructor",
        " * @see other.",
        " */",
        "var foo = function() {};");
    Descriptor foo = Iterables.getOnlyElement(docRegistry.getTypes());
    assertEquals("foo", foo.getFullName());
    assertTrue(foo.isConstructor());
    assertEquals(ImmutableList.of("other."), foo.getJsDoc().getSeeClauses());
  }

  @Test
  public void parsesSeeTags_multilineComment() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * @see foo.",
        " * @see bar.",
        " * @constructor",
        " */",
        "function Foo(){}");
    assertEquals(
        ImmutableList.of("foo.", "bar."),
        doc.getSeeClauses());
  }

  @Test
  public void parseSingleThrowsClause_singleLine() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * @throws {string} Hello.",
        " * @constructor",
        " */",
        "function Foo(){}");
    JsDoc.ThrowsClause tc = Iterables.getOnlyElement(doc.getThrowsClauses());
    assertEquals("Hello.", tc.getDescription());
    assertTrue(tc.getType().isPresent());
  }

  @Test
  public void parseSingleThrowsClause_multiLine() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * @throws {string} Hello.",
        " *     Goodbye.",
        " * @constructor",
        " */",
        "function Foo(){}");
    JsDoc.ThrowsClause tc = Iterables.getOnlyElement(doc.getThrowsClauses());
    assertEquals("Hello.\n     Goodbye.", tc.getDescription());
    assertTrue(tc.getType().isPresent());
  }

  @Test
  public void parseMultipleThrowsClauses() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * @throws {string} Hello.",
        " *     Goodbye.",
        " * @throws {Error} boom.",
        " * @constructor",
        " */",
        "function Foo(){}");
    Iterator<JsDoc.ThrowsClause> it = doc.getThrowsClauses().iterator();
    JsDoc.ThrowsClause tc = it.next();
    assertEquals("Hello.\n     Goodbye.", tc.getDescription());

    tc = it.next();
    assertEquals("boom.", tc.getDescription());

    assertFalse(it.hasNext());
  }

  @Test
  public void parseCommentThatDoesNotStartOnLine1() {
    JsDoc doc = getClassJsDoc(
        "",
        "",
        "",
        "/***************",
        "",
        "foo bar",
        "",
        "       final line of block comment.",
        "   * @param {string} a is for",
        "       *     apples.",
        "       * @param {string} b is for bananas.",
        "       * @param {(string|Object)=} opt_c is for an optional",
        "       *     parameter.",
        "       * @constructor */",
        "function Foo(a, b, opt_c) {}");

    assertEquals(
        lines(
            "foo bar",
            "",
            "       final line of block comment."),
        doc.getBlockComment());

    Iterator<ArgDescriptor> parameters = doc.getParameters().iterator();

    ArgDescriptor param = parameters.next();
    assertEquals("is for\n     apples.", param.getDescription());

    param = parameters.next();
    assertEquals("is for bananas.", param.getDescription());

    param = parameters.next();
    assertEquals("is for an optional\n     parameter.", param.getDescription());

    assertFalse(parameters.hasNext());
  }

  @Test
  public void parsesFileoverviewComments() {
    Node script = getScriptNode(
        "",
        "/**",
        " * @fileoverview line one",
        " * line two",
        " * line three",
        " */",
        "",
        "var x = {};");

    JsDoc doc = new JsDoc(script.getJSDocInfo());
    assertEquals(
        "line one\n line two\n line three",
        doc.getFileoverview());
  }

  private Node getScriptNode(String... lines) {
    util.compile(path("foo/bar.js"), lines);
    return util.getCompiler().getRoot()
        .getFirstChild()  // Synthetic extern block.
        .getNext()        // Input sources synthetic block.
        .getFirstChild();
  }

  private JsDoc getClassJsDoc(String... lines) {
    util.compile(path("foo/bar.js"), lines);
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
    assertTrue(descriptor.isConstructor());
    return descriptor.getJsDoc();
  }

  private static void assertSameRootNode(JSTypeExpression a, JSTypeExpression b) {
    assertSame(a.getRootNode(), b.getRootNode());
  }

  private static String lines(String... lines) {
    return Joiner.on('\n').join(lines);
  }

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }
}
