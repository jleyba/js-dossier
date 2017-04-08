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

import static com.github.jsdossier.TypeExpressions.NULL_TYPE;
import static com.github.jsdossier.TypeExpressions.VOID_TYPE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static java.util.stream.Collectors.toList;

import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NodeLibrary;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.RecordType;
import com.github.jsdossier.proto.TypeExpression;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.Ordering;
import com.google.common.html.types.SafeUrls;
import com.google.javascript.rhino.Node;
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
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Parses JS type expressions into Soy-friendly comment objects. */
@AutoFactory
final class TypeExpressionParser {

  private final DossierFileSystem dfs;
  private final TypeRegistry typeRegistry;
  private final JSTypeRegistry jsTypeRegistry;
  private final NodeLibrary nodeLibrary;
  private final LinkFactory linkFactory;

  TypeExpressionParser(
      @Provided DossierFileSystem dfs,
      @Provided TypeRegistry typeRegistry,
      @Provided JSTypeRegistry jsTypeRegistry,
      @Provided NodeLibrary nodeLibrary,
      LinkFactory linkFactory) {
    this.dfs = dfs;
    this.typeRegistry = typeRegistry;
    this.jsTypeRegistry = jsTypeRegistry;
    this.nodeLibrary = nodeLibrary;
    this.linkFactory = linkFactory;
  }

  /**
   * Converts the given JavaScript type to a type expression message.
   *
   * @param type the type to parse.
   */
  public TypeExpression parse(JSType type) {
    Parser parser = new Parser();
    return parser.parse(type);
  }

  /** A {@link JSType} visitor that converts the type into a type expression. */
  private class Parser implements Visitor<Void> {

    private final TypeExpression.Builder expression = TypeExpression.newBuilder();
    private final Deque<TypeExpression.Builder> expressions = new ArrayDeque<>();

    TypeExpression parse(JSType type) {
      expression.clear();
      expressions.clear();
      expressions.addLast(expression);
      type.visit(this);
      return expression.build();
    }

    @Nullable
    @CheckReturnValue
    private NominalType resolve(JSType type) {
      Collection<NominalType> types = typeRegistry.getTypes(type); // Exact check first.
      if (types.isEmpty()) {
        types = typeRegistry.findTypes(type); // Slow equivalence check next.
      }
      if (types.isEmpty()) {
        return null;
      }
      return types.iterator().next();
    }

    private com.github.jsdossier.proto.NamedType.Builder createNamedType(String name) {
      NominalType nominalType = linkFactory.getTypeContext().resolveType(name);
      if (nominalType != null) {
        return createNamedType(nominalType);
      }

      JSType jsType = jsTypeRegistry.getType(name);
      if (jsType != null) {
        nominalType = resolve(jsType);
        if (nominalType != null) {
          return createNamedType(nominalType);
        }
      }

      if (Module.Type.NODE.isModuleId(name)) {
        String id = Module.Type.NODE.stripModulePrefix(name);
        if (nodeLibrary.isModuleId(id)) {
          return com.github.jsdossier.proto.NamedType.newBuilder().setName(id);
        }

        int index = id.indexOf('.');
        if (index != 1 && nodeLibrary.isModuleId(id.substring(0, index))) {
          return com.github.jsdossier.proto.NamedType.newBuilder().setName(id);
        }
      }

      int index = name.indexOf("$$module$");
      if (index > 0) {
        name = name.substring(0, index);
        return createNamedType(name);
      }

      return com.github.jsdossier.proto.NamedType.newBuilder().setName(name);
    }

    @Nullable
    @CheckReturnValue
    private com.github.jsdossier.proto.NamedType.Builder createNamedType(JSType type) {
      NominalType ntype = resolve(type);
      if (ntype == null) {
        return null;
      }
      return createNamedType(ntype);
    }

    private com.github.jsdossier.proto.NamedType.Builder createNamedType(NominalType type) {
      return linkFactory.createTypeReference(type).toBuilder();
    }

    private TypeExpression.Builder currentExpression() {
      return expressions.getLast();
    }

    private void appendNativeType(String type) {
      com.github.jsdossier.proto.NamedType link =
          checkNotNull(linkFactory.createNativeExternLink(type));
      currentExpression().setNamedType(link);
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
        com.github.jsdossier.proto.NamedType link = linkFactory.createTypeReference(types.get(0));
        currentExpression().setNamedType(link);
      }
      return null;
    }

    @Override
    public Void caseAllType() {
      expressions.getLast().setAnyType(true);
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
        currentExpression().getNamedTypeBuilder().setName("Function");
        return null;
      }

      com.github.jsdossier.proto.FunctionType.Builder functionType =
          currentExpression().getFunctionTypeBuilder();

      if (type.isConstructor()) {
        functionType.setIsConstructor(true);
        expressions.addLast(functionType.getInstanceTypeBuilder());
        type.getTypeOfThis().visit(this);
        expressions.removeLast();

      } else if (!type.getTypeOfThis().isUnknownType()
          || type.getTypeOfThis() instanceof NamedType) {
        expressions.addLast(functionType.getInstanceTypeBuilder());
        type.getTypeOfThis().visit(this);
        expressions.removeLast();
      }

      for (Node node : type.getParameters()) {
        TypeExpression.Builder parameterType = functionType.addParameterBuilder();
        expressions.addLast(parameterType);

        if (node.isVarArgs()) {
          parameterType.setIsVarargs(true);
        }

        if (node.getJSType().isUnionType()) {
          caseUnionType((UnionType) node.getJSType(), node.isVarArgs() || node.isOptionalArg());
        } else {
          node.getJSType().visit(this);
        }

        if (node.isOptionalArg()) {
          // Not sure if this is possible, but varargs implies optional and we only permit one
          // bit to be set.
          if (!parameterType.getIsVarargs()) {
            parameterType.setIsOptional(true);
          }
        }

        expressions.removeLast();
      }

      if (type.getReturnType() != null && !type.isConstructor()) {
        expressions.addLast(functionType.getReturnTypeBuilder());
        type.getReturnType().visit(this);
        expressions.removeLast();
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

        com.github.jsdossier.proto.NamedType.Builder namedType =
            createNamedType(obj.getConstructor());
        if (namedType != null) {
          namedType.setName(namedType.getName() + ".prototype");
          currentExpression().setNamedType(namedType);
        } else {
          caseInstanceType(obj.getReferenceName() + ".prototype", obj);
        }
      } else if (!type.getOwnPropertyNames().isEmpty()) {
        com.github.jsdossier.proto.NamedType.Builder namedType = createNamedType(type);

        if (namedType == null && type.isEnumType()) {
          namedType =
              com.github.jsdossier.proto.NamedType.newBuilder().setName(type.getDisplayName());
        }

        if (namedType != null) {
          currentExpression().setNamedType(namedType);
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
      String displayName =
          nominalType == null ? type.getReferenceName() : dfs.getDisplayName(nominalType);
      caseInstanceType(displayName, type);
    }

    private void caseInstanceType(String displayName, ObjectType type) {
      TypeExpression.Builder expression = currentExpression();

      com.github.jsdossier.proto.UnionType.Builder unionType = null;
      if (type.isNullable()) {
        unionType = expression.getUnionTypeBuilder();
        expression = unionType.addTypeBuilder();
      }

      com.github.jsdossier.proto.NamedType.Builder namedType =
          createNamedType(type.getConstructor());
      if (namedType == null) {
        com.github.jsdossier.proto.NamedType link =
            linkFactory.createNativeExternLink(type.getReferenceName());
        if (link == null) {
          expression.setNamedType(linkFactory.resolveTypeReference(displayName));
        } else {
          expression.setNamedType(link);
        }
      } else {
        expression.setNamedType(namedType);
      }

      if (unionType != null && type.isNullable()) {
        unionType.addTypeBuilder().setNullType(true);
      }
    }

    private void caseRecordType(final ObjectType type) {
      Iterable<Property> properties =
          type.getOwnPropertyNames()
              .stream()
              .map(type::getOwnSlot)
              .filter(input -> input != null && !input.getType().isNoType())
              .sorted((o1, o2) -> o1.getName().compareTo(o2.getName()))
              .collect(toList());

      RecordType.Builder recordType = currentExpression().getRecordTypeBuilder();
      for (Property property : properties) {
        RecordType.Entry.Builder entry = recordType.addEntryBuilder();
        entry.setKey(property.getName());
        expressions.addLast(entry.getValueBuilder());
        property.getType().visit(this);
        expressions.removeLast();
      }
    }

    @Override
    public Void caseUnknownType() {
      currentExpression().setUnknownType(true);
      return null;
    }

    @Override
    public Void caseNullType() {
      currentExpression().setNullType(true);
      return null;
    }

    @Override
    public Void caseNamedType(NamedType type) {
      String name = type.getReferenceName();

      com.github.jsdossier.proto.NamedType.Builder namedType = createNamedType(name);
      if (SafeUrls.fromProto(namedType.getLink().getHref()).getSafeUrlString().isEmpty()) {
        // If there is no href, we were not able to resolve the type, so assume it is
        // nullable by default.
        currentExpression()
            .getUnionTypeBuilder()
            .addType(TypeExpression.newBuilder().setNamedType(namedType))
            .addType(NULL_TYPE);
      } else {
        currentExpression().setNamedType(namedType);
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
      currentExpression().setVoidType(true);
      return null;
    }

    @Override
    public Void caseUnionType(UnionType type) {
      caseUnionType(type, false);
      return null;
    }

    private void caseUnionType(UnionType type, boolean filterVoid) {
      com.github.jsdossier.proto.UnionType.Builder unionType =
          currentExpression().getUnionTypeBuilder();

      Set<TypeExpression> alternateTypes = new LinkedHashSet<>();
      for (JSType alternate : type.getAlternates()) {
        TypeExpression.Builder alternateType = TypeExpression.newBuilder();

        expressions.addLast(alternateType);
        alternate.visit(this);
        expressions.removeLast();

        if (TypeExpression.NodeTypeCase.UNION_TYPE.equals(alternateType.getNodeTypeCase())) {
          alternateTypes.addAll(alternateType.getUnionType().getTypeList());
        } else {
          alternateTypes.add(alternateType.build());
        }
      }

      if (filterVoid) {
        alternateTypes.remove(VOID_TYPE);
        if (alternateTypes.size() == 1) {
          currentExpression().mergeFrom(alternateTypes.iterator().next());
        }
        return;
      }

      unionType.addAllType(UNION_ORDERING.sortedCopy(alternateTypes));
      if (unionType.getTypeCount() == 1) {
        currentExpression().clearUnionType();
        currentExpression().mergeFrom(unionType.getType(0));
      }
    }

    @Override
    public Void caseTemplatizedType(TemplatizedType type) {
      type.getReferencedType().visit(this);
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
      }
      return null;
    }

    @Override
    public Void caseTemplateType(TemplateType templateType) {
      currentExpression().getNamedTypeBuilder().setName(templateType.getReferenceName());
      return null;
    }
  }

  private static final Ordering<TypeExpression> UNION_ORDERING =
      Ordering.from(
          (o1, o2) -> {
            if (o1.equals(o2)) {
              return 0;
            }
            if (o1.getAnyType() || o2.getAnyType()) {
              return o1.getAnyType() ? 1 : -1;
            }
            if (o1.getVoidType() || o2.getVoidType()) {
              return o1.getVoidType() ? 1 : -1;
            }
            if (o1.getNullType() || o2.getNullType()) {
              return o1.getNullType() ? 1 : -1;
            }
            if (o1.getUnknownType() || o2.getUnknownType()) {
              return o1.getUnknownType() ? 1 : -1;
            }
            return 0;
          });
}
