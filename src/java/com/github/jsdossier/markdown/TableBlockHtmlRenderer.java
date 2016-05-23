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

package com.github.jsdossier.markdown;

import com.google.common.collect.ImmutableSet;

import org.commonmark.html.HtmlWriter;
import org.commonmark.html.renderer.NodeRenderer;
import org.commonmark.html.renderer.NodeRendererContext;
import org.commonmark.node.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Renders a {@link TableBlock} to HTML.
 */
final class TableBlockHtmlRenderer implements NodeRenderer {

  private final NodeRendererContext context;

  TableBlockHtmlRenderer(NodeRendererContext context) {
    this.context = context;
  }

  @Override
  public Set<Class<? extends Node>> getNodeTypes() {
    return ImmutableSet.of(
        TableBlock.class, TableCaptionNode.class, TableHeadNode.class, TableBodyNode.class,
        TableRowNode.class, TableCellNode.class);
  }

  @Override
  public void render(Node node) {
    if (node instanceof TableBlock) {
      renderBlock(node, "table");

    } else if (node instanceof TableCaptionNode) {
      renderBlock(node, "caption");

    } else if (node instanceof TableHeadNode) {
      renderBlock(node, "thead");

    } else if (node instanceof TableBodyNode) {
      renderBlock(node, "tbody");

    } else if (node instanceof TableRowNode) {
      renderBlock(node, "tr");

    } else if (node instanceof TableCellNode) {
      renderCell((TableCellNode) node);
    }
  }

  private void renderBlock(Node node, String tagName) {
    HtmlWriter writer = context.getHtmlWriter();
    writer.line();
    writer.tag(tagName);
    renderChildren(node);
    writer.tag("/" + tagName);
    writer.line();
  }

  private void renderCell(TableCellNode cell) {
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
    context.getHtmlWriter().tag(tag, attributes);
    renderChildren(cell);
    context.getHtmlWriter().tag("/" + tag);
  }

  private void renderChildren(Node node) {
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      context.render(child);
    }
  }
}
