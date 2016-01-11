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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.newArrayList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.commonmark.node.Block;
import org.commonmark.node.Node;
import org.commonmark.parser.InlineParser;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jleyba on 1/1/16.
 */
final class TableBlockParser extends AbstractBlockParser {

  private static final Pattern HEADER_SEPARATOR = Pattern.compile(
      "^ {0,3}(?:" +
          "(?<single>(?:\\|\\s*:?-+:?\\s*\\|?|:?-+:?\\s*\\|))" +
          "|" +
          "(?:\\|\\s*)?" +
          "(?<first>:?-+:?)" +
          "(?<rest>(?:\\s*\\|\\s*:?-+:?)+)" +
          "\\s*\\|?)\\s*$");

  private static final Pattern CAPTION_LINE = Pattern.compile(
      "^ {0,3}\\[(?<content>.*)\\]\\s*$");

  private static final Splitter COLUMN_SPLITTER =
      Splitter.on('|').trimResults().omitEmptyStrings();

  private final ImmutableList<Alignment> columns;
  private final CharSequence headerRow;
  private final List<CharSequence> rowData = new ArrayList<>();
  private final TableBlock block = new TableBlock();

  private TableBlockParser(CharSequence firstRow, ImmutableList<Alignment> columns) {
    this.columns = columns;
    this.headerRow = firstRow;
  }

  @Override
  public Block getBlock() {
    return block;
  }

  @Override
  public BlockContinue tryContinue(ParserState state) {
    if (state.getLine().toString().contains("|")
        || CAPTION_LINE.matcher(state.getLine()).matches()) {
      return BlockContinue.atIndex(state.getIndex());
    } else {
      return BlockContinue.none();
    }
  }

  @Override
  public void addLine(CharSequence line) {
    rowData.add(line);
  }

  @Override
  public void parseInlines(InlineParser inlineParser) {
    Node headNode = new TableHeadNode();
    block.appendChild(headNode);
    headNode.appendChild(parseRow(headerRow.toString(), inlineParser));

    // The first row of data is always the column alignments, which we've already parsed.
    Node bodyNode =new TableBodyNode();
    block.appendChild(bodyNode);
    String caption = null;
    for (CharSequence line : Iterables.skip(rowData, 1)) {
      Matcher captionMatcher = CAPTION_LINE.matcher(line);
      if (captionMatcher.matches()) {
        caption = captionMatcher.group("content").trim();
      } else {
        TableRowNode row = parseRow(line.toString(), inlineParser);
        bodyNode.appendChild(row);
      }
    }

    if (!isNullOrEmpty(caption)) {
      TableCaptionNode captionNode = new TableCaptionNode();
      headNode.insertBefore(captionNode);
      inlineParser.parse(caption.trim(), captionNode);
    }
  }

  private TableRowNode parseRow(String rowStr, InlineParser inlineParser) {
    rowStr = rowStr.trim();
    if (rowStr.startsWith("|")) {
      rowStr = rowStr.substring(1);
    }

    TableRowNode row = new TableRowNode();

    boolean isEscaped = false;
    int currentColumn = 0;
    CharBuffer data = CharBuffer.wrap(rowStr);
    for (int index = 0; data.hasRemaining(); index++) {
      if (isEscaped) {
        isEscaped = false;
        continue;
      }

      char c = data.charAt(index);
      if (c == '\\') {
        isEscaped = true;
        continue;
      }

      if (c == '|' || (index + 1) >= data.remaining()) {
        int end = c == '|' ? index : index + 1;
        String content = data.subSequence(0, end).toString();
        data.position(data.position() + end);

        int colSpan = 0;
        while (data.hasRemaining() && data.charAt(0) == '|') {
          colSpan++;
          data.position(data.position() + 1);
        }
        index = -1;  // Account for post-forloop increment.
        Alignment alignment = currentColumn < columns.size()
            ? columns.get(currentColumn)
            : Alignment.NONE;
        currentColumn += colSpan;

        TableCellNode cell = new TableCellNode(colSpan, alignment);
        inlineParser.parse(content.trim(), cell);
        row.appendChild(cell);
      }
    }

    return row;
  }

  public static class Factory extends AbstractBlockParserFactory {

    @Override
    public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
      CharSequence line = state.getLine();
      CharSequence previousLine = matchedBlockParser.getParagraphStartLine();
      if (previousLine != null && previousLine.toString().contains("|")) {
        line = line.subSequence(state.getIndex(), line.length());
        ImmutableList<Alignment> columnAlignments = parseHeaderDivider(line);
        if (!columnAlignments.isEmpty()) {
          return BlockStart.of(new TableBlockParser(previousLine, columnAlignments))
              .atIndex(state.getIndex())
              .replaceActiveBlockParser();
        }
      }

      return BlockStart.none();
    }
  }

  @VisibleForTesting
  static ImmutableList<Alignment> parseHeaderDivider(CharSequence data) {
    Matcher matcher = HEADER_SEPARATOR.matcher(data);
    if (!matcher.matches()) {
      return ImmutableList.of();
    }

    String singleColumn = matcher.group("single");
    if (singleColumn != null) {
      if (singleColumn.startsWith("|")) {
        singleColumn = singleColumn.substring(1);
      }
      if (singleColumn.endsWith("|")) {
        singleColumn = singleColumn.substring(0, singleColumn.length() - 1);
      }
      return ImmutableList.of(parseAlignment(singleColumn.trim()));
    }

    List<Alignment> columns = new ArrayList<>();

    String first = matcher.group("first");
    if (isNullOrEmpty(first)) {
      return ImmutableList.of();
    }
    columns.add(parseAlignment(first));

    String rest = matcher.group("rest");
    if (!isNullOrEmpty(rest)) {
      for (String column : COLUMN_SPLITTER.split(rest)) {
        columns.add(parseAlignment(column));
      }
    }

    return ImmutableList.copyOf(columns);
  }

  private static Alignment parseAlignment(String data) {
    Alignment alignment = Alignment.NONE;
    if (data.startsWith(":")) {
      alignment = Alignment.LEFT;
    }
    if (data.endsWith(":")) {
      alignment = alignment == Alignment.NONE ? Alignment.RIGHT : Alignment.CENTER;
    }
    return alignment;
  }
}
