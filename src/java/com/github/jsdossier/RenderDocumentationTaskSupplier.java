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

import static com.github.jsdossier.TypeInspector.fakeNodeForType;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Suppliers.memoize;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.skip;
import static com.google.common.collect.Lists.transform;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.github.jsdossier.jscomp.JsDoc;
import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.jscomp.Types;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Enumeration;
import com.github.jsdossier.proto.JsType;
import com.github.jsdossier.proto.JsTypeOrBuilder;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.PageData;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Generates tasks for rendering a list of types. */
@AutoFactory
final class RenderDocumentationTaskSupplier implements Supplier<ImmutableList<RenderTask>> {

  private final ImmutableList<NominalType> types;
  private final RenderDocumentationTaskSupplier_NominalTypeProcessorFactory processorFactory;
  private final RenderDocumentationTaskSupplier_RenderDocumentationTaskFactory renderTaskFactory;

  RenderDocumentationTaskSupplier(
      @Provided RenderDocumentationTaskSupplier_NominalTypeProcessorFactory processorFactory,
      @Provided RenderDocumentationTaskSupplier_RenderDocumentationTaskFactory renderTaskFactory,
      ImmutableList<NominalType> types) {
    this.processorFactory = processorFactory;
    this.renderTaskFactory = renderTaskFactory;
    this.types = types;
  }

  @Override
  public ImmutableList<RenderTask> get() {
    final List<NominalTypeProcessor> processors = transform(types, processorFactory::create);

    final Supplier<List<JsType>> typeSupplier =
        memoize(
            () -> {
              List<JsType> types1 = new ArrayList<>();
              for (NominalTypeProcessor processor : processors) {
                types1.add(processor.buildJsType());
              }
              return types1;
            });

    return processors
        .stream()
        .map(p -> renderTaskFactory.create(p.getHtmlOutput(), typeSupplier))
        .collect(toImmutableList());
  }

  @AutoFactory
  static final class RenderDocumentationTask implements RenderTask {
    private final PageRenderer renderer;
    private final Path output;
    private final Supplier<List<JsType>> types;

    RenderDocumentationTask(
        @Provided PageRenderer renderer, Path output, Supplier<List<JsType>> types) {
      this.renderer = renderer;
      this.output = output;
      this.types = types;
    }

    @Override
    public Path call() throws Exception {
      PageData page =
          PageData.newBuilder()
              .setTypes(PageData.TypeCollection.newBuilder().addAllType(types.get()))
              .build();

      renderer.render(output, page);
      return output;
    }
  }

  @AutoFactory
  static final class NominalTypeProcessor {
    private final DossierFileSystem dfs;
    private final CommentParser parser;
    private final TypeRegistry typeRegistry;
    private final JSTypeRegistry jsRegistry;
    private final StaticTypedScope globalScope;
    private final LinkFactory linkFactory;
    private final TypeExpressionParserFactory expressionParserFactory;
    private final TypeInspector typeInspector;
    private final IndexBuilder.IndexReference indexReference;
    private final NominalType type;

    NominalTypeProcessor(
        @Provided LinkFactoryBuilder linkFactoryBuilder,
        @Provided DossierFileSystem dfs,
        @Provided CommentParser parser,
        @Provided TypeRegistry typeRegistry,
        @Provided JSTypeRegistry jsTypeRegistry,
        @Provided StaticTypedScope globalScope,
        @Provided TypeExpressionParserFactory expressionParserFactory,
        @Provided TypeInspectorFactory typeInspectorFactory,
        @Provided IndexBuilder typeIndex,
        NominalType type) {
      this.dfs = dfs;
      this.parser = parser;
      this.typeRegistry = typeRegistry;
      this.jsRegistry = jsTypeRegistry;
      this.globalScope = globalScope;
      this.expressionParserFactory = expressionParserFactory;
      this.linkFactory = linkFactoryBuilder.create(type).withTypeContext(type);
      this.typeInspector = typeInspectorFactory.create(type);
      this.type = type;
      this.indexReference = updateTypeIndex(typeIndex);
    }

    private IndexBuilder.IndexReference updateTypeIndex(IndexBuilder typeIndex) {
      if (type.getModule().isPresent() && !type.getModule().get().isClosure()) {
        if (type.isModuleExports()) {
          return typeIndex.addModule(type);
        }

        Module module = type.getModule().get();
        NominalType moduleType = typeRegistry.getType(module.getId());
        IndexBuilder.IndexReference moduleRef = typeIndex.addModule(moduleType);
        return moduleRef.addNestedType(type);
      } else {
        return typeIndex.addType(type);
      }
    }

    Path getHtmlOutput() {
      return dfs.getPath(type);
    }

    JsType buildJsType() {
      JsType.Builder typeSpec =
          JsType.newBuilder()
              .setName(dfs.getDisplayName(type))
              .setQualifiedName(dfs.getQualifiedDisplayName(type))
              .setFilename(dfs.getPath(type).getFileName().toString())
              .setSource(
                  linkFactory.createSourceLink(type.getSourceFile(), type.getSourcePosition()));

      addDescription(typeSpec);
      addParentLink(typeSpec);
      addNestedTypeInfo(typeSpec);
      addTypedefInfo(typeSpec);
      addMainFunctionInfo(typeSpec);
      addTypeInheritanceInfo(typeSpec);
      addEnumValues(typeSpec);
      addStaticProperties(typeSpec);
      addInstanceProperties(typeSpec);

      JsDoc jsdoc = type.getJsDoc();
      typeSpec
          .getTagsBuilder()
          .setIsModule(type.isModuleExports())
          .setIsInterface(type.getType().isInterface())
          .setIsRecord(type.getType().isStructuralInterface())
          .setIsDeprecated(jsdoc.isDeprecated())
          .setIsFinal(jsdoc.isFinal())
          .setIsDict(jsdoc.isDict())
          .setIsStruct(jsdoc.isStruct());
      if (jsdoc.isDeprecated()) {
        typeSpec.setDeprecation(getDeprecation(jsdoc));
      }

      return typeSpec.build();
    }

    private void addDescription(JsType.Builder renderSpec) {
      Comment description = typeInspector.getTypeDescription();

      NominalType primary = getPrimaryDefinition(type);
      if (primary != type) {
        if (description.getTokenCount() == 0) {
          description =
              parser.parseComment(
                  primary.getJsDoc().getBlockComment(), linkFactory.withTypeContext(primary));
        }
      }

      renderSpec.setDescription(description);
    }

    private NominalType getPrimaryDefinition(NominalType type) {
      return typeRegistry.getTypes(type.getType()).iterator().next();
    }

    private void addParentLink(JsType.Builder spec) {
      if (type.isModuleExports() && !type.getModule().get().isClosure()) {
        return;
      }

      if (type.isNamespace()
          && !type.getModule().isPresent()
          && !typeRegistry.isProvided(type.getName())) {
        return;
      }

      NominalType declaringType;
      if (type.getModule().isPresent()) {
        if (type.isModuleExports()) {
          declaringType = type;
        } else {
          declaringType = getParent(type);
          while (declaringType != null && !declaringType.isModuleExports()) {
            declaringType = getParent(declaringType);
          }
        }
      } else {
        if (typeRegistry.isProvided(type.getName())) {
          declaringType = type;
        } else {
          declaringType = getParent(type);
          while (declaringType != null && !typeRegistry.isProvided(declaringType.getName())) {
            declaringType = getParent(declaringType);
          }
        }
      }

      if (declaringType != null) {
        JsType.Declaration.Builder metadata = spec.getDeclarationBuilder();
        metadata.setType(linkFactory.createTypeReference(declaringType));
        if (declaringType.getModule().isPresent()) {
          if (declaringType.getModule().get().isClosure()) {
            if (declaringType == type) {
              verify(declaringType.isModuleExports());
              metadata.setGoogModule(true);
            } else {
              metadata.setModuleExport(true);
            }
          } else {
            metadata.setModuleExport(true);
          }
        } else if (typeRegistry.isProvided(declaringType.getName())) {
          metadata.setGoogProvide(true);
        }
      }
    }

    @Nullable
    @CheckReturnValue
    private NominalType getParent(NominalType type) {
      String name = type.getName();
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
        if (typeRegistry.isType(name)) {
          return typeRegistry.getType(name);
        }
      }
      return null;
    }

    private void addNestedTypeInfo(JsType.Builder spec) {
      Iterable<NominalType> types =
          typeRegistry
              .getNestedTypes(type)
              .stream()
              .sorted(new QualifiedNameComparator())
              .collect(toList());
      for (NominalType child : types) {
        if (child.isNamespace() || child.getJsDoc().isTypedef()) {
          continue;
        }

        NamedType link = linkFactory.createTypeReference(child);
        Comment summary = typeInspector.getTypeDescription(child, true);

        JsType.TypeSummary.Builder summaryBuilder;
        if (child.getType().isConstructor()) {
          summaryBuilder = spec.getNestedBuilder().addClass_Builder();
        } else if (child.getType().isInterface()) {
          summaryBuilder = spec.getNestedBuilder().addInterfaceBuilder();
          summaryBuilder.getTagsBuilder().setIsInterface(true);
        } else if (child.getType().isEnumType()) {
          summaryBuilder = spec.getNestedBuilder().addEnumBuilder();
        } else {
          throw new AssertionError("unknown nested type: " + child.getName());
        }

        if (child.getJsDoc().isDeprecated()) {
          summaryBuilder.getTagsBuilder().setIsDeprecated(true);
        }

        summaryBuilder
            .setName(getNestedTypeName(child))
            .setHref(link.getLink().getHref())
            .setSummary(summary);
      }
    }

    private void addTypedefInfo(JsType.Builder spec) {
      Iterable<NominalType> typedefs =
          typeRegistry
              .getNestedTypes(type)
              .stream()
              .filter(NominalType::isTypedef)
              .sorted(new QualifiedNameComparator())
              .collect(toList());
      for (NominalType typedef : typedefs) {
        String name = getNestedTypeName(typedef);
        indexReference.addStaticProperty(name);
        JSDocInfo.Visibility visibility = typeRegistry.getVisibility(typedef);

        JSTypeExpression expression = checkNotNull(typedef.getJsDoc().getType());
        JSType type = Types.evaluate(expression, globalScope, jsRegistry);

        com.github.jsdossier.proto.Property.Builder builder = spec.addTypeDefBuilder();

        builder.setType(
            expressionParserFactory.create(linkFactory.withTypeContext(typedef)).parse(type));

        builder
            .getBaseBuilder()
            .setName(name)
            .setSource(
                linkFactory.createSourceLink(typedef.getSourceFile(), typedef.getSourcePosition()))
            .setDescription(
                parser.parseComment(
                    typedef.getJsDoc().getBlockComment(), linkFactory.withTypeContext(typedef)));
        if (JSDocInfo.Visibility.PUBLIC != visibility) {
          Protos.setVisibility(builder.getBaseBuilder().getVisibilityBuilder(), visibility);
        }

        if (typedef.getJsDoc().isDeprecated()) {
          builder.getBaseBuilder().getTagsBuilder().setIsDeprecated(true);
          builder.getBaseBuilder().setDeprecation(getDeprecation(typedef.getJsDoc()));
        }
      }
    }

    private String getNestedTypeName(NominalType child) {
      String parentName = type.getName();
      String childName = child.getName();
      verify(childName.startsWith(parentName + "."));
      childName = childName.substring(parentName.length() + 1);

      if (!type.isNamespace() && !type.isModuleExports()) {
        childName = getBasename(type) + "." + childName;
      }

      return childName;
    }

    private String getBasename(NominalType type) {
      String name = dfs.getDisplayName(type);
      int index = name.lastIndexOf('.');
      if (index != -1) {
        return name.substring(index + 1);
      } else if (type.isModuleExports() && (index = name.lastIndexOf('/')) != -1) {
        return name.substring(index + 1);
      }
      return name;
    }

    private void addMainFunctionInfo(JsType.Builder spec) {
      if (!type.getType().isFunctionType()) {
        return;
      }
      NominalType context = type;
      JsDoc docs = type.getJsDoc();

      if (isNullOrEmpty(docs.getOriginalCommentString())) {
        NominalType aliased = getPrimaryDefinition(type);
        if (aliased != null && aliased != type) {
          docs = aliased.getJsDoc();
          context = aliased;
        }
      }

      // TODO: should not be using Node here.
      Node fakeNode = fakeNodeForType(type);
      FunctionType mainFn =
          checkNotNull(
              type.getType().toMaybeFunctionType(),
              "Expected %s to be a function: %s",
              type.getName(),
              type.getType());
      spec.setMainFunction(
          typeInspector.getFunctionData(getBasename(type), mainFn, fakeNode, context, docs));
    }

