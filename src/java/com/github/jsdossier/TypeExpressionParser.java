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

import static com.github.jsdossier.jscomp.Types.externToOriginalName;
import static com.github.jsdossier.jscomp.Types.isExternModule;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.filter;

import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.RecordType;
import com.github.jsdossier.proto.TypeExpression;
import com.github.jsdossier.proto.TypeLink;
import com.github.jsdossier.proto.TypeLinkOrBuilder;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.NoType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.PrototypeObjectType;
import com.google.javascript.rhino.jstype.ProxyObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Parses JS type expressions into Soy-friendly comment objects.
 */
@AutoFactory
final class TypeExpressionParser {

  private final DossierFileSystem dfs;
  private final TypeRegistry typeRegistry;
  private final JSTypeRegistry jsTypeRegistry;
  private final LinkFactory linkFactory;

  TypeExpressionParser(
      @Provided DossierFileSystem dfs,
      @Provided TypeRegistry typeRegistry,
      @Provided JSTypeRegistry jsTypeRegistry,
      LinkFactory linkFactory) {
    this.dfs = dfs;
    this.typeRegistry = typeRegistry;
    this.jsTypeRegistry = jsTypeRegistry;
    this.linkFactory = linkFactory;
  }

  /**
   * Parses the type expression attached to the given node.
   */
  public Comment parse(Node node) {
    JSType type = node.getJSType();
    if (type == null) {
      return Comment.newBuilder().build();
    }
    return new CommentTypeParser().parse(type, ParseModifier.forNode(node));
  }

  /**
   * Parses the given JS type expression.
   */
  public Comment parse(JSTypeExpression expression) {
    return new CommentTypeParser().parse(expression);
  }

  /**
   * Parses the given JS type object.
   */
  public Comment parse(@Nullable JSType type) {
    if (type == null) {
      return Comment.newBuilder().build();
    }
    return new CommentTypeParser().parse(type, ParseModifier.NONE);
  }

  public TypeExpression parseExpression(JSType type) {
    CommentTypeParser parser = new CommentTypeParser();
    parser.parse(type, ParseModifier.NONE);
    return parser.expression.build();
  }

  public TypeExpression parseExpression(JSTypeExpression expression) {
    CommentTypeParser parser = new CommentTypeParser();
    parser.parse(expression);
    return parser.expression.build();
  }

  @VisibleForTesting
  static final TypeExpression ANY_TYPE =
      TypeExpression.newBuilder()
          .setAllowNull(true)
          .setAllowUndefined(true)
          .setAnyType(true)
          .build();

  @VisibleForTesting
  static final TypeExpression UNKNOWN_TYPE =
      TypeExpression.newBuilder()
          .setAllowNull(true)
          .setAllowUndefined(true)
          .setUnknownType(true)
          .build();

  @Nullable
  @CheckReturnValue
  private NominalType resolve(JSType type) {
    Collection<NominalType> types = typeRegistry.getTypes(type);  // Exact check first.
    if (types.isEmpty()) {
      types = typeRegistry.findTypes(type);  // Slow equivalence check next.
    }
    if (types.isEmpty()) {
      return null;
    }
    return types.iterator().next();
  }

  @Nullable
  @CheckReturnValue
  private TypeLink getLink(JSType type) {
    NominalType ntype = resolve(type);
    if (ntype == null) {
      return null;
    }
    return linkFactory.createLink(ntype);
  }

  private TypeLink getNamedTypeLink(String name) {
    TypeLink link = linkFactory.createLink(name);
    if (link.getHref().isEmpty()) {
      JSType jsType = jsTypeRegistry.getType(name);
      if (jsType != null) {
        NominalType ntype = resolve(jsType);
        if (ntype != null) {
          link = linkFactory.createLink(ntype);
        }
      }

      if (link.getHref().isEmpty()) {
        int index = name.indexOf("$$module$");
        if (index > 0) {
          name = name.substring(0, index);
          link = linkFactory.createLink(name);
        }
      }
    }
    return link;
  }

