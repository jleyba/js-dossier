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

import static com.github.jleyba.dossier.CommentUtil.getBlockDescription;
import static com.github.jleyba.dossier.CommentUtil.getFileoverview;
import static com.github.jleyba.dossier.CommentUtil.getSummary;
import static com.github.jleyba.dossier.CommentUtil.parseComment;
import static com.github.jleyba.dossier.proto.Dossier.Deprecation;
import static com.github.jleyba.dossier.proto.Dossier.Enumeration;
import static com.github.jleyba.dossier.proto.Dossier.IndexFileRenderSpec;
import static com.github.jleyba.dossier.proto.Dossier.JsType;
import static com.github.jleyba.dossier.proto.Dossier.JsTypeRenderSpec;
import static com.github.jleyba.dossier.proto.Dossier.Resources;
import static com.github.jleyba.dossier.proto.Dossier.SourceFile;
import static com.github.jleyba.dossier.proto.Dossier.SourceFileRenderSpec;
import static com.github.jleyba.dossier.proto.Dossier.TypeLink;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;
import static java.nio.file.Files.createDirectories;

import com.github.jleyba.dossier.proto.Dossier;
import com.github.jleyba.dossier.soy.Renderer;
import com.github.rjeschke.txtmark.Processor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

/**
 * Generates HTML documentation.
 */
class HtmlDocWriter implements DocWriter {

  private static final String INDEX_FILE_NAME = "index.html";

  private final Config config;
  private final TypeRegistry typeRegistry;
  private final Linker linker;

  private final Renderer renderer = new Renderer();

  private Iterable<Path> sortedFiles;
  private Iterable<NominalType> sortedTypes;
  private Iterable<NominalType> sortedModules;
  private ImmutableSet<NominalType> namespaces;
  private Dossier.Index masterIndex;
  private NominalType currentModule;

  HtmlDocWriter(Config config, TypeRegistry typeRegistry) {
    this.config = checkNotNull(config);
    this.typeRegistry = checkNotNull(typeRegistry);
    this.linker = new Linker(config, typeRegistry);
  }

