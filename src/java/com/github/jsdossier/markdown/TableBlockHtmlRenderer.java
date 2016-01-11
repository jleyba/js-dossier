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

import org.commonmark.html.CustomHtmlRenderer;
import org.commonmark.html.HtmlWriter;
import org.commonmark.node.Node;
import org.commonmark.node.Visitor;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders a {@link TableBlock} to HTML.
 */
final class TableBlockHtmlRenderer implements CustomHtmlRenderer {
  @Override
  public boolean render(Node node, HtmlWriter writer, Visitor visitor) {
    if (node instanceof TableBlock) {
      renderBlock(node, writer, visitor, "table");

    } else if (node instanceof TableCaptionNode) {
      renderBlock(node, writer, visitor, "caption");

    } else if (node instanceof TableHeadNode) {
      renderBlock(node, writer, visitor, "thead");

    } else if (node instanceof TableBodyNode) {
      renderBlock(node, writer, visitor, "tbody");

    } else if (node instanceof TableRowNode) {
      renderBlock(node, writer, visitor, "tr");

    } else if (node instanceof TableCellNode) {
      renderCell((TableCellNode) node, writer, visitor);

    } else {
      return false;
    }

    return true;
  }

  private void renderBlock(Node node, HtmlWriter writer, Visitor visitor, String tagName) {
    writer.line();
    writer.tag(tagName);
    renderChildren(node, visitor);
    writer.tag("/" + tagName);
    writer.line();
  }

  private void renderCell(TableCellNode cell, HtmlWriter writer, Visitor visitor) {
    Map<String, String> attributes = new HashMap<>();
    if (cell.getColSpan() > 1) {
      attributes.put("colspan", String.valueOf(cell.getColSpan()));
    }
    if (cell.getAlignment() != Alignment.NONE) {
      attributes.put("align", cell.getAlignment().name().toLowerCase());
    }

    Node parent = cell.getParent();
    boolean isHead = parent != null && parent.getParent() instanceof TableHeadNode;
    String tag = isHead ? "th" : "td";
    writer.tag(tag, attributes);
    renderChildren(cell, visitor);
    writer.tag("/" + tag);
  }

  private void renderChildren(Node node, Visitor visitor) {
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      child.accept(visitor);
    }
  }
}
