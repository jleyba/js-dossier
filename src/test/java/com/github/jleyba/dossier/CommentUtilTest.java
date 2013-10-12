package com.github.jleyba.dossier;

import static com.github.jleyba.dossier.CommentUtil.getSummary;
import static com.github.jleyba.dossier.CommentUtil.parseComment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jleyba.dossier.proto.Dossier.Comment;
import com.github.jleyba.dossier.proto.Dossier.Comment.Token;
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
  public void parseCommentWithUnterminatedInlineTaglet() {
    Comment comment = parseComment("Hello {@code world", mockLinker);
    assertPlainText(Iterables.getOnlyElement(comment.getTokenList()),
        "Hello {@code world");
  }

  @Test
  public void parseCommentWithCodeTaglet() {
    Comment comment = parseComment("Hello {@code world}", mockLinker);
    assertEquals(2, comment.getTokenCount());
    assertPlainText(comment.getToken(0), "Hello ");
    assertCode(comment.getToken(1), "world");
  }

  @Test
  public void parseCommentWithInlineCodeAtBeginning() {
    Comment comment = parseComment("{@code Hello} world", mockLinker);
    assertEquals(2, comment.getTokenCount());
    assertCode(comment.getToken(0), "Hello");
    assertPlainText(comment.getToken(1), " world");
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
    assertPlainText(comment.getToken(0), "red ");
    assertLiteral(comment.getToken(1), "&");
    assertPlainText(comment.getToken(2), " green");
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
    assertPlainText(comment.getToken(0), "A ");
    assertUnresolvedCodeLink(comment.getToken(1), "link");
  }

  @Test
  public void parseCommentWithUnresolvablePlainLink() {
    Comment comment = parseComment("A {@linkplain link}", mockLinker);
    assertEquals(2, comment.getTokenCount());
    assertPlainText(comment.getToken(0), "A ");
    assertUnresolvedPlainLink(comment.getToken(1), "link");
  }

  @Test
  public void parseCommentWithTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn("/path/to/foo");

    Comment comment = parseComment("A link to {@link foo.Bar foo}.", mockLinker);
    assertEquals(3, comment.getTokenCount());
    assertPlainText(comment.getToken(0), "A link to ");
    assertCodeLink(comment.getToken(1), "/path/to/foo", "foo");
    assertPlainText(comment.getToken(2), ".");
  }

  @Test
  public void parseCommentWithPlainTypeLinks() {
    when(mockLinker.getLink("foo.Bar")).thenReturn("/path/to/foo");

    Comment comment = parseComment("A link to {@linkplain foo.Bar foo}.", mockLinker);
    assertEquals(3, comment.getTokenCount());
    assertPlainText(comment.getToken(0), "A link to ");
    assertPlainLink(comment.getToken(1), "/path/to/foo", "foo");
    assertPlainText(comment.getToken(2), ".");
  }

  private static void assertPlainText(Token token, String text) {
    assertEquals(text, token.getText());
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