  @Override
  public void generateDocs(final JSTypeRegistry registry) throws IOException {
    sortedFiles = FluentIterable.from(concat(config.getSources(), config.getModules()))
        .toSortedList(new PathComparator());
    sortedTypes = FluentIterable.from(typeRegistry.getNominalTypes())
        .filter(isNonEmpty())
        .filter(not(isTypedef()))
        .filter(isPublic())
        .toSortedList(new QualifiedNameComparator());
    sortedModules = FluentIterable.from(typeRegistry.getModules())
        .toSortedList(new DisplayNameComparator());

    namespaces = FluentIterable.from(sortedTypes)
        .filter(isNamespace())
        .toSet();

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

  private Dossier.Index generateNavIndex(Path path) {
    Dossier.Index.Builder builder = Dossier.Index.newBuilder()
        .mergeFrom(masterIndex);

    Path toRoot = path.getParent().relativize(config.getOutput());

    builder.setHome(toUrlPath(toRoot.resolve(INDEX_FILE_NAME)));

    for (Dossier.Index.Module.Builder module : builder.getModuleBuilderList()) {
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

  private Dossier.Index generateNavIndex() {
    Dossier.Index.Builder builder = Dossier.Index.newBuilder();

    builder.setHome(INDEX_FILE_NAME);

    for (NominalType module : sortedModules) {
      builder.addModuleBuilder()
          .setLink(TypeLink.newBuilder()
          .setHref(toUrlPath(config.getOutput().relativize(linker.getFilePath(module))))
          .setText(linker.getDisplayName(module)));
      // TODO: module types.
    }

    for (NominalType type : sortedTypes) {
      if (isEmptyNamespace(type)) {
        continue;
      }

      // TODO: resolveTypeAlias.
//      Descriptor resolvedType = resolveTypeAlias(type);
      Path path = linker.getFilePath(type);
      builder.addType(TypeLink.newBuilder()
          .setHref(toUrlPath(config.getOutput().relativize(path)))
          .setText(type.getQualifiedName()));
    }

    return builder.build();
  }

  private void generateIndex() throws IOException {
    Dossier.Comment readme = Dossier.Comment.getDefaultInstance();
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
    Path output = linker.getFilePath(module);
    createDirectories(output.getParent());

//    // Always generate documentation for both the internal and external typedefs since
//    // they're most likely used in other jsdoc type annotations.
//    // TODO: handle this in a cleaner way.
//    Iterable<? extends JsType.TypeDef> typeDefs = concat(
//        getTypeDefInfo(module.getExportedProperties()),
//        getTypeDefInfo(module.getInternalTypeDefs()));
//
    currentModule = module;
    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setName(linker.getDisplayName(module))
        .setSource(linker.getSourceLink(module.getNode()))
        .setDescription(getFileoverview(linker,
            typeRegistry.getFileOverview(module.getModule().getModulePath())))
        .addAllNested(getNestedTypeInfo(module.getTypes()))
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
      jsTypeBuilder.setMainFunction(getFunctionData(
          linker.getDisplayName(module),
          module.getJsType(),
          module.getNode(),
          module.getJsdoc()));
    }

    JsTypeRenderSpec.Builder spec = JsTypeRenderSpec.newBuilder()
        .setResources(getResources(output))
        .setType(jsTypeBuilder.build())
        .setIndex(generateNavIndex(output));
    renderer.render(output, spec.build());

    for (NominalType type : module.getTypes()) {
      if (type.getJsType().isConstructor()
          || type.getJsType().isInterface()
          || type.getJsType().isEnumType()) {
        generateDocs(type);
      }
    }
    currentModule = null;
  }

  private void generateDocs(NominalType type) throws IOException {
    Path output = linker.getFilePath(type);
    createDirectories(output.getParent());

    String name = type.getQualifiedName();
    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setName(name);

    if (type.getModule() != null && !type.isModuleExports()) {
      jsTypeBuilder.setModule(TypeLink.newBuilder()
          .setText(linker.getDisplayName(type.getModule()))
          .setHref(linker.getLink(type.getModule().getVarName())));
    }

// TODO: type alias.
//    Descriptor aliased = resolveTypeAlias(descriptor);
//    if (aliased != descriptor) {
//      jsTypeBuilder.setAliasedType(TypeLink.newBuilder()
//          .setText(aliased.getFullName())
//          .setHref(nullToEmpty(linker.getLink(aliased)))
//          .build());
//      descriptor = aliased;
//    }

    jsTypeBuilder
        .addAllNested(getNestedTypeInfo(type.getTypes()))
        .setSource(linker.getSourceLink(type.getNode()))
        .setDescription(getBlockDescription(linker, type.getJsdoc()))
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
      jsTypeBuilder.setMainFunction(getFunctionData(
          name,
          type.getJsType(),
          type.getNode(),
          type.getJsdoc()));
    }

    JsTypeRenderSpec.Builder spec = JsTypeRenderSpec.newBuilder()
        .setResources(getResources(output))
        .setType(jsTypeBuilder.build())
        .setIndex(generateNavIndex(output));
    renderer.render(output, spec.build());
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

      // TODO: resolve
//      Descriptor resolvedType = resolveTypeAlias(descriptor);
      String dest = config.getOutput().relativize(linker.getFilePath(type)).toString();

      JsonObject details = new JsonObject();
      details.addProperty("name", type.getQualifiedName());
      details.addProperty("href", dest);
      details.addProperty("isInterface", type.getJsdoc() != null && type.getJsdoc().isInterface());
      details.addProperty("isTypedef", type.getJsdoc() != null && type.getJsdoc().isTypedef());
      array.add(details);

      // Also include typedefs. These will not be included in the main
      // index, but will be searchable.
      // TODO: resolve
//      List<Descriptor> typedefs = FluentIterable.from(resolvedType.getProperties())
      List<NominalType> typedefs = FluentIterable.from(type.getTypes())
          .filter(isTypedef())
          .toSortedList(new NameComparator());
      for (NominalType typedef : typedefs) {
        JsonObject typedefDetails = new JsonObject();
        typedefDetails.addProperty("name", typedef.getQualifiedName());
        typedefDetails.addProperty("href", "");  // TODO: nullToEmpty(linker.getLink(typedef.getFullName())));
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
        .addScript(pathToRoot.resolve("types.js").toString())
        .addScript(pathToRoot.resolve("dossier.js").toString())
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

  private List<Dossier.Comment> getInheritedTypes(NominalType nominalType) {
    JSType type = nominalType.getJsType();
    if (!type.isConstructor()) {
      return ImmutableList.of();
    }

    LinkedList<JSType> types = typeRegistry.getTypeHierarchy(type);
    List<Dossier.Comment> list = Lists.newArrayListWithExpectedSize(types.size());
    // Skip bottom of stack (type of this). Handled specially below.
    while (types.size() > 1) {
      JSType base = types.pop();
      verify(base.isInstanceType());
      list.add(linker.formatTypeExpression(base));
    }
    list.add(Dossier.Comment.newBuilder()
        .addToken(Dossier.Comment.Token.newBuilder()
            .setText(linker.getDisplayName(nominalType)))
        .build());
    return list;
  }

  private Iterable<TypeLink> getImplementedTypes(NominalType nominalType) {
    Set<JSType> interfaces = typeRegistry.getImplementedTypes(nominalType);
    return transform(Ordering.usingToString().sortedCopy(interfaces),
        new Function<JSType, TypeLink>() {
          @Override
          public TypeLink apply(JSType input) {
            return getTypeLink(input);
          }
        }
    );
  }

  @VisibleForTesting TypeLink getTypeLink(JSType type) {
    checkArgument(
        type.isInstanceType() || type.isConstructor() || type.isInterface(),
        "Unable to compute link for type %s", type);
    if (type.isInstanceType()) {
      type = type.toObjectType().getConstructor();
    }

    TypeLink link = linker.getLink(type);
    if (link != null) {
      return link;
    }
    return TypeLink.newBuilder()
        .setText(((FunctionType) type).getInstanceType().getReferenceName())
        .setHref("")
        .build();
  }

  private void extractEnumData(NominalType type, Enumeration.Builder enumBuilder) {
    checkArgument(type.getJsType().isEnumType());

    JSType elementType = ((EnumType) type.getJsType()).getElementsType();
    enumBuilder.setType(linker.formatTypeExpression(elementType));

    JsDoc jsdoc = type.getJsdoc();
    if (jsdoc != null) {
      enumBuilder.setVisibility(Dossier.Visibility.valueOf(jsdoc.getVisibility().name()));
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
        .filter(isPublic())
        .transform(new Function<NominalType, JsType.TypeDef>() {
          @Override
          public JsType.TypeDef apply(NominalType typedef) {
            JsDoc jsdoc = checkNotNull(typedef.getJsdoc());
            String name = type.getName() + "." + typedef.getName();

            JsType.TypeDef.Builder builder = JsType.TypeDef.newBuilder()
                .setName(name)
                .setType(linker.formatTypeExpression(jsdoc.getType()))
                .setSource(linker.getSourceLink(typedef.getNode()))
                .setDescription(getBlockDescription(linker, jsdoc))
                .setVisibility(Dossier.Visibility.valueOf(jsdoc.getVisibility().name()));

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

  /**
   * If the given {@code descriptor} is a constructor alias for a known type, this method will
   * return the aliased type. Otherwise, this method will return the original descriptor. A type
   * alias would occur as:
   * <pre><code>
   *   \** @constructor *\ var Foo = function(){};
   *   \** @type {function(new: Foo)} *\ var Bar = Foo;
   * </code></pre>
   */
  private Descriptor resolveTypeAlias(Descriptor descriptor) {
    // TODO
//    JSType type = descriptor.getType();
//    if (type.isConstructor()) {
//      type = ((FunctionType) type).getTypeOfThis();
//    }
//    Descriptor alias = docRegistry.getType(type);
//    if (alias == null) {
//      String name = getTypeName(type);
//      alias = docRegistry.resolve(name, currentModule);
//
//      // The descriptor might just be forwarding the declaration from another module:
//      //
//      //     [foo.js]
//      //     /** @constructor */
//      //     var Original = function() {};
//      //     exports.Original = Original;
//      //
//      //     [bar.js]
//      //     exports.Original = require('./foo').Original;
//      //
//      // Check for this by trying to resolve the descriptor's literal type name.
//      if (alias == descriptor && currentModule != null) {
//        alias = docRegistry.resolve(type.toString(), currentModule);
//      }
//    }
//
//    return firstNonNull(alias, descriptor);
    return descriptor;
  }

  private List<JsType.TypeSummary> getNestedTypeInfo(Collection<NominalType> nestedTypes) {
    List<JsType.TypeSummary> types = new ArrayList<>(nestedTypes.size());

    for (NominalType child : FluentIterable.from(nestedTypes).toSortedList(new NameComparator())) {
      JSType childType = child.getJsType();

      if (!childType.isConstructor() && !childType.isEnumType() && !childType.isInterface()) {
        continue;
      }

      // TODO: type alias
//      Descriptor resolvedType = resolveTypeAlias(child);
      String href = linker.getFilePath(child).getFileName().toString();

      Dossier.Comment summary = getSummary("No description.", linker);

      JsDoc jsdoc = child.getJsdoc();
      if (jsdoc != null && !isNullOrEmpty(jsdoc.getBlockComment())) {
        summary =  getSummary(jsdoc.getBlockComment(), linker);
      } else {
        // TODO: alias
//        jsdoc = resolvedType.getJsDoc();
//        if (jsdoc  != null && !isNullOrEmpty(jsdoc.getBlockComment())) {
//          summary =  getSummary(jsdoc.getBlockComment(), linker);
//        }
      }

      types.add(JsType.TypeSummary.newBuilder()
          .setHref(href)
          .setSummary(summary)
          .setName(child.getQualifiedName())
          .build());
    }

    return types;
  }

  private void getPrototypeData(JsType.Builder jsTypeBuilder, NominalType nominalType) {
    Iterable<JSType> assignableTypes;
    if (nominalType.getJsType().isConstructor()) {
      assignableTypes = Lists.reverse(typeRegistry.getTypeHierarchy(nominalType.getJsType()));
    } else if (nominalType.getJsType().isInterface()) {
      assignableTypes = typeRegistry.getImplementedTypes(nominalType);
    } else {
      return;
    }

    Map<String, Property> properties = new TreeMap<>();
    for (JSType assignableType : assignableTypes) {
      if (assignableType.isTemplatizedType()) {
        assignableType = ((TemplatizedType) assignableType).getReferencedType();
      }
      if (assignableType.isInstanceType()) {
        assignableType = ((ObjectType) assignableType).getConstructor();
      }
      verify(assignableType.isFunctionType(),
          "%s may be assigned to non function %s", nominalType.getQualifiedName(), assignableType);

      ObjectType object = ((FunctionType) assignableType).getInstanceType();
      for (String pname : object.getOwnPropertyNames()) {
        if (!properties.containsKey(pname)) {
          properties.put(pname, object.getOwnSlot(pname));
        }
      }

      ObjectType prototype = ObjectType.cast(((FunctionType) assignableType)
          .getPropertyType("prototype"));
      verify(prototype != null);

      for (String pname : prototype.getOwnPropertyNames()) {
        if (!properties.containsKey(pname)) {
          properties.put(pname, prototype.getOwnSlot(pname));
        }
      }
    }

    if (properties.isEmpty()) {
      return;
    }

    // TODO: remove prototype chain from generated documentation.
    // TODO: replace with "defined on" field (specified on for interface).
    Dossier.Prototype.Builder protoBuilder = jsTypeBuilder.addPrototypeBuilder()
        .setName("");
    for (Property property : properties.values()) {
      JsDoc jsdoc = JsDoc.from(property.getJSDocInfo());
      if (jsdoc != null && jsdoc.getVisibility() == JSDocInfo.Visibility.PRIVATE) {
        continue;
      }

      if (property.getType().isFunctionType()) {
        protoBuilder.addFunction(getFunctionData(
            property.getName(),
            property.getType(),
            property.getNode(),
            jsdoc));
        jsTypeBuilder.setHasInstanceMethods(true);
      } else {
        protoBuilder.addProperty(getPropertyData(property));
        jsTypeBuilder.setHasInstanceProperties(true);
      }
    }
  }

  private void getStaticData(JsType.Builder jsTypeBuilder, NominalType type) {
    ImmutableList<Property> properties = FluentIterable.from(type.getProperties())
        .toSortedList(new PropertyNameComparator());

    for (Property property : properties) {
      JSDocInfo info = property.getJSDocInfo();
      if (info != null && info.isDefine()) {
        jsTypeBuilder.addCompilerConstant(getPropertyData(property));

      } else if (property.getType().isFunctionType()) {
        jsTypeBuilder.addStaticFunction(getFunctionData(
            property.getName(),
            property.getType(),
            property.getNode(),
            JsDoc.from(property.getJSDocInfo())));

      } else if (!property.getType().isEnumElementType()) {
        jsTypeBuilder.addStaticProperty(getPropertyData(property));
      }
    }
  }

  private Dossier.BaseProperty getBasePropertyDetails(
      String name, JSType type, Node node, @Nullable JsDoc jsdoc) {
    Dossier.BaseProperty.Builder builder = Dossier.BaseProperty.newBuilder()
        .setName(name)
        .setDescription(getBlockDescription(linker, jsdoc))
        .setSource(linker.getSourceLink(node));
    if (jsdoc != null) {
      builder.setVisibility(Dossier.Visibility.valueOf(jsdoc.getVisibility().name()))
          .getTagsBuilder()
          .setIsDeprecated(jsdoc.isDeprecated())
          .setIsConst(!type.isFunctionType() && (jsdoc.isConst() || jsdoc.isDefine()));
      if (jsdoc.isDeprecated()) {
        builder.setDeprecation(getDeprecation(jsdoc));
      }
    }
    return builder.build();
  }

  private Dossier.Property getPropertyData(Property property) {
    JsDoc jsDoc = JsDoc.from(property.getJSDocInfo());

    Dossier.Property.Builder builder = Dossier.Property.newBuilder()
        .setBase(getBasePropertyDetails(
            property.getName(), property.getType(), property.getNode(), jsDoc));

    if (jsDoc != null && jsDoc.getType() != null) {
      builder.setType(linker.formatTypeExpression(jsDoc.getType()));
    } else if (property.getType() != null) {
      builder.setType(linker.formatTypeExpression(property.getType()));
    }

    return builder.build();
  }

  private Dossier.Function getFunctionData(
      String name, JSType type, Node node, @Nullable JsDoc jsDoc) {
    checkArgument(type.isFunctionType());

    boolean isConstructor = type.isConstructor() && (jsDoc == null || jsDoc.isConstructor());
    boolean isInterface = !isConstructor && type.isInterface();

    Dossier.Function.Builder builder = Dossier.Function.newBuilder()
        .setBase(getBasePropertyDetails(name, type, node, jsDoc))
        .setIsConstructor(isConstructor);

    if (!isConstructor && !isInterface) {
      Dossier.Function.Detail.Builder detail = Dossier.Function.Detail.newBuilder();
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

    builder.addAllParameter(transform(getParameters(type, node, jsDoc),
        new Function<Parameter, Dossier.Function.Detail>() {
          @Override
          public Dossier.Function.Detail apply(@Nullable Parameter param) {
            Dossier.Comment type = Dossier.Comment.getDefaultInstance();
            if (param.getType() != null) {
              type = linker.formatTypeExpression(param.getType());
            }
            return Dossier.Function.Detail.newBuilder()
                .setName(param.getName())
                .setType(type)
                .setDescription(parseComment(param.getDescription(), linker))
                .build();
          }
        }
    ));

    return builder.build();
  }

  private Iterable<Dossier.Function.Detail> buildThrowsData(JsDoc jsDoc) {
    return transform(jsDoc.getThrowsClauses(),
        new Function<JsDoc.ThrowsClause, Dossier.Function.Detail>() {
          @Override
          public Dossier.Function.Detail apply(JsDoc.ThrowsClause input) {
            Dossier.Comment thrownType = Dossier.Comment.getDefaultInstance();
            if (input.getType().isPresent()) {
              thrownType = linker.formatTypeExpression(input.getType().get());
            }
            return Dossier.Function.Detail.newBuilder()
                .setType(thrownType)
                .setDescription(parseComment(input.getDescription(), linker))
                .build();
          }
        }
    );
  }

  private Dossier.Comment getReturnType(@Nullable JsDoc jsdoc, JSType function) {
    JSType returnType;
    if (jsdoc != null && jsdoc.getReturnType() != null) {
      returnType = typeRegistry.evaluate(jsdoc.getReturnType());
    } else {
      returnType = ((FunctionType) function).getReturnType();
    }
    Dossier.Comment comment = linker.formatTypeExpression(returnType);
    // Ignore vacuous return types.
    if (comment.getTokenCount() != 1
        || (!"undefined".equals(comment.getToken(0).getText())
        && !"void".equals(comment.getToken(0).getText())
        && !"?".equals(comment.getToken(0).getText())
        && !"*".equals(comment.getToken(0).getText()))) {
      return comment;
    }
    return Dossier.Comment.getDefaultInstance();
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

  private static Predicate<NominalType> isPublic() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(@Nullable NominalType input) {
        if (input == null) {
          return false;
        }
        JsDoc jsdoc = input.getJsdoc();
        return jsdoc == null
            || jsdoc.getVisibility() == JSDocInfo.Visibility.PUBLIC
            || jsdoc.getVisibility() == JSDocInfo.Visibility.INHERITED && apply(input.getParent());
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

  private boolean isFunction(JSType type) {
    return type.isFunctionType()
        && !type.isConstructor()
        && !type.isInterface();
  }

  private static boolean isNamespace(NominalType type) {
    JSType jsType = type.getJsType();
    return !jsType.isConstructor()
        && !jsType.isInterface()
        && !jsType.isEnumType();
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
      if (!isNamespace(child)) {
        return false;
      }
    }
    return isNamespace(type) && type.getProperties().isEmpty();
  }

  private static Predicate<NominalType> isModuleExports() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(@Nullable NominalType input) {
        return input.isModuleExports();
      }
    };
  }

  private static String getDisplayName(JSType type) {
    if (isNullOrEmpty(type.getDisplayName())) {
      return type.toString();
    }
    return type.getDisplayName();
  }

  private static Predicate<Descriptor> notOwnPropertyOf(final Iterable<Descriptor> descriptors) {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        for (Descriptor descriptor : descriptors) {
          if (descriptor.hasOwnInstanceProprety(input.getSimpleName())) {
            return false;
          }
        }
        return true;
      }
    };
  }

  private static Predicate<NominalType> isNamespace() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(@Nullable NominalType input) {
        return input != null
            && !input.getJsType().isConstructor()
            && !input.getJsType().isInterface()
            && !input.getJsType().isEnumType()
            && (input.getJsdoc() == null || !input.getJsdoc().isTypedef());
      }
    };
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

  private static List<Parameter> getParameters(JSType type, Node node, @Nullable JsDoc docs) {
    checkArgument(type.isFunctionType());
    final JsDoc jsdoc = docs == null && type.getJSDocInfo() != null
        ? JsDoc.from(type.getJSDocInfo())
        : docs;

    // Parameters may not be documented in the order they actually appear in the function
    // declaration, so we have to parse that directly.
    @Nullable Node paramList = findParamList(node);
    if (paramList == null) {
      if (jsdoc != null) {
        return jsdoc.getParameters();
      }
      return jsdoc != null
          ? jsdoc.getParameters()
          : ImmutableList.<Parameter>of();
    }
    verify(paramList.isParamList());

    List<String> names = new ArrayList<>(paramList.getChildCount());
    for (Node name = paramList.getFirstChild(); name != null; name = name.getNext()) {
      names.add(name.getString());
    }
    return Lists.transform(names, new Function<String, Parameter>() {
      @Override
      public Parameter apply(String name) {
        if (jsdoc != null) {
          try {
            return jsdoc.getParameter(name);
          } catch (IllegalArgumentException ignored) {
            // Do nothing; undocumented parameter.
          }
        }
        return new Parameter(name);
      }
    });
  }

  @Nullable
  private static Node findParamList(Node src) {
    if (src.isName() && src.getParent().isFunction()) {
      verify(src.getNext().isParamList());
      return src.getNext();
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
}
