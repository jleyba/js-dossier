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

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Marker;
import com.google.javascript.rhino.JSDocInfo.StringPosition;
import com.google.javascript.rhino.JSTypeExpression;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Wraps a {@link JSDocInfo} object to compensate for the loss of information from
 * compiler's original parsing:
 * <ol>
 *   <li>guarantees parameter info will be returned in order of declaration in the original
 *       comment</li>
 *   <li>preserves whitespace on multi-line comments</li>
 * </ol>
 */
public class JsDoc {

  private final JSDocInfo info;

  private final Map<String, Parameter> parameters = new LinkedHashMap<>();
  private final List<ThrowsClause> throwsClauses = new LinkedList<>();
  private final List<String> seeClauses = new LinkedList<>();

  private String blockComment = "";
  private String returnDescription = "";
  private String defineComment = "";
  private String deprecationReason = "";
  private String fileoverview = "";
  private boolean parsed = false;

  public JsDoc(JSDocInfo info) {
    this.info = checkNotNull(info, "null info");
  }

  @Nullable
  public static JsDoc from(@Nullable JSDocInfo info) {
    return info == null ? null : new JsDoc(info);
  }

  public JSDocInfo getInfo() {
    return info;
  }

  public String getOriginalCommentString() {
    return info.getOriginalCommentString();
  }

  public boolean isConstructor() {
    return info.isConstructor();
  }

  public boolean isInterface() {
    return info.isInterface();
  }

  public boolean isEnum() {
    return info.getEnumParameterType() != null;
  }

  public boolean isDeprecated() {
    return info.isDeprecated();
  }

  public boolean isDefine() {
    return info.isDefine();
  }

  public boolean isConst() {
    return info.isConstant();
  }

  public boolean isFinal() {
    return hasAnnotation(Annotation.FINAL);
  }

  public boolean isDict() {
    return hasAnnotation(Annotation.DICT);
  }

  public boolean isStruct() {
    return hasAnnotation(Annotation.STRUCT);
  }

  public boolean isTypedef() {
    return info.getTypedefType() != null;
  }

  @Nullable
  public JSTypeExpression getType() {
    if (isEnum()) {
      return info.getEnumParameterType();
    } else if (isTypedef()) {
      return info.getTypedefType();
    } else {
      return info.getType();
    }
  }

  public JSDocInfo.Visibility getVisibility() {
    // TODO(jleyba): Properly handle Visibility.INHERITED
    if (info.getVisibility() == JSDocInfo.Visibility.INHERITED) {
      return JSDocInfo.Visibility.PUBLIC;
    }
    return info.getVisibility();
  }

  public List<JSTypeExpression> getExtendedInterfaces() {
    return info.getExtendedInterfaces();
  }

  /**
   * Returns the comment string for the {@literal @fileoverview} annotation. Returns an empty string
   * if the annotation was not present.
   */
  public String getFileoverview() {
    parse();
    return fileoverview;
  }

  /**
   * Returns the block comment listed before any annotations. If this comment does not have a block
   * comment, but has a {@link #getFileoverview()} or {@link #getDefinition()}, then those will be
   * used as the block comment.
   */
  public String getBlockComment() {
    parse();
    return blockComment;
  }

  /**
   * Returns the comment string for the {@literal @define} annotation. Returns an empty string if
   * the annotation was not present.
   */
  public String getDefinition() {
    parse();
    return defineComment;
  }

  public String getDeprecationReason() {
    checkState(isDeprecated());
    parse();
    return deprecationReason;
  }

  public ImmutableList<Parameter> getParameters() {
    parse();
    return ImmutableList.copyOf(parameters.values());
  }

  public boolean hasParameter(String name) {
    parse();
    return parameters.containsKey(name);
  }

  public Parameter getParameter(String name) {
    parse();
    checkArgument(parameters.containsKey(name), "No parameter named %s", name);
    return parameters.get(name);
  }

  @Nullable
  public JSTypeExpression getReturnType() {
    return info.getReturnType();
  }

  public String getReturnDescription() {
    parse();
    return returnDescription;
  }

  public ImmutableList<String> getSeeClauses() {
    parse();
    return ImmutableList.copyOf(seeClauses);
  }

