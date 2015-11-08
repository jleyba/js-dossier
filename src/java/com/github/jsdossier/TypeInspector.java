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
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.transform;

import com.github.jsdossier.jscomp.JsDoc;
import com.github.jsdossier.jscomp.JsDoc.Annotation;
import com.github.jsdossier.jscomp.JsDoc.TypedDescription;
import com.github.jsdossier.jscomp.NominalType2;
import com.github.jsdossier.jscomp.Parameter;
import com.github.jsdossier.jscomp.TypeRegistry2;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Function.Detail;
import com.github.jsdossier.proto.Visibility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Extracts on the functions and properties in a type suitable for injection into a Soy template. 
 */
final class TypeInspector {

  private final Linker linker;
  private final CommentParser parser;
  private final TypeRegistry typeRegistry;
  private final TypeRegistry2 registry;

  @Inject
  TypeInspector(
      Linker linker,
      CommentParser parser,
      TypeRegistry typeRegistry,
      TypeRegistry2 registry) {
    this.linker = linker;
    this.parser = parser;
    this.typeRegistry = typeRegistry;
    this.registry = registry;
  }

  /**
   * Extracts information on the properties defined directly on the given nominal type. For
   * classes and interfaces, this will return information on the <em>static</em> properties, not
   * instance properties.
   */
  public Report inspectType(NominalType2 nominalType) {
    List<Property> properties = getProperties(nominalType.getType());
    Collections.sort(properties, new PropertyNameComparator());
    
    Report report = new Report();
    
    for (Property property : properties) {
    }

    return report;
  }
  
  private List<Property> getProperties(JSType type) {
    checkArgument(type.isObject());
    ObjectType object = ObjectType.cast(type);
    boolean isFunction = type.isFunctionType();
    List<Property> properties = new ArrayList<>();
    for (String name : object.getOwnPropertyNames()) {
      if (isFunction
          && !"apply".equals(name)
          && !"bind".equals(name)
          && !"call".equals(name)
          && !"prototype".equals(name)) {
        properties.add(object.getOwnSlot(name));
      } else if (!"prototype".equals(name)) {
        properties.add(object.getOwnSlot(name));
      }
    }
    return properties;
  }

  public Report inspectType(NominalType nominalType) {
    ImmutableList<Property> properties = FluentIterable.from(nominalType.getProperties())
        .toSortedList(new PropertyNameComparator());
    if (properties.isEmpty()) {
      return new Report();
    }

    Report report = new Report();
    linker.pushContext(nominalType);

    for (Property property : properties) {
      String name = property.getName();
      if (!nominalType.isModuleExports() && !nominalType.isNamespace()) {
        name = nominalType.getName() + "." + name;
      }

      JsDoc jsdoc = JsDoc.from(property.getJSDocInfo());

      // If this property does not have any docs and is part of a CommonJS module's exported API,
      // check if the property is a reference to one of the module's internal variables and we
      // can use those docs instead.
      if ((jsdoc == null || isNullOrEmpty(jsdoc.getOriginalCommentString()))
          && nominalType.getModule() != null
          && nominalType.isModuleExports()) {
        String internalName = nominalType.getModule().getInternalName(
            nominalType.getQualifiedName(true) + "." + property.getName());
        jsdoc = nominalType.getModule().getInternalVarDocs(internalName);
      }

      if (jsdoc != null && jsdoc.getVisibility() == JSDocInfo.Visibility.PRIVATE
          || (name.endsWith(".superClass_") && property.getType().isFunctionPrototypeType())) {
        continue;
      }
      
      if (jsdoc != null && jsdoc.isDefine()) {
        report.addCompilerConstant(getPropertyData(
            name,
            property.getType(),
            property.getNode(),
            jsdoc));

      } else if (property.getType().isFunctionType()) {
        report.addFunction(getFunctionData(
            name,
            property.getType(),
            property.getNode(),
            jsdoc));

      } else if (!property.getType().isEnumElementType()) {
        report.addProperty(getPropertyData(
            name,
            property.getType(),
            property.getNode(),
            jsdoc));
      }
    }

    linker.popContext();
    return report;
  }

  /**
   * Extracts information on the members (both functions and properties) of the given type.
   * 
   * <p>The returned report will include information on all properties on the type, regardless of
   * whether the property is defined directly on the nominal type or one of its super
   * types/interfaces.
   */
  public Report inspectInstanceType(NominalType nominalType) {
    if (!nominalType.getJsType().isConstructor() && !nominalType.getJsType().isInterface()) {
      return new Report();
    }
    
    linker.pushContext(nominalType);
    Report report = new Report();
    Multimap<String, InstanceProperty> properties = MultimapBuilder
        .treeKeys()
        .linkedHashSetValues()
        .build();

    for (JSType assignableType : getAssignableTypes(nominalType.getJsType())) {
      for (Map.Entry<String, InstanceProperty> entry
          : getInstanceProperties(assignableType).entrySet()) {
        properties.put(entry.getKey(), entry.getValue());
      }
    }

    final JSType currentType = ((FunctionType) nominalType.getJsType()).getInstanceType();

    for (String key : properties.keySet()) {
      LinkedList<InstanceProperty> definitions = new LinkedList<>(properties.get(key));
      JSType propertyType = findPropertyType(definitions);
      InstanceProperty property = definitions.removeFirst();

      if (property.getJsDoc() != null
          && property.getJsDoc().getVisibility() == JSDocInfo.Visibility.PRIVATE) {
        continue;
      }

      Comment definedBy = getDefinedByComment(currentType, property);

      if (propertyType.isFunctionType()) {
        report.addFunction(getFunctionData(
            property.getName(),
            propertyType,
            property.getNode(),
            property.getJsDoc(),
            definedBy,
            definitions));
      } else {
        report.addProperty(getPropertyData(
            property.getName(),
            propertyType,
            property.getNode(),
            property.getJsDoc(),
            definedBy,
            definitions));
      }
    }

    linker.popContext();
    return report;
  }
  
  private JSType findPropertyType(Iterable<InstanceProperty> definitions) {
    for (InstanceProperty def : definitions) {
      if (!def.getType().isUnknownType()) {
        return def.getType();
      }
    }
    return definitions.iterator().next().getType();
  }

  private Set<JSType> getAssignableTypes(JSType type) {
    if (type.isConstructor() || type.isInterface()) {
      type = ((FunctionType) type).getInstanceType();
    }
    Set<JSType> types = new LinkedHashSet<>();
    types.add(type);
    for (JSType iface : typeRegistry.getDeclaredInterfaces(type)) {
      types.addAll(getAssignableTypes(iface));
    }
    List<JSType> typeHierarchy = typeRegistry.getTypeHierarchy(type);
    for (int i = 1; i < typeHierarchy.size(); i++) {
      types.addAll(getAssignableTypes(typeHierarchy.get(i)));
    }
    return types;
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
   * Finds the properties defined on a class.
   */
  @Nullable
  @CheckReturnValue
  private InstanceProperty findFirstClassOverride(Iterable<InstanceProperty> properties) {
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
      return property;
    }
    return null;
  }

  /**
   * Given a list of properties, finds those that are specified on an interface.
   */
  private Iterable<InstanceProperty> findSpecifications(Iterable<InstanceProperty> properties) {
    return FluentIterable.from(properties).filter(new Predicate<InstanceProperty>() {
      @Override
      public boolean apply(InstanceProperty property) {
        JSType definedOn = property.getDefinedOn();
        if (!definedOn.isInterface()) {
          JSType ctor = null;
          if (definedOn.isInstanceType()) {
            ctor = definedOn.toObjectType().getConstructor();
          }
          if (ctor == null || !ctor.isInterface()) {
            return false;
          }
        }
        return true;
      }
    });
  }

  public Function getFunctionData(
      String name,
      JSType type,
      Node node,
      @Nullable JsDoc jsDoc) {
    return getFunctionData(name, type, node, jsDoc, null,
        ImmutableList.<InstanceProperty>of());
  }

  private Function getFunctionData(
      String name,
      JSType type,
      Node node,
      @Nullable JsDoc jsDoc,
      @Nullable Comment definedBy,
      Iterable<InstanceProperty> overrides) {
    checkArgument(type.isFunctionType(), "%s is not a function type: %s", name, type);

    boolean isConstructor = type.isConstructor() && !isFunctionTypeConstructor(type);
    boolean isInterface = !isConstructor && type.isInterface();

    Function.Builder builder = Function.newBuilder()
        .setBase(getBasePropertyDetails(name, type, node, jsDoc, definedBy, overrides));
    
    if (isConstructor) {
      builder.setIsConstructor(true);
    }

    if (!isConstructor && !isInterface) {
      JsDoc returnDocs = findJsDoc(jsDoc, overrides, new Predicate<JsDoc>() {
        @Override
        public boolean apply(@Nullable JsDoc input) {
          return input != null && input.hasAnnotation(Annotation.RETURN);
        }
      });

      if (returnDocs != null) {
        Comment returnComment = parser.parseComment(
            returnDocs.getReturnClause().getDescription(), linker);
        if (returnComment.getTokenCount() > 0) {
          builder.getReturnBuilder().setDescription(returnComment);
        }
      }

      Comment returnType = getReturnType(returnDocs, overrides, (FunctionType) type);
      if (returnType.getTokenCount() > 0) {
        builder.getReturnBuilder().setType(returnType);
      }
    }

    if (jsDoc != null) {
      builder.addAllTemplateName(jsDoc.getTemplateTypeNames())
          .addAllThrown(buildThrowsData(jsDoc));
    }

    builder.addAllParameter(getParameters(type, node, jsDoc, overrides));

    return builder.build();
  }

  private Iterable<Function.Detail> getParameters(
      JSType type,
      Node node,
      @Nullable JsDoc docs,
      Iterable<InstanceProperty> overrides) {
    checkArgument(type.isFunctionType());
    
    JsDoc foundDocs = findJsDoc(docs, overrides, new Predicate<JsDoc>() {
      @Override
      public boolean apply(@Nullable JsDoc input) {
        return input != null && input.hasAnnotation(Annotation.PARAM);
      }
    });

    // Even though we found docs with @param annotations, they may have been
    // meaningless docs with a type, no name, and no description:
    //   \**
    //    * @param {number}
    //    * @param {number}
    //    *\
    //   Clazz.prototype.add = function(x, y) { return x + y; };
    if (foundDocs != null && !foundDocs.getParameters().isEmpty()) {
      return FluentIterable.from(foundDocs.getParameters())
          .transform(new com.google.common.base.Function<Parameter, Detail>() {
            @Override
            public Detail apply(Parameter input) {
              Detail.Builder detail = Detail.newBuilder().setName(input.getName());
              if (!isNullOrEmpty(input.getDescription())) {
                detail.setDescription(parser.parseComment(input.getDescription(), linker));
              }
              if (input.getType() != null) {
                detail.setType(linker.formatTypeExpression(input.getType()));
              }
              return detail.build();
            }
          });
    }
    
    if (isFunctionTypeConstructor(type)) {
      return ImmutableList.of();
    }

    List<Node> parameterNodes = Lists.newArrayList(((FunctionType) type).getParameters());
    List<Detail> details = new ArrayList<>(parameterNodes.size());
    @Nullable Node paramList = findParamList(node);

    for (int i = 0; i < parameterNodes.size(); i++) {
      Detail.Builder detail = Detail.newBuilder().setName("arg" + i);
      Node parameterNode = parameterNodes.get(i);
      if (!parameterNode.getJSType().isNoType() && !parameterNode.getJSType().isNoResolvedType()) {
        detail.setType(linker.formatTypeExpression(parameterNode));
      }
      if (paramList != null && i < paramList.getChildCount()) {
        String name = paramList.getChildAtIndex(i).getString();
        detail.setName(name);
      }
      details.add(detail.build());
    }
    return details;
  }

