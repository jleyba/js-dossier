// Copyright 2013 Jason Leyba
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.filter;

import com.github.jleyba.dossier.proto.Dossier;
import com.github.jleyba.dossier.proto.Dossier.Comment;
import com.google.common.base.Predicate;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.NoType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.PrototypeObjectType;
import com.google.javascript.rhino.jstype.ProxyObjectType;
import com.google.javascript.rhino.jstype.RecordType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Utility class for working with JSDoc comments.
 */
public class CommentUtil {
  private CommentUtil() {}  // Utility class.

  private static Pattern SUMMARY_REGEX = Pattern.compile("(.*?\\.)[\\s$]", Pattern.DOTALL);
  private static final Pattern TAGLET_START_PATTERN = Pattern.compile("\\{@(\\w+)\\s");

  /**
   * Extracts summary sentence from the provided comment text. This is the substring up to the
   * first period (.) followed by a blank, tab, or newline.
   */
  public static Dossier.Comment getSummary(String text, Linker linker) {
    Matcher matcher = SUMMARY_REGEX.matcher(text);
    if (matcher.find()) {
      return parseComment(matcher.group(1), linker);
    }
    return parseComment(text, linker);
  }

  /**
   * Extracs the fileoverview comment string from the given {@link JsDoc} object.
   */
  public static Dossier.Comment getFileoverview(Linker linker, @Nullable JsDoc jsdoc) {
    if (jsdoc == null) {
      return Dossier.Comment.getDefaultInstance();
    }
    return parseComment(jsdoc.getFileoverview(), linker);
  }

  /**
   * Extracts the block comment string from the given {@link JsDoc} object.
   */
  public static Dossier.Comment getBlockDescription(Linker linker, @Nullable JsDoc jsdoc) {
    if (jsdoc == null) {
      return Dossier.Comment.getDefaultInstance();
    }
    return parseComment(jsdoc.getBlockComment(), linker);
  }

  private static Dossier.Comment.Token.Builder newToken(String text) {
    return Dossier.Comment.Token.newBuilder().setText(text);
  }

