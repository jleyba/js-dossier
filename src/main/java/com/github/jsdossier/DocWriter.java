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
package com.github.jsdossier;

import static com.github.jsdossier.CommentUtil.getBlockDescription;
import static com.github.jsdossier.CommentUtil.getFileoverview;
import static com.github.jsdossier.CommentUtil.getSummary;
import static com.github.jsdossier.CommentUtil.parseComment;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;
import static java.nio.file.Files.createDirectories;

import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Deprecation;
import com.github.jsdossier.proto.Enumeration;
import com.github.jsdossier.proto.Index;
import com.github.jsdossier.proto.IndexFileRenderSpec;
import com.github.jsdossier.proto.JsType;
import com.github.jsdossier.proto.JsTypeRenderSpec;
import com.github.jsdossier.proto.Resources;
import com.github.jsdossier.proto.SourceFile;
import com.github.jsdossier.proto.SourceFileRenderSpec;
import com.github.jsdossier.proto.TypeLink;
import com.github.jsdossier.proto.Visibility;
import com.github.jsdossier.soy.Renderer;
import com.github.rjeschke.txtmark.Processor;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.TemplatizedType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Generates HTML documentation.
 */
class DocWriter {

  private static final String INDEX_FILE_NAME = "index.html";

  private final Config config;
  private final TypeRegistry typeRegistry;
  private final Linker linker;

  private final Renderer renderer = new Renderer();

  private Iterable<Path> sortedFiles;
  private Iterable<NominalType> sortedTypes;
  private Iterable<NominalType> sortedModules;
  private Index masterIndex;

  DocWriter(Config config, TypeRegistry typeRegistry) {
    this.config = checkNotNull(config);
    this.typeRegistry = checkNotNull(typeRegistry);
    this.linker = new Linker(config, typeRegistry);
  }

  public void generateDocs() throws IOException {
    sortedFiles = FluentIterable.from(concat(config.getSources(), config.getModules()))
        .toSortedList(new PathComparator());
    sortedTypes = FluentIterable.from(typeRegistry.getNominalTypes())
        .filter(isNonEmpty())
        .filter(not(isTypedef()))
        .toSortedList(new QualifiedNameComparator());
    sortedModules = FluentIterable.from(typeRegistry.getModules())
        .toSortedList(new DisplayNameComparator());

    masterIndex = generateNavIndex();

    createDirectories(config.getOutput());
    copyResources();
    copySourceFiles();
    generateIndex();

    for (NominalType type : sortedTypes) {
      if (isEmptyNamespace(type)) {
        continue;
      }
      generateDocs(type);
    }

    for (NominalType module : sortedModules) {
      generateModuleDocs(module);
    }

    writeTypesJson();
  }

  private Index generateNavIndex(Path path) {
    Index.Builder builder = Index.newBuilder()
        .mergeFrom(masterIndex);

    Path toRoot = path.getParent().relativize(config.getOutput());

    builder.setHome(toUrlPath(toRoot.resolve(INDEX_FILE_NAME)));

    for (Index.Module.Builder module : builder.getModuleBuilderList()) {
      module.getLinkBuilder().setHref(
          toUrlPath(toRoot.resolve(module.getLinkBuilder().getHref())));
    }

    for (TypeLink.Builder link : builder.getTypeBuilderList()) {
      link.setHref(toUrlPath(toRoot.resolve(link.getHref())));
    }

    return builder.build();
  }

  private static String toUrlPath(Path p) {
    return Joiner.on('/').join(p.iterator());
  }

  private Index generateNavIndex() {
    Index.Builder builder = Index.newBuilder();

    builder.setHome(INDEX_FILE_NAME);

    for (NominalType module : sortedModules) {
      Index.Module.Builder moduleBuilder = builder.addModuleBuilder()
          .setLink(TypeLink.newBuilder()
          .setHref(toUrlPath(config.getOutput().relativize(linker.getFilePath(module))))
          .setText(linker.getDisplayName(module)));

      Iterable<NominalType> types = FluentIterable.from(module.getTypes())
          .toSortedList(new QualifiedNameComparator());
      for (NominalType type : types) {
        if (isEmptyNamespace(type)) {
          continue;
        }
        Path path = linker.getFilePath(type);
        moduleBuilder.addTypeBuilder()
            .setHref(toUrlPath(config.getOutput().relativize(path)))
            .setText(type.getQualifiedName(false));
      }
    }

    for (NominalType type : sortedTypes) {
      if (isEmptyNamespace(type)) {
        continue;
      }

      Path path = linker.getFilePath(type);
      builder.addTypeBuilder()
          .setHref(toUrlPath(config.getOutput().relativize(path)))
          .setText(type.getQualifiedName());
    }

    return builder.build();
  }

