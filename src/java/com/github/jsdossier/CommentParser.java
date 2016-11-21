/*
 Copyright 2013-2016 Jason Leyba

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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.github.jsdossier.markdown.MarkdownTableExtension;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.soy.Renderer;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.escape.CharEscaperBuilder;
import com.google.common.escape.Escaper;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.google.common.html.types.SafeUrls;
import org.commonmark.Extension;
import org.commonmark.html.HtmlRenderer;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.owasp.html.HtmlChangeListener;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Utility class for working with JSDoc comments.
 */
public class CommentParser {

  private static final Logger log = Logger.getLogger(CommentParser.class.getName());

  private static Escaper HTML_ESCAPER = new CharEscaperBuilder()
      .addEscape('"', "&quot;")
      .addEscape('\'', "&#39;")
      .addEscape('&', "&amp;")
      .addEscape('<', "&lt;")
      .addEscape('>', "&gt;")
      .toEscaper();

  private static final Pattern HTML_TITLE = Pattern.compile(
      "[\\p{L}\\p{N}\\s\\-_',:\\[\\]!\\./\\\\\\(\\)&]*");
  private static final Pattern HTML4_ID = Pattern.compile("[A-Za-z][A-Za-z0-9\\-_:\\.]*");
  private static final Pattern NUMBER = Pattern.compile("[0-9]+");
  private static final Pattern NUMBER_OR_PERCENT = Pattern.compile("[0-9]+%?");
  private static final Pattern ALIGN = Pattern.compile("(?i)center|left|right|justify|char");
  private static final Pattern VALIGN = Pattern.compile("(?i)baseline|bottom|middle|top");
  private static final Pattern HTML_DIR = Pattern.compile("(?i)ltr|rtl|auto");
  private static final Pattern LANGUAGE_INFO = Pattern.compile("language-[A-Za-z]+");

  private static final PolicyFactory HTML_POLICY = new HtmlPolicyBuilder()
      .allowElements(
          "a",
          "h1", "h2", "h3", "h4", "h5", "h6",
          "p", "div", "span", "blockquote", "pre",
          "b", "i", "strong", "em", "tt", "code", "ins", "del", "sup", "sub", "kbd", "samp", "q",
          "var", "cite", "strike", "center",
          "hr", "br", "wbr",
          "ul", "ol", "li", "dd", "dt", "dl",
          "table", "caption", "tbody", "thead", "tfoot", "td", "th", "tr", "colgroup", "col")
      .allowStandardUrlProtocols()
      .allowAttributes("id", "name").matching(HTML4_ID).globally()
      .allowAttributes("title").matching(HTML_TITLE).globally()
      .allowAttributes("dir").matching(HTML_DIR).globally()
      .allowAttributes("lang").matching(Pattern.compile("[a-zA-Z]{2,20}")).globally()
      .allowAttributes("href").onElements("a")
      .allowAttributes("data-json").onElements("a")
      .allowAttributes("border", "cellpadding", "cellspacing").matching(NUMBER).onElements("table")
      .allowAttributes("colspan").matching(NUMBER).onElements("td", "th")
      .allowAttributes("nowrap").onElements("td", "th")
      .allowAttributes("height", "width").matching(NUMBER_OR_PERCENT).onElements(
          "table", "td", "th", "tr")
      .allowAttributes("align").matching(ALIGN).onElements(
          "thead", "tbody", "tfoot", "td", "th", "tr", "colgroup", "col")
      .allowAttributes("valign").matching(VALIGN).onElements(
          "thead", "tbody", "tfoot", "td", "th", "tr", "colgroup", "col")
      .allowAttributes("charoff").matching(NUMBER_OR_PERCENT).onElements(
          "td", "th", "tr", "colgroup", "col", "thead", "tbody", "tfoot")
      .allowAttributes("colspan", "rowspan").matching(NUMBER).onElements("td", "th")
      .allowAttributes("span", "width").matching(NUMBER_OR_PERCENT).onElements("colgroup", "col")
      .allowAttributes("cite").onElements("q")
      .allowAttributes("class").matching(LANGUAGE_INFO).onElements("code")
      .allowAttributes("start").matching(NUMBER).onElements("ol")
      .toFactory()
      .and(Sanitizers.BLOCKS)
      .and(Sanitizers.FORMATTING)
      .and(Sanitizers.IMAGES);

  private static final Pattern TAGLET_START_PATTERN = Pattern.compile("\\{@(\\w+)\\s");

  private final Renderer soyRenderer;

  private final List<? extends Extension> extensions =
      ImmutableList.of(new MarkdownTableExtension());
  private final Parser parser = Parser.builder()
      .extensions(extensions)
      .build();
  private final HtmlRenderer renderer = HtmlRenderer.builder()
      .escapeHtml(false)
      .extensions(extensions)
      .build();

