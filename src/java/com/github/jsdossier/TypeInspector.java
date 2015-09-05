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

package com.github.jsdossier;

import static com.github.jsdossier.Comments.isVacuousTypeComment;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.transform;

import com.github.jsdossier.jscomp.JsDoc;
import com.github.jsdossier.jscomp.JsDoc.TypedDescription;
import com.github.jsdossier.jscomp.Parameter;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Function.Detail;
import com.github.jsdossier.proto.Visibility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.TemplatizedType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;

final class TypeInspector {

  private final Linker linker;
  private final CommentParser parser;
  private final TypeRegistry typeRegistry;

  @Inject
  TypeInspector(Linker linker, CommentParser parser, TypeRegistry typeRegistry) {
    this.linker = linker;
    this.parser = parser;
    this.typeRegistry = typeRegistry;
  }

  /**
   * Extracts information on the members (both functions and properties) of the given type.
   */
  public Report inspectMembers(NominalType nominalType) {
    if (!nominalType.getJsType().isConstructor() && !nominalType.getJsType().isInterface()) {
      return new Report();
    }

    Iterable<JSType> interfaces = typeRegistry.getImplementedTypes(nominalType);
    List<JSType> classes = ImmutableList.of();
    if (nominalType.getJsType().isConstructor()) {
      classes = Lists.reverse(typeRegistry.getTypeHierarchy(nominalType.getJsType()));
    } else {
      interfaces = Iterables.concat(
          ImmutableSet.of(nominalType.getJsType()), interfaces);
    }

    Report report = new Report();
    Multimap<String, InstanceProperty> properties = MultimapBuilder
        .treeKeys()
        .linkedHashSetValues()
        .build();

    for (JSType assignableType : Iterables.concat(classes, interfaces)) {
      for (Map.Entry<String, InstanceProperty> entry
          : getInstanceProperties(assignableType).entrySet()) {
        properties.put(entry.getKey(), entry.getValue());
      }
    }

    final JSType currentType = ((FunctionType) nominalType.getJsType()).getInstanceType();

    for (String key : properties.keySet()) {
      LinkedList<InstanceProperty> definitions = new LinkedList<>(properties.get(key));
      InstanceProperty property = definitions.removeFirst();

      JsDoc jsdoc = JsDoc.from(property.getJSDocInfo());
      if (jsdoc != null && jsdoc.getVisibility() == JSDocInfo.Visibility.PRIVATE) {
        continue;
      }

      Comment definedBy = getDefinedByComment(currentType, property);
      Comment overrides = findOverriddenType(definitions);
      Iterable<Comment> specifications = findSpecifications(definitions);

      if (property.getType().isFunctionType()) {
        report.addFunction(getFunctionData(
            property.getName(),
            property.getType(),
            property.getNode(),
            jsdoc,
            definedBy,
            overrides,
            specifications));
      } else {
        report.addProperty(getPropertyData(
            property.getName(),
            property.getType(),
            property.getNode(),
            jsdoc,
            definedBy,
            overrides,
            specifications));
      }
    }

    return report;
  }

  @VisibleForTesting
  Map<String, InstanceProperty> getInstanceProperties(JSType type) {
    Map<String, InstanceProperty> properties = new HashMap<>();

    if (type.isConstructor() || type.isInterface()) {
      type = ((FunctionType) type).getInstanceType();
    }

    ObjectType object = type.toObjectType();
    FunctionType ctor = object.getConstructor();
    if (ctor != null) {
      ObjectType prototype = ObjectType.cast(ctor.getPropertyType("prototype"));
      verify(prototype != null);
      properties = getOwnProperties(prototype);
    }
    properties.putAll(getOwnProperties(object));
    return properties;
  }

  private Map<String, InstanceProperty> getOwnProperties(ObjectType object) {
    ObjectType definingType = object;
    if (definingType.isFunctionPrototypeType()) {
      definingType = definingType.getOwnerFunction();
    } else if (definingType.isInstanceType()) {
      definingType = definingType.getConstructor();
    }

    Map<String, InstanceProperty> properties = new HashMap<>();
    for (String name : object.getOwnPropertyNames()) {
      if (!"constructor".equals(name)) {
        Property property = object.getOwnSlot(name);
        properties.put(property.getName(), new InstanceProperty(
            definingType,
            property.getName(),
            getType(object, property),
            property.getNode(),
            property.getJSDocInfo()));
      }
    }
    return properties;
  }