  private enum ParseModifier {
    NONE,
    NON_NULL,
    OPTIONAL_ARG,
    VAR_ARGS;

    static ParseModifier forNode(Node node) {
      if (node.isVarArgs()) {
        return VAR_ARGS;
      } else if (node.isOptionalArg()) {
        return OPTIONAL_ARG;
      }
      return NONE;
    }
  }

  /**
   * A {@link JSType} visitor that converts the type into a comment type expression.
   */
  private class CommentTypeParser implements Visitor<Void> {

    private final Comment.Builder comment = Comment.newBuilder();
    private final TypeExpression.Builder expression = TypeExpression.newBuilder();
    private final Deque<TypeExpression.Builder> expressions = new ArrayDeque<>();

    private String currentText = "";

    Comment parse(JSTypeExpression expression) {
      startParse(ParseModifier.NONE);
      parseNode(expression.getRoot());
      return finishParse(ParseModifier.NONE);
    }

    Comment parse(JSType type, ParseModifier modifier) {
      startParse(modifier);
      if (modifier != ParseModifier.NONE && type.isUnionType()) {
        caseUnionType((UnionType) type, true);
      } else {
        type.visit(this);
      }
      return finishParse(modifier);
    }

    private TypeExpression.Builder currentExpression() {
      return expressions.getLast();
    }

    private void startParse(ParseModifier modifier) {
      expression.clear();
      expressions.clear();
      expressions.addLast(expression);

      comment.clear();
      currentText = "";

      if (modifier == ParseModifier.VAR_ARGS) {
        currentText = "...";
        currentExpression().setIsVarargs(true);
      }

      if (modifier == ParseModifier.NON_NULL) {
        currentText += "!";
        currentExpression().clearAllowNull();
      }
    }

    private Comment finishParse(ParseModifier modifier) {
      if (modifier == ParseModifier.OPTIONAL_ARG) {
        currentText += "=";
        currentExpression().setIsOptional(true);
      }

      if (!currentText.isEmpty()) {
        comment.addTokenBuilder().setText(currentText);
        currentText = "";
      }
      return comment.build();
    }

    private void appendText(String text) {
      currentText += text;
    }

    private void appendNativeType(String type) {
      appendLink(checkNotNull(linkFactory.createNativeExternLink(type)));
    }

    private void appendLink(TypeLinkOrBuilder link) {
      appendLink(link.getText(), link.getHref());
    }

    private void appendLink(String text, String href) {
      if (!currentText.isEmpty()) {
        if (href.isEmpty()) {
          currentText += text;
          currentExpression().getNamedTypeBuilder().setName(text);
          return;
        }
        comment.addTokenBuilder().setText(currentText);
        currentText = "";
      }
      Comment.Token.Builder token = comment.addTokenBuilder();
      token.setText(text);

      com.github.jsdossier.proto.NamedType.Builder namedType =
          currentExpression().getNamedTypeBuilder();
      namedType.setName(text);

      if (!href.isEmpty()) {
        token.setHref(href);
        namedType.setHref(href);
      }
    }

    private void parseNode(Node n, TypeExpression.Builder expression) {
      expressions.addLast(expression);
      parseNode(n);
      expressions.removeLast();
    }

