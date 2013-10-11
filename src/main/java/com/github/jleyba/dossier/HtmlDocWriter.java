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
import static com.google.common.collect.Iterables.transform;

import com.github.jleyba.dossier.proto.Dossier;
import com.github.rjeschke.txtmark.Processor;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
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
import java.util.Iterator;
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
        .toSortedList(DescriptorNameComparator.INSTANCE);
    sortedFiles = FluentIterable.from(config.getSources())
        .toSortedList(PathComparator.INSTANCE);

    Files.createDirectories(config.getOutput());
    copyResources();
    copySourceFiles();
    generateIndex();
    generateLicense();

    for (Descriptor descriptor : sortedTypes) {
      generateDocs(descriptor, registry);
    }

    writeTypesJson();
  }

  private void generateIndex() throws IOException {
    String readmeHtml = "";
    if (config.getReadme().isPresent()) {
      String text = new String(Files.readAllBytes(config.getReadme().get()), Charsets.UTF_8);
      readmeHtml = Processor.process(text);
      // One more pass to process any inline taglets (e.g. {@code} or {@link}).
      readmeHtml = CommentUtil.formatCommentText(linker, readmeHtml);
    }

    Path index = config.getOutput().resolve("index.html");
    renderer.render(index, IndexFileRenderSpec.newBuilder()
        .setResources(Resources.newBuilder()
            .addCss("dossier.css")
            .addScript("types.js")
            .addScript("dossier.js")
            .build())
        .setHasLicense(config.getLicense().isPresent())
        .setReadmeHtml(readmeHtml)
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
        .setName(descriptor.getFullName())
        .setNested(getNestedTypes(descriptor))
        .setSource(Strings.nullToEmpty(source))
        .setDescriptionHtml(CommentUtil.getBlockDescription(linker, descriptor.getInfo()))
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
      Path fileDir = config.getSourceOutput();
      for (Path source : sortedFiles) {
        Path simpleSource = config.getSrcPrefix().relativize(source);
        Path dest = fileDir.resolve(simpleSource.toString() + ".src.html");
        dest = config.getOutput().relativize(dest);

        files.put(new JSONObject()
            .put("name", simpleSource.toString())
            .put("href", dest.toString()));
      }

      JSONArray types = new JSONArray();
      for (Descriptor descriptor : sortedTypes) {
        String dest = config.getOutput().relativize(
            linker.getFilePath(descriptor)).toString();
        types.put(new JSONObject()
            .put("name", descriptor.getFullName())
            .put("href", dest)
            .put("isInterface", descriptor.isInterface()));

        // Also include typedefs. These will not be included in the main
        // index, but will be searchable.
        List<Descriptor> typedefs = FluentIterable.from(descriptor.getProperties())
            .filter(isTypedef())
            .toSortedList(DescriptorNameComparator.INSTANCE);
        for (Descriptor typedef : typedefs) {
          types.put(new JSONObject()
              .put("name", typedef.getFullName())
              .put("href", linker.getLink(typedef.getFullName()))
              .put("isTypedef", true));
        }
      }

      JSONObject json = new JSONObject()
          .put("files", files)
          .put("types", types);

      String content = "var TYPES = " + json + ";";

      Path outputPath = config.getOutput().resolve("types.js");
      Files.write(outputPath, content.getBytes(Charsets.UTF_8));
    } catch (JSONException e) {
      throw new IOException(e);
    }
  }

  private Path getPathToOutputDir(Path from) {
    if (!Files.isDirectory(from)) {
      from = from.getParent();
    }
    Path path = from.getFileSystem().getPath("..");
    for (Iterator<Path> it = from.iterator(); it.hasNext(); it.next()) {
      path = path.resolve("..");
    }
    return path;
  }

  private void copySourceFiles() throws IOException {
    Path fileDir = config.getSourceOutput();
    for (Path source : config.getSources()) {
      Path simpleSource = config.getSrcPrefix().relativize(source);
      Path dest = fileDir.resolve(simpleSource.toString() + ".src.html");

      Path relativePath = getPathToOutputDir(simpleSource);
      Path toDossierCss = relativePath.resolve("dossier.css");
      Path toTypesJs = relativePath.resolve("types.js");
      Path toDossierJs = relativePath.resolve("dossier.js");

      Resources resources = Resources.newBuilder()
          .addCss(toDossierCss.toString())
          .addScript(toTypesJs.toString())
          .addScript(toDossierJs.toString())
          .build();

      SourceFile file = SourceFile.newBuilder()
          .setBaseName(source.getFileName().toString())
          .setPath(simpleSource.toString())
          .addAllLines(Files.readAllLines(source, Charsets.UTF_8))
          .build();

      renderer.render(dest,
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
          .setHref(linker.getLink(type))
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
                .setHref(linker.getLink(input))
                .build();
          }
        });
  }

  private void extractEnumData(Descriptor descriptor, JsType.Builder builder) {
    if (!descriptor.isEnum()) {
      return;
    }
    JSDocInfo info = checkNotNull(descriptor.getInfo(),
        "Should never happen; check for used to statisfy static analysis tools: %s",
        descriptor.getFullName());

    Enumeration.Builder enumBuilder = Dossier.Enumeration.newBuilder()
        .setTypeHtml(CommentUtil.formatTypeExpression(info.getEnumParameterType(), linker))
        .setVisibility(Dossier.Visibility.valueOf(descriptor.getVisibility().name()));

    ObjectType object = descriptor.toObjectType();
    for (String name : object.getOwnPropertyNames()) {
      JSType type = object.getPropertyType(name);
      if (type.isEnumElementType()) {
        Node node = object.getPropertyNode(name);
        JSDocInfo valueInfo = node == null ? null : node.getJSDocInfo();

        Enumeration.Value.Builder valueBuilder = Enumeration.Value.newBuilder()
            .setName(name)
            .setDescriptionHtml(CommentUtil.getBlockDescription(linker, valueInfo));

        if (valueInfo != null && valueInfo.isDeprecated()) {
          valueBuilder.setDeprecation(Deprecation.newBuilder()
              .setNoticeHtml(CommentUtil.formatCommentText(
                  linker, valueInfo.getDeprecationReason()))
              .build());
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
            JSDocInfo info = checkNotNull(typedef.getInfo());
            JsType.TypeDef.Builder builder = JsType.TypeDef.newBuilder()
                .setName(typedef.getFullName())
                .setTypeHtml(CommentUtil.formatTypeExpression(info.getTypedefType(), linker))
                .setHref(linker.getSourcePath(typedef))
                .setDescriptionHtml(CommentUtil.getBlockDescription(linker, info))
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
        .setNoticeHtml(CommentUtil.formatCommentText(linker,
            descriptor.getDeprecationReason()))
        .build();
  }

  private JsType.NestedTypes.Builder getNestedTypes(Descriptor descriptor) {
    List<Descriptor> children = FluentIterable.from(descriptor.getProperties())
        .toSortedList(DescriptorNameComparator.INSTANCE);

    JsType.NestedTypes.Builder builder = JsType.NestedTypes.newBuilder();

    for (Descriptor child : children) {
      if (!child.isConstructor() && !child.isEnum() && !child.isInterface()) {
        continue;
      }

      JSDocInfo info = checkNotNull(child.getInfo());
      String comment = CommentUtil.getBlockDescription(linker, info);
      String summary = CommentUtil.getSummary(comment);

      JsType.NestedTypes.TypeSummary.Builder typeSummary =
          JsType.NestedTypes.TypeSummary.newBuilder()
              .setHref(linker.getFilePath(child).getFileName().toString())
              .setSummaryHtml(summary)
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

      List<Descriptor> properties = unsorted.toSortedList(DescriptorNameComparator.INSTANCE);
      seen.add(typeDescriptor);
      if (properties.isEmpty()) {
        continue;
      }

      Prototype.Builder protoBuilder = Prototype.newBuilder()
          .setName(typeDescriptor.getFullName());
      if (typeDescriptor != descriptor) {
        protoBuilder.setHref(linker.getLink(typeDescriptor.getFullName()));
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
        .toSortedList(DescriptorNameComparator.INSTANCE);
    for (Descriptor property : props) {
      if (isProperty(property)) {
        jsTypeBuilder.addStaticProperty(getPropertyData(property));
      } else if (isFunction(property)) {
        jsTypeBuilder.addStaticFunction(getFunctionData(property));
      }
    }
  }

  private BaseProperty getBasePropertyDetails(Descriptor property) {
    String name = property.isConstructor() || property.isInterface()
        ? property.getFullName()
        : property.getName();

    BaseProperty.Builder builder = BaseProperty.newBuilder()
        .setName(name)
        .setSource(Strings.nullToEmpty(linker.getSourcePath(property)))
        .setDescriptionHtml(CommentUtil.getBlockDescription(linker, property.getInfo()))
        .setVisibility(Dossier.Visibility.valueOf(property.getVisibility().name()));

    if (property.isDeprecated()) {
      builder.setDeprecation(getDeprecation(property));
    }

    return builder.build();
  }

  private Property getPropertyData(Descriptor property) {
    Property.Builder builder = Property.newBuilder()
        .setBase(getBasePropertyDetails(property));

    JSDocInfo info = property.getInfo();
    if (info != null && info.getType() != null) {
      builder.setTypeHtml(CommentUtil.formatTypeExpression(info.getType(), linker));
    } else if (property.getType() != null) {
      Descriptor propertyTypeDescriptor =
          docRegistry.getType(property.getType().toString());
      if (propertyTypeDescriptor == null) {
        propertyTypeDescriptor = docRegistry.getType(property.getType().toString());
      }

      if (propertyTypeDescriptor != null) {
        builder.setTypeHtml(String.format("<a href=\"%s\">%s</a>",
            linker.getLink(propertyTypeDescriptor.getFullName()),
            propertyTypeDescriptor.getFullName()));
      } else {
        builder.setTypeHtml(property.getType().toString());
      }
    }

    return builder.build();
  }

  private Dossier.Function getFunctionData(Descriptor function) {
    Dossier.Function.Builder builder = Dossier.Function.newBuilder()
        .setBase(getBasePropertyDetails(function));

    JSDocInfo info = function.getInfo();
    if (!function.isConstructor()) {
      Dossier.Function.Detail.Builder detail = Dossier.Function.Detail.newBuilder();
      detail.setTypeHtml(getReturnType(function));
      if (info != null) {
        detail.setDescriptionHtml(
            CommentUtil.formatCommentText(linker, info.getReturnDescription()));
      }
      builder.setReturn(detail);
    }

    if (info != null) {
      builder.addAllTemplateName(info.getTemplateTypeNames())
          .addAllThrown(buildThrowsData(info));
    }

    builder.addAllParameter(transform(function.getArgs(),
        new Function<ArgDescriptor, Dossier.Function.Detail>() {
      @Override
      public Dossier.Function.Detail apply(ArgDescriptor arg) {
        return Dossier.Function.Detail.newBuilder()
            .setName(arg.getName())
            .setTypeHtml(arg.getType() == null ? "" :
                CommentUtil.formatTypeExpression(arg.getType(), linker))
            .setDescriptionHtml(arg.getDescription())
            .build();
      }
    }));

    return builder.build();
  }

  private List<Dossier.Function.Detail> buildThrowsData(JSDocInfo info) {
    List<Dossier.Function.Detail> throwsData = new LinkedList<>();
    // Manually scan the function markers so we can associated thrown types with the
    // document description for why that type would be thrown.
    for (JSDocInfo.Marker marker : info.getMarkers()) {
      if (!"throws".equals(marker.getAnnotation().getItem())) {
        continue;
      }

      @Nullable String thrownType = null;
      @Nullable String thrownDescription = null;
      if (marker.getType() != null) {
        JSTypeExpression expr = new JSTypeExpression(
            marker.getType().getItem(), info.getSourceName());
        thrownType = CommentUtil.formatTypeExpression(expr, linker);
      }

      if (marker.getDescription() != null) {
        thrownDescription = marker.getDescription().getItem();
      }

      throwsData.add(Dossier.Function.Detail.newBuilder()
          .setTypeHtml(thrownType)
          .setDescriptionHtml(thrownDescription)
          .build());
    }
    return throwsData;
  }

  @Nullable
  private String getReturnType(Descriptor function) {
    JSDocInfo info = function.getInfo();
    if (info != null) {
      JSTypeExpression expr = info.getReturnType();
      if (expr != null) {
        return CommentUtil.formatTypeExpression(expr, linker);
      }
    }
    JSType type = ((FunctionType) function.toObjectType()).getReturnType();
    return type == null ? null : type.toString();
  }

  private static Predicate<Descriptor> isTypedef() {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        return input.getInfo() != null && input.getInfo().getTypedefType() != null;
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
          if (descriptor.hasOwnInstanceProprety(input.getName())) {
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
    if (type == null) {
      return true;
    }
    return !type.isFunctionPrototypeType()
        && !type.isEnumType()
        && !type.isEnumElementType()
        && !type.isFunctionType()
        && !type.isConstructor()
        && !type.isInterface();
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
}
