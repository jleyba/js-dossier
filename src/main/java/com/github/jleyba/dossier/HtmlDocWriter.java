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

import static com.github.jleyba.dossier.CommentUtil.formatTypeExpression;
import static com.github.jleyba.dossier.CommentUtil.getBlockDescription;
import static com.github.jleyba.dossier.CommentUtil.getFileoverview;
import static com.github.jleyba.dossier.CommentUtil.getSummary;
import static com.github.jleyba.dossier.CommentUtil.parseComment;
import static com.github.jleyba.dossier.proto.Dossier.BaseProperty;
import static com.github.jleyba.dossier.proto.Dossier.Deprecation;
import static com.github.jleyba.dossier.proto.Dossier.Enumeration;
import static com.github.jleyba.dossier.proto.Dossier.IndexFileRenderSpec;
import static com.github.jleyba.dossier.proto.Dossier.JsType;
import static com.github.jleyba.dossier.proto.Dossier.JsTypeRenderSpec;
import static com.github.jleyba.dossier.proto.Dossier.License;
import static com.github.jleyba.dossier.proto.Dossier.LicenseRenderSpec;
import static com.github.jleyba.dossier.proto.Dossier.Property;
import static com.github.jleyba.dossier.proto.Dossier.Prototype;
import static com.github.jleyba.dossier.proto.Dossier.Resources;
import static com.github.jleyba.dossier.proto.Dossier.SourceFile;
import static com.github.jleyba.dossier.proto.Dossier.SourceFileRenderSpec;
import static com.github.jleyba.dossier.proto.Dossier.TypeLink;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Iterables.transform;

import com.github.jleyba.dossier.proto.Dossier;
import com.github.rjeschke.txtmark.Processor;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticScope;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Generates HTML documentation.
 */
class HtmlDocWriter implements DocWriter {

  private final Config config;
  private final DocRegistry docRegistry;
  private final Linker linker;

  private final Renderer renderer = new Renderer();

  private Iterable<Descriptor> sortedTypes;
  private Iterable<Path> sortedFiles;
  private Iterable<ModuleDescriptor> sortedModules;

  private ModuleDescriptor currentModule;

  HtmlDocWriter(Config config, DocRegistry registry) {
    this.config = checkNotNull(config);
    this.docRegistry = checkNotNull(registry);
    this.linker = new Linker(config, registry);
  }

  @Override
  public void generateDocs(final JSTypeRegistry registry) throws IOException {
    sortedTypes = FluentIterable.from(docRegistry.getTypes())
        .toSortedList(DescriptorNameComparator.INSTANCE);
    sortedFiles = FluentIterable.from(Iterables.concat(config.getSources(), config.getModules()))
        .toSortedList(PathComparator.INSTANCE);
    sortedModules = FluentIterable.from(docRegistry.getModules())
        .toSortedList(new ModueDisplayPathComparator());

    Files.createDirectories(config.getOutput());
    copyResources();
    copySourceFiles();
    generateIndex();
    generateLicense();

    for (Descriptor descriptor : sortedTypes) {
      if (descriptor.isEmptyNamespace()) {
        continue;
      }
      generateDocs(descriptor, registry);
    }

    for (ModuleDescriptor descriptor : sortedModules) {
      generateModuleDocs(descriptor, registry);
    }

    writeTypesJson();
  }

  private void generateIndex() throws IOException {
    Dossier.Comment readme = Dossier.Comment.getDefaultInstance();
    if (config.getReadme().isPresent()) {
      String text = new String(Files.readAllBytes(config.getReadme().get()), Charsets.UTF_8);
      String readmeHtml = Processor.process(text);
      // One more pass to process any inline taglets (e.g. {@code} or {@link}).
      readme = parseComment(readmeHtml, linker);
    }

    Path index = config.getOutput().resolve("index.html");
    renderer.render(index, IndexFileRenderSpec.newBuilder()
        .setResources(getResources(index))
        .setHasLicense(config.getLicense().isPresent())
        .setReadme(readme)
        .build());
  }