    private void parseNode(Node n) {
      switch (n.getKind()) {
        case LC:
          parseRecordType(n);
          break;

        case BANG:
          appendText("!");
          parseNode(n.getFirstChild());
          currentExpression().clearAllowNull();
          break;

        case QMARK:
          appendText("?");
          if (n.getFirstChild() == null) {
            currentExpression()
                .setAllowNull(true)
                .setAllowUndefined(true)
                .setUnknownType(true);
          } else {
            parseNode(n.getFirstChild());
            currentExpression().setAllowNull(true);
          }
          break;

        case EQUALS:
          parseNode(n.getFirstChild());
          currentExpression().setIsOptional(true).setAllowUndefined(true);
          appendText("=");
          break;

        case ELLIPSIS:
          appendText("...");
          currentExpression().setIsVarargs(true);
          if (n.getFirstChild() != null) {
            parseNode(n.getFirstChild());
          }
          break;

        case STAR:
          appendText("*");
          currentExpression().setAnyType(true).setAllowUndefined(true).setAllowNull(true);
          break;

        case PIPE:
          appendText("(");
          com.github.jsdossier.proto.UnionType.Builder unionType =
              currentExpression().getUnionTypeBuilder();
          boolean allowNull = false;
          boolean allowUndefined = false;
          for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
            TypeExpression.Builder alternate = unionType.addTypeBuilder();
            parseNode(child, alternate);
            allowNull = allowNull || alternate.getAllowNull();
            allowUndefined = allowUndefined || alternate.getAllowUndefined();
            alternate.clearAllowNull().clearAllowUndefined();
            if (child.getNext() != null) {
              appendText("|");
            }
          }
          if (allowNull) {
            currentExpression().setAllowNull(true);
          }
          if (allowUndefined) {
            currentExpression().setAllowUndefined(true);
          }
          appendText(")");
          break;

        case EMPTY:
          appendText("?");
          break;

        case VOID:
          appendText("void");
          break;

        case STRING:
        case NAME:
          parseNamedType(n);
          break;

        case FUNCTION:
          parseFunction(n);
          break;

        default:
          throw new AssertionError("Unexpected node in type expression: " + n);
      }
    }

    private void parseNamedType(Node n) {
      checkArgument(n.getKind() == Token.STRING || n.getKind() == Token.NAME);
      TypeLink link = getNamedTypeLink(n.getString());
      appendLink(link);

      // Template types!
      if (n.getFirstChild() != null && n.getFirstChild().getKind() == Token.BLOCK) {
        appendText("<");
        for (Node child = n.getFirstFirstChild(); child != null; child = child.getNext()) {
          parseNode(child);
          if (child.getNext() != null) {
            appendText(", ");
          }
        }
        appendText(">");
      }
    }

    private void parseFunction(Node n) {
      checkArgument(n.getKind() == Token.FUNCTION);
      appendText("function(");

      com.github.jsdossier.proto.FunctionType.Builder functionType =
          currentExpression().getFunctionTypeBuilder();

      Node current = n.getFirstChild();
      boolean isCtor = current.getKind() == Token.NEW;
      if (isCtor) {
        functionType.setIsConstructor(true);
      }
      if (current.getKind() == Token.THIS || isCtor) {
        appendText(current.getKind() == Token.THIS ? "this: " : "new: ");
        TypeExpression.Builder instanceType = functionType.getInstanceTypeBuilder();
        parseNode(current.getFirstChild(), instanceType);
        current = current.getNext();
        if (current.getKind() == Token.PARAM_LIST) {
          appendText(", ");
        }
      }

      if (current.getKind() == Token.PARAM_LIST) {
        for (Node param = current.getFirstChild(); param != null; param = param.getNext()) {
          parseNode(param, functionType.addParameterBuilder());
          if (param.getNext() != null) {
            appendText(", ");
          }
        }
        current = current.getNext();
      }
      appendText(")");

      if (!isCtor && current != null && current.getKind() != Token.EMPTY) {
        appendText(": ");
        parseNode(current, functionType.getReturnTypeBuilder());
      }

    }

