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

package com.github.jsdossier;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.github.jsdossier.jscomp.DossierCompiler;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.Property;
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

  private TypeRegistry typeRegistry;
  private CompilerUtil util;

  @Before
  public void setUp() {
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.<Path>of());
    typeRegistry = new TypeRegistry(compiler.getTypeRegistry());
    CompilerOptions options = Main.createOptions(Jimfs.newFileSystem(), typeRegistry, compiler);

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
        "Hello, world!\nGoodbye, world!",
        doc.getBlockComment());
  }

  @Test
  public void canExtractBlockComment_multiLineIndented() {
    JsDoc doc = getClassJsDoc(
        "  /**",
        "   * Hello, world!",
        "   * Goodbye, world!",
        "   * @constructor",
        "   */",
        "  function Foo(){}");
    assertEquals(
        "Hello, world!\nGoodbye, world!",
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
        "Hello, world! @not_an_annotation\nGoodbye, world!",
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
    Iterator<Parameter> parameters = doc.getParameters().iterator();

    Parameter param = parameters.next();
    assertEquals("a", param.getName());
    assertEquals("is for apples.", param.getDescription());
    assertSameRootNode(rawInfo.getParameterType("a"), param.getType());

    param = parameters.next();
    assertEquals("b", param.getName());
    assertEquals("is for\n    bananas.", param.getDescription());
    assertSameRootNode(rawInfo.getParameterType("b"), param.getType());

    param = parameters.next();
    assertEquals("c", param.getName());
    assertEquals("this comment\n    span multiple\n    lines.",
        param.getDescription());
    assertSameRootNode(rawInfo.getParameterType("c"), param.getType());

    assertFalse(parameters.hasNext());
  }

  @Test
  public void parsesParamDescriptions_singleLineComment() {
    JsDoc doc = getClassJsDoc(
        "/** @constructor @param {string} x a name. */",
        "function foo(x) {}");
    Parameter param = getOnlyElement(doc.getParameters());
    assertEquals("x", param.getName());
    assertEquals("a name.", param.getDescription());
  }


  @Test
  public void parsesParamDescriptions_indentedSingleLineComment() {
    JsDoc doc = getClassJsDoc(
        "  /** @constructor @param {string} x a name. */",
        "  function foo(x) {}");
    Parameter param = getOnlyElement(doc.getParameters());
    assertEquals("x", param.getName());
    assertEquals("a name.", param.getDescription());
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
    assertEquals("Use something\n    else.", doc.getDeprecationReason());
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
    assertEquals("Use\n    something\n    else.", doc.getDeprecationReason());
  }

  @Test
  public void parsesReturnDescription_oneLineA() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "var Foo = function() {};",
        "/** @return nothing. */",
        "Foo.bar = function() { return ''; };");
    NominalType foo =  getOnlyElement(typeRegistry.getNominalTypes());
    Property bar = getOnlyElement(foo.getProperties());
    assertEquals("bar", bar.getName());
    assertTrue(bar.getType().isFunctionType());
    assertEquals("nothing.", JsDoc.from(bar.getJSDocInfo()).getReturnDescription());
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
    NominalType foo =  getOnlyElement(typeRegistry.getNominalTypes());
    Property bar = getOnlyElement(foo.getProperties());
    assertEquals("bar", bar.getName());
    assertTrue(bar.getType().isFunctionType());
    assertEquals("nothing.", JsDoc.from(bar.getJSDocInfo()).getReturnDescription());
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
    NominalType foo =  getOnlyElement(typeRegistry.getNominalTypes());
    Property bar = getOnlyElement(foo.getProperties());
    assertEquals("bar", bar.getName());
    assertTrue(bar.getType().isFunctionType());
    assertEquals("nothing over\n    two lines.",
        JsDoc.from(bar.getJSDocInfo()).getReturnDescription());
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
    NominalType foo =  getOnlyElement(typeRegistry.getNominalTypes());
    Property bar = getOnlyElement(foo.getProperties());
    assertEquals("bar", bar.getName());
    assertTrue(bar.getType().isFunctionType());
    assertEquals("nothing over\n    many\n    lines.",
        JsDoc.from(bar.getJSDocInfo()).getReturnDescription());
  }

  @Test
  public void parsesSeeTags_singleLineComment() {
    util.compile(path("foo/bar.js"),
        "/**",
        " * @constructor",
        " * @see other.",
        " */",
        "var foo = function() {};");
    NominalType foo =  getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("foo", foo.getQualifiedName());
    assertTrue(foo.getJsType().isConstructor());
    assertThat(foo.getJsdoc().getSeeClauses()).containsExactly("other.");
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
    assertThat(doc.getSeeClauses()).containsExactly("foo.", "bar.");
  }

  @Test
  public void parseSingleThrowsClause_singleLine() {
    JsDoc doc = getClassJsDoc(
        "/**",
        " * @throws {string} Hello.",
        " * @constructor",
        " */",
        "function Foo(){}");
    JsDoc.ThrowsClause tc = getOnlyElement(doc.getThrowsClauses());
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
    JsDoc.ThrowsClause tc = getOnlyElement(doc.getThrowsClauses());
    assertEquals("Hello.\n    Goodbye.", tc.getDescription());
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
    assertEquals("Hello.\n    Goodbye.", tc.getDescription());

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

    Iterator<Parameter> parameters = doc.getParameters().iterator();

    Parameter param = parameters.next();
    assertEquals("is for\n    apples.", param.getDescription());

    param = parameters.next();
    assertEquals("is for bananas.", param.getDescription());

    param = parameters.next();
    assertEquals("is for an optional\n    parameter.", param.getDescription());

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
        "line one\nline two\nline three",
        doc.getFileoverview());
  }

  @Test
  public void blockCommentsFromGoogDefinedClass_usesClassCommentIfNoCommentOnConstructor() {
    JsDoc docs = getClassJsDoc(
        "/**",
        " * This is the class level description.",
        " * <pre>",
        " *   it contains a pre block",
        " * </pre>",
        " */",
        "var Foo = goog.defineClass(null, {",
        "  /**",
        "   * @param {string} a A parameter.",
        "   */",
        "  constructor: function(a) {}",
        "});");
    // Note the compiler strips all leading and trailing whitespace on each line, so the
    // pre block's indendentation is ruined.
    assertEquals(
        "This is the class level description.\n<pre>\nit contains a pre block\n</pre>",
        docs.getBlockComment());
  }

  @Test
  public void blockCommentsFromGoogDefinedClass_usesCtorCommentIfProvided() {
    JsDoc docs = getClassJsDoc(
        "/**",
        " * This is the class level description.",
        " * <pre>",
        " *   it contains a pre block",
        " * </pre>",
        " */",
        "var Foo = goog.defineClass(null, {",
        "  /**",
        "   * This is a comment on the constructor and should be used as the class comment.",
        "   * <pre>",
        "   *    This is a pre-formatted block.",
        "   * </pre>",
        "   * @param {string} a A parameter.",
        "   */",
        "  constructor: function(a) {}",
        "});");
    assertEquals(
        "This is a comment on the constructor and should be used as the class comment.\n"
            + "<pre>\n"
            + "   This is a pre-formatted block.\n"
            + "</pre>",
        docs.getBlockComment());
  }

  @Test
  public void extractsDefineComments_blockCommentAboveAnnotation() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo');",
        "",
        "/**",
        " * Hello, world!",
        " * @define {boolean}",
        " */",
        "foo.bar = false;");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertThat(type.getQualifiedName()).isEqualTo("foo");

    Property property = getOnlyElement(type.getProperties());
    assertThat(property.getName()).isEqualTo("bar");

    JsDoc doc = JsDoc.from(property.getJSDocInfo());
    assertThat(doc).isNotNull();
    assertThat(doc.getBlockComment()).isEqualTo("Hello, world!");
  }

  @Test
  public void extractsDefineComments_commentInlineWithAnnotation() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo');",
        "",
        "/**",
        " * @define {boolean} Hello, world!",
        " *     Goodbye, world!",
        " */",
        "foo.bar = false;");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertThat(type.getQualifiedName()).isEqualTo("foo");

    Property property = getOnlyElement(type.getProperties());
    assertThat(property.getName()).isEqualTo("bar");

    JsDoc doc = JsDoc.from(property.getJSDocInfo());
    assertThat(doc).isNotNull();
    assertThat(doc.getBlockComment()).isEqualTo(
        "Hello, world!\n" +
        "    Goodbye, world!");
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
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertWithMessage("Not a constructor: " + type.getQualifiedName())
        .that(type.getJsType().isConstructor());
    return type.getJsdoc();
  }

  private static void assertSameRootNode(JSTypeExpression a, JSTypeExpression b) {
    assertSame(a.getRoot(), b.getRoot());
  }

  private static String lines(String... lines) {
    return Joiner.on('\n').join(lines);
  }

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }
}
