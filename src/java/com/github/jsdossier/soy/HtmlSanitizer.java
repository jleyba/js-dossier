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

package com.github.jsdossier.soy;

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
  private static final Pattern HTML4_ID = Pattern.compile("[A-Za-z][A-Za-z0-9\\-_:\\.]*");
  private static final Pattern NUMBER = Pattern.compile("[0-9]+");
  private static final Pattern NUMBER_OR_PERCENT = Pattern.compile("[0-9]+%?");
  private static final Pattern ALIGN = Pattern.compile("(?i)center|left|right|justify|char");
  private static final Pattern VALIGN = Pattern.compile("(?i)baseline|bottom|middle|top");
  private static final Pattern HTML_DIR = Pattern.compile("(?i)ltr|rtl|auto");

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
      .toFactory()
      .and(Sanitizers.BLOCKS)
      .and(Sanitizers.FORMATTING)
      .and(Sanitizers.IMAGES);

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