  @Nullable
  private Comment getDefinedByComment(JSType currentType, InstanceProperty property) {
    JSType propertyDefinedOn = property.getDefinedOn();
    if (propertyDefinedOn.isConstructor() || propertyDefinedOn.isInterface()) {
      propertyDefinedOn = ((FunctionType) propertyDefinedOn).getInstanceType();
    }
    if (currentType.equals(propertyDefinedOn)) {
      return null;
    }
    JSType definedByType = stripTemplateTypeInformation(propertyDefinedOn);
    return addPropertyHash(linker.formatTypeExpression(definedByType), property.getName());
  }
  /**
   * Given a list of properties, finds the first one defined on a class or (non-interface) object.
   */
  @Nullable
  private Comment findOverriddenType(Iterable<InstanceProperty> properties) {
    for (InstanceProperty property : properties) {
      JSType definedOn = property.getDefinedOn();
      if (definedOn.isInterface()) {
        continue;
      } else if (definedOn.isInstanceType()) {
        FunctionType ctor = definedOn.toObjectType().getConstructor();
        if (ctor != null && ctor.isInterface()) {
          continue;
        }
      }

      if (definedOn.isConstructor()) {
        definedOn = ((FunctionType) definedOn).getInstanceType();
      }
      definedOn = stripTemplateTypeInformation(definedOn);
      return addPropertyHash(linker.formatTypeExpression(definedOn), property.getName());
    }
    return null;
  }

  /**
   * Given a list of properties, finds those that are specified on an interface.
   */
  private Iterable<Comment> findSpecifications(Collection<InstanceProperty> properties) {
    List<Comment> specifications = new ArrayList<>(properties.size());
    for (InstanceProperty property : properties) {
      JSType definedOn = property.getDefinedOn();
      if (!definedOn.isInterface()) {
        JSType ctor = null;
        if (definedOn.isInstanceType()) {
          ctor = definedOn.toObjectType().getConstructor();
        }
        if (ctor == null || !ctor.isInterface()) {
          continue;
        }
      }
      definedOn = stripTemplateTypeInformation(definedOn);
      definedOn = ((FunctionType) definedOn).getInstanceType();
      specifications.add(
          addPropertyHash(linker.formatTypeExpression(definedOn), property.getName()));
    }
    return specifications;
  }

  public Function getFunctionData(
      String name,
      JSType type,
      Node node,
      @Nullable JsDoc jsDoc) {
    return getFunctionData(name, type, node, jsDoc, null, null, ImmutableList.<Comment>of());
  }

  public Function getFunctionData(
      String name,
      JSType type,
      Node node,
      @Nullable JsDoc jsDoc,
      @Nullable Comment definedBy,
      @Nullable Comment overrides,
      Iterable<Comment> specifications) {
    checkArgument(type.isFunctionType(), "%s is not a function type: %s", name, type);

    boolean isConstructor = type.isConstructor();
    boolean isInterface = !isConstructor && type.isInterface();

    Function.Builder builder = Function.newBuilder()
        .setBase(getBasePropertyDetails(
            name, type, node, jsDoc, definedBy, overrides, specifications))
        .setIsConstructor(isConstructor);

    if (!isConstructor && !isInterface) {
      Function.Detail.Builder detail = builder.getReturnBuilder();
      detail.setType(getReturnType(jsDoc, (FunctionType) type));
      if (jsDoc != null) {
        detail.setDescription(
            parser.parseComment(jsDoc.getReturnClause().getDescription(), linker));
      }
    }

    if (jsDoc != null) {
      builder.addAllTemplateName(jsDoc.getTemplateTypeNames())
          .addAllThrown(buildThrowsData(jsDoc));
    }

    builder.addAllParameter(getParameters(type, node, jsDoc));

    return builder.build();
  }

