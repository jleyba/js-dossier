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

package com.github.jsdossier.markdown;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.commonmark.Extension;
import org.commonmark.html.HtmlRenderer;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Tests for {@link MarkdownTableExtension}.
 */
@RunWith(JUnit4.class)
public class MarkdownTableExtensionTest {
  private static final List<? extends Extension> EXTENSIONS =
      ImmutableList.of(new MarkdownTableExtension());
  private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
  private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
      .escapeHtml(false)
      .extensions(EXTENSIONS)
      .build();

  @Test
  public void requiresASeparatorLine() {
    assertHtml("A|B", "<p>A|B</p>");
    assertHtml("|A|B", "<p>|A|B</p>");
    assertHtml("|A|B|", "<p>|A|B|</p>");
    assertHtml("  |A|B|", "<p>|A|B|</p>");
    assertHtml("A  |    B", "<p>A  |    B</p>");
    assertHtml("|   A   |   B    ", "<p>|   A   |   B</p>");
  }

  @Test
  public void firstLineMayHaveUpTo3SpacesBeforeFirstCharacter() {
    String html = Joiner.on('\n').join(
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");

    assertHtml("A|B\n-|-", html);
    assertHtml(" A|B\n-|-", html);
    assertHtml("  A|B\n-|-", html);
    assertHtml("   A|B\n-|-", html);
    assertHtml("    A|B\n-|-",
        "<pre><code>A|B",
        "</code></pre>",
        "<p>-|-</p>");
  }

  @Test
  public void separatorLineMayHaveUpTo3SpacesBeforeFirstCharacter() {
    String html = Joiner.on('\n').join(
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");

    assertHtml("A|B\n-|-", html);
    assertHtml("A|B\n -|-", html);
    assertHtml("A|B\n  -|-", html);
    assertHtml("A|B\n   -|-", html);
    assertHtml("A|B\n    -|-", "<p>A|B\n-|-</p>");

    assertHtml("A|B\n|-|-", html);
    assertHtml("A|B\n |-|-", html);
    assertHtml("A|B\n  |-|-", html);
    assertHtml("A|B\n   |-|-", html);
    assertHtml("A|B\n    |-|-", "<p>A|B\n|-|-</p>");
  }

  @Test
  public void singleColumnRequiresAtLeastOnePipe() {
    String html = Joiner.on('\n').join(
        "<table>",
        "<thead>",
        "<tr><th>A</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");

    assertHtml("|A\n-", "<h2>|A</h2>");
    assertHtml("A\n-|", "<p>A\n-|</p>");

    assertHtml("|A\n|-", html);
    assertHtml("A|\n|-", html);
    assertHtml("|A|\n|-", html);

    assertHtml("|A\n|-", html);
    assertHtml("|A\n-|", html);
    assertHtml("|A\n|-|", html);

    assertHtml("|A|\n|-", html);
    assertHtml("|A|\n-|", html);
    assertHtml("|A|\n|-|", html);
  }

  @Test
  public void singleColumnWithBody() {
    String html = Joiner.on('\n').join(
        "<table>",
        "<thead>",
        "<tr><th>A</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>B</td></tr>",
        "</tbody>",
        "</table>");

    assertHtml("A|\n-|\n|B|", html);
    assertHtml("A|\n-|\nB|", html);
    assertHtml("A|\n-|\n|B", html);

    assertHtml("A|\n|-|\n|B|", html);
    assertHtml("A|\n|-|\nB|", html);
    assertHtml("A|\n|-|\n|B", html);

    assertHtml("|A|\n-|\n|B|", html);
    assertHtml("A|\n-|\nB|", html);
    assertHtml("|A\n-|\n|B", html);

    // No pipe for body
    assertHtml("|A\n|-\nbody",
        "<table>",
        "<thead>",
        "<tr><th>A</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>",
        "<p>body</p>");
  }

  @Test
  public void headerCellsCanSpanMultipleColumns() {
    assertHtml("|A|B||C|||\n|-|-|-|-|-|-",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th colspan=\"2\">B</th><th colspan=\"3\">C</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");
  }

  @Test
  public void alignmentAppliesToHeaderCells() {
    assertHtml("A|B|C|D\n-|:-|:-:|-:",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th align=\"left\">B</th>" +
            "<th align=\"center\">C</th><th align=\"right\">D</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");
  }

  @Test
  public void pullsAlignmentFromLeftmostCellInSpan() {
    assertHtml("A||B\n-|-|-:",
        "<table>",
        "<thead>",
        "<tr><th colspan=\"2\">A</th><th align=\"right\">B</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");
  }

  @Test
  public void moreSeparatorsThanHeaderCells() {
    assertHtml("A|B\n-|-|-",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");
  }

  @Test
  public void moreHeaderCellsThanSeparators() {
    assertHtml("A|B|C|D\n-|-",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th><th>C</th><th>D</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");
  }

  @Test
  public void moreHeaderCellsThanSeparatorsWithColspans() {
    assertHtml("A|B|C||D|||\n-|-",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th><th colspan=\"2\">C</th><th colspan=\"3\">D</th></tr>",
        "</thead>",
        "<tbody></tbody>",
        "</table>");
  }

  @Test
  public void twoByTwo() {
    assertHtml("A|B\n-|-\nc|d",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void twoColumnsThreeRows() {
    assertHtml("A|B\n-|-\nc|d\n|e|f|",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "<tr><td>e</td><td>f</td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void aRowWithExtraColumns() {
    assertHtml("A|B\n-|-\nc|d\n|e|f|g|h",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "<tr><td>e</td><td>f</td><td>g</td><td>h</td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void cellsCanHaveWhitespacePadding() {
    String html = Joiner.on('\n').join(
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>");

    assertHtml("A|B\n-|-\nc|d", html);
    assertHtml("A| B\n-|-\nc|d", html);
    assertHtml("A|  B\n-|-\nc|d", html);
    assertHtml("A|   B\n-|-\nc|d", html);
    assertHtml("A|         B\n-|-\nc|d", html);

    assertHtml("A|B\n-|-\nc| d", html);
    assertHtml("A|B\n-|-\nc|  d", html);
    assertHtml("A|B\n-|-\nc|   d", html);
    assertHtml("A|B\n-|-\nc|         d", html);

    assertHtml("A |B\n-|-\nc| d", html);
    assertHtml("A  |B\n-|-\nc| d", html);
    assertHtml("A   |B\n-|-\nc| d", html);

    assertHtml("A|B\n-|-\nc | d", html);
    assertHtml("A|B\n-|-\nc  | d", html);
    assertHtml("A|B\n-|-\nc   | d", html);
    assertHtml("A|B\n-|-\nc    | d", html);
  }

  @Test
  public void leadingCellInBodyMayBeIndentedPastCodeBlockLimit() {
    assertHtml("A|B\n-|-\n         c|d",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void parsesInlineContentWithinCells() {
    assertHtml(
        "*A* | `B`\n --- | ---\n __c__ | ***d***",
        "<table>",
        "<thead>",
        "<tr><th><em>A</em></th><th><code>B</code></th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td><strong>c</strong></td><td><strong><em>d</em></strong></td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void respectsEscapedPipes() {
    assertHtml("A\\||B\\\\|\n-|-\nc|\\|d",
        "<table>",
        "<thead>",
        "<tr><th>A|</th><th>B\\</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>|d</td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void tableInsideBlockQuote() {
    assertHtml("> A|B\n> -|-\n> c|d",
        "<blockquote>",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>",
        "</blockquote>");
  }

  @Test
  public void separatorCanHaveUpToThreeLeadingSpacesInsideBlockQuote() {
    String html = Joiner.on('\n').join(
        "<blockquote>",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>",
        "</blockquote>");

    assertHtml("> A|B\n>-|-\n> c|d", html);
    assertHtml("> A|B\n> -|-\n> c|d", html);
    assertHtml("> A|B\n>  -|-\n> c|d", html);
    assertHtml("> A|B\n>   -|-\n> c|d", html);
    assertHtml("> A|B\n>    -|-\n> c|d", html);

    // Too many spaces on the separator line.
    assertHtml("> A|B\n>     -|-\n> c|d",
        "<blockquote>",
        "<p>A|B",
        "-|-",
        "c|d</p>",
        "</blockquote>");
  }

  @Test
  public void tableInsideNestedBlockQuote() {
    assertHtml("> > A|B\n> > -|-\n> > c|d",
        "<blockquote>",
        "<blockquote>",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>",
        "</blockquote>",
        "</blockquote>");
  }

  @Test
  public void tableInsideList() {
    assertHtml("1. A|B\n   -|-\n   c|d",
        "<ol>",
        "<li>",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>",
        "</li>",
        "</ol>");
  }

  @Test
  public void tableWithCaption() {
    assertHtml("A|B\n-|-\nc|d\n[hello, world]",
        "<table>",
        "<caption>hello, world</caption>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void tableWithCaptionWithInlineFormatting() {
    assertHtml("A|B\n-|-\nc|d\n[*hello*, `world`]",
        "<table>",
        "<caption><em>hello</em>, <code>world</code></caption>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void tableWithCaption_usesLastCaptionEncountered() {
    assertHtml("A|B\n-|-\n[first caption]\nc|d\n[hello, world]\n[final caption]",
        "<table>",
        "<caption>final caption</caption>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>");
  }

  @Test
  public void tableCaptionMayBeIndentedUpToThreeSpaces() {
    String html = Joiner.on('\n').join(
        "<table>",
        "<caption>hello, world</caption>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>");

    assertHtml("A|B\n-|-\nc|d\n[hello, world]", html);
    assertHtml("A|B\n-|-\nc|d\n [hello, world]", html);
    assertHtml("A|B\n-|-\nc|d\n  [hello, world]", html);
    assertHtml("A|B\n-|-\nc|d\n   [hello, world]", html);

    assertHtml("A|B\n-|-\nc|d\n    [hello, world]",
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>c</td><td>d</td></tr>",
        "</tbody>",
        "</table>",
        "<pre><code>[hello, world]",
        "</code></pre>");
  }

  @Test
  public void tableParsingIsConsistentInThePresenceOfLeadingWhitespace() {
    String html = Joiner.on('\n').join(
        "<table>",
        "<thead>",
        "<tr><th>A</th><th>B</th><th>C</th></tr>",
        "</thead>",
        "<tbody>",
        "<tr><td>a</td><td>b</td><td>c</td></tr>",
        "<tr><td>d</td><td colspan=\"2\">e</td></tr>",
        "</tbody>",
        "</table>");

    assertHtml(
        Joiner.on('\n').join(
            "| A | B | C |",
            "| - | - | - |",
            "| a | b | c |",
            "| d |   e  ||"),
        html);

    assertHtml(
        Joiner.on('\n').join(
            " | A | B | C |",
            " | - | - | - |",
            " | a | b | c |",
            " | d |   e  ||"),
        html);

    assertHtml(
        Joiner.on('\n').join(
            "  | A | B | C |",
            "  | - | - | - |",
            "  | a | b | c |",
            "  | d |   e  ||"),
        html);

    assertHtml(
        Joiner.on('\n').join(
            "   | A | B | C |",
            "   | - | - | - |",
            "   | a | b | c |",
            "   | d |   e  ||"),
        html);
  }

  private static void assertHtml(String input, String... output) {
    Node root = PARSER.parse(input);
    String html = RENDERER.render(root).trim();
    assertThat(html).isEqualTo(Joiner.on('\n').join(output));
  }
}
