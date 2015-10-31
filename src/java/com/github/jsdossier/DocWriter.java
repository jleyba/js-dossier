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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.transform;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.Readme;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.annotations.TypeFilter;
import com.github.jsdossier.annotations.Types;
import com.github.jsdossier.jscomp.JsDoc;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Enumeration;
import com.github.jsdossier.proto.HtmlRenderSpec;
import com.github.jsdossier.proto.JsType;
import com.github.jsdossier.proto.JsTypeRenderSpec;
import com.github.jsdossier.proto.Resources;
import com.github.jsdossier.proto.SourceFile;
import com.github.jsdossier.proto.SourceFileRenderSpec;
import com.github.jsdossier.proto.TypeLink;
import com.github.jsdossier.proto.Visibility;
import com.github.jsdossier.soy.Renderer;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.Property;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Generates HTML documentation.
 */
class DocWriter {

  private static final String INDEX_FILE_NAME = "index.html";

  private final Path outputDir;
  private final ImmutableSet<Path> inputFiles;
  private final ImmutableList<NominalType> sortedTypes;
  private final ImmutableList<NominalType> sortedModules;
  private final Path sourcePrefix;
  private final Optional<Path> readme;
  private final TypeRegistry typeRegistry;
  private final Predicate<NominalType> typeFilter;
  private final ImmutableList<MarkdownPage> markdownPages;
  private final Linker linker;
  private final CommentParser parser;
  private final NavIndexFactory navIndex;
  private final DocTemplate template;
  private final TypeInspector typeInspector;

  private final Renderer renderer = new Renderer();
  private final TypeIndex typeIndex = new TypeIndex();

  @Inject
  DocWriter(
      @Output Path outputDir,
      @Input Iterable<Path> inputFiles,
      @Types ImmutableList<NominalType> sortedTypes,
      @Modules ImmutableList<NominalType> sortedModules,
      @SourcePrefix Path sourcePrefix,
      @Readme Optional<Path> readme,
      ImmutableList<MarkdownPage> markdownPages,
      TypeRegistry typeRegistry,
      @TypeFilter Predicate<NominalType> typeFilter,
      Linker linker,
      CommentParser parser,
      NavIndexFactory navIndex,
      DocTemplate template,
      TypeInspector typeInspector) {
    this.template = template;
    this.outputDir = checkNotNull(outputDir);
    this.inputFiles = ImmutableSet.copyOf(inputFiles);
    this.sortedTypes = sortedTypes;
    this.sortedModules = sortedModules;
    this.sourcePrefix = checkNotNull(sourcePrefix);
    this.readme = checkNotNull(readme);
    this.markdownPages = checkNotNull(markdownPages);
    this.typeRegistry = checkNotNull(typeRegistry);
    this.typeFilter = checkNotNull(typeFilter);
    this.linker = checkNotNull(linker);
    this.parser = checkNotNull(parser);
    this.navIndex = navIndex;
    this.typeInspector = typeInspector;
  }

  public void generateDocs() throws IOException {
    createDirectories(outputDir);
    copyResources();
    copySourceFiles();
    generateIndex();
    generateCustomPages();

    for (NominalType type : sortedTypes) {
      if (type.isEmptyNamespace()) {
        continue;
      }
      generateDocs(typeIndex.addType(type));
    }

    for (NominalType module : sortedModules) {
      generateModuleDocs(module);
    }

    writeTypesJson();
  }

  private void generateIndex() throws IOException {
    generateHtmlPage(
        new CommentParser(),
        "Index",
        outputDir.resolve(INDEX_FILE_NAME),
        readme);
  }

  private void generateCustomPages() throws IOException {
    CommentParser parser = new CommentParser();
    for (MarkdownPage page : markdownPages) {
      String name = page.getName();
      checkArgument(!"index".equalsIgnoreCase(name), "reserved page name: %s", name);
      generateHtmlPage(
          parser,
          name,
          outputDir.resolve(name.replace(' ', '_') + ".html"),
          Optional.of(page.getPath()));
    }
  }

  private void generateHtmlPage(
      CommentParser parser, String title, Path output, Optional<Path> input)
      throws IOException {
    Comment content = Comment.getDefaultInstance();
    if (input.isPresent()) {
      String text = new String(Files.readAllBytes(input.get()), Charsets.UTF_8);
      content = parser.parseComment(text, linker);
    }
    HtmlRenderSpec.Builder spec = HtmlRenderSpec.newBuilder()
        .setResources(getResources(output))
        .setTitle(title)
        .setIndex(navIndex.create(output))
        .setContent(content);
    renderer.render(output, spec.build());
  }

