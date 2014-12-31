package com.github.jleyba.dossier.soy;

import com.google.common.base.Joiner;
import org.owasp.html.HtmlChangeListener;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.logging.Logger;
import java.util.regex.Pattern;

final class HtmlSanitizer {
  private static final Pattern HTML_TITLE = Pattern.compile(
      "[\\p{L}\\p{N}\\s\\-_',:\\[\\]!\\./\\\\\\(\\)&]*");

  private static final PolicyFactory HTML_POLICY = new HtmlPolicyBuilder()
      .allowElements(
          "a",
          "h1", "h2", "h3", "h4", "h5", "h6",
          "p", "i", "b", "u", "strong", "em", "small", "big", "pre", "code",
          "cite", "samp", "sub", "sup", "strike", "center", "blockquote",
          "hr", "br", "col", "font", "map", "span", "div", "img",
          "ul", "ol", "li", "dd", "dt", "dl",
          "tbody", "thead", "tfoot", "table", "td", "th", "tr", "colgroup")
      .allowAttributes("title").matching(HTML_TITLE).globally()
      .toFactory()
      .and(Sanitizers.BLOCKS)
      .and(Sanitizers.FORMATTING)
      .and(Sanitizers.IMAGES)
      .and(Sanitizers.LINKS);

  private static final Logger log = Logger.getLogger(HtmlSanitizer.class.getName());

  private HtmlSanitizer() {}

  static String sanitize(final String html) {
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
}