  private void generateIndex() throws IOException {
    Comment readme = Comment.getDefaultInstance();
    if (config.getReadme().isPresent()) {
      String text = new String(Files.readAllBytes(config.getReadme().get()), Charsets.UTF_8);
      String readmeHtml = Processor.process(text);
      // One more pass to process any inline taglets (e.g. {@code} or {@link}).
      readme = parseComment(readmeHtml, linker);
    }

    Path index = config.getOutput().resolve(INDEX_FILE_NAME);
    IndexFileRenderSpec.Builder spec = IndexFileRenderSpec.newBuilder()
        .setResources(getResources(index))
        .setIndex(masterIndex)
        .setReadme(readme);
    renderer.render(index, spec.build());
  }

  private void generateModuleDocs(NominalType module) throws IOException {
    linker.pushContext(module);

    Path output = linker.getFilePath(module);
    createDirectories(output.getParent());

    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setName(linker.getDisplayName(module))
        .setSource(linker.getSourceLink(module.getNode()))
        .setDescription(getFileoverview(linker,
            typeRegistry.getFileOverview(module.getModule().getPath())))
        .addAllNested(getNestedTypeInfo(module))
        .addAllTypeDef(getTypeDefInfo(module))
        .addAllExtendedType(getInheritedTypes(module))
        .addAllImplementedType(getImplementedTypes(module));

    JSType jsType = module.getJsType();
    JsDoc jsdoc = module.getJsdoc();

    jsTypeBuilder.getTagsBuilder()
        .setIsModule(true)
        .setIsInterface(jsType.isInterface())
        .setIsDeprecated(jsdoc != null && jsdoc.isDeprecated())
        .setIsFinal(jsdoc != null && jsdoc.isFinal())
        .setIsDict(jsdoc != null && jsdoc.isDict())
        .setIsStruct(jsdoc != null && jsdoc.isStruct());

    getStaticData(jsTypeBuilder, module);
    getPrototypeData(jsTypeBuilder, module);

    if (jsdoc != null && jsdoc.isDeprecated()) {
      jsTypeBuilder.setDeprecation(getDeprecation(jsdoc));
    }

    if (jsType.isEnumType()) {
      extractEnumData(module, jsTypeBuilder.getEnumerationBuilder());
    }

    if (jsType.isFunctionType()) {
      JsDoc docs = module.getJsdoc();
      if (docs == null || isNullOrEmpty(docs.getOriginalCommentString())) {
        ModuleDescriptor internalModule = module.getModule();
        docs = JsDoc.from(internalModule.getInternalVarDocs(module.getName()));
      }
      jsTypeBuilder.setMainFunction(getFunctionData(
          linker.getDisplayName(module),
          module.getJsType(),
          module.getNode(),
          docs));
    }

    JsTypeRenderSpec.Builder spec = JsTypeRenderSpec.newBuilder()
        .setResources(getResources(output))
        .setType(jsTypeBuilder.build())
        .setIndex(generateNavIndex(output));
    renderer.render(output, spec.build());

    for (NominalType type : module.getTypes()) {
      if (!isEmptyNamespace(type)) {
        generateDocs(type);
      }
    }
    linker.popContext();
  }

