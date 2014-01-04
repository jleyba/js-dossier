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
import static com.google.common.collect.Iterables.transform;

import com.github.jleyba.dossier.proto.Dossier;
import com.github.rjeschke.txtmark.Processor;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
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
import java.util.Stack;

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

  HtmlDocWriter(Config config, DocRegistry registry) {
    this.config = checkNotNull(config);
    this.docRegistry = checkNotNull(registry);
    this.linker = new Linker(config, registry);
  }

  @Override
  public void generateDocs(final JSTypeRegistry registry) throws IOException {
    sortedTypes = FluentIterable.from(docRegistry.getTypes())
        .toSortedList(new DescriptorNameComparator());
    sortedFiles = FluentIterable.from(Iterables.concat(config.getSources(), config.getModules()))
        .toSortedList(PathComparator.INSTANCE);

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
        .setResources(Resources.newBuilder()
            .addCss("dossier.css")
            .addScript("types.js")
            .addScript("dossier.js")
            .build())
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
            .setResources(Resources.newBuilder()
                .addCss("dossier.css")
                .addScript("types.js")
                .addScript("dossier.js")
                .build())
            .build());
  }

  private void generateDocs(Descriptor descriptor, JSTypeRegistry registry) throws IOException {
    Path output = linker.getFilePath(descriptor);
    Files.createDirectories(output.getParent());

    String source = linker.getSourcePath(descriptor);

    JsType.Builder jsTypeBuilder = JsType.newBuilder()
        .setName(linker.getDisplayName(descriptor))
        .setNested(getNestedTypes(descriptor))
        .setSource(Strings.nullToEmpty(source))
        .setDescription(getBlockDescription(linker, descriptor.getJsDoc()))
        .addAllTypeDef(getTypeDefs(descriptor))
        .addAllExtendedType(getInheritedTypes(descriptor, registry))
        .addAllImplementedType(getImplementedTypes(descriptor, registry))
        .setIsInterface(descriptor.isInterface());

    getStaticData(jsTypeBuilder, descriptor);
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
            .setResources(Resources.newBuilder()
                .addCss("dossier.css")
                .addScript("types.js")
                .addScript("dossier.js")
                .build())
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

      JSONArray types = new JSONArray();
      for (Descriptor descriptor : sortedTypes) {
        if (descriptor.isEmptyNamespace()) {
          continue;
        }

        String dest = config.getOutput().relativize(
            linker.getFilePath(descriptor)).toString();
        types.put(new JSONObject()
            .put("name", linker.getDisplayName(descriptor))
            .put("href", dest)
            .put("isInterface", descriptor.isInterface()));

        // Also include typedefs. These will not be included in the main
        // index, but will be searchable.
        List<Descriptor> typedefs = FluentIterable.from(descriptor.getProperties())
            .filter(isTypedef())
            .toSortedList(new DescriptorNameComparator());
        for (Descriptor typedef : typedefs) {
          types.put(new JSONObject()
              .put("name", typedef.getFullName())
              .put("href", Strings.nullToEmpty(linker.getLink(typedef.getFullName())))
              .put("isTypedef", true));
        }
      }

      JSONObject json = new JSONObject()
          .put("files", files)
          .put("types", types);

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

  private void copySourceFiles() throws IOException {
    for (Path source : Iterables.concat(config.getSources(), config.getModules())) {
      Path displayPath = config.getSrcPrefix().relativize(source);

      Resources resources = Resources.newBuilder()
          .addCss("dossier.css")
          .addScript("types.js")
          .addScript("dossier.js")
          .build();

      SourceFile file = SourceFile.newBuilder()
          .setBaseName(source.getFileName().toString())
          .setPath(displayPath.toString())
          .addAllLines(Files.readAllLines(source, Charsets.UTF_8))
          .build();

      renderer.render(
          linker.getFilePath(source),
          SourceFileRenderSpec.newBuilder()
              .setFile(file)
              .setResources(resources)
              .setHasLicense(config.getLicense().isPresent())
              .build());
    }
  }

  private List<TypeLink> getInheritedTypes(
      Descriptor descriptor, JSTypeRegistry registry) {
    Stack<String> types = descriptor.getAllTypes(registry);
    List<TypeLink> list = Lists.newArrayListWithExpectedSize(
        types.size());
    while (!types.isEmpty()) {
      String type = types.pop();
      list.add(TypeLink.newBuilder()
          .setText(type)
          .setHref(Strings.nullToEmpty(linker.getLink(type)))
          .build());
    }
    return list;
  }

  private Iterable<TypeLink> getImplementedTypes(
      Descriptor descriptor, JSTypeRegistry registry) {
    Set<String> interfaces = descriptor.isInterface()
        ? descriptor.getExtendedInterfaces(registry)
        : descriptor.getImplementedInterfaces(registry);
    return transform(Ordering.natural().sortedCopy(interfaces),
        new Function<String, TypeLink>() {
          @Override
          public TypeLink apply(String input) {
            return TypeLink.newBuilder()
                .setText(input)
                .setHref(Strings.nullToEmpty(linker.getLink(input)))
                .build();
          }
        });
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

  private List<JsType.TypeDef> getTypeDefs(Descriptor descriptor) {
    return FluentIterable.from(descriptor.getProperties())
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

  private JsType.NestedTypes.Builder getNestedTypes(Descriptor descriptor) {
    List<Descriptor> children = FluentIterable.from(descriptor.getProperties())
        .toSortedList(new DescriptorNameComparator());

    JsType.NestedTypes.Builder builder = JsType.NestedTypes.newBuilder();

    for (Descriptor child : children) {
      if (!child.isConstructor() && !child.isEnum() && !child.isInterface()) {
        continue;
      }

      JsDoc jsdoc = checkNotNull(child.getJsDoc());
      Dossier.Comment summary = getSummary(jsdoc.getBlockComment(), linker);

      JsType.NestedTypes.TypeSummary.Builder typeSummary =
          JsType.NestedTypes.TypeSummary.newBuilder()
              .setHref(linker.getFilePath(child).getFileName().toString())
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
    for (String type : descriptor.getAssignableTypes(registry)) {
      Descriptor typeDescriptor = docRegistry.getType(type);
      if (typeDescriptor == null) {
        continue;
      }

      FluentIterable<Descriptor> unsorted = FluentIterable
          .from(typeDescriptor.getInstanceProperties());
      if (typeDescriptor != descriptor) {
        // Filter out properties that have been overridden.
        unsorted = unsorted.filter(notOwnPropertyOf(seen));
      }

      List<Descriptor> properties = unsorted.toSortedList(new DescriptorNameComparator());
      seen.add(typeDescriptor);
      if (properties.isEmpty()) {
        continue;
      }

      Prototype.Builder protoBuilder = Prototype.newBuilder()
          .setName(typeDescriptor.getFullName());
      if (typeDescriptor != descriptor) {
        protoBuilder.setHref(
            Strings.nullToEmpty(linker.getLink(typeDescriptor.getFullName())));
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

  private void getStaticData(JsType.Builder jsTypeBuilder, Descriptor descriptor) {
    List<Descriptor> props = FluentIterable.from(descriptor.getProperties())
        .toSortedList(new DescriptorNameComparator());
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
        .setSource(Strings.nullToEmpty(linker.getSourcePath(property)))
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

      Descriptor propertyTypeDescriptor = docRegistry.getType(propertyType.toString());
      if (propertyTypeDescriptor != null) {
        String link = linker.getLink(propertyTypeDescriptor.getFullName());
        checkState(!Strings.isNullOrEmpty(link),
            "Unable to compute link to %s; this should never happen since %s was previously" +
                " found in the type registry.", propertyTypeDescriptor.getFullName());
        String fullName = propertyTypeDescriptor.getFullName();
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

  private class DescriptorNameComparator implements Comparator<Descriptor> {

    @Override
    public int compare(Descriptor a, Descriptor b) {
      return linker.getDisplayName(a).compareTo(linker.getDisplayName(b));
    }
  }

  private static enum PathComparator implements Comparator<Path> {
    INSTANCE;

    @Override
    public int compare(Path o1, Path o2) {
      return o1.toString().compareTo(o2.toString());
    }
  }
}