  public ImmutableList<ThrowsClause> getThrowsClauses() {
    parse();
    return ImmutableList.copyOf(throwsClauses);
  }

  public ImmutableList<String> getTemplateTypeNames() {
    return info.getTemplateTypeNames();
  }

  private boolean hasAnnotation(Annotation target) {
    for (Marker marker : info.getMarkers()) {
      Optional<Annotation> annotation = Annotation.forMarker(marker);
      if (target.equals(annotation.orNull())) {
        return true;
      }
    }
    return false;
  }

  public Optional<Marker> getMarker(Annotation target) {
    for (Marker marker : info.getMarkers()) {
      Optional<Annotation> annotation = Annotation.forMarker(marker);
      if (target.equals(annotation.orNull())) {
        return Optional.of(marker);
      }
    }
    return Optional.absent();
  }

  private static final Pattern EOL_PATTERN = Pattern.compile("\r?\n");

  private void parse() {
    if (parsed) {
      return;
    }
    parsed = true;

    String original = Strings.nullToEmpty(info.getOriginalCommentString());
    if (original.isEmpty()) {
      return;
    }

    List<String> lines = Splitter.on(EOL_PATTERN).splitToList(
        original.subSequence(0, original.length() - 2));  // subtract closing */
    Offset firstAnnotation = findFirstAnnotationLine(lines);
    Offset annotationOffset = new Offset(0, 0);
    if (firstAnnotation != null && !info.getMarkers().isEmpty()) {
      blockComment = processBlockCommentLines(Iterables.limit(lines, firstAnnotation.line));

      JSDocInfo.StringPosition firstAnnotationPosition =
          info.getMarkers().iterator().next().getAnnotation();

      annotationOffset = new Offset(
          firstAnnotationPosition.getStartLine() - firstAnnotation.line,
          firstAnnotationPosition.getPositionOnStartLine() - firstAnnotation.column);
    } else {
      blockComment = processBlockCommentLines(lines);
    }

    // If we failed to extract a block comment, yet the original JSDoc has one, we've
    // probably encountered a case where the compiler merged multiple JSDoc comments
    // into one. Try to recover by parsing the compiler's provided block comment.
    if (isNullOrEmpty(blockComment) && !isNullOrEmpty(info.getBlockDescription())) {
      blockComment = processBlockCommentLines(
          Splitter.on('\n').split(info.getBlockDescription()));
    }

    for (JSDocInfo.Marker marker : info.getMarkers()) {
      Optional<Annotation> annotation = Annotation.forMarker(marker);
      if (!annotation.isPresent()) {
        continue;  // Unrecognized/unsupported annotation.
      }

      JSDocInfo.StringPosition description = marker.getDescription();
      if (description == null) {
        continue;
      }

      switch (annotation.get()) {
        case DEFINE:
          defineComment = processDescriptionLines(lines, annotationOffset, description);
          break;
        case DEPRECATED:
          deprecationReason = processDescriptionLines(lines, annotationOffset, description);
          break;
        case FILEOVERVIEW:
          fileoverview = processDescriptionLines(lines, annotationOffset, description);
          break;
        case PARAM:
          String name = marker.getNameNode().getItem().getString();
          parameters.put(name, new Parameter(
              name,
              getJsTypeExpression(marker),
              processDescriptionLines(lines, annotationOffset, description)));
          break;
        case RETURN:
          returnDescription = processDescriptionLines(lines, annotationOffset, description);
          break;
        case SEE:
          seeClauses.add(processDescriptionLines(lines, annotationOffset, description));
          break;
        case THROWS:
          throwsClauses.add(new ThrowsClause(
              getJsTypeExpression(marker),
              processDescriptionLines(lines, annotationOffset, description)));
          break;
      }
    }

    if (isNullOrEmpty(blockComment)) {
      if (!isNullOrEmpty(fileoverview)) {
        blockComment = fileoverview;
      } else if (!isNullOrEmpty(defineComment)) {
        blockComment = defineComment;
      }
    }
  }

  @Nullable
  private JSTypeExpression getJsTypeExpression(JSDocInfo.Marker marker) {
    if (marker.getType() == null) {
      return null;
    }
    return new JSTypeExpression(marker.getType().getItem(), "");
  }

  private static final Pattern STAR_PREFIX = Pattern.compile("^\\s*\\*+\\s?");