    private void parseRecordType(Node n) {
      checkArgument(n.getKind() == Token.LC);
      appendText("{");

      RecordType.Builder recordType = currentExpression().getRecordTypeBuilder();
      for (Node fieldType = n.getFirstFirstChild();
           fieldType != null;
           fieldType = fieldType.getNext()) {
        Node fieldName = fieldType;
        boolean hasType = false;
        if (fieldType.getKind() == Token.COLON) {
          fieldName = fieldType.getFirstChild();
          hasType = true;
        }

        String name = fieldName.getString();
        if (name.startsWith("'") || name.startsWith("\"")) {
          name = name.substring(1, name.length() - 1);
        }

        RecordType.Entry.Builder entry = recordType.addEntryBuilder();
        entry.setKey(name);

        appendText(name + ": ");
        if (hasType) {
          parseNode(fieldType.getLastChild(), entry.getValueBuilder());
        } else {
          entry.getValueBuilder().setAllowNull(true).setAllowUndefined(true).setUnknownType(true);
          appendText("?");
        }

        if (fieldType.getNext() != null) {
          appendText(", ");
        }
      }
      appendText("}");
    }

    @Override
    public Void caseNoType(NoType type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void caseEnumElementType(EnumElementType type) {
      List<NominalType> types = typeRegistry.getTypes(type.getEnumType());
      if (types.isEmpty()) {
        type.getEnumType().visit(this);
      } else {
        TypeLink link = linkFactory.createLink(types.get(0));
        appendLink(link);
      }
      return null;
    }

    @Override
    public Void caseAllType() {
      expressions.getLast()
          .setAllowNull(true)
          .setAllowUndefined(true)
          .setAnyType(true);
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
        currentExpression()
            .getNamedTypeBuilder()
            .setName("Function");
        appendText("Function");
        return null;
      }

      appendText("function(");

      com.github.jsdossier.proto.FunctionType.Builder functionType =
          currentExpression().getFunctionTypeBuilder();

      if (type.isConstructor()) {
        functionType.setIsConstructor(true);
        appendText("new: ");

        TypeExpression.Builder thisType = functionType.getInstanceTypeBuilder();

        expressions.addLast(thisType);
        type.getTypeOfThis().visit(this);
        expressions.removeLast();

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
        TypeExpression.Builder parameterType = functionType.addParameterBuilder();
        expressions.addLast(parameterType);

        Node node = parameters.next();

        if (node.isVarArgs()) {
          parameterType.setIsVarargs(true);
          appendText("...");
        }

        if (node.getJSType().isUnionType()) {
          caseUnionType((UnionType) node.getJSType(),
              node.isVarArgs() || node.isOptionalArg());
        } else {
          node.getJSType().visit(this);
        }

        if (node.isOptionalArg()) {
          // Not sure if this is possible, but varargs implies optional and we only permit one
          // bit to be set.
          if (!parameterType.getIsVarargs()) {
            parameterType.setIsOptional(true);
          }
          appendText("=");
        }

        if (parameters.hasNext()) {
          appendText(", ");
        }
        expressions.removeLast();
      }
      appendText(")");

      if (type.getReturnType() != null && !type.isConstructor()) {
        appendText(": ");
        type.getReturnType().visit(this);
      }
      return null;
    }

    @Override
    public Void caseObjectType(ObjectType type) {
      if (type.isRecordType()) {
        caseRecordType(type);
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

        TypeLink link = getLink(obj.getConstructor());
        if (link != null) {
          appendLink(link.getText() + ".prototype", link.getHref());
        } else {
          caseInstanceType(obj.getReferenceName() + ".prototype", obj);
        }
      } else if (!type.getOwnPropertyNames().isEmpty()) {
        TypeLink link = getLink(type);
        if (link != null) {
          appendLink(link);
        } else {
          caseRecordType(type);
        }
      } else {
        verify("{}".equals(type.toString()), "Unexpected type: %s", type);
        type.getImplicitPrototype().visit(this);
      }
    }

    private void caseInstanceType(ObjectType type) {
      NominalType nominalType = resolve(type.getConstructor());
      String displayName = nominalType == null
          ? type.getReferenceName()
          : dfs.getDisplayName(nominalType);
      caseInstanceType(displayName, type);
    }