  private void generateLicense() throws IOException {
    if (!config.getLicense().isPresent()) {
      return;
    }

    Path license = config.getOutput().resolve("license.html");
    String text = new String(Files.readAllBytes(config.getLicense().get()), Charsets.UTF_8);

    renderer.render(
        license,
        LicenseRenderSpec.newBuilder()
            .setLicense(License.newBuilder().setText(text).build())
            .setResources(getResources(license))
            .build());
  }

  private void generateModuleDocs(ModuleDescriptor module, JSTypeRegistry registry)
      throws IOException {
    Path output = linker.getFilePath(module);
    Files.createDirectories(output.getParent());

    // Always generate documentation for both the internal and external typedefs since
    // they're most likely used in other jsdoc type annotations.
    // TODO: handle this in a cleaner way.
    Iterable<? extends JsType.TypeDef> typeDefs = Iterables.concat(
        getTypeDefInfo(module.getExportedProperties()),
        getTypeDefInfo(module.getInternalTypeDefs()));

    Descriptor descriptor = module.getDescriptor();
    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setIsModule(true)
        .setName(linker.getDisplayName(module))
        .setSource(linker.getSourcePath(module))
        .setDescription(getFileoverview(linker, module.getJsDoc()))
        .setNested(getNestedTypeInfo(module.getExportedProperties()))
        .addAllTypeDef(typeDefs)
        .addAllExtendedType(getInheritedTypes(descriptor, registry))
        .addAllImplementedType(getImplementedTypes(descriptor, registry));

    getStaticData(jsTypeBuilder, descriptor.getProperties());
    getPrototypeData(jsTypeBuilder, descriptor, registry);

    if (module.getDescriptor().isDeprecated()) {
      jsTypeBuilder.setDeprecation(getDeprecation(module.getDescriptor()));
    }

    if (descriptor.isEnum()) {
      extractEnumData(descriptor, jsTypeBuilder);
    }

    if (descriptor.isConstructor() || descriptor.isInterface()) {
      jsTypeBuilder.setConstructor(getFunctionData(descriptor));
    }

    renderer.render(
        output,
        JsTypeRenderSpec.newBuilder()
            .setResources(getResources(output))
            .setHasLicense(config.getLicense().isPresent())
            .setType(jsTypeBuilder.build())
            .build());

    currentModule = module;
    for (Descriptor property : module.getExportedProperties()) {
      // If the exported descriptor is an alias for another documented type, there is no
      // need to generate an additional set of docs as we can just link to the original.
      if (property == resolveTypeAlias(property)) {
        generateDocs(property, registry);
      }
    }
    currentModule = null;
  }

  private void generateDocs(Descriptor descriptor, JSTypeRegistry registry) throws IOException {
    Path output = linker.getFilePath(descriptor);
    Files.createDirectories(output.getParent());

    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setName(descriptor.getFullName())
        .setNested(getNestedTypeInfo(descriptor.getProperties()))
        .setSource(nullToEmpty(linker.getSourcePath(descriptor)))
        .setDescription(getBlockDescription(linker, descriptor.getJsDoc()))
        .addAllTypeDef(getTypeDefInfo(descriptor.getProperties()))
        .addAllExtendedType(getInheritedTypes(descriptor, registry))
        .addAllImplementedType(getImplementedTypes(descriptor, registry))
        .setIsInterface(descriptor.isInterface());

    getStaticData(jsTypeBuilder, descriptor.getProperties());
    getPrototypeData(jsTypeBuilder, descriptor, registry);

    if (descriptor.isDeprecated()) {
      jsTypeBuilder.setDeprecation(getDeprecation(descriptor));
    }

    if (descriptor.isEnum()) {
      extractEnumData(descriptor, jsTypeBuilder);
    }

    if (descriptor.isConstructor() || descriptor.isInterface()) {
      jsTypeBuilder.setConstructor(getFunctionData(descriptor));
    }

    renderer.render(
        output,
        JsTypeRenderSpec.newBuilder()
            .setResources(getResources(output))
            .setHasLicense(config.getLicense().isPresent())
            .setType(jsTypeBuilder.build())
            .build());
  }