  private static int skipChar(String line, int offset, char c) {
    while (offset < line.length() && line.charAt(offset) == c) {
      offset++;
    }
    return offset;
  }

  private static boolean isAlpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  @Nullable
  private static Offset findFirstAnnotationLine(Iterable<String> lines) {
    int lineNum = 0;
    for (Iterator<String> it = lines.iterator(); it.hasNext(); lineNum++) {
      String line = it.next();
      int offset = 0;
      if (lineNum == 0) {
        int start = line.indexOf("/**");
        if (start != -1) {
          offset = start + 3;
        }
      }
      offset = skipChar(line, offset, ' ');
      offset = skipChar(line, offset, '*');
      offset = skipChar(line, offset, ' ');
      if (offset >= line.length() || line.charAt(offset) != '@') {
        continue;
      }
      int startAnnotation = offset;
      offset += 1;
      if (offset >= line.length() || !isAlpha(line.charAt(offset))) {
        continue;
      }
      while (offset < line.length() && isAlpha(line.charAt(offset))) {
        offset += 1;
      }

      StringPosition position = new StringPosition();
      position.setPositionInformation(lineNum, startAnnotation, lineNum, offset);
      return new Offset(lineNum, startAnnotation);
    }
    return null;  // Not found.
  }

  private static String processBlockCommentLines(Iterable<String> lines) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String line : lines) {
      if (first) {
        first = false;
        int index = line.indexOf("/**");
        if (index != -1) {
          line = line.substring(index + 3);
        }
      }
      Matcher matcher = STAR_PREFIX.matcher(line);
      if (matcher.find(0)) {
        line = line.substring(matcher.end());
      }
      builder.append(line).append('\n');
    }
    return builder.toString().trim();
  }

  private String processDescriptionLines(
      List<String> lines, Offset annotationOffset, JSDocInfo.StringPosition position) {
    int startLine = position.getStartLine() - annotationOffset.line;
    int numLines = Math.max(position.getEndLine() - position.getStartLine(), 1);

    Iterable<String> descriptionLines = Iterables.skip(lines, startLine);
    descriptionLines = Iterables.limit(descriptionLines, numLines);

    StringBuilder builder = new StringBuilder();
    boolean isFirst = true;
    for (String line : descriptionLines) {
      if (isFirst) {
        isFirst = false;
        int pos = position.getPositionOnStartLine();
        if (lines.size() == 1) {
          pos -= annotationOffset.column;
        }
        line = line.substring(pos);
      } else {
        Matcher matcher = STAR_PREFIX.matcher(line);
        if (matcher.find(0)) {
          line = line.substring(matcher.end());
        }
      }

      builder.append(line).append('\n');
    }
    return builder.toString().trim();
  }

  public static class ThrowsClause {

    private final Optional<JSTypeExpression> type;
    private final String description;

    private ThrowsClause(@Nullable JSTypeExpression type, String description) {
      this.type = Optional.fromNullable(type);
      this.description = description;
    }

    public Optional<JSTypeExpression> getType() {
      return type;
    }

    public String getDescription() {
      return description;
    }
  }

  public static enum Annotation {
    CONST("const"),
    DEFINE("define"),
    DEPRECATED("deprecated"),
    DICT("dict"),
    FILEOVERVIEW("fileoverview"),
    FINAL("final"),
    PARAM("param"),
    PRIVATE("private"),
    PROTECTED("protected"),
    PUBLIC("public"),
    RETURN("return"),
    SEE("see"),
    STRUCT("struct"),
    THROWS("throws"),
    TYPE("type")
    ;

    private final String annotation;

    private Annotation(String annotation) {
      this.annotation = annotation;
    }

    static Optional<Annotation> forMarker(JSDocInfo.Marker marker) {
      for (Annotation a : Annotation.values()) {
        if (a.annotation.equals(marker.getAnnotation().getItem())) {
          return Optional.of(a);
        }
      }
      return Optional.absent();
    }

    String getAnnotation() {
      return annotation;
    }
  }

  private static final class Offset {
    private final int line;
    private final int column;

    private Offset(int line, int column) {
      this.line = line;
      this.column = column;
    }

    @Override
    public String toString() {
      return line + ":" + column;
    }
  }
}
