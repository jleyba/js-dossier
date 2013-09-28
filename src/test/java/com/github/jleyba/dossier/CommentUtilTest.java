package com.github.jleyba.dossier;

import static com.github.jleyba.dossier.CommentUtil.formatCommentText;
import static com.github.jleyba.dossier.CommentUtil.getSummary;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommentUtilTest {

  private Linker mockLinker;

  @Before
  public void setUp() {
    mockLinker = mock(Linker.class);
  }

  @Test
  public void getSummaryWhenEntireCommentIsSummary() {
    String comment = "hello, world.";
    assertEquals(comment, getSummary(comment));

    comment = "nothing left";
    assertEquals(comment, getSummary(comment));
  }

  @Test
  public void getSummaryFromMultipleSentences() {
    assertEquals("Hello, world.", getSummary("Hello, world. Goodbye, world."));
  }

  @Test
  public void getSummaryFromMultipleLines() {
    assertEquals("Hello, world.", getSummary("Hello, world.\nGoodbye, world."));
  }

  @Test
  public void getSummaryFromMultipleTabs() {
    assertEquals("Hello, world.", getSummary("Hello, world.\tGoodbye, world."));
  }

  @Test
  public void getSummaryWithInlineTag() {
    assertEquals("Hello, {@code world.",
        getSummary("Hello, {@code world. }Goodbye, world."));
  }

  @Test
  public void formatCommentWithUnterminatedInlineTaglet() {
    assertEquals("Hello {@code world", formatCommentText(mockLinker, "Hello {@code world"));
  }

  @Test
  public void formatCommentWithInlineCode() {
    assertEquals("Hello <code>world</code>", formatCommentText(mockLinker, "Hello {@code world}"));
  }

  @Test
  public void formatCommentWithInlineCodeAtBeginning() {
    assertEquals("<code>Hello</code>world", formatCommentText(mockLinker, "{@code Hello}world"));
  }

  @Test
  public void formatCommentWithAdjacentInlineCodeTaglets() {
    assertEquals("<code>Hello</code><code>world</code>",
        formatCommentText(mockLinker, "{@code Hello}{@code world}"));
  }

  @Test
  public void formatInlineLiteral() {
    assertEquals("red &amp; green", formatCommentText(mockLinker, "red {@literal &} green"));
  }

  @Test
  public void formatNestedInlineCode() {
    assertEquals("<code>{@code hi}</code>", formatCommentText(mockLinker, "{@code {@code hi}}"));
  }

  @Test
  public void formatLiteralInsideCode() {
    assertEquals("<code>a {@literal b} c</code>",
        formatCommentText(mockLinker, "{@code a {@literal b} c}"));
  }

  @Test
  public void formatCodeInsideLiteral() {
    assertEquals("a {@code b} c", formatCommentText(mockLinker, "{@literal a {@code b} c}"));
  }

  @Test
  public void formattingUnresolvedLinks() {
    assertEquals("A <code><a class=\"unresolved\">link</a></code>",
        formatCommentText(mockLinker, "A {@link link}"));
  }

  @Test
  public void formattingUnresolvedPlainLinks() {
    assertEquals("A <a class=\"unresolved\">link</a>",
        formatCommentText(mockLinker, "A {@linkplain link}"));
  }

  @Test
  public void formattingTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn("/path/to/foo");

    assertEquals(
        "A link to <code><a href=\"/path/to/foo\">foo</a></code>.",
        formatCommentText(mockLinker, "A link to {@link foo.Bar foo}."));
  }
}