  private void generateModuleDocs(NominalType module) throws IOException {
    ModuleIndexReference ref = typeIndex.addModule(module);
    linker.pushContext(module);

    Path output = linker.getFilePath(module);
    createDirectories(output.getParent());

    Comment description = null;
    if (module.getJsType().isFunctionType()) {
      description = parser.getBlockDescription(linker, module);
    }

    if (description == null) {
      description = parser.getFileoverview(linker,
          typeRegistry.getFileOverview(module.getModule().getPath()));
    }

    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setName(linker.getDisplayName(module))
        .setSource(linker.getSourceLink(module.getNode()))
        .setDescription(description)
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

    getStaticData(jsTypeBuilder, ref);
    getPrototypeData(jsTypeBuilder, ref);

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
        docs = internalModule.getInternalVarDocs(module.getName());
      }
      String name = linker.getDisplayName(module);
      int index = name.lastIndexOf('/');
      if (index != -1 && !name.endsWith("/")) {
        name = name.substring(index + 1);
      }
      jsTypeBuilder.setMainFunction(typeInspector.getFunctionData(
          name,
          module.getJsType(),
          module.getNode(),
          docs));
    }

    JsTypeRenderSpec.Builder spec = JsTypeRenderSpec.newBuilder()
        .setResources(getResources(output))
        .setType(jsTypeBuilder.build())
        .setIndex(navIndex.create(output));
    renderer.render(output, spec.build());

    for (NominalType type : module.getTypes()) {
      if (!type.isEmptyNamespace()) {
        generateDocs(ref.addType(type));
      }
    }
    linker.popContext();
  }

  private void generateDocs(IndexReference ref) throws IOException {
    NominalType type = ref.getNominalType();
    linker.pushContext(type);

    Path output = linker.getFilePath(type);
    createDirectories(output.getParent());

    String name = type.getQualifiedName(type.isNamespace());
    if (type.getModule() != null
        && type.isCommonJsModule()
        && name.startsWith(type.getModule().getName())) {
      name = name.substring(type.getModule().getName().length() + 1);
    }
    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setName(name);

    addParentLink(jsTypeBuilder, type);

    Comment description = parser.getBlockDescription(linker, type);
    NominalType aliased = typeRegistry.resolve(type.getJsType());

    if (aliased != type) {
      if (!typeFilter.apply(aliased)) {
        String aliasDisplayName = linker.getDisplayName(aliased);
        if (aliased != null && aliased.isCommonJsModule()) {
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
      }

      if (description.getTokenCount() == 0) {
        description = parser.getBlockDescription(linker, aliased);
      }
    }

    if (description.getTokenCount() == 0 && type.isModuleExports()) {
      Path path = sourcePrefix.getFileSystem().getPath(type.getNode().getSourceFileName());
      description = parser.getFileoverview(linker, typeRegistry.getFileOverview(path));
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

    getStaticData(jsTypeBuilder, ref);
    getPrototypeData(jsTypeBuilder, ref);

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
      jsTypeBuilder.setMainFunction(typeInspector.getFunctionData(
          name,
          type.getJsType(),
          type.getNode(),
          docs));
    }

    JsTypeRenderSpec.Builder spec = JsTypeRenderSpec.newBuilder()
        .setResources(getResources(output))
        .setType(jsTypeBuilder.build())
        .setIndex(navIndex.create(output));
    renderer.render(output, spec.build());
    linker.popContext();
  }

  private void copyResources() throws IOException {
    for (TemplateFile file
        : Iterables.concat(template.getCss(), template.getHeadJs(), template.getTailJs())) {
      try (InputStream input = file.getSource().openStream()) {
        Path output = outputDir.resolve(file.getName());
        Files.copy(input, output, REPLACE_EXISTING);
      }
    }
  }

  private void writeTypesJson() throws IOException {
    // NOTE: JSON is not actually a subset of JavaScript, but in our case we know we only
    // have valid JavaScript input, so we can use JSONObject#toString() as a quick-and-dirty
    // formatting mechanism.
    String content = "var TYPES = " + typeIndex + ";";

    Path outputPath = outputDir.resolve("types.js");
    Files.write(outputPath, content.getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING);
  }

  private Resources getResources(Path forPathFromRoot) {
    final Path pathToRoot = outputDir
        .resolve(forPathFromRoot)
        .getParent()
        .relativize(outputDir);
    Function<TemplateFile, String> toOutputPath = new Function<TemplateFile, String>() {
      @Override
      public String apply(TemplateFile input) {
        return resolve(pathToRoot, input.getName());
      }
    };
    return Resources.newBuilder()
        .addAllCss(FluentIterable.from(template.getCss()).transform(toOutputPath))
        .addAllHeadScript(FluentIterable.from(template.getHeadJs()).transform(toOutputPath))
        .addTailScript(resolve(pathToRoot, "types.js"))
        .addAllTailScript(FluentIterable.from(template.getTailJs()).transform(toOutputPath))
        .build();
  }

  private static String resolve(Path path, String name) {
    return path.getNameCount() == 0 ? name : path.resolve(name).toString();
  }

  private void copySourceFiles() throws IOException {
    for (Path source : inputFiles) {
      Path displayPath = sourcePrefix.relativize(source);
      Path relativePath = sourcePrefix
          .relativize(source.toAbsolutePath().normalize())
          .resolveSibling(source.getFileName() + ".src.html");

      Path renderPath = outputDir
          .resolve("source")
          .resolve(relativePath.toString());

      SourceFile file = SourceFile.newBuilder()
          .setBaseName(source.getFileName().toString())
          .setPath(displayPath.toString())
          .addAllLines(Files.readAllLines(source, Charsets.UTF_8))
          .build();

      SourceFileRenderSpec.Builder spec = SourceFileRenderSpec.newBuilder()
          .setFile(file)
          .setResources(getResources(renderPath))
          .setIndex(navIndex.create(renderPath));

      renderer.render(renderPath, spec.build());
    }
  }

  private void addParentLink(JsType.Builder jsTypeBuilder, NominalType type) {
    if (type.isModuleExports()) {
      return;
    }
    if (type.isNamespace() && type.getModule() == null) {
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

    List<JSType> types = Lists.reverse(typeRegistry.getTypeHierarchy(type));
    List<Comment> list = Lists.newArrayListWithExpectedSize(types.size());
    // Skip bottom of stack (type of this). Handled specially below.
    for (int i = 0; i < (types.size() - 1); i++) {
      JSType base = types.get(i);
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
        JsDoc valueJsDoc = JsDoc.from(valueInfo);
        valueBuilder.setDescription(parser.parseComment(valueJsDoc.getBlockComment(), linker));

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
                .setDescription(parser.getBlockDescription(linker, typedef))
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

  private Comment getDeprecation(JsDoc jsdoc) {
    checkArgument(jsdoc != null, "null jsdoc");
    checkArgument(jsdoc.isDeprecated(), "no deprecation in jsdoc");
    return parser.parseComment(jsdoc.getDeprecationReason(), linker);
  }

  private List<JsType.TypeSummary> getNestedTypeInfo(NominalType parent) {
    Collection<NominalType> nestedTypes = parent.getTypes();
    List<JsType.TypeSummary> types = new ArrayList<>(nestedTypes.size());

    for (NominalType child : FluentIterable.from(nestedTypes).toSortedList(new NameComparator())) {
      if (typeFilter.apply(child)) {
        continue;
      }

      JSType childType = child.getJsType();
      if (!childType.isConstructor() && !childType.isEnumType() && !childType.isInterface()) {
        continue;
      }

      String href = linker.getFilePath(child).getFileName().toString();

      Comment summary = parser.getSummary("No description.", linker);

      JsDoc jsdoc = child.getJsdoc();
      if (jsdoc != null && !isNullOrEmpty(jsdoc.getBlockComment())) {
        summary =  parser.getSummary(jsdoc.getBlockComment(), linker);
      } else {
        NominalType aliased = typeRegistry.resolve(childType);
        if (aliased != null
            && aliased != child
            && aliased.getJsdoc() != null
            && !isNullOrEmpty(aliased.getJsdoc().getBlockComment())) {
          summary = parser.getSummary(aliased.getJsdoc().getBlockComment(), linker);
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

  private void getPrototypeData(JsType.Builder jsTypeBuilder, IndexReference indexReference) {
    NominalType nominalType = indexReference.getNominalType();
    TypeInspector.Report report = typeInspector.inspectInstanceType(nominalType);
    for (com.github.jsdossier.proto.Property prop : report.getProperties()) {
      jsTypeBuilder.addField(prop);
      updateIndex(jsTypeBuilder, indexReference, prop.getBase());
    }
    for (com.github.jsdossier.proto.Function func : report.getFunctions()) {
      jsTypeBuilder.addMethod(func);
      updateIndex(jsTypeBuilder, indexReference, func.getBase());
    }
  }

  private void updateIndex(
      JsType.Builder jsTypeBuilder, IndexReference indexReference, BaseProperty base) {
    // Do not include the property in the search index if the parent type is an alias,
    // the property is inherited from another type, or the property overrides a parent
    // property but does not provide a comment of its own.
    if (!jsTypeBuilder.hasAliasedType() && !base.hasDefinedBy()
        && (!base.hasOverrides()
        || (base.hasDescription() && base.getDescription().getTokenCount() > 0))) {
      indexReference.addInstanceProperty(base.getName());
    }
  }

  private void getStaticData(
      JsType.Builder jsTypeBuilder, IndexReference indexReference) {
    NominalType type = indexReference.getNominalType();
    TypeInspector.Report report = typeInspector.inspectType(type);

    for (com.github.jsdossier.proto.Property prop : report.getCompilerConstants()) {
      jsTypeBuilder.addCompilerConstant(prop);
      updateStaticPropertyIndex(jsTypeBuilder, indexReference, type, prop.getBase());
    }

    for (com.github.jsdossier.proto.Property prop : report.getProperties()) {
      jsTypeBuilder.addStaticProperty(prop);
      updateStaticPropertyIndex(jsTypeBuilder, indexReference, type, prop.getBase());
    }

    for (com.github.jsdossier.proto.Function func : report.getFunctions()) {
      jsTypeBuilder.addStaticFunction(func);
      updateStaticPropertyIndex(jsTypeBuilder, indexReference, type, func.getBase());
    }
  }
  
  private void updateStaticPropertyIndex(
      JsType.Builder jsTypeBuilder,
      IndexReference indexReference,
      NominalType type,
      BaseProperty base) {
    if (!jsTypeBuilder.hasAliasedType()
        && base != null
        && typeRegistry.getNominalType(base.getName()) == null
        && type.getType(base.getName()) == null) {
      indexReference.addStaticProperty(base.getName());
    }
  }

  private static Predicate<NominalType> isTypedef() {
    return new Predicate<NominalType>() {
      @Override
      public boolean apply(@Nullable NominalType input) {
        return input != null && input.isTypedef();
      }
    };
  }

  private static class NameComparator implements Comparator<NominalType> {
    @Override
    public int compare(NominalType a, NominalType b) {
      return a.getName().compareTo(b.getName());
    }
  }

  private static JsonArray getJsonArray(JsonObject object, String name) {
    if (!object.has(name)) {
      object.add(name, new JsonArray());
    }
    return object.get(name).getAsJsonArray();
  }

  class TypeIndex {

    private final JsonObject json;

    TypeIndex() {
      this.json = new JsonObject();
    }

    @Override
    public String toString() {
      return json.toString();
    }

    ModuleIndexReference addModule(NominalType module) {
      String dest = outputDir.relativize(linker.getFilePath(module)).toString();

      JsonObject obj = new JsonObject();
      obj.addProperty("name", linker.getDisplayName(module));
      obj.addProperty("href", dest);

      getJsonArray(json, "modules").add(obj);
      return new ModuleIndexReference(module, obj);
    }

    IndexReference addType(NominalType type) {
      return addTypeInfo(getJsonArray(json, "types"), type);
    }
  }

  private class IndexReference {
    private final NominalType type;
    protected final JsonObject index;

    private IndexReference(NominalType type, JsonObject index) {
      this.type = type;
      this.index = index;
    }

    NominalType getNominalType() {
      return type;
    }

    void addStaticProperty(String name) {
      getJsonArray(index, "statics").add(new JsonPrimitive(name));
    }

    void addInstanceProperty(String name) {
      getJsonArray(index, "members").add(new JsonPrimitive(name));
    }
  }

  private class ModuleIndexReference extends IndexReference {
    private ModuleIndexReference(NominalType module, JsonObject index) {
      super(module, index);
    }

    IndexReference addType(NominalType type) {
      checkArgument(type.getParent() == getNominalType());
      return addTypeInfo(getJsonArray(index, "types"), type);
    }
  }

  private IndexReference addTypeInfo(JsonArray array, NominalType type) {
    String dest = outputDir.relativize(linker.getFilePath(type)).toString();

    JsonObject details = new JsonObject();
    details.addProperty("name", type.getQualifiedName());
    details.addProperty("href", dest);
    details.addProperty("namespace",
        !type.getJsType().isInstanceType()
            && !type.getJsType().isConstructor()
            && !type.getJsType().isEnumType());
    details.addProperty("interface", type.getJsType().isInterface());
    array.add(details);

    NominalType resolvedType = typeRegistry.resolve(type.getJsType());
    boolean isAlias = resolvedType != type;
    if (!isAlias || typeFilter.apply(resolvedType)) {
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
        array.add(typedefDetails);
      }
    }
    return new IndexReference(type, details);
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
