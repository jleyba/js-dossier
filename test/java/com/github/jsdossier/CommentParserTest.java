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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.TypeLink;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.collect.Iterables;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;

import javax.inject.Inject;

@RunWith(JUnit4.class)
public class CommentParserTest {

  @Rule
  public GuiceRule guice = GuiceRule.builder(this).build();

  private final Linker mockLinker = mock(Linker.class);

  @Inject @Input FileSystem fileSystem;
  @Inject CompilerUtil util;
  @Inject CommentParser parser;
  @Inject TypeRegistry typeRegistry;

  @Test
  public void getSummaryWhenEntireCommentIsSummary_fullSentence() {
    String original = "hello, world.";
    Comment comment = parser.getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>" + original + "</p>");

    original = "nothing left";
    comment = parser.getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>" + original + "</p>");
  }

  @Test
  public void getSummaryWhenEntireCommentIsSummary_sentenceFragment() {
    String original = "nothing left";
    Comment comment = parser.getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>" + original + "</p>");
  }

  @Test
  public void getSummaryFromMultipleSentences() {
    Comment comment = parser.getSummary("Hello, world. Goodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello, world.</p>");
  }

  @Test
  public void getSummaryFromMultipleLines() {
    Comment comment = parser.getSummary("Hello, world.\nGoodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello, world.</p>");
  }

  @Test
  public void getSummaryFromMultipleTabs() {
    Comment comment = parser.getSummary("Hello, world.\tGoodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello, world.</p>");
  }

  @Test
  public void getSummaryWithInlineTag() {
    Comment comment = parser.getSummary("Hello, {@code world. }Goodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello, {@code world.</p>");
  }

  @Test
  public void parseCommentWithUnterminatedInlineTaglet() {
    Comment comment = parser.parseComment("Hello {@code world", mockLinker);
    assertHtmlText(Iterables.getOnlyElement(comment.getTokenList()),
        "<p>Hello {@code world</p>");
  }

  @Test
  public void parseCommentWithCodeTaglet() {
    Comment comment = parser.parseComment("Hello {@code world}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>Hello <code>world</code></p>");
  }

  @Test
  public void parseCommentWithInlineCodeAtBeginning() {
    Comment comment = parser.parseComment("{@code Hello} world", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>Hello</code> world</p>");
  }

  @Test
  public void parseCommentWithAdjacentInlineCodeTaglets() {
    Comment comment = parser.parseComment("{@code Hello}{@code world}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>Hello</code><code>world</code></p>");
  }

  @Test
  public void parseLiteralTaglet() {
    Comment comment = parser.parseComment("red {@literal &} green", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>red &amp; green</p>");
  }

  @Test
  public void parseNestedCodeTaglets() {
    Comment comment = parser.parseComment("{@code {@code hi}}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>{@code hi}</code></p>");
  }

  @Test
  public void parseLiteralInsideCode() {
    Comment comment = parser.parseComment("{@code a {@literal b} c}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>a {@literal b} c</code></p>");
  }

  @Test
  public void parseCodeInsideLiteral() {
    Comment comment = parser.parseComment("{@literal a {@code b} c}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>a {@code b} c</p>");
  }

  @Test
  public void parseCommentWithUnresolvableLink() {
    Comment comment = parser.parseComment("A {@link link}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>A <code>link</code></p>");
  }

  @Test
  public void parseCommentWithUnresolvablePlainLink() {
    Comment comment = parser.parseComment("A {@linkplain link}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>A link</p>");
  }

  @Test
  public void parseCommentWithTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parser.parseComment("A link to {@link foo.Bar foo}.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>A link to <a href=\"/path/to/foo\"><code>foo</code></a>.</p>");
  }

  @Test
  public void parseCommentWithTypeLinkWhereLinkTextStartsOnNewline() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parser.parseComment("A link to {@link foo.Bar\nfoo}.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>A link to <a href=\"/path/to/foo\"><code>foo</code></a>.</p>");
  }

  @Test
  public void parseCommentWithTypeLinkWithTextSpanningMultipleLines() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parser.parseComment("A link to {@link foo.Bar foo\nbar}.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>A link to <a href=\"/path/to/foo\"><code>foo\nbar</code></a>.</p>");
  }

  @Test
  public void parseCommentWithPlainTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parser.parseComment("A link to {@linkplain foo.Bar foo}.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>A link to <a href=\"/path/to/foo\">foo</a>.</p>");
  }

  @Test
  public void doesNotTreatUnderscoresWithinAWordAsEmphasisBlocks() {
    Comment comment = parser.parseComment("Description references CONSTANT_ENUM_VALUE.", mockLinker);
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
    Comment comment = parser.parseComment(
        type.getJsdoc().getBlockComment(),
        mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>This is a class comment.</p>\n" +
            "<ul>\n" +
            "<li>with\nlist</li>\n" +
            "<li>items</li>\n" +
            "</ul>\n<p>And a</p>\n" +
            "<pre><code> code block\n" +
            "</code></pre>");
  }

  @Test
  public void parseCommentWithNoLeadingStars() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "",
        "/**",
        "This is a class comment.",
        "",
        "- This is a list item",
        "",
        "and this",
        "",
        "    is a code block",
        "",
        "@constructor",
        " */",
        "foo.Bar = function() {};");

    NominalType type = checkNotNull(typeRegistry.getNominalType("foo.Bar"));
    Comment comment = parser.parseComment(
        type.getJsdoc().getBlockComment(),
        mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>This is a class comment.</p>\n" +
            "<ul>\n" +
            "<li>This is a list item</li>\n" +
            "</ul>\n" +
            "<p>and this</p>\n" +
            "<pre><code>is a code block\n" +
            "</code></pre>");
  }

  @Test
  public void escapesHtmlCommentWithinCodeTaglet() {
    Comment comment = parser.parseComment("{@code <em>Hello</em>}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p><code>&lt;em&gt;Hello&lt;/em&gt;</code></p>");
  }

  @Test
  public void escapesHtmlCommentWithinLiteralTaglet() {
    Comment comment = parser.parseComment("{@literal <em>Hello</em>}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<p>&lt;em&gt;Hello&lt;/em&gt;</p>");
  }

  @Test
  public void requiresBlankLineBetweenParagraphsBeforeOpeningACodeBlock() {
    Comment comment = parser.parseComment("A promise for a\n    file path.\n\n    code now", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>A promise for a\nfile path.</p>\n" +
            "<pre><code>code now\n" +
            "</code></pre>");
  }

  @Test
  public void parsesCommentWithMarkdownTable() {
    Comment comment = parser.parseComment(
        "Leading paragraph.\n" +
            "\n" +
            "|  a  |  b  |  c  |\n" +
            "| --- | --- | --- |\n" +
            "|  d  |  e  |  f  |\n",
        mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0),
        "<p>Leading paragraph.</p>\n" +
            "<table>\n" +
            "<thead>\n" +
            "<tr><th>a</th><th>b</th><th>c</th></tr>\n" +
            "</thead>\n" +
            "<tbody>\n" +
            "<tr><td>d</td><td>e</td><td>f</td></tr>\n" +
            "</tbody>\n" +
            "</table>\n");
  }

  private static void assertHtmlText(Comment.Token token, String text) {
    if (!text.endsWith("\n")) {
      text += "\n";
    }
    assertEquals(text, token.getHtml());
    assertEquals("", token.getHref());
  }
}