package com.github.jsdossier;

import static com.github.jsdossier.CommentUtil.getSummary;
import static com.github.jsdossier.CommentUtil.parseComment;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.TypeLink;
import com.google.common.collect.Iterables;
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
  public void getSummaryWhenEntireCommentIsSummary_sullSentence() {
    String original = "hello, world.";
    Comment comment = getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), original);

    original = "nothing left";
    comment = getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), original);
  }

  @Test
  public void getSummaryWhenEntireCommentIsSummary_sentenceFragment() {
    String original = "nothing left";
    Comment comment = getSummary(original, mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), original);
  }

  @Test
  public void getSummaryFromMultipleSentences() {
    Comment comment = getSummary("Hello, world. Goodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "Hello, world.");
  }

  @Test
  public void getSummaryFromMultipleLines() {
    Comment comment = getSummary("Hello, world.\nGoodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "Hello, world.");
  }

  @Test
  public void getSummaryFromMultipleTabs() {
    Comment comment = getSummary("Hello, world.\tGoodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "Hello, world.");
  }

  @Test
  public void getSummaryWithInlineTag() {
    Comment comment = getSummary("Hello, {@code world. }Goodbye, world.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "Hello, {@code world.");
  }

  @Test
  public void parseCommentWithUnterminatedInlineTaglet() {
    Comment comment = parseComment("Hello {@code world", mockLinker);
    assertHtmlText(Iterables.getOnlyElement(comment.getTokenList()),
        "Hello {@code world");
  }

  @Test
  public void parseCommentWithCodeTaglet() {
    Comment comment = parseComment("Hello {@code world}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "Hello <code>world</code>");
  }

  @Test
  public void parseCommentWithInlineCodeAtBeginning() {
    Comment comment = parseComment("{@code Hello} world", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<code>Hello</code> world");
  }

  @Test
  public void parseCommentWithAdjacentInlineCodeTaglets() {
    Comment comment = parseComment("{@code Hello}{@code world}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<code>Hello</code><code>world</code>");
  }

  @Test
  public void parseLiteralTaglet() {
    Comment comment = parseComment("red {@literal &} green", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "red &amp; green");
  }

  @Test
  public void parseNestedCodeTaglets() {
    Comment comment = parseComment("{@code {@code hi}}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<code>{@code hi}</code>");
  }

  @Test
  public void parseLiteralInsideCode() {
    Comment comment = parseComment("{@code a {@literal b} c}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "<code>a {@literal b} c</code>");
  }

  @Test
  public void parseCodeInsideLiteral() {
    Comment comment = parseComment("{@literal a {@code b} c}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "a {@code b} c");
  }

  @Test
  public void parseCommentWithUnresolvableLink() {
    Comment comment = parseComment("A {@link link}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "A <code>link</code>");
  }

  @Test
  public void parseCommentWithUnresolvablePlainLink() {
    Comment comment = parseComment("A {@linkplain link}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "A link");
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
        "A link to <code><a href=\"/path/to/foo\">foo</a></code>.");
  }

  @Test
  public void parseCommentWithPlainTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parseComment("A link to {@linkplain foo.Bar foo}.", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "A link to <a href=\"/path/to/foo\">foo</a>.");
  }

  private static void assertHtmlText(Comment.Token token, String text) {
    assertEquals(text, token.getHtml());
    assertEquals("", token.getHref());
  }
}