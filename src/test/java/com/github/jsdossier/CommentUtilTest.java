package com.github.jsdossier;

import static com.github.jsdossier.CommentUtil.getSummary;
import static com.github.jsdossier.CommentUtil.parseComment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jsdossier.proto.Dossier;
import com.github.jsdossier.proto.Dossier.Comment;
import com.github.jsdossier.proto.Dossier.Comment.Token;
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
    assertEquals(2, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "Hello ");
    assertCode(comment.getToken(1), "world");
  }

  @Test
  public void parseCommentWithInlineCodeAtBeginning() {
    Comment comment = parseComment("{@code Hello} world", mockLinker);
    assertEquals(2, comment.getTokenCount());
    assertCode(comment.getToken(0), "Hello");
    assertHtmlText(comment.getToken(1), " world");
  }

  @Test
  public void parseCommentWithAdjacentInlineCodeTaglets() {
    Comment comment = parseComment("{@code Hello}{@code world}", mockLinker);
    assertEquals(2, comment.getTokenCount());
    assertCode(comment.getToken(0), "Hello");
    assertCode(comment.getToken(1), "world");
  }

  @Test
  public void parseLiteralTaglet() {
    Comment comment = parseComment("red {@literal &} green", mockLinker);
    assertEquals(3, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "red ");
    assertLiteral(comment.getToken(1), "&");
    assertHtmlText(comment.getToken(2), " green");
  }

  @Test
  public void parseNestedCodeTaglets() {
    Comment comment = parseComment("{@code {@code hi}}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertCode(comment.getToken(0), "{@code hi}");
  }

  @Test
  public void parseLiteralInsideCode() {
    Comment comment = parseComment("{@code a {@literal b} c}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertCode(comment.getToken(0), "a {@literal b} c");
  }

  @Test
  public void parseCodeInsideLiteral() {
    Comment comment = parseComment("{@literal a {@code b} c}", mockLinker);
    assertEquals(1, comment.getTokenCount());
    assertLiteral(comment.getToken(0), "a {@code b} c");
  }

  @Test
  public void parseCommentWithUnresolvableLink() {
    Comment comment = parseComment("A {@link link}", mockLinker);
    assertEquals(2, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "A ");
    assertUnresolvedCodeLink(comment.getToken(1), "link");
  }

  @Test
  public void parseCommentWithUnresolvablePlainLink() {
    Comment comment = parseComment("A {@linkplain link}", mockLinker);
    assertEquals(2, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "A ");
    assertUnresolvedPlainLink(comment.getToken(1), "link");
  }

  @Test
  public void parseCommentWithTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(Dossier.TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parseComment("A link to {@link foo.Bar foo}.", mockLinker);
    assertEquals(3, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "A link to ");
    assertCodeLink(comment.getToken(1), "/path/to/foo", "foo");
    assertHtmlText(comment.getToken(2), ".");
  }

  @Test
  public void parseCommentWithPlainTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(Dossier.TypeLink.newBuilder()
        .setText("")
        .setHref("/path/to/foo")
        .build());

    Comment comment = parseComment("A link to {@linkplain foo.Bar foo}.", mockLinker);
    assertEquals(3, comment.getTokenCount());
    assertHtmlText(comment.getToken(0), "A link to ");
    assertPlainLink(comment.getToken(1), "/path/to/foo", "foo");
    assertHtmlText(comment.getToken(2), ".");
  }

  private static void assertHtmlText(Token token, String text) {
    assertEquals(text, token.getHtml());
    assertFalse(token.getIsLiteral());
    assertFalse(token.getIsCode());
    assertEquals("", token.getHref());
    assertFalse(token.getUnresolvedLink());
  }

  private static void assertCode(Token token, String text) {
    assertEquals(text, token.getText());
    assertFalse(token.getIsLiteral());
    assertTrue(token.getIsCode());
    assertEquals("", token.getHref());
    assertFalse(token.getUnresolvedLink());
  }

  private static void assertLiteral(Token token, String text) {
    assertEquals(text, token.getText());
    assertTrue(token.getIsLiteral());
    assertFalse(token.getIsCode());
    assertEquals("", token.getHref());
    assertFalse(token.getUnresolvedLink());
  }

  private static void assertCodeLink(Token token, String href, String text) {
    assertEquals(text, token.getText());
    assertFalse(token.getIsLiteral());
    assertTrue(token.getIsCode());
    assertEquals(href, token.getHref());
    assertFalse(token.getUnresolvedLink());
  }

  private static void assertPlainLink(Token token, String href, String text) {
    assertEquals(text, token.getText());
    assertFalse(token.getIsLiteral());
    assertFalse(token.getIsCode());
    assertEquals(href, token.getHref());
    assertFalse(token.getUnresolvedLink());
  }

  private static void assertUnresolvedCodeLink(Token token, String text) {
    assertEquals(text, token.getText());
    assertFalse(token.getIsLiteral());
    assertTrue(token.getIsCode());
    assertEquals("", token.getHref());
    assertTrue(token.getUnresolvedLink());
  }

  private static void assertUnresolvedPlainLink(Token token, String text) {
    assertEquals(text, token.getText());
    assertFalse(token.getIsLiteral());
    assertFalse(token.getIsCode());
    assertEquals("", token.getHref());
    assertTrue(token.getUnresolvedLink());
  }
}