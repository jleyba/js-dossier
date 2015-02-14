package com.github.jsdossier;

import static com.github.jsdossier.CommentUtil.getSummary;
import static com.github.jsdossier.CommentUtil.parseComment;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.TypeLink;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DossierCompiler;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class CommentUtilTest {

  private FileSystem fileSystem;
  private TypeRegistry typeRegistry;
  private CompilerUtil util;
  private Linker mockLinker;

  @Before
  public void setUp() {
    fileSystem = Jimfs.newFileSystem();
    mockLinker = mock(Linker.class);

    ErrorReporter errorReporter = mock(ErrorReporter.class);
    JSTypeRegistry jsTypeRegistry = new JSTypeRegistry(errorReporter);
    typeRegistry = new TypeRegistry(jsTypeRegistry);

    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.<Path>of());
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);

    util = new CompilerUtil(compiler, options);
  }

  @Test
  public void getSummaryWhenEntireCommentIsSummary_fullSentence() {
    String original = "hello, world.";
    Comment comment = getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>" + original + "</p>");

    original = "nothing left";
    comment = getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>" + original + "</p>");
  }

  @Test
  public void getSummaryWhenEntireCommentIsSummary_sentenceFragment() {
    String original = "nothing left";
    Comment comment = getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>" + original + "</p>");
  }

  @Test
  public void getSummaryFromMultipleSentences() {
    Comment comment = getSummary("Hello, world. Goodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello, world.</p>");
  }

  @Test
  public void getSummaryFromMultipleLines() {
    Comment comment = getSummary("Hello, world.\nGoodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello, world.</p>");
  }

  @Test
  public void getSummaryFromMultipleTabs() {
    Comment comment = getSummary("Hello, world.\tGoodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello, world.</p>");
  }

  @Test
  public void getSummaryWithInlineTag() {
    Comment comment = getSummary("Hello, {@code world. }Goodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello, {@code world.</p>");
  }

  @Test
  public void parseCommentWithUnterminatedInlineTaglet() {
    Comment comment = parseComment("Hello {@code world", mockLinker);
    assertHtmlText(Iterables.getOnlyElement(comment.getTokenList()),
        "<p>Hello {@code world</p>");
  }

  @Test
  public void parseCommentWithCodeTaglet() {
    Comment comment = parseComment("Hello {@code world}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello <code>world</code></p>");
  }

  @Test
  public void parseCommentWithInlineCodeAtBeginning() {
    Comment comment = parseComment("{@code Hello} world", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>Hello</code> world</p>");
  }

  @Test
  public void parseCommentWithAdjacentInlineCodeTaglets() {
    Comment comment = parseComment("{@code Hello}{@code world}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>Hello</code><code>world</code></p>");
  }

  @Test
  public void parseLiteralTaglet() {
    Comment comment = parseComment("red {@literal &} green", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>red &amp; green</p>");
  }

  @Test
  public void parseNestedCodeTaglets() {
    Comment comment = parseComment("{@code {@code hi}}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>{@code hi}</code></p>");
  }

  @Test
  public void parseLiteralInsideCode() {
    Comment comment = parseComment("{@code a {@literal b} c}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>a {@literal b} c</code></p>");
  }

  @Test
  public void parseCodeInsideLiteral() {
    Comment comment = parseComment("{@literal a {@code b} c}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>a {@code b} c</p>");
  }

  @Test
  public void parseCommentWithUnresolvableLink() {
    Comment comment = parseComment("A {@link link}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>A <code>link</code></p>");
  }

  @Test
  public void parseCommentWithUnresolvablePlainLink() {
    Comment comment = parseComment("A {@linkplain link}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>A link</p>");
  }

  @Test
  public void parseCommentWithTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parseComment("A link to {@link foo.Bar foo}.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>A link to <a href=\"/path/to/foo\"><code>foo</code></a>.</p>");
  }

  @Test
  public void parseCommentWithPlainTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parseComment("A link to {@linkplain foo.Bar foo}.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>A link to <a href=\"/path/to/foo\">foo</a>.</p>");
  }

  @Test
  public void doesNotTreatUnderscoresWithinAWordAsEmphasisBlocks() {
    Comment comment = parseComment("Description references CONSTANT_ENUM_VALUE.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Description references CONSTANT_ENUM_VALUE.</p>");
  }

  @Test
  public void parseClassDescription() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "",
        "/**",
        " * This is a class comment.",
        " *",
        " * - with",
        " *     list",
        " * - items",
        " *",
        " * And a",
        " *",
        " *     code block",
        " * @constructor",
        " */",
        "foo.Bar = function() {};");

    NominalType type = checkNotNull(typeRegistry.getNominalType("foo.Bar"));
    Comment comment = parseComment(
        type.getJsdoc().getBlockComment(),
        mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>This is a class comment.</p>\n" +
            "<ul>\n" +
            "<li>with\nlist</li>\n" +
            "<li>items</li>\n" +
            "</ul>\n" +
            "<p>And a</p>\n" +
            "<pre><code>code block\n" +
            "</code></pre>");
  }

  private static void assertHtmlText(Comment.Token token, String text) {
    assertEquals(text, token.getHtml());
    assertEquals("", token.getHref());
  }
}