  private void generateDocs(NominalType type) throws IOException {
    linker.pushContext(type);

    Path output = linker.getFilePath(type);
    createDirectories(output.getParent());

    String name = type.getQualifiedName(type.isNamespace());
    if (type.getModule() != null
        && type.getModule().isCommonJsModule()
        && name.startsWith(type.getModule().getName())) {
      name = name.substring(type.getModule().getName().length() + 1);
    }
    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setName(name);

    addParentLink(jsTypeBuilder, type);

    Comment description = getBlockDescription(linker, type);
    NominalType aliased = typeRegistry.resolve(type.getJsType());

    if (aliased != type) {
      String aliasDisplayName = linker.getDisplayName(aliased);
      if (aliased.isCommonJsModule()) {
        if (aliased.isModuleExports()) {
          aliasDisplayName = "module(" + aliasDisplayName + ")";
        } else {
          aliasDisplayName =
              "module(" + linker.getDisplayName(aliased.getModule()) + ")." + aliasDisplayName;
        }
      }
      jsTypeBuilder.setAliasedType(TypeLink.newBuilder()
          .setText(aliasDisplayName)
          .setHref(linker.getFilePath(aliased).getFileName().toString()));

      if (description.getTokenCount() == 0) {
        description = getBlockDescription(linker, aliased);
      }
    }

    jsTypeBuilder
        .addAllNested(getNestedTypeInfo(type))
        .setSource(linker.getSourceLink(type.getNode()))
        .setDescription(description)
        .addAllTypeDef(getTypeDefInfo(type))
        .addAllExtendedType(getInheritedTypes(type))
        .addAllImplementedType(getImplementedTypes(type));

    JSType jsType = type.getJsType();
    JsDoc jsdoc = type.getJsdoc();

    jsTypeBuilder.getTagsBuilder()
        .setIsInterface(jsType.isInterface())
        .setIsDeprecated(jsdoc != null && jsdoc.isDeprecated())
        .setIsFinal(jsdoc != null && jsdoc.isFinal())
        .setIsDict(jsdoc != null && jsdoc.isDict())
        .setIsStruct(jsdoc != null && jsdoc.isStruct());

    getStaticData(jsTypeBuilder, type);
    getPrototypeData(jsTypeBuilder, type);

    if (jsdoc != null && jsdoc.isDeprecated()) {
      jsTypeBuilder.setDeprecation(getDeprecation(jsdoc));
    }

    if (jsType.isEnumType()) {
      extractEnumData(type, jsTypeBuilder.getEnumerationBuilder());
    }

    if (jsType.isFunctionType()) {
      JsDoc docs = type.getJsdoc();
      if (aliased != null && aliased != type && aliased.getJsdoc() != null) {
        docs = aliased.getJsdoc();
      }
      jsTypeBuilder.setMainFunction(getFunctionData(
          name,
          type.getJsType(),
          type.getNode(),
          docs));
    }

    JsTypeRenderSpec.Builder spec = JsTypeRenderSpec.newBuilder()
        .setResources(getResources(output))
        .setType(jsTypeBuilder.build())
        .setIndex(generateNavIndex(output));
    renderer.render(output, spec.build());
    linker.popContext();
  }

  private void copyResources() throws IOException {
    FileSystem fs = config.getOutput().getFileSystem();
    copyResource(fs.getPath("resources/dossier.css"), config.getOutput());
    copyResource(fs.getPath("resources/dossier.js"), config.getOutput());
  }

  private static void copyResource(Path resourcePath, Path outputDir) throws IOException {
    try (InputStream stream = DocPass.class.getResourceAsStream(resourcePath.toString())) {
      Path outputPath = outputDir.resolve(resourcePath.getFileName());
      Files.copy(stream, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void writeTypesJson() throws IOException {
    JsonArray files = new JsonArray();
    for (Path source : sortedFiles) {
      Path displayPath = config.getSrcPrefix().relativize(source);
      String dest = config.getOutput().relativize(
          linker.getFilePath(source)).toString();

      JsonObject obj = new JsonObject();
      obj.addProperty("name", displayPath.toString());
      obj.addProperty("href", dest);

      files.add(obj);
    }

    JsonArray modules = new JsonArray();
    for (NominalType module : sortedModules) {
      String dest = config.getOutput().relativize(linker.getFilePath(module)).toString();

      JsonObject obj = new JsonObject();
      obj.addProperty("name", linker.getDisplayName(module));
      obj.addProperty("href", dest);
      obj.add("types", getTypeInfo(module.getTypes()));
      modules.add(obj);
    }

    JsonObject json = new JsonObject();
    json.add("files", files);
    json.add("modules", modules);
    json.add("types", getTypeInfo(sortedTypes));

    // NOTE: JSON is not actually a subset of JavaScript, but in our case we know we only
    // have valid JavaScript input, so we can use JSONObject#toString() as a quick-and-dirty
    // formatting mechanism.
    String content = "var TYPES = " + json + ";";

    Path outputPath = config.getOutput().resolve("types.js");
    Files.write(outputPath, content.getBytes(Charsets.UTF_8));
  }

  private JsonArray getTypeInfo(Iterable<NominalType> types) {
    JsonArray array = new JsonArray();
    for (NominalType type : types) {
      if (isEmptyNamespace(type)) {
        continue;
      }

      String dest = config.getOutput().relativize(linker.getFilePath(type)).toString();

      JsonObject details = new JsonObject();
      details.addProperty("name", type.getQualifiedName());
      details.addProperty("href", dest);
      details.addProperty("isInterface", type.getJsdoc() != null && type.getJsdoc().isInterface());
      details.addProperty("isTypedef", type.getJsdoc() != null && type.getJsdoc().isTypedef());
      array.add(details);

      // Also include typedefs. These will not be included in the main
      // index, but will be searchable.
      List<NominalType> typedefs = FluentIterable.from(type.getTypes())
          .filter(isTypedef())
          .toSortedList(new NameComparator());
      for (NominalType typedef : typedefs) {
        TypeLink link = linker.getLink(typedef);
        if (link == null) {
          continue;  // TODO: decide what to do with global type links.
        }
        JsonObject typedefDetails = new JsonObject();
        typedefDetails.addProperty("name", link.getText());
        typedefDetails.addProperty("href", link.getHref());
        typedefDetails.addProperty("isTypedef", true);
        array.add(typedefDetails);
      }
    }
    return array;
  }

  private Resources getResources(Path forPathFromRoot) {
    Path pathToRoot = config.getOutput()
        .resolve(forPathFromRoot)
        .getParent()
        .relativize(config.getOutput());
    return Resources.newBuilder()
        .addCss(pathToRoot.resolve("dossier.css").toString())
        .addTailScript(pathToRoot.resolve("types.js").toString())
        .addTailScript(pathToRoot.resolve("dossier.js").toString())
        .build();
  }

  private void copySourceFiles() throws IOException {
    for (Path source : concat(config.getSources(), config.getModules())) {
      Path displayPath = config.getSrcPrefix().relativize(source);
      Path renderPath = config.getOutput()
          .resolve("source")
          .resolve(config.getSrcPrefix()
              .relativize(source.toAbsolutePath().normalize())
              .resolveSibling(source.getFileName() + ".src.html"));

      SourceFile file = SourceFile.newBuilder()
          .setBaseName(source.getFileName().toString())
          .setPath(displayPath.toString())
          .addAllLines(Files.readAllLines(source, Charsets.UTF_8))
          .build();

      SourceFileRenderSpec.Builder spec = SourceFileRenderSpec.newBuilder()
          .setFile(file)
          .setResources(getResources(renderPath))
          .setIndex(generateNavIndex(renderPath));

      renderer.render(renderPath, spec.build());
    }
  }

  private void addParentLink(JsType.Builder jsTypeBuilder, NominalType type) {
    if (type.isModuleExports()) {
      return;
    }
    if (type.isNamespace()) {
      return;
    }
    NominalType parent;
    if (type.getModule() != null) {
      parent = type.getParent();
      while (!parent.isModuleExports()) {
        parent = parent.getParent();
      }
    } else {
      parent = type.getParent();
      while (parent != null && !parent.isNamespace()) {
        parent = parent.getParent();
      }
    }

    if (parent != null) {
      jsTypeBuilder.getParentBuilder()
          .setLink(linker.getLink(parent))
          .setIsModule(parent.isModuleExports() && parent.isCommonJsModule());
    }
  }

  private List<Comment> getInheritedTypes(NominalType nominalType) {
    JSType type = nominalType.getJsType();
    if (!type.isConstructor()) {
      return ImmutableList.of();
    }

    LinkedList<JSType> types = typeRegistry.getTypeHierarchy(type);
    List<Comment> list = Lists.newArrayListWithExpectedSize(types.size());
    // Skip bottom of stack (type of this). Handled specially below.
    while (types.size() > 1) {
      JSType base = types.pop();
      if (base.isConstructor() || base.isInterface()) {
        base = ((FunctionType) base).getInstanceType();
      }
      verify(base.isInstanceType() || base instanceof NamedType);
      list.add(linker.formatTypeExpression(base));
    }
    list.add(Comment.newBuilder()
        .addToken(Comment.Token.newBuilder()
            .setText(linker.getDisplayName(nominalType)))
        .build());
    return list;
  }

  private Iterable<Comment> getImplementedTypes(NominalType nominalType) {
    Set<JSType> interfaces = typeRegistry.getImplementedTypes(nominalType);
    return transform(Ordering.usingToString().sortedCopy(interfaces),
        new Function<JSType, Comment>() {
          @Override
          public Comment apply(JSType input) {
            return linker.formatTypeExpression(input);
          }
        }
    );
  }

  private void extractEnumData(NominalType type, Enumeration.Builder enumBuilder) {
    checkArgument(type.getJsType().isEnumType());

    JSType elementType = ((EnumType) type.getJsType()).getElementsType();
    enumBuilder.setType(linker.formatTypeExpression(elementType));

    JsDoc jsdoc = type.getJsdoc();
    if (jsdoc != null) {
      enumBuilder.setVisibility(Visibility.valueOf(jsdoc.getVisibility().name()));
    }

    // Type may be documented as an enum without an associated object literal for us to analyze:
    //     /** @enum {string} */ namespace.foo;
    List<Property> properties = FluentIterable.from(type.getProperties())
        .toSortedList(new PropertyNameComparator());
    for (Property property : properties) {
      if (!property.getType().isEnumElementType()) {
        continue;
      }

      Node node = property.getNode();
      JSDocInfo valueInfo = node == null ? null : node.getJSDocInfo();

      Enumeration.Value.Builder valueBuilder = enumBuilder.addValueBuilder()
          .setName(property.getName());

      if (valueInfo != null) {
        JsDoc valueJsDoc = new JsDoc(valueInfo);
        valueBuilder.setDescription(parseComment(valueJsDoc.getBlockComment(), linker));

        if (valueJsDoc.isDeprecated()) {
          valueBuilder.setDeprecation(getDeprecation(valueJsDoc));
        }
      }
    }
  }

  private List<JsType.TypeDef> getTypeDefInfo(final NominalType type) {
    return FluentIterable.from(type.getTypes())
        .filter(isTypedef())
        .transform(new Function<NominalType, JsType.TypeDef>() {
          @Override
          public JsType.TypeDef apply(NominalType typedef) {
            JsDoc jsdoc = checkNotNull(typedef.getJsdoc());
            String name = type.getName() + "." + typedef.getName();

            JsType.TypeDef.Builder builder = JsType.TypeDef.newBuilder()
                .setName(name)
                .setType(linker.formatTypeExpression(jsdoc.getType()))
                .setSource(linker.getSourceLink(typedef.getNode()))
                .setDescription(getBlockDescription(linker, typedef))
                .setVisibility(Visibility.valueOf(jsdoc.getVisibility().name()));

            if (jsdoc.isDeprecated()) {
              builder.setDeprecation(getDeprecation(jsdoc));
            }

            return builder.build();
          }
        })
        .toSortedList(new Comparator<JsType.TypeDef>() {
          @Override
          public int compare(JsType.TypeDef a, JsType.TypeDef b) {
            return a.getName().compareTo(b.getName());
          }
        });
  }

  private Deprecation getDeprecation(JsDoc jsdoc) {
    checkArgument(jsdoc != null, "null jsdoc");
    checkArgument(jsdoc.isDeprecated(), "no deprecation in jsdoc");
    return Deprecation.newBuilder()
        .setNotice(parseComment(jsdoc.getDeprecationReason(), linker))
        .build();
  }

  private List<JsType.TypeSummary> getNestedTypeInfo(NominalType parent) {
    Collection<NominalType> nestedTypes = parent.getTypes();
    List<JsType.TypeSummary> types = new ArrayList<>(nestedTypes.size());

    for (NominalType child : FluentIterable.from(nestedTypes).toSortedList(new NameComparator())) {
      JSType childType = child.getJsType();

      if (!childType.isConstructor() && !childType.isEnumType() && !childType.isInterface()) {
        continue;
      }

      String href = linker.getFilePath(child).getFileName().toString();

      Comment summary = getSummary("No description.", linker);

      JsDoc jsdoc = child.getJsdoc();
      if (jsdoc != null && !isNullOrEmpty(jsdoc.getBlockComment())) {
        summary =  getSummary(jsdoc.getBlockComment(), linker);
      } else {
        NominalType aliased = typeRegistry.resolve(childType);
        if (aliased != null
            && aliased != child
            && aliased.getJsdoc() != null
            && !isNullOrEmpty(aliased.getJsdoc().getBlockComment())) {
          summary = getSummary(aliased.getJsdoc().getBlockComment(), linker);
        }
      }

      types.add(JsType.TypeSummary.newBuilder()
          .setHref(href)
          .setSummary(summary)
          .setName(child.getQualifiedName(false))
          .build());
    }

    return types;
  }

  private void getPrototypeData(JsType.Builder jsTypeBuilder, NominalType nominalType) {
    Iterable<JSType> assignableTypes;
    if (nominalType.getJsType().isConstructor()) {
      assignableTypes = Iterables.concat(
          Lists.reverse(typeRegistry.getTypeHierarchy(nominalType.getJsType())),
          typeRegistry.getImplementedTypes(nominalType));

    } else if (nominalType.getJsType().isInterface()) {
      assignableTypes = Iterables.concat(
          ImmutableSet.of(nominalType.getJsType()),
          typeRegistry.getImplementedTypes(nominalType));
    } else {
      return;
    }

    Multimap<String, InstanceProperty> properties = MultimapBuilder
        .treeKeys()
        .linkedHashSetValues()
        .build();

    for (JSType assignableType : assignableTypes) {
      if (assignableType.isConstructor() || assignableType.isInterface()) {
        assignableType = ((FunctionType) assignableType).getInstanceType();
      }

      ObjectType object = assignableType.toObjectType();
      FunctionType ctor = object.getConstructor();
      Set<String> ownProps = new HashSet<>();

      for (String pname : object.getOwnPropertyNames()) {
        if (!"constructor".equals(pname)) {
          ownProps.add(pname);
          Property property = object.getOwnSlot(pname);
          properties.put(pname, new InstanceProperty(
              object,
              property.getName(),
              getType(object, property),
              property.getNode(),
              property.getJSDocInfo()));
        }
      }

      if (ctor == null) {
        continue;
      }

      ObjectType prototype = ObjectType.cast(ctor.getPropertyType("prototype"));
      verify(prototype != null);

      for (String pname : prototype.getOwnPropertyNames()) {
        if (!"constructor".equals(pname) && !ownProps.contains(pname)) {
          Property property = prototype.getOwnSlot(pname);
          properties.put(pname, new InstanceProperty(
              object,
              property.getName(),
              getType(object, property),
              property.getNode(),
              property.getJSDocInfo()));
        }
      }
    }

    if (properties.isEmpty()) {
      return;
    }

    JSType docType = nominalType.getJsType();
    if (docType.isConstructor() || docType.isInterface()) {
      docType = ((FunctionType) docType).getInstanceType();
    }

    for (String key : properties.keySet()) {
      LinkedList<InstanceProperty> definitions = new LinkedList<>(properties.get(key));
      InstanceProperty property = definitions.removeFirst();

      Comment definedBy = null;
      if (!docType.equals(property.getDefinedOn())) {
        JSType definedByType = stripTemplateTypeInformation(property.getDefinedOn());
        definedBy = linker.formatTypeExpression(definedByType);
      }
      Comment overrides = findOverriddenType(definitions);
      Iterable<Comment> specifications = findSpecifications(definitions);

      JsDoc jsdoc = JsDoc.from(property.getJSDocInfo());
      if (jsdoc != null && jsdoc.getVisibility() == JSDocInfo.Visibility.PRIVATE) {
        continue;
      }

      JSType propType = property.getType();
      if (propType.isFunctionType()) {
        jsTypeBuilder.addMethod(getFunctionData(
            property.getName(),
            propType,
            property.getNode(),
            jsdoc,
            definedBy,
            overrides,
            specifications));
      } else {
        jsTypeBuilder.addField(getPropertyData(
            property.getName(),
            propType,
            property.getNode(),
            jsdoc,
            definedBy,
            overrides,
            specifications));
      }
    }
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
      definedOn = stripTemplateTypeInformation(definedOn);
      return linker.formatTypeExpression(definedOn);
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
      specifications.add(linker.formatTypeExpression(definedOn));
    }
    return specifications;
  }

  private static JSType stripTemplateTypeInformation(JSType type) {
    if (type.isTemplatizedType()) {
      return ((TemplatizedType) type).getReferencedType();
    }
    return type;
  }

  private static JSType getType(ObjectType object, Property property) {
    JSType type = object.getPropertyType(property.getName());
    if (type.isUnknownType()) {
      type = property.getType();
    }
    return type;
  }

  private void getStaticData(JsType.Builder jsTypeBuilder, NominalType type) {
    ImmutableList<Property> properties = FluentIterable.from(type.getProperties())
        .toSortedList(new PropertyNameComparator());

    for (Property property : properties) {
      String name = property.getName();
      if (!type.isModuleExports() && !type.isNamespace()) {
        name = type.getName() + "." + name;
      }

      JsDoc jsdoc = JsDoc.from(property.getJSDocInfo());

      // If this property does not have any docs and is part of a CommonJS module's exported API,
      // check if the property is a reference to one of the module's internal variables and we
      // can use those docs instead.
      if ((jsdoc == null || isNullOrEmpty(jsdoc.getOriginalCommentString()))
          && type.isModuleExports()) {
        String internalName = type.getModule().getInternalName(
            type.getQualifiedName(true) + "." + property.getName());
        jsdoc = JsDoc.from(type.getModule().getInternalVarDocs(internalName));
      }

      if (jsdoc != null && jsdoc.getVisibility() == JSDocInfo.Visibility.PRIVATE) {
        continue;
      }

      if (jsdoc != null && jsdoc.isDefine()) {
        jsTypeBuilder.addCompilerConstant(getPropertyData(
            name,
            property.getType(),
            property.getNode(),
            jsdoc));

      } else if (property.getType().isFunctionType()) {
        jsTypeBuilder.addStaticFunction(getFunctionData(
            name,
            property.getType(),
            property.getNode(),
            jsdoc));

      } else if (!property.getType().isEnumElementType()) {
        jsTypeBuilder.addStaticProperty(getPropertyData(
            name,
            property.getType(),
            property.getNode(),
            jsdoc));
      }
    }
  }

  private BaseProperty getBasePropertyDetails(
      String name, JSType type, Node node, @Nullable JsDoc jsdoc,
      @Nullable Comment definedBy,
      @Nullable Comment overrides,
      Iterable<Comment> specifications) {
    BaseProperty.Builder builder = BaseProperty.newBuilder()
        .setName(name)
        .setDescription(getBlockDescription(linker, jsdoc))
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
        builder.setDeprecation(getDeprecation(jsdoc));
      }
    }
    return builder.build();
  }