  /**
   * Parses the {@code text} of a JSDoc block comment.
   */
  public static Dossier.Comment parseComment(String text, Linker linker) {
    Dossier.Comment.Builder builder = Dossier.Comment.newBuilder();
    if (isNullOrEmpty(text)) {
      return builder.build();
    }

    int start = 0;
    while (true) {
      int tagletStart = findInlineTagStart(text, start);
      if (tagletStart == -1) {
        if (start < text.length()) {
          builder.addToken(newToken(text.substring(start)));
        }
        break;
      } else if (tagletStart > start) {
        builder.addToken(newToken(text.substring(start, tagletStart)));
      }

      int tagletEnd = findInlineTagEnd(text, tagletStart + 1);
      if (tagletEnd == -1) {
        builder.addToken(newToken(text.substring(start)));
        break;
      }

      String tagletName = getTagletName(text, tagletStart);
      String tagletPrefix = "{@" + tagletName + " ";
      String tagletText = text.substring(tagletStart + tagletPrefix.length(), tagletEnd);
      switch (tagletName) {
        case "code":
          builder.addToken(newToken(tagletText).setIsCode(true));
          break;

        case "link":
        case "linkplain":
          LinkInfo info = LinkInfo.fromText(tagletText);
          @Nullable String link = linker.getLink(info.type);

          Dossier.Comment.Token.Builder token = newToken(info.text)
              .setIsCode("link".equals(tagletName))
              .setUnresolvedLink(link == null);
          if (link != null) {
            token.setHref(link);
          }
          builder.addToken(token);
          break;

        case "literal":
          builder.addToken(newToken(tagletText).setIsLiteral(true));
          break;

        default:
          builder.addToken(newToken(tagletText));
      }
      start = tagletEnd + 1;
    }

    return builder.build();
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

  public static Dossier.Comment formatTypeExpression(@Nullable JSType type, final Linker linker) {
    if (type == null) {
      return Dossier.Comment.newBuilder().build();
    }
    return new CommentTypeParser(linker).parse(type);
  }

  /**
   * A {@link JSType} visitor that converts the type into a comment type expression.
   */
  private static class CommentTypeParser implements Visitor<Void> {

    private final Linker linker;
    private final Comment.Builder comment = Comment.newBuilder();

    private String currentText = "";

    private CommentTypeParser(Linker linker) {
      this.linker = linker;
    }

    Comment parse(JSType type) {
      comment.clear();
      currentText = "";
      type.visit(this);
      if (!currentText.isEmpty()) {
        comment.addTokenBuilder()
            .setIsLiteral(true)
            .setText(currentText);
        currentText = "";
      }
      return comment.build();
    }

    private void appendText(String text) {
      currentText += text;
    }

    private void appendNativeType(String type) {
      appendLink(type, checkNotNull(linker.getExternLink(type)));
    }

    private void appendLink(String text, String href) {
      if (!currentText.isEmpty()) {
        comment.addTokenBuilder()
            .setIsLiteral(true)
            .setText(currentText);
        currentText = "";
      }
      comment.addTokenBuilder().setText(text).setHref(href);
    }

    @Override
    public Void caseNoType(NoType type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void caseEnumElementType(EnumElementType type) {
      return type.getPrimitiveType().visit(this);
    }

    @Override
    public Void caseAllType() {
      appendText("*");
      return null;
    }

    @Override
    public Void caseBooleanType() {
      appendNativeType("boolean");
      return null;
    }

    @Override
    public Void caseNoObjectType() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void caseFunctionType(FunctionType type) {
      if ("Function".equals(type.getReferenceName())) {
        appendText("Function");
        return null;
      }
      appendText("function(");

      if (type.isConstructor()) {
        appendText("new: ");
        type.getTypeOfThis().visit(this);
        if (type.getParameters().iterator().hasNext()) {
          appendText(", ");
        }
      } else if (!type.getTypeOfThis().isUnknownType()
          || type.getTypeOfThis() instanceof NamedType) {
        appendText("this: ");
        type.getTypeOfThis().visit(this);
        if (type.getParameters().iterator().hasNext()) {
          appendText(", ");
        }
      }

      Iterator<Node> parameters = type.getParameters().iterator();
      while (parameters.hasNext()) {
        Node node = parameters.next();
        if (node.isVarArgs()) {
          appendText("...");
        }

        if (node.getJSType().isUnionType()) {
          caseUnionType((UnionType) node.getJSType(), node.isOptionalArg());
        } else {
          node.getJSType().visit(this);
        }

        if (node.isOptionalArg()) {
          appendText("=");
        }

        if (parameters.hasNext()) {
          appendText(", ");
        }
      }
      appendText(")");

      if (type.getReturnType() != null) {
        appendText(": ");
        type.getReturnType().visit(this);
      }
      return null;
    }

    @Override
    public Void caseObjectType(ObjectType type) {
      if (type.isRecordType()) {
        caseRecordType((RecordType) type);
      } else if (type.isInstanceType()) {
        caseInstanceType(type);
      } else if (type instanceof PrototypeObjectType) {
        casePrototypeObjectType((PrototypeObjectType) type);
      } else {
        throw new UnsupportedOperationException();
      }
      return null;
    }

    private void casePrototypeObjectType(PrototypeObjectType type) {
      if (type.getOwnerFunction() != null) {
        ObjectType obj = type.getOwnerFunction().getTypeOfThis().toObjectType();
        caseInstanceType(obj.getReferenceName() + ".prototype", obj);
      } else {
        verify("{}".equals(type.toString()));
        type.getImplicitPrototype().visit(this);
      }
    }

    private void caseInstanceType(ObjectType type) {
      caseInstanceType(type.getReferenceName(), type);
    }

    private void caseInstanceType(String displayName, ObjectType type) {
      Dossier.TypeLink link = linker.getLink(type.getConstructor());
      if (link == null) {
        String href = nullToEmpty(linker.getExternLink(type.getReferenceName()));
        appendLink(displayName, href);
      } else {
        appendLink(displayName, link.getHref());
      }
    }

    private void caseRecordType(RecordType type) {
      appendText("{");
      Iterator<String> properties = type.getOwnPropertyNames().iterator();
      while (properties.hasNext()) {
        Property property = type.getOwnSlot(properties.next());
        appendText(property.getName() + ": ");
        property.getType().visit(this);
        if (properties.hasNext()) {
          appendText(", ");
        }
      }
      appendText("}");
    }

    @Override
    public Void caseUnknownType() {
      appendText("?");
      return null;
    }

    @Override
    public Void caseNullType() {
      appendNativeType("null");
      return null;
    }

    @Override
    public Void caseNamedType(NamedType type) {
      String link = linker.getLink(type.getReferenceName());
      if (!isNullOrEmpty(link)) {
        appendLink(type.getReferenceName(), link);
      } else {
        appendText(type.getReferenceName());
      }
      return null;
    }

    @Override
    public Void caseProxyObjectType(ProxyObjectType type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void caseNumberType() {
      appendNativeType("number");
      return null;
    }

    @Override
    public Void caseStringType() {
      appendNativeType("string");
      return null;
    }

    @Override
    public Void caseVoidType() {
      appendNativeType("undefined");
      return null;
    }

    @Override
    public Void caseUnionType(UnionType type) {
      caseUnionType(type, false);
      return null;
    }

    private void caseUnionType(UnionType type, boolean filterVoid) {
      int numAlternates = 0;
      int nullAlternates = 0;
      int voidAlternates = 0;
      boolean containsNonNullable = false;
      for (JSType alternate : type.getAlternates()) {
        numAlternates += 1;
        if (alternate.isNullType()) {
          nullAlternates += 1;
        }
        if (alternate.isVoidType() && filterVoid) {
          voidAlternates += 1;
        }
        containsNonNullable = containsNonNullable || alternate.isNullable();
      }

      Iterable<JSType> alternates = type.getAlternates();
      if (nullAlternates > 0 || voidAlternates > 0) {
        System.out.println("Simplifying union: " + type);

        numAlternates -= nullAlternates;
        numAlternates -= voidAlternates;

        alternates = filter(alternates, new Predicate<JSType>() {
          @Override
          public boolean apply(JSType input) {
            return !input.isNullType() && !input.isVoidType();
          }
        });
      }

      if (containsNonNullable) {
        appendText("?");
      }

      if (numAlternates == 1) {
        alternates.iterator().next().visit(this);
      } else {
        appendText("(");
        Iterator<JSType> types = alternates.iterator();
        while (types.hasNext()) {
          types.next().visit(this);
          if (types.hasNext()) {
            appendText("|");
          }
        }
        appendText(")");
      }
    }

    @Override
    public Void caseTemplatizedType(TemplatizedType type) {
      type.getReferencedType().visit(this);
      appendText("<");
      Iterator<JSType> types = type.getTemplateTypes().iterator();
      while (types.hasNext()) {
        types.next().visit(this);
        if (types.hasNext()) {
          appendText(", ");
        }
      }
      appendText(">");
      return null;
    }

    @Override
    public Void caseTemplateType(TemplateType templateType) {
      appendText(templateType.getReferenceName());
      return null;
    }
  }

  private static class LinkInfo {

    private final String type;
    private final String text;

    private LinkInfo(String type, String text) {
      this.type = type;
      this.text = text;
    }

    static LinkInfo fromText(String text) {
      String linkedType = text;
      String linkText = text;
      int index = text.indexOf(' ');
      if (index != -1) {
        linkedType = text.substring(0, index);
        linkText= text.substring(index + 1);
      }
      return new LinkInfo(linkedType, linkText);
    }
  }
}