  private void copyResources() throws IOException {
    FileSystem fs = config.getOutput().getFileSystem();
    copyResource(fs.getPath("/dossier.css"), config.getOutput());
    copyResource(fs.getPath("/dossier.js"), config.getOutput());
  }

  private static void copyResource(Path resourcePath, Path outputDir) throws IOException {
    try (InputStream stream = DocPass.class.getResourceAsStream(resourcePath.toString())) {
      Path outputPath = outputDir.resolve(resourcePath.getFileName());
      Files.copy(stream, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void writeTypesJson() throws IOException {
    try {
      JSONArray files = new JSONArray();
      for (Path source : sortedFiles) {
        Path displayPath = config.getSrcPrefix().relativize(source);
        String dest = config.getOutput().relativize(
            linker.getFilePath(source)).toString();
        files.put(new JSONObject()
            .put("name", displayPath.toString())
            .put("href", dest));
      }

      JSONArray modules = new JSONArray();
      for (ModuleDescriptor module : sortedModules) {
        String dest = config.getOutput().relativize(linker.getFilePath(module)).toString();
        modules.put(new JSONObject()
            .put("name", linker.getDisplayName(module))
            .put("href", dest));
      }

      JSONObject json = new JSONObject()
          .put("files", files)
          .put("modules", modules)
          .put("types", getTypeInfo(sortedTypes));

      // NOTE: JSON is not actually a subset of JavaScript, but in our case we know we only
      // have valid JavaScript input, so we can use JSONObject#toString() as a quick-and-dirty
      // formatting mechanism.
      String content = "var TYPES = " + json + ";";

      Path outputPath = config.getOutput().resolve("types.js");
      Files.write(outputPath, content.getBytes(Charsets.UTF_8));
    } catch (JSONException e) {
      throw new IOException(e);
    }
  }

  private JSONArray getTypeInfo(Iterable<Descriptor> types) throws JSONException {
    JSONArray array = new JSONArray();
    for (Descriptor descriptor : types) {
      if (descriptor.isEmptyNamespace()) {
        continue;
      }

      String dest = config.getOutput().relativize(linker.getFilePath(descriptor)).toString();
      array.put(new JSONObject()
          .put("name", descriptor.getFullName())
          .put("href", dest)
          .put("isInterface", descriptor.isInterface()));

      // Also include typedefs. These will not be included in the main
      // index, but will be searchable.
      List<Descriptor> typedefs = FluentIterable.from(descriptor.getProperties())
          .filter(isTypedef())
          .toSortedList(DescriptorNameComparator.INSTANCE);
      for (Descriptor typedef : typedefs) {
        array.put(new JSONObject()
            .put("name", typedef.getFullName())
            .put("href", nullToEmpty(linker.getLink(typedef.getFullName())))
            .put("isTypedef", true));
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
    for (Path source : Iterables.concat(config.getSources(), config.getModules())) {
      Path displayPath = config.getSrcPrefix().relativize(source);
      Path renderPath = linker.getFilePath(source);

      SourceFile file = SourceFile.newBuilder()
          .setBaseName(source.getFileName().toString())
          .setPath(displayPath.toString())
          .addAllLines(Files.readAllLines(source, Charsets.UTF_8))
          .build();

      renderer.render(
          renderPath,
          SourceFileRenderSpec.newBuilder()
              .setFile(file)
              .setResources(getResources(renderPath))
              .setHasLicense(config.getLicense().isPresent())
              .build());
    }
  }

  private List<TypeLink> getInheritedTypes(
      Descriptor descriptor, JSTypeRegistry registry) {
    LinkedList<JSType> types = descriptor.getAllTypes(registry);
    List<TypeLink> list = Lists.newArrayListWithExpectedSize(types.size());
    while (!types.isEmpty()) {
      list.add(getTypeLink(types.pop()));
    }
    return list;
  }

  private Iterable<TypeLink> getImplementedTypes(
      Descriptor descriptor, JSTypeRegistry registry) {
    Set<JSType> interfaces = descriptor.isInterface()
        ? descriptor.getExtendedInterfaces(registry)
        : descriptor.getImplementedInterfaces(registry);
    return transform(Ordering.usingToString().sortedCopy(interfaces),
        new Function<JSType, TypeLink>() {
          @Override
          public TypeLink apply(JSType input) {
            return getTypeLink(input);
          }
        });
  }

  private TypeLink getTypeLink(JSType type) {
    String typeName = getTypeName(type);
    String link;
    Descriptor descriptor = docRegistry.resolve(typeName, currentModule);
    if (descriptor != null) {
      typeName = descriptor.getFullName();
      link = linker.getLink(descriptor);
    } else {
      link = linker.getLink(typeName);
    }
    return TypeLink.newBuilder()
        .setText(typeName)
        .setHref(nullToEmpty(link))
        .build();
  }

  private String getTypeName(JSType type) {
    String typeName = type.toString();
    if (type.getJSDocInfo() != null) {
      JSDocInfo info = type.getJSDocInfo();
      if (info.getAssociatedNode() != null) {
        Node node = info.getAssociatedNode();
        if (node.isVar() || node.isFunction()) {
          checkState(node.getFirstChild().isName());
          typeName = Objects.firstNonNull(
              (String) node.getFirstChild().getProp(Node.ORIGINALNAME_PROP),
              typeName);
        }
      }
    }
    return typeName;
  }

  private void extractEnumData(Descriptor descriptor, JsType.Builder builder) {
    if (!descriptor.isEnum()) {
      return;
    }
    JsDoc jsdoc = checkNotNull(descriptor.getJsDoc(),
        "No jsdoc for enum %s; this should never happen!", descriptor.getFullName());

    Enumeration.Builder enumBuilder = Dossier.Enumeration.newBuilder()
        .setTypeHtml(formatTypeExpression(jsdoc.getType(), linker))
        .setVisibility(Dossier.Visibility.valueOf(descriptor.getVisibility().name()));

    ObjectType object = descriptor.toObjectType();
    for (String name : object.getOwnPropertyNames()) {
      JSType type = object.getPropertyType(name);
      if (type.isEnumElementType()) {
        Node node = object.getPropertyNode(name);
        JSDocInfo valueInfo = node == null ? null : node.getJSDocInfo();

        Enumeration.Value.Builder valueBuilder = Enumeration.Value.newBuilder()
            .setName(name);

        if (valueInfo != null) {
          JsDoc valueJsDoc = new JsDoc(valueInfo);
          valueBuilder.setDescription(parseComment(valueJsDoc.getBlockComment(), linker));

          if (valueJsDoc.isDeprecated()) {
            valueBuilder.setDeprecation(Deprecation.newBuilder()
                .setNotice(parseComment(valueJsDoc.getDeprecationReason(), linker))
                .build());
          }
        }

        enumBuilder.addValue(valueBuilder);
      }
    }

    builder.setEnumeration(enumBuilder);
  }

  private List<JsType.TypeDef> getTypeDefInfo(Iterable<Descriptor> properties) {
    return FluentIterable.from(properties)
        .filter(isTypedef())
        .transform(new Function<Descriptor, JsType.TypeDef>() {
          @Override
          public JsType.TypeDef apply(Descriptor typedef) {
            JsDoc jsdoc = checkNotNull(typedef.getJsDoc());
            JsType.TypeDef.Builder builder = JsType.TypeDef.newBuilder()
                .setName(typedef.getFullName())
                .setTypeHtml(formatTypeExpression(jsdoc.getType(), linker))
                .setHref(linker.getSourcePath(typedef))
                .setDescription(getBlockDescription(linker, jsdoc))
                .setVisibility(Dossier.Visibility.valueOf(typedef.getVisibility().name()));

            if (typedef.isDeprecated()) {
              builder.setDeprecation(getDeprecation(typedef));
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

  private Deprecation getDeprecation(Descriptor descriptor) {
    return Deprecation.newBuilder()
        .setNotice(parseComment(descriptor.getDeprecationReason(), linker))
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
    JSType type = descriptor.getType();
    if (type.isConstructor()) {
      type = ((FunctionType) type).getTypeOfThis();
    }
    Descriptor alias = docRegistry.getType(type);
    if (alias == null) {
      String name = getTypeName(type);
      alias = docRegistry.resolve(name, currentModule);
    }

    if (alias != null) {
      return alias;
    }
    return descriptor;
  }

  private JsType.NestedTypes.Builder getNestedTypeInfo(Iterable<Descriptor> properties) {
    List<Descriptor> children = FluentIterable.from(properties)
        .toSortedList(DescriptorNameComparator.INSTANCE);

    JsType.NestedTypes.Builder builder = JsType.NestedTypes.newBuilder();

    for (Descriptor child : children) {
      if (!child.isConstructor() && !child.isEnum() && !child.isInterface()) {
        continue;
      }

      // If our child property is an alias for another type, just link to that:
      //   --- one.js
      //   /** @constructor */ exports.Bar = function(){};
      //   -- two.js
      //   var one = require('./one');
      //   /** @type {function(new: one.Bar)} */ exports.Bar = one.Bar;
      Descriptor resolvedType = resolveTypeAlias(child);
      Path filename = linker.getFilePath(resolvedType).getFileName();

      Dossier.Comment summary = getSummary("No description.", linker);
      JsDoc jsdoc = Optional.fromNullable(resolvedType.getJsDoc())
          .or(Optional.fromNullable(child.getJsDoc()))
          .orNull();
      if (jsdoc != null) {
        summary =  getSummary(jsdoc.getBlockComment(), linker);
      }

      JsType.NestedTypes.TypeSummary.Builder typeSummary =
          JsType.NestedTypes.TypeSummary.newBuilder()
              .setHref(filename.toString())
              .setSummary(summary)
              .setName(child.getFullName());

      if (child.isEnum()) {
        builder.addEnums(typeSummary);
      } else if (child.isInterface()) {
        builder.addInterfaces(typeSummary);
      } else if (child.isConstructor()) {
        builder.addClasses(typeSummary);
      }
    }

    return builder;
  }

  private void getPrototypeData(
      JsType.Builder jsTypeBuilder, Descriptor descriptor, JSTypeRegistry registry) {
    if (!descriptor.isConstructor() && !descriptor.isInterface()) {
      return;
    }

    List<Descriptor> seen = new LinkedList<>();
    Iterable<JSType> assignableTypes =
        Lists.reverse(descriptor.getAssignableTypes(registry));

    for (JSType type : assignableTypes) {
      String assignableTypeName = getTypeName(type);
      Descriptor typeDescriptor = docRegistry.resolve(assignableTypeName, currentModule);
      if (typeDescriptor == null) {
        Node typeNode = null;
        if (type instanceof StaticScope) {
          typeNode = ((StaticScope<?>) type).getRootNode();
        }
        typeDescriptor = new Descriptor(
            assignableTypeName, typeNode, type, type.getJSDocInfo());
      }

      FluentIterable<Descriptor> unsorted = FluentIterable
          .from(typeDescriptor.getInstanceProperties());
      if (typeDescriptor != descriptor) {
        // Filter out properties that have been overridden.
        unsorted = unsorted.filter(notOwnPropertyOf(seen));
      }

      List<Descriptor> properties = unsorted.toSortedList(DescriptorNameComparator.INSTANCE);
      seen.add(typeDescriptor);
      if (properties.isEmpty()) {
        continue;
      }

      Prototype.Builder protoBuilder = Prototype.newBuilder()
          .setName(typeDescriptor.getFullName());
      if (typeDescriptor != descriptor) {
        protoBuilder.setHref(nullToEmpty(linker.getLink(typeDescriptor)));
      }

      for (Descriptor property : properties) {
        if (isProperty(property)) {
          protoBuilder.addProperty(getPropertyData(property));
          jsTypeBuilder.setHasInstanceProperties(true);
        } else if (isFunction(property)) {
          protoBuilder.addFunction(getFunctionData(property));
          jsTypeBuilder.setHasInstanceMethods(true);
        }
      }
      jsTypeBuilder.addPrototype(protoBuilder);
    }
  }

  private void getStaticData(
      JsType.Builder jsTypeBuilder, Iterable<Descriptor> properties) {
    List<Descriptor> props = FluentIterable.from(properties)
        .toSortedList(DescriptorNameComparator.INSTANCE);
    for (Descriptor property : props) {
      if (property.isCompilerConstant()) {
        jsTypeBuilder.addCompilerConstant(getPropertyData(property));
      } else if (isProperty(property)) {
        jsTypeBuilder.addStaticProperty(getPropertyData(property));
      } else if (isFunction(property)) {
        jsTypeBuilder.addStaticFunction(getFunctionData(property));
      }
    }
  }

  private BaseProperty getBasePropertyDetails(Descriptor property) {
    String name = property.isConstructor() || property.isInterface()
        ? property.getFullName()
        : property.getSimpleName();

    BaseProperty.Builder builder = BaseProperty.newBuilder()
        .setName(name)
        .setSource(nullToEmpty(linker.getSourcePath(property)))
        .setDescription(getBlockDescription(linker, property.getJsDoc()))
        .setVisibility(Dossier.Visibility.valueOf(property.getVisibility().name()));

    if (property.isDeprecated()) {
      builder.setDeprecation(getDeprecation(property));
    }

    return builder.build();
  }

  private Property getPropertyData(Descriptor property) {
    Property.Builder builder = Property.newBuilder()
        .setBase(getBasePropertyDetails(property));

    JsDoc jsDoc = property.getJsDoc();
    if (jsDoc != null && jsDoc.getType() != null) {
      builder.setTypeHtml(formatTypeExpression(jsDoc.getType(), linker));
    } else if (property.getType() != null) {
      JSType propertyType = property.getType();

      // Since we don't document prototype objects, if the property refers to a prototype,
      // try to link to the owner function.
      boolean isPrototype = propertyType.isFunctionPrototypeType();
      if (isPrototype) {
        propertyType = ObjectType.cast(propertyType).getOwnerFunction();

        // If the owner function is a constructor, the propertyType will be of the form
        // "function(new: Type)".  We want to link to "Type".
        if (propertyType.isConstructor()) {
          propertyType = propertyType.toObjectType().getTypeOfThis();
        }
      }

      Descriptor propertyTypeDescriptor = null;
      if (!propertyType.isUnknownType()) {
        propertyTypeDescriptor = docRegistry.getType(propertyType);
        if (propertyTypeDescriptor == null) {
          String name = getTypeName(propertyType);
          propertyTypeDescriptor = docRegistry.resolve(name, currentModule);
        }
      }

      if (propertyTypeDescriptor != null) {
        String link = linker.getLink(propertyTypeDescriptor);
        checkState(!Strings.isNullOrEmpty(link),
            "Unable to compute link to %s; this should never happen since %s was previously" +
                " found in the type registry.", propertyTypeDescriptor.getFullName());
        String fullName = linker.getDisplayName(propertyTypeDescriptor);
        if (isPrototype) {
          fullName += ".prototype";
        }
        builder.setTypeHtml(String.format("<a href=\"%s\">%s</a>", link, fullName));

      } else {
        String typeName = propertyType.toString();
        if (isPrototype) {
          typeName += ".prototype";
        }

        String link = linker.getLink(propertyType.toString());
        if (link == null) {
          builder.setTypeHtml(typeName);
        } else {
          builder.setTypeHtml(String.format("<a href=\"%s\">%s</a>", link, typeName));
        }
      }
    }

    return builder.build();
  }

  private Dossier.Function getFunctionData(Descriptor function) {
    Dossier.Function.Builder builder = Dossier.Function.newBuilder()
        .setBase(getBasePropertyDetails(function));

    JsDoc jsDoc = function.getJsDoc();
    if (!function.isConstructor() && !function.isInterface()) {
      Dossier.Function.Detail.Builder detail = Dossier.Function.Detail.newBuilder();
      detail.setTypeHtml(getReturnType(function));
      if (jsDoc != null) {
        detail.setDescription(parseComment(jsDoc.getReturnDescription(), linker));
      }
      builder.setReturn(detail);
    }

    if (jsDoc != null) {
      builder.addAllTemplateName(jsDoc.getTemplateTypeNames())
          .addAllThrown(buildThrowsData(jsDoc));
    }

    builder.addAllParameter(transform(function.getArgs(),
        new Function<ArgDescriptor, Dossier.Function.Detail>() {
      @Override
      public Dossier.Function.Detail apply(ArgDescriptor arg) {
        return Dossier.Function.Detail.newBuilder()
            .setName(arg.getName())
            .setTypeHtml(arg.getType() == null ? "" :
                formatTypeExpression(arg.getType(), linker))
            .setDescription(parseComment(arg.getDescription(), linker))
            .build();
      }
    }));

    return builder.build();
  }

  private Iterable<Dossier.Function.Detail> buildThrowsData(JsDoc jsDoc) {
    return transform(jsDoc.getThrowsClauses(),
        new Function<JsDoc.ThrowsClause, Dossier.Function.Detail>() {
          @Override
          public Dossier.Function.Detail apply(JsDoc.ThrowsClause input) {
            String thrownType = "";
            if (input.getType().isPresent()) {
              thrownType = formatTypeExpression(input.getType().get(), linker);
            }
            return Dossier.Function.Detail.newBuilder()
                .setTypeHtml(thrownType)
                .setDescription(parseComment(input.getDescription(), linker))
                .build();
          }
        });
  }

  @Nullable
  private String getReturnType(Descriptor function) {
    JsDoc jsdoc = function.getJsDoc();
    if (jsdoc != null) {
      JSTypeExpression expr = jsdoc.getReturnType();
      if (expr != null) {
        return formatTypeExpression(expr, linker);
      }
    }
    JSType type = ((FunctionType) function.toObjectType()).getReturnType();
    return type == null ? null : type.toString();
  }

  private static Predicate<Descriptor> isTypedef() {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        return input.getJsDoc() != null && input.getJsDoc().isTypedef();
      }
    };
  }

  private boolean isFunction(Descriptor descriptor) {
    return descriptor.isFunction()
        && !descriptor.isConstructor()
        && !descriptor.isInterface();
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

  private boolean isProperty(Descriptor descriptor) {
    if (docRegistry.isKnownType(descriptor.getFullName())) {
      return false;
    }

    JSType type = descriptor.getType();
    return type == null
        || (!type.isEnumType()
        && !type.isEnumElementType()
        && !type.isFunctionType()
        && !type.isConstructor()
        && !type.isInterface());
  }

  private static enum DescriptorNameComparator implements Comparator<Descriptor> {
    INSTANCE;

    @Override
    public int compare(Descriptor a, Descriptor b) {
      return a.getFullName().compareTo(b.getFullName());
    }
  }

  private static enum PathComparator implements Comparator<Path> {
    INSTANCE;

    @Override
    public int compare(Path o1, Path o2) {
      return o1.toString().compareTo(o2.toString());
    }
  }

  private class ModueDisplayPathComparator implements Comparator<ModuleDescriptor> {

    @Override
    public int compare(ModuleDescriptor o1, ModuleDescriptor o2) {
      return linker.getDisplayName(o1).compareTo(linker.getDisplayName(o2));
    }
  }
}