    private void caseInstanceType(String displayName, ObjectType type) {
      TypeLink link = getLink(type.getConstructor());
      if (link == null) {
        link = linkFactory.createNativeExternLink(type.getReferenceName());
        appendLink(displayName, link == null ? "" : link.getHref());
      } else {
        appendLink(displayName, link.getHref());
      }

      if (type.isNullable()) {
        currentExpression().setAllowNull(true);
      }
    }

    private void caseRecordType(final ObjectType type) {
      appendText("{");

      Iterator<Property> properties = FluentIterable.from(type.getOwnPropertyNames())
          .transform(new Function<String, Property>() {
            @Override
            public Property apply(String input) {
              return type.getOwnSlot(input);
            }
          })
          .filter(new Predicate<Property>() {
            @Override
            public boolean apply(@Nullable Property input) {
              return input != null && !input.getType().isNoType();
            }
          })
          .toSortedList(new Comparator<Property>() {
            @Override
            public int compare(Property o1, Property o2) {
              return o1.getName().compareTo(o2.getName());
            }
          })
          .iterator();

      RecordType.Builder recordType = currentExpression().getRecordTypeBuilder();
      while (properties.hasNext()) {
        Property property = properties.next();
        appendText(property.getName() + ": ");
        RecordType.Entry.Builder entry = recordType.addEntryBuilder();
        entry.setKey(property.getName());
        expressions.addLast(entry.getValueBuilder());
        property.getType().visit(this);
        expressions.removeLast();
        if (properties.hasNext()) {
          appendText(", ");
        }
      }
      appendText("}");
    }

    @Override
    public Void caseUnknownType() {
      appendText("?");
      currentExpression().setUnknownType(true);
      return null;
    }

    @Override
    public Void caseNullType() {
      appendNativeType("null");
      return null;
    }

    @Override
    public Void caseNamedType(NamedType type) {
      String name = type.getReferenceName();
      TypeLink link = getNamedTypeLink(name);

      if (link != null) {
        appendLink(link);
      } else if (isExternModule(type.getReferenceName())) {
        appendText(externToOriginalName(type.getReferenceName()));
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

        if (alternate.isVoidType()) {
          if (filterVoid) {
            voidAlternates += 1;
            continue;
          }
        }

        containsNonNullable = containsNonNullable || !alternate.isNullable();
      }

      Iterable<JSType> alternates = type.getAlternates();
      if (nullAlternates > 0 || voidAlternates > 0) {
        numAlternates -= nullAlternates;
        numAlternates -= voidAlternates;

        alternates = filter(alternates, new Predicate<JSType>() {
          @Override
          public boolean apply(JSType input) {
            return !input.isNullType() && !input.isVoidType();
          }
        });
      }

      boolean allowNull = containsNonNullable && nullAlternates > 0;
      if (allowNull) {
        appendText("?");
        currentExpression().setAllowNull(true);
      }

      if (numAlternates == 1) {
        alternates.iterator().next().visit(this);

      } else {
        com.github.jsdossier.proto.UnionType.Builder unionType =
            currentExpression().getUnionTypeBuilder();

        appendText("(");
        Iterator<JSType> types = alternates.iterator();
        while (types.hasNext()) {
          TypeExpression.Builder alternate = unionType.addTypeBuilder();
          expressions.addLast(alternate);

          JSType alternateType = types.next();
          alternateType.visit(this);

          expressions.removeLast();
          if (alternateType.isVoidType()) {
            unionType.removeType(unionType.getTypeCount() - 1);
            currentExpression().setAllowUndefined(true);
          }

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

      if (currentExpression().getNamedType() == null) {
        throw new IllegalStateException("unexpected templatized type structure");
      }
      com.github.jsdossier.proto.NamedType.Builder namedType =
          currentExpression().getNamedTypeBuilder();

      while (types.hasNext()) {
        JSType templateType = types.next();
        expressions.addLast(namedType.addTemplateTypeBuilder());
        templateType.visit(this);
        expressions.removeLast();
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
      currentExpression().getNamedTypeBuilder().setName(templateType.getReferenceName());
      return null;
    }
  }
}