  /**
   * Returns whether the given {@code type} looks like the base function type constructor (that is,
   * {@code @type {!Function}}).
   */
  private static boolean isFunctionTypeConstructor(JSType type) {
    return type.isConstructor()
        && ((FunctionType) type).getInstanceType().isUnknownType();
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

  private Comment getReturnType(
      @Nullable JsDoc jsdoc,
      Iterable<InstanceProperty> overrides,
      FunctionType function) {
    JsDoc returnDocs = findJsDoc(jsdoc, overrides, new Predicate<JsDoc>() {
      @Override
      public boolean apply(@Nullable JsDoc input) {
        return input != null
            && input.getReturnClause().getType().isPresent();
      }
    });
    JSType returnType = function.getReturnType();
    if (returnType.isUnknownType() && returnDocs != null) {
      returnType = typeRegistry.evaluate(returnDocs.getReturnClause().getType().get());
    }
    if (returnType.isUnknownType()) {
      for (InstanceProperty property : overrides) {
        if (property.getType() != null && property.getType().isFunctionType()) {
          FunctionType fn = (FunctionType) property.getType();
          if (fn.getReturnType() != null && !fn.getReturnType().isUnknownType()) {
            returnType = fn.getReturnType();
            break;
          }
        }
      }
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
      JsDoc jsDoc) {
    return getPropertyData(name, type, node, jsDoc, null,
        ImmutableList.<InstanceProperty>of());
  }

  private com.github.jsdossier.proto.Property getPropertyData(
      String name,
      JSType type,
      Node node,
      @Nullable JsDoc jsDoc,
      @Nullable Comment definedBy,
      Iterable<InstanceProperty> overrides) {
    com.github.jsdossier.proto.Property.Builder builder =
        com.github.jsdossier.proto.Property.newBuilder()
            .setBase(getBasePropertyDetails(
                name, type, node, jsDoc, definedBy, overrides));

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
      JsDoc jsdoc,
      @Nullable Comment definedBy,
      Iterable<InstanceProperty> overrides) {
    BaseProperty.Builder builder = BaseProperty.newBuilder()
        .setName(name)
        .setDescription(findBlockComment(jsdoc, overrides))
        .setSource(linker.getSourceLink(node));

    if (definedBy != null) {
      builder.setDefinedBy(definedBy);
    }

    InstanceProperty immediateOverride = findFirstClassOverride(overrides);
    if (immediateOverride != null) {
      builder.setOverrides(getPropertyLink(immediateOverride));
    }
    
    for (InstanceProperty property : findSpecifications(overrides)) {
      builder.addSpecifiedBy(getPropertyLink(property));
    }

    if (jsdoc != null) {
      if (jsdoc.getVisibility() != JSDocInfo.Visibility.PUBLIC) {
        builder.setVisibility(Visibility.valueOf(jsdoc.getVisibility().name()));
      }
      
      if (jsdoc.isDeprecated()) {
        builder.getTagsBuilder().setIsDeprecated(true);
        builder.setDeprecation(parser.parseComment(jsdoc.getDeprecationReason(), linker));
      }

      if (!type.isFunctionType() && (jsdoc.isConst() || jsdoc.isDefine())) {
        builder.getTagsBuilder().setIsConst(true);
      }
    }
    return builder.build();
  }
  
  private Comment findBlockComment(JsDoc typeDocs, Iterable<InstanceProperty> overrides) {
    JsDoc docs = findJsDoc(typeDocs, overrides, new Predicate<JsDoc>() {
      @Override
      public boolean apply(@Nullable JsDoc input) {
        return input != null && !isNullOrEmpty(input.getBlockComment());
      }
    });
    return docs == null
        ? Comment.getDefaultInstance()
        : parser.parseComment(docs.getBlockComment(), linker);
  }

  @Nullable
  private JsDoc findJsDoc(
      JsDoc typeDocs,
      Iterable<InstanceProperty> overrides,
      Predicate<JsDoc> predicate) {
    if (predicate.apply(typeDocs)) {
      return typeDocs;
    }
    for (InstanceProperty property : overrides) {
      if (predicate.apply(property.getJsDoc())) {
        return property.getJsDoc();
      }
    }
    return null;
  }

  private Comment getPropertyLink(InstanceProperty property) {
    JSType type = property.getDefinedOn();
    if (type.isConstructor() || type.isInterface()) {
      type = ((FunctionType) type).getInstanceType();
    }
    type = stripTemplateTypeInformation(type);
    return addPropertyHash(linker.formatTypeExpression(type), property.getName());
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
    private List<com.github.jsdossier.proto.Property> compilerConstants = new ArrayList<>();

    private void addFunction(com.github.jsdossier.proto.Function function) {
      functions.add(function);
    }

    private void addProperty(com.github.jsdossier.proto.Property property) {
      properties.add(property);
    }
    
    private void addCompilerConstant(com.github.jsdossier.proto.Property property) {
      compilerConstants.add(property);
    }

    public List<com.github.jsdossier.proto.Function> getFunctions() {
      return functions;
    }

    public List<com.github.jsdossier.proto.Property> getProperties() {
      return properties;
    }

    public List<com.github.jsdossier.proto.Property> getCompilerConstants() {
      return compilerConstants;
    }
  }

  @VisibleForTesting
  static final class InstanceProperty {
    private final JSType definedOn;
    private final String name;
    private final JSType type;
    private final Node node;
    private final JsDoc jsDoc;

    private InstanceProperty(
        JSType definedOn, String name, JSType type, Node node, JSDocInfo info) {
      this.definedOn = definedOn;
      this.name = name;
      this.type = type;
      this.node = node;
      this.jsDoc = JsDoc.from(info);
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

    public JsDoc getJsDoc() {
      return jsDoc;
    }
  }
}
