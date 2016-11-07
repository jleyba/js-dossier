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

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Marker;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  private static final JsDoc DEFAULT = new JsDoc(new JSDocInfoBuilder(true).build(true));

  private final JSDocInfo info;

  private final Map<String, Parameter> parameters = new LinkedHashMap<>();
  private final List<TypedDescription> throwsClauses = new LinkedList<>();
  private final List<String> seeClauses = new LinkedList<>();

  private String blockComment = "";
  private TypedDescription returnDescription = new TypedDescription(null, "");
  private String defineComment = "";
  private String deprecationReason = "";
  private String fileoverview = "";
  private boolean parsed = false;

  private JsDoc(JSDocInfo info) {
    this.info = info;
  }

  public static JsDoc from(@Nullable JSDocInfo info) {
    return info == null ? DEFAULT : new JsDoc(info);
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
    return info.getVisibility();
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

  public TypedDescription getReturnClause() {
    parse();
    return returnDescription;
  }

  public ImmutableList<String> getSeeClauses() {
    parse();
    return ImmutableList.copyOf(seeClauses);
  }

  public ImmutableList<TypedDescription> getThrowsClauses() {
    parse();
    return ImmutableList.copyOf(throwsClauses);
  }

  public ImmutableList<String> getTemplateTypeNames() {
    return info.getTemplateTypeNames();
  }

  public boolean hasAnnotation(Annotation target) {
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

  private void parse() {
    if (parsed) {
      return;
    }
    parsed = true;
    blockComment = nullToEmpty(info.getBlockDescription()).trim();
    deprecationReason = nullToEmpty(info.getDeprecationReason()).trim();
    fileoverview = nullToEmpty(info.getFileOverview()).trim();
    returnDescription = new TypedDescription(
        info.getReturnType(), info.getReturnDescription());

    for (String name : info.getParameterNames()) {
      parameters.put(name, new Parameter(
          name,
          info.getParameterType(name),
          info.getDescriptionForParameter(name)));
    }

    Set<JSTypeExpression> missingThrowsDescriptions = new HashSet<>();
    for (JSTypeExpression thrown : info.getThrownTypes()) {
      // NB: this is interesting. The thrown types to descriptions is a LinkedHashMap, from
      // which we cannot reliably extract a JSTypeExpression (the hash must not be stable?)
      // Try to lookup the description this way. If it cannot be found, we'll scan the JSDoc
      // annotations below, and finally resort to just documenting the type. We check for a
      // description here first for the documentation of transpiled classes (the thrown
      // descriptions are recorded for ES6 constructors, but the original marker is discarded).
      String description = info.getThrowsDescriptionForType(thrown);
      if (isNullOrEmpty(description)) {
        missingThrowsDescriptions.add(thrown);
      } else {
        throwsClauses.add(new TypedDescription(thrown, description));
      }
    }

    for (JSDocInfo.Marker marker : info.getMarkers()) {
      Optional<Annotation> annotation = Annotation.forMarker(marker);
      if (!annotation.isPresent()) {
        continue;  // Unrecognized/unsupported annotation.
      }

      String description = marker.getDescription() == null
          ? "" : nullToEmpty(marker.getDescription().getItem()).trim();
      switch (annotation.get()) {
        case DEFINE:
          defineComment = description;
          break;
        case SEE:
          seeClauses.add(description);
          break;
        case THROWS: {
          JSTypeExpression markerType = getJsTypeExpression(marker);
          if (missingThrowsDescriptions.contains(markerType)) {
            missingThrowsDescriptions.remove(markerType);
            throwsClauses.add(new TypedDescription(markerType, description));
          }
          break;
        }
      }
    }

    for (JSTypeExpression thrown : missingThrowsDescriptions) {
      throwsClauses.add(new TypedDescription(thrown, null));
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

  public static final class TypedDescription {

    private final Optional<JSTypeExpression> type;
    private final String description;

    private TypedDescription(@Nullable JSTypeExpression type, @Nullable String description) {
      this.type = Optional.fromNullable(type);
      this.description = nullToEmpty(description).trim();
    }

    public Optional<JSTypeExpression> getType() {
      return type;
    }

    public String getDescription() {
      return description;
    }
  }

  public enum Annotation {
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

    Annotation(String annotation) {
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
}