    private void addTypeInheritanceInfo(JsType.Builder spec) {
      spec.addAllImplementedType(typeInspector.getImplementedTypes());
      spec.addAllImplementation(typeInspector.getKnownImplementations());
      spec.addAllSubtype(typeInspector.getSubtypes());

      List<NamedType> hierarchy = typeInspector.getTypeHierarchy();
      spec.addAllExtendedType(skip(hierarchy, 1)); // First entry is always the type itself.

      spec.addAllKnownAlias(typeInspector.getKnownAliases());
      NamedType aliasedType = typeInspector.getAliasedType();
      if (aliasedType != null) {
        spec.setAliasedType(aliasedType);
      }
    }

    private void addEnumValues(JsType.Builder spec) {
      if (!type.getType().isEnumType()) {
        return;
      }
      JSType elementType = ((EnumType) type.getType()).getElementsType();
      JSDocInfo.Visibility visibility = typeRegistry.getVisibility(type);

      Enumeration.Builder enumBuilder =
          spec.getEnumerationBuilder()
              .setType(
                  expressionParserFactory
                      .create(linkFactory)
                      .parse(elementType.toMaybeEnumElementType().getPrimitiveType()));
      if (JSDocInfo.Visibility.PUBLIC != visibility) {
        Protos.setVisibility(enumBuilder.getVisibilityBuilder(), visibility);
      }

      // Type may be documented as an enum without an associated object literal for us to analyze:
      //     /** @enum {string} */ namespace.foo;
      List<Property> properties = typeInspector.getProperties(type);
      properties.sort(comparing(Property::getName));
      for (Property property : properties) {
        if (!property.getType().isEnumElementType()) {
          continue;
        }

        Node node = property.getNode();
        JSDocInfo valueInfo = node == null ? null : node.getJSDocInfo();

        Enumeration.Value.Builder valueBuilder =
            enumBuilder.addValueBuilder().setName(property.getName());

        if (valueInfo != null) {
          JsDoc valueJsDoc = JsDoc.from(valueInfo);
          valueBuilder.setDescription(
              parser.parseComment(valueJsDoc.getBlockComment(), linkFactory));

          if (valueJsDoc.isDeprecated()) {
            valueBuilder.setDeprecation(getDeprecation(valueJsDoc));
          }
        }
      }
    }

    private void addStaticProperties(JsType.Builder spec) {
      TypeInspector.Report report = typeInspector.inspectType();

      for (com.github.jsdossier.proto.Property prop : report.getCompilerConstants()) {
        spec.addCompilerConstant(prop);
        if (!spec.hasAliasedType()) {
          indexReference.addStaticProperty(prop.getBase().getName());
        }
      }

      for (com.github.jsdossier.proto.Property prop : report.getProperties()) {
        if (prop.getBase().getTags().getIsModule()) {
          spec.addReexportedModule(prop);

        } else {
          spec.addStaticProperty(prop);
          if (!spec.hasAliasedType()) {
            indexReference.addStaticProperty(prop.getBase().getName());
          }
        }
      }

      for (com.github.jsdossier.proto.Function func : report.getFunctions()) {
        spec.addStaticFunction(func);
        if (!spec.hasAliasedType()) {
          indexReference.addStaticProperty(func.getBase().getName());
        }
      }
    }

    private void addInstanceProperties(JsType.Builder spec) {
      TypeInspector.Report report = typeInspector.inspectInstanceType();
      for (com.github.jsdossier.proto.Property prop : report.getProperties()) {
        spec.addField(prop);
        updateInstancePropertyIndex(spec, prop.getBase());
      }
      for (com.github.jsdossier.proto.Function func : report.getFunctions()) {
        spec.addMethod(func);
        updateInstancePropertyIndex(spec, func.getBase());
      }
    }

    private void updateInstancePropertyIndex(JsTypeOrBuilder spec, BaseProperty base) {
      // Do not include the property in the search index if the parent type is an alias,
      // the property is inherited from another type, or the property overrides a parent
      // property but does not provide a comment of its own.
      if (!spec.hasAliasedType()
          && !base.hasDefinedBy()
          && (!base.hasOverrides()
              || (base.hasDescription() && base.getDescription().getTokenCount() > 0))) {
        indexReference.addInstanceProperty(base.getName());
      }
    }

    private Comment getDeprecation(JsDoc jsdoc) {
      checkArgument(jsdoc.isDeprecated(), "no deprecation in jsdoc: %s", type.getName());
      return parser.parseComment(jsdoc.getDeprecationReason(), linkFactory);
    }
  }
}