  private List<com.github.jsdossier.proto.Function.Detail> getParameters(JSType type, Node node, @Nullable JsDoc docs) {
    checkArgument(type.isFunctionType());
    final JsDoc jsdoc = docs == null && type.getJSDocInfo() != null
        ? JsDoc.from(type.getJSDocInfo())
        : docs;

    // TODO: simplify this mess by adding a check that JSDoc parameter names are consistent with
    // the param list (order and number)
    List<Node> parameterNodes = Lists.newArrayList(((FunctionType) type).getParameters());
    List<com.github.jsdossier.proto.Function.Detail> details = new ArrayList<>(parameterNodes.size());
    @Nullable Node paramList = findParamList(node);

    for (int i = 0; i < parameterNodes.size(); i++) {
      // Try to find the matching parameter in the jsdoc.
      Parameter parameter = null;
      if (paramList != null && i < paramList.getChildCount()) {
        String name = paramList.getChildAtIndex(i).getString();
        if (jsdoc != null && jsdoc.hasParameter(name)) {
          parameter = jsdoc.getParameter(name);
        }
      } else if (jsdoc != null && i < jsdoc.getParameters().size()) {
        parameter = jsdoc.getParameters().get(i);
      }

      com.github.jsdossier.proto.Function.Detail.Builder detail =
          com.github.jsdossier.proto.Function.Detail.newBuilder()
              .setName("arg" + i);

      // If the compiler hasn't determined a type yet, try to map back to the jsdoc.
      Node parameterNode = parameterNodes.get(i);
      if (parameterNode.getJSType().isNoType() || parameterNode.getJSType().isNoResolvedType()) {
        if (parameter != null) {
          detail.setType(linker.formatTypeExpression(parameter.getType()));
        }
      } else {
        detail.setType(linker.formatTypeExpression(parameterNode));
      }

      // Try to match up names from code to type and jsdoc information.
      if (paramList != null && i < paramList.getChildCount()) {
        String name = paramList.getChildAtIndex(i).getString();
        detail.setName(name);
        if (jsdoc != null && jsdoc.hasParameter(name)) {
          detail.setDescription(parser.parseComment(
              jsdoc.getParameter(name).getDescription(),
              linker));
        }

      } else if (parameter != null) {
        detail.setName(parameter.getName());
        detail.setDescription(parser.parseComment(parameter.getDescription(), linker));
      }

      details.add(detail.build());
    }
    return details;
  }

  @Nullable
  private static Node findParamList(Node src) {
    if (src.isName() && src.getParent().isFunction()) {
      verify(src.getNext().isParamList());
      return src.getNext();
    }

    if (src.isGetProp()
        && src.getParent().isAssign()
        && src.getParent().getFirstChild() != null
        && src.getParent().getFirstChild().getNext().isFunction()) {
      src = src.getParent().getFirstChild().getNext();
      return src.getFirstChild().getNext();
    }

    if (!src.isFunction()
        && src.getFirstChild() != null
        && src.getFirstChild().isFunction()) {
      src = src.getFirstChild();
    }

    if (src.isFunction()) {
      Node node = src.getFirstChild().getNext();
      verify(node.isParamList());
      return node;
    }

    return null;
  }

  private Iterable<Function.Detail> buildThrowsData(JsDoc jsDoc) {
    return transform(jsDoc.getThrowsClauses(),
        new com.google.common.base.Function<TypedDescription, Detail>() {
          @Override
          public Function.Detail apply(TypedDescription input) {
            Comment thrownType = Comment.getDefaultInstance();
            if (input.getType().isPresent()) {
              thrownType = linker.formatTypeExpression(input.getType().get());
            }
            return Function.Detail.newBuilder()
                .setType(thrownType)
                .setDescription(parser.parseComment(input.getDescription(), linker))
                .build();
          }
        }
    );
  }

  private Comment getReturnType(@Nullable JsDoc jsdoc, FunctionType function) {
    JSType returnType = function.getReturnType();
    if (returnType.isUnknownType()
        && jsdoc != null
        && jsdoc.getReturnClause().getType().isPresent()) {
      returnType = typeRegistry.evaluate(jsdoc.getReturnClause().getType().get());
    }
    Comment comment = linker.formatTypeExpression(returnType);
    if (isVacuousTypeComment(comment)) {
      return Comment.getDefaultInstance();
    }
    return comment;
  }

  public com.github.jsdossier.proto.Property getPropertyData(
      String name,
      JSType type,
      Node node,
      @Nullable JsDoc jsDoc) {
    return getPropertyData(name, type, node, jsDoc, null, null, ImmutableList.<Comment>of());
  }