  @Inject
  CommentParser(Renderer soyRenderer) {
    this.soyRenderer = soyRenderer;
  }

  /**
   * Parses the {@code text} of a JSDoc block comment.
   */
  public Comment parseComment(String text, LinkFactory factory) {
    String html = parseMarkdown(text, factory);
    if (html.isEmpty()) {
      return Comment.getDefaultInstance();
    }
    return Comment.newBuilder()
        .addToken(Comment.Token.newBuilder().setHtml(html))
        .build();
  }

  private String parseMarkdown(String text, LinkFactory linkFactory) {
    if (isNullOrEmpty(text)) {
      return "";
    }

    StringBuilder builder = new StringBuilder(text.length());

    int start = 0;
    while (true) {
      int tagletStart = findInlineTagStart(text, start);
      if (tagletStart == -1) {
        if (start < text.length()) {
          builder.append(text.substring(start));
        }
        break;
      } else if (tagletStart > start) {
        builder.append(text.substring(start, tagletStart));
      }

      int tagletEnd = findInlineTagEnd(text, tagletStart + 1);
      if (tagletEnd == -1) {
        builder.append(text.substring(start));
        break;
      }

      String tagletName = getTagletName(text, tagletStart);
      String tagletPrefix = "{@" + tagletName + " ";
      String tagletText = text.substring(tagletStart + tagletPrefix.length(), tagletEnd);
      switch (tagletName) {
        case "code":
          builder.append("<code>").append(HTML_ESCAPER.escape(tagletText)).append("</code>");
          break;

        case "link":
        case "linkplain":
          LinkInfo info = LinkInfo.fromText(tagletText);
          @Nullable NamedType type = linkFactory.resolveTypeReference(info.type);

          String linkText = info.text;
          boolean codeLink = "link".equals(tagletName);

          if (type == null
              || SafeUrls.fromProto(type.getLink().getHref()).getSafeUrlString().isEmpty()) {
            if (codeLink) {
              linkText = "<code>" + linkText + "</code>";
            }
            builder.append(linkText);
          } else {
            try {
              soyRenderer.render(builder, linkText, type.getLink(), codeLink);
            } catch (IOException e) {
              throw new AssertionError("should never happen", e);
            }
          }
          break;

        case "literal":
        default:
          builder.append(HTML_ESCAPER.escape(tagletText));
      }
      start = tagletEnd + 1;
    }

    String markdown = builder.toString();
    Node root = parser.parse(markdown);
    return sanitize(renderer.render(root));
  }

  private static String sanitize(final String html) {
    return HTML_POLICY.sanitize(html, new HtmlChangeListener<Void>() {
      @Override
      public void discardedTag(Void context, String elementName) {
        log.warning("Discarded tag \"" + elementName + "\" in text:\n" + html);
      }

      @Override
      public void discardedAttributes(
          Void context, String tagName, String... attributeNames) {
        log.warning("In tag \"" + tagName + "\", removed attributes: [\""
            + Joiner.on("\", \"").join(attributeNames) + "\"], from text:\n" + html);
      }
    }, null);
  }

  private static int findInlineTagStart(String text, int start) {
    Matcher matcher = TAGLET_START_PATTERN.matcher(text);
    if (!matcher.find(start)) {
      return -1;
    } else if (text.indexOf('}', matcher.start()) == -1) {
      return -1;
    } else {
      return matcher.start();
    }
  }

  private static String getTagletName(String text, int start) {
    Matcher matcher = TAGLET_START_PATTERN.matcher(text);
    checkArgument(matcher.find(start));
    return matcher.group(1);
  }

  private static int findInlineTagEnd(String text, int start) {
    int end = text.indexOf('}', start);
    if (end == -1) {
      return -1;
    }

    int nestedOpen = text.indexOf('{', start);
    if (nestedOpen != -1 && nestedOpen < end) {
      int nestedClose = findInlineTagEnd(text, nestedOpen + 1);
      if (nestedClose == -1) {
        return -1;
      }
      return findInlineTagEnd(text, nestedClose + 1);
    }

    return end;
  }

  private static class LinkInfo {

    private final String type;
    private final String text;

    private LinkInfo(String type, String text) {
      this.type = type;
      this.text = text;
    }

    private static Pattern SEPARATOR = Pattern.compile("\\s");

    static LinkInfo fromText(String text) {
      String linkedType = text;
      String linkText = text;
      Matcher matcher = SEPARATOR.matcher(text);
      if (matcher.find()) {
        int index = matcher.start();
        linkedType = text.substring(0, matcher.start());
        linkText = text.substring(index + 1);
      }
      return new LinkInfo(linkedType, linkText);
    }
  }
}