  private com.github.jsdossier.proto.Property getPropertyData(
      String name, JSType type, Node node, @Nullable JsDoc jsDoc) {
    return getPropertyData(
        name, type, node, jsDoc, null, null, ImmutableList.<Comment>of());
  }

  private com.github.jsdossier.proto.Property getPropertyData(
      String name, JSType type, Node node, @Nullable JsDoc jsDoc,
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

  private com.github.jsdossier.proto.Function getFunctionData(
      String name, JSType type, Node node, @Nullable JsDoc jsDoc) {
    return getFunctionData(
        name, type, node, jsDoc, null, null, ImmutableList.<Comment>of());
  }

  private com.github.jsdossier.proto.Function getFunctionData(
      String name, JSType type, Node node, @Nullable JsDoc jsDoc,
      @Nullable Comment definedBy,
      @Nullable Comment overrides,
      Iterable<Comment> specifications) {
    checkArgument(type.isFunctionType());

    boolean isConstructor = type.isConstructor();
    boolean isInterface = !isConstructor && type.isInterface();

    com.github.jsdossier.proto.Function.Builder builder =
        com.github.jsdossier.proto.Function.newBuilder()
            .setBase(getBasePropertyDetails(
                name, type, node, jsDoc, definedBy, overrides, specifications))
        .setIsConstructor(isConstructor);

    if (!isConstructor && !isInterface) {
      com.github.jsdossier.proto.Function.Detail.Builder detail =
          com.github.jsdossier.proto.Function.Detail.newBuilder();
      detail.setType(getReturnType(jsDoc, type));
      if (jsDoc != null) {
        detail.setDescription(parseComment(jsDoc.getReturnDescription(), linker));
      }
      builder.setReturn(detail);
    }

    if (jsDoc != null) {
      builder.addAllTemplateName(jsDoc.getTemplateTypeNames())
          .addAllThrown(buildThrowsData(jsDoc));
    }

    builder.addAllParameter(getParameters(type, node, jsDoc));

    return builder.build();
  }

  private Iterable<com.github.jsdossier.proto.Function.Detail> buildThrowsData(JsDoc jsDoc) {
    return transform(jsDoc.getThrowsClauses(),
        new Function<JsDoc.ThrowsClause, com.github.jsdossier.proto.Function.Detail>() {
          @Override
          public com.github.jsdossier.proto.Function.Detail apply(JsDoc.ThrowsClause input) {
            Comment thrownType = Comment.getDefaultInstance();
            if (input.getType().isPresent()) {
              thrownType = linker.formatTypeExpression(input.getType().get());
            }
            return com.github.jsdossier.proto.Function.Detail.newBuilder()
                .setType(thrownType)
                .setDescription(parseComment(input.getDescription(), linker))
                .build();
          }
        }
    );
  }

  private Comment getReturnType(@Nullable JsDoc jsdoc, JSType function) {
    JSType returnType = ((FunctionType) function).getReturnType();
    if (returnType.isUnknownType() && jsdoc != null && jsdoc.getReturnType() != null) {
      returnType = typeRegistry.evaluate(jsdoc.getReturnType());
    }
    Comment comment = linker.formatTypeExpression(returnType);
    if (isVacuousTypeComment(comment)) {
      return Comment.getDefaultInstance();
    }
    return comment;
  }

  private static Predicate<NominalType> isTypedef() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(@Nullable NominalType input) {
        return input != null
            && input.getJsdoc() != null
            && input.getJsdoc().isTypedef();
      }
    };
  }

  private static Predicate<NominalType> isNonEmpty() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(NominalType input) {
        return !isEmptyNamespace(input);
      }
    };
  }

  /**
   * Tests if a nominal type is an "empty" namespace. A type is considered empty if it is not
   * a constructor, interface, enum and has no non-namespace children.
   */
  private static boolean isEmptyNamespace(NominalType type) {
    if (type.isModuleExports()) {
      return false;
    }
    for (NominalType child : type.getTypes()) {
      if (!child.isNamespace()) {
        return false;
      }
    }
    return type.isNamespace() && type.getProperties().isEmpty();
  }

  private static boolean isVacuousTypeComment(Comment comment) {
    return comment.getTokenCount() == 1
        && ("undefined".equals(comment.getToken(0).getText())
        || "void".equals(comment.getToken(0).getText())
        || "?".equals(comment.getToken(0).getText())
        || "*".equals(comment.getToken(0).getText()));
  }

  private static class NameComparator implements Comparator<NominalType> {
    @Override
    public int compare(NominalType a, NominalType b) {
      return a.getName().compareTo(b.getName());
    }
  }

  private static class QualifiedNameComparator implements Comparator<NominalType> {
    @Override
    public int compare(NominalType a, NominalType b) {
      return a.getQualifiedName().compareTo(b.getQualifiedName());
    }
  }

  private static class PropertyNameComparator implements Comparator<Property> {
    @Override
    public int compare(Property a, Property b) {
      return a.getName().compareTo(b.getName());
    }
  }

  private class DisplayNameComparator implements Comparator<NominalType> {
    @Override
    public int compare(NominalType a, NominalType b) {
      return linker.getDisplayName(a).compareTo(linker.getDisplayName(b));
    }
  }

  private static class PathComparator implements Comparator<Path> {
    @Override
    public int compare(Path o1, Path o2) {
      return o1.toString().compareTo(o2.toString());
    }
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

      com.github.jsdossier.proto.Function.Detail.Builder detail = com.github.jsdossier.proto.Function.Detail.newBuilder()
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
          detail.setDescription(parseComment(
              jsdoc.getParameter(name).getDescription(),
              linker));
        }

      } else if (parameter != null) {
        detail.setName(parameter.getName());
        detail.setDescription(parseComment(parameter.getDescription(), linker));
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

  private static class InstanceProperty {
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