  public com.github.jsdossier.proto.Property getPropertyData(
      String name,
      JSType type,
      Node node,
      @Nullable JsDoc jsDoc,
      @Nullable Comment definedBy,
      @Nullable Comment overrides,
      Iterable<Comment> specifications) {
    com.github.jsdossier.proto.Property.Builder builder =
        com.github.jsdossier.proto.Property.newBuilder()
            .setBase(getBasePropertyDetails(
                name, type, node, jsDoc, definedBy, overrides, specifications));

    if (jsDoc != null && jsDoc.getType() != null) {
      builder.setType(linker.formatTypeExpression(jsDoc.getType()));
    } else if (type != null) {
      builder.setType(linker.formatTypeExpression(type));
    }

    return builder.build();
  }

  private BaseProperty getBasePropertyDetails(
      String name,
      JSType type,
      Node node,
      @Nullable JsDoc jsdoc,
      @Nullable Comment definedBy,
      @Nullable Comment overrides,
      Iterable<Comment> specifications) {
    BaseProperty.Builder builder = BaseProperty.newBuilder()
        .setName(name)
        .setDescription(parser.getBlockDescription(linker, jsdoc))
        .setSource(linker.getSourceLink(node))
        .addAllSpecifiedBy(specifications);

    if (definedBy != null) {
      builder.setDefinedBy(definedBy);
    }

    if (overrides != null) {
      builder.setOverrides(overrides);
    }

    if (jsdoc != null) {
      builder.setVisibility(Visibility.valueOf(jsdoc.getVisibility().name()))
          .getTagsBuilder()
          .setIsDeprecated(jsdoc.isDeprecated())
          .setIsConst(!type.isFunctionType() && (jsdoc.isConst() || jsdoc.isDefine()));
      if (jsdoc.isDeprecated()) {
        builder.setDeprecation(parser.parseDeprecation(jsdoc.getDeprecationReason(), linker));
      }
    }
    return builder.build();
  }

  private static JSType stripTemplateTypeInformation(JSType type) {
    if (type.isTemplatizedType()) {
      return ((TemplatizedType) type).getReferencedType();
    }
    return type;
  }

  private static JSType getType(ObjectType object, Property property) {
    JSType type = object.findPropertyType(property.getName());
    if (type.isUnknownType()) {
      type = property.getType();
    }
    return type;
  }

  private static Comment addPropertyHash(Comment comment, String hash) {
    // TODO: find a non-hacky way for this.
    if (comment.getTokenCount() == 1 && comment.getToken(0).hasHref()) {
      Comment.Builder cbuilder = comment.toBuilder();
      Comment.Token.Builder token = cbuilder.getToken(0).toBuilder();
      token.setHref(token.getHref() + "#" + hash);
      cbuilder.setToken(0, token);
      comment = cbuilder.build();
    }
    return comment;
  }

  public static final class Report {
    private List<com.github.jsdossier.proto.Function> functions = new ArrayList<>();
    private List<com.github.jsdossier.proto.Property> properties = new ArrayList<>();

    private void addFunction(com.github.jsdossier.proto.Function function) {
      functions.add(function);
    }

    private void addProperty(com.github.jsdossier.proto.Property property) {
      properties.add(property);
    }

    public List<com.github.jsdossier.proto.Function> getFunctions() {
      return functions;
    }

    public List<com.github.jsdossier.proto.Property> getProperties() {
      return properties;
    }
  }

  @VisibleForTesting
  static final class InstanceProperty {
    private final JSType definedOn;
    private final String name;
    private final JSType type;
    private final Node node;
    private final JSDocInfo info;

    private InstanceProperty(
        JSType definedOn, String name, JSType type, Node node, JSDocInfo info) {
      this.definedOn = definedOn;
      this.name = name;
      this.type = type;
      this.node = node;
      this.info = info;
    }

    public JSType getDefinedOn() {
      return definedOn;
    }

    public String getName() {
      return name;
    }

    public JSType getType() {
      return type;
    }

    public Node getNode() {
      return node;
    }

    public JSDocInfo getJSDocInfo() {
      return info;
    }
  }
}
