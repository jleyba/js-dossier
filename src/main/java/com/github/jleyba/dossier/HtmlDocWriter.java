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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
  private final LinkResolver resolver;

  HtmlDocWriter(Config config, DocRegistry registry, LinkResolver resolver) {
    this.config = checkNotNull(config);
    this.docRegistry = checkNotNull(registry);
    this.resolver = checkNotNull(resolver);
  }

  @Override
  public void generateDocs(final JSTypeRegistry registry) throws IOException {
    Files.createDirectories(config.outputDir);
    copyResources();
    copySourceFiles();

    Iterable<Descriptor> descriptors = FluentIterable.from(docRegistry.getTypes())
        .filter(new Predicate<Descriptor>() {
          @Override
          public boolean apply(Descriptor input) {
            if (input.getSource() == null) {
              return true;
            }
            Path path = config.outputDir.getFileSystem().getPath(input.getSource());
            return !config.excludeDocs.contains(path);
          }
        });

    for (Descriptor descriptor : descriptors) {
      generateDocs(descriptor, registry);
    }

    writeTypesJson();
  }

  private void generateDocs(Descriptor descriptor, JSTypeRegistry registry) throws IOException {
    Path output = resolver.getFilePath(descriptor);
    Files.createDirectories(output.getParent());

    try (FileOutputStream os = new FileOutputStream(output.toFile())) {
      PrintStream stream = new PrintStream(os);
      stream.println("<!DOCTYPE html>");
      stream.println("<meta charset=\"UTF-8\">");
      stream.println("<title>" + descriptor.getFullName() + "</title>");
      stream.println("<link href=\"dossier.css\" type=\"text/css\" rel=\"stylesheet\">");
      stream.println();
      stream.println();
      printTopNav(stream);
      stream.println();
      stream.println();
      printSideNav(stream);
      stream.println();
      stream.println();
      stream.println("<article id=\"content\">");
      stream.println("<header>");
      printTitle(stream, descriptor);
      stream.println(getSourceLink(descriptor));
      printEnumType(stream, descriptor);
      printInheritanceTree(stream, descriptor, registry);
      printInterfaces(stream, "All implemented interfaces:",
          descriptor.getImplementedInterfaces(registry));
      printInterfaces(stream, "All extended interfaces:",
          descriptor.getExtendedInterfaces(registry));
      stream.println(printDeprecation(descriptor));
      stream.println("</header>");

      stream.println();
      stream.println();
      stream.println("<section>");
      printSummary(stream, descriptor.getInfo());
      printConstructor(stream, descriptor);
      printEnumValues(stream, descriptor);
      stream.println("</section>");

      printTypedefs(stream, descriptor);
      printNestedTypeSummaries(stream, "Interfaces", descriptor, isInterface());
      printNestedTypeSummaries(stream, "Classes", descriptor, isClass());
      printNestedTypeSummaries(stream, "Enumerations", descriptor, isEnum());
      printInstanceMethods(stream, descriptor, registry);
      printInstanceProperties(stream, descriptor, registry);
      printFunctions(stream, descriptor);
      printProperties(stream, descriptor);

      stream.println();
      stream.println();
      stream.println("</article>");
      stream.println("<footer><div>Generated by " +
          "<a href=\"https://github.com/jleyba/js-dossier\">dossier</a></div></footer>");
      stream.println("<script src=\"types.js\"></script>");
      stream.println("<script src=\"dossier.js\"></script>");
    }
  }

  private void copyResources() throws IOException {
    FileSystem fs = config.outputDir.getFileSystem();
    copyResource(fs.getPath("/dossier.css"), config.outputDir);
    copyResource(fs.getPath("/dossier.js"), config.outputDir);
  }

  private static void copyResource(Path resourcePath, Path outputDir) throws IOException {
    try (InputStream stream = DocPass.class.getResourceAsStream(resourcePath.toString())) {
      Path outputPath = outputDir.resolve(resourcePath.getFileName());
      Files.copy(stream, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void writeTypesJson() throws IOException {
    JSONArray classes = new JSONArray();
    JSONArray enums = new JSONArray();
    JSONArray interfaces = new JSONArray();
    JSONArray namespaces = new JSONArray();

    Iterable<Descriptor> types = FluentIterable.from(docRegistry.getTypes())
        .filter(new Predicate<Descriptor>() {
          @Override
          public boolean apply(Descriptor input) {
            String path = input.getSource();
            return path == null
                || !config.excludeDocs.contains(config.outputDir.getFileSystem().getPath(path));
          }
        })
        .toSortedList(DescriptorNameComparator.INSTANCE);

    for (Descriptor descriptor : types) {
      String name = descriptor.getFullName();
      if (descriptor.isConstructor()) {
        classes.put(name);
      } else if (descriptor.isInterface()) {
        interfaces.put(name);
      } else if (descriptor.isEnum()) {
        enums.put(name);
      } else {
        namespaces.put(name);
      }
    }

    try {
      JSONObject json = new JSONObject()
          .put("classes", classes)
          .put("interfaces", interfaces)
          .put("enums", enums)
          .put("namespaces", namespaces);

      String content = "var TYPES = " + json + ";";

      Path outputPath = config.outputDir.resolve("types.js");
      Files.write(outputPath, content.getBytes(Charsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.WRITE);
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
    Path fileDir = config.outputDir.resolve("source");
    for (Path source : config.filteredDocSrcs()) {
      Path simpleSource = simplifySourcePath(source);
      Path dest = fileDir.resolve(simpleSource.toString() + ".src.html");

      Path relativePath = getPathToOutputDir(simpleSource);
      Path toDossierCss = relativePath.resolve("dossier.css");
      Path toTypesJs = relativePath.resolve("types.js");
      Path toDossierJs = relativePath.resolve("dossier.js");

      Splitter lineSplitter = Splitter.on('\n');
      Files.createDirectories(dest.getParent());
      try (FileOutputStream fos = new FileOutputStream(dest.toString())) {
        PrintStream stream = new PrintStream(fos);
        stream.println("<!DOCTYPE html>");
        stream.println("<meta charset=\"UTF-8\">");
        stream.println("<title>" + source.getFileName() + "</title>");
        stream.println("<link href=\"" + toDossierCss + "\" type=\"text/css\" rel=\"stylesheet\">");
        stream.println();
        stream.println();
        printTopNav(stream);
        stream.println();
        stream.println();
        stream.println("<article class=\"srcfile\" id=\"content\">");
        stream.println("<header>");
        stream.println("<h1>" + simpleSource + "</h1>");
        stream.println("</header>");
        stream.print("<pre><table><tbody>");

        Iterable<String> lines = lineSplitter.split(
            com.google.common.io.Files.toString(source.toFile(), Charsets.UTF_8));
        int count = 1;
        for (String line : lines) {
          stream.printf("<tr><td><a name=\"l%d\" href=\"#l%d\">%d</a><td>",
              count, count, count);
          stream.println(line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
          count += 1;
        }

        stream.println("</table></pre>");
        stream.println("</article>");
        stream.println("<footer class=\"srcfile\"><div>Generated by " +
            "<a href=\"https://github.com/jleyba/js-dossier\">dossier</a></div></footer>");
        stream.printf("<script src=\"%s\"></script>\n", toTypesJs);
        stream.printf("<script src=\"%s\"></script>\n", toDossierJs);
      }
    }
  }

  private static Path simplifySourcePath(Path path) {
    Iterator<Path> parts = path.iterator();
    Path output = path.getFileSystem().getPath("");
    while (parts.hasNext()) {
      Path part = parts.next();
      if (!part.toString().equals(".") && !part.toString().equals("..")) {
        output = output.resolve(part);
      }
    }
    return output;
  }

  private void printSideNav(PrintStream stream) {
    stream.println("<nav id=\"left\"><div>Loading...</div></nav>");
  }

  private void printInheritanceTree(
      PrintStream stream, Descriptor descriptor, JSTypeRegistry registry) {
    Stack<String> types = descriptor.getAllTypes(registry);
    if (types.size() < 2) {
      return;
    }

    stream.print("<pre><code>" + getTypeLink(types.pop()));
    int depth = 0;
    while (!types.isEmpty()) {
      String indent = "  ";
      if (++depth > 1) {
        indent += Strings.repeat("    ", depth - 1);
      }
      String type = types.pop();
      stream.printf("\n%s&#x2514; %s", indent,
          types.isEmpty() ? type :  getTypeLink(type));
    }
    stream.println("</code></pre>\n");
  }

  private void printEnumType(PrintStream stream, Descriptor descriptor) {
    if (descriptor.isEnum()) {
      JSDocInfo info = descriptor.getInfo();
      if (info != null) {
        String type = CommentUtil.formatTypeExpression(info.getEnumParameterType(), resolver);
        stream.print("<dl><dt>Type: <code class=\"type\">" + type + "</code></dl>");
      }
    }
  }

  private void printInterfaces(PrintStream stream, String header, Set<String> interfaces) {
    if (interfaces.isEmpty()) {
      return;
    }
    stream.println("<dl><dt>" + header + "</dt><dd>");
    Iterable<String> tmp = transform(Ordering.natural().sortedCopy(interfaces),
        new Function<String, String>() {
          @Override
          public String apply(String input) {
            return new StringBuilder("<code>")
                .append(getTypeLink(input))
                .append("</code>")
                .toString();
          }
        });
    stream.print(Joiner.on(", ").join(tmp));
    stream.println("</dd></dl>");
  }

  private void printTitle(PrintStream stream, Descriptor descriptor) {
    stream.print("<h1>" + getTitlePrefix(descriptor) + " " + descriptor.getFullName());
    if (descriptor.isDeprecated()) {
      stream.print(" <span class=\"deprecation-notice\">(deprecated)</span>");
    }
    stream.println("</h1>");
  }

  private void printConstructor(PrintStream stream, Descriptor descriptor) {
    if (!descriptor.isConstructor()) {
      return;
    }

    List<ArgDescriptor> args = descriptor.getArgs();

    stream.println("<h2>Constructor</h2>");
    stream.print("<div><span class=\"member");
    if (descriptor.isDeprecated()) {
      stream.print(" deprecation-notice");
    }
    stream.print("\">" + descriptor.getFullName() + "</span> <span class=\"args\">(");
    stream.print(Joiner.on(", ").join(transform(args, new Function<ArgDescriptor, String>() {

      @Override
      public String apply(ArgDescriptor input) {
        return input.getName();
      }
    })));
    stream.println(")</span></div>");

    StringBuilder details = new StringBuilder()
        .append(printArgs(args, true))
        .append(printThrows(descriptor));
    if (details.length() > 0) {
      stream.println("<div class=\"info\">");
      stream.println("<table><tbody>");
      stream.println(details);
      stream.println("</table>");
      stream.println("</div>");
    }
  }

  private void printEnumValues(PrintStream stream, Descriptor descriptor) {
    if (!descriptor.isEnum()) {
      return;
    }
    stream.println("<h2>Values and Descriptions</h2>");
    stream.println("<div class=\"type-summary\"><table><tbody><tr><td><dl>");
    ObjectType object = descriptor.toObjectType();
    for (String name : object.getOwnPropertyNames()) {
      JSType type = object.getPropertyType(name);
      if (type.isEnumElementType()) {
        Node node = object.getPropertyNode(name);
        JSDocInfo info = node == null ? null : node.getJSDocInfo();

        stream.print("<dt><a class=\"enum member");
        if (info != null && info.isDeprecated()) {
          stream.print(" deprecation-notice");
        }
        stream.printf("\" name=\"%s\">%s</a>\n", descriptor.getFullName() + "." + name, name);

        if (node == null || info == null) {
          continue;
        }

        StringBuilder comment = new StringBuilder();
        if (info.isDeprecated()) {
          comment.append(formatDeprecationNotice(info.getDeprecationReason()));
        }
        comment.append(CommentUtil.getBlockDescription(resolver, info));

        if (comment.length() > 0) {
          stream.println("<dd>" + comment);
        }
      }
    }
    stream.println("</dl></table></div>");
  }

  private String getTypeLink(String type) {
    String path = resolver.getLink(type);
    if (path == null) {
      return type;
    }
    return String.format("<a href=\"%s\">%s</a>", path, type);
  }

  private void printSummary(PrintStream stream, @Nullable JSDocInfo info) {
    if (info == null) {
      return;
    }

    String summary = CommentUtil.getBlockDescription(resolver, info);
    if (summary.isEmpty()) {
      return;
    }

    stream.println("<p>" + summary);
  }

  private String getSourceLink(Descriptor descriptor) {
    String sourcePath = resolver.getSourcePath(descriptor);
    if (null != sourcePath) {
      return String.format("<a class=\"source\" href=\"%s\">code &raquo;</a>\n", sourcePath);
    }
    return "";
  }

  private void printTypedefs(PrintStream stream, Descriptor descriptor) {
    List<Descriptor> typedefs = FluentIterable.from(descriptor.getProperties())
        .filter(isTypedef())
        .toSortedList(DescriptorNameComparator.INSTANCE);
    if (typedefs.isEmpty()) {
      return;
    }
    stream.println();
    stream.println();
    stream.println("<section><h2>Type Definitions</h2>");
    for (Descriptor typedef : typedefs) {
      JSDocInfo info = checkNotNull(typedef.getInfo());
      stream.println("<details><summary>");
      stream.print("<div>" + getSourceLink(typedef));
      stream.print("<a class=\"member");
      if (typedef.isDeprecated()) {
        stream.print(" deprecation-notice");
      }
      stream.print("\" name=\"" + typedef.getFullName() + "\">");
      stream.print(typedef.getFullName() + "</a> : ");
      String type = CommentUtil.formatTypeExpression(info.getTypedefType(), resolver);
      stream.println("<code class=\"type\">" + type + "</code></div>");
      stream.println(printDeprecation(typedef));

      String comment = CommentUtil.getBlockDescription(resolver, info);
      if (!comment.isEmpty()) {
        stream.println("<div>" + comment + "</div>");
      }
      stream.println("</summary></details>");
    }
    stream.println("</section>");
  }

  private void printNestedTypeSummaries(
      PrintStream stream, String title, Descriptor descriptor, Predicate<Descriptor> predicate) {
    List<Descriptor> children = FluentIterable.from(descriptor.getProperties())
        .filter(predicate)
        .toSortedList(DescriptorNameComparator.INSTANCE);

    if (children.isEmpty()) {
      return;
    }

    stream.println();
    stream.println();
    stream.println("<section>");
    stream.println("<h2>" + title + "</h2>");
    stream.println("<div class=\"type-summary\"><table><tbody><tr><td><dl>");
    for (Descriptor child : children) {
      JSDocInfo info = checkNotNull(child.getInfo());

      Path path = resolver.getFilePath(child);
      stream.println("<dt><a href=\"" + path.getFileName() + "\">" + child.getFullName() + "</a>");

      String comment = CommentUtil.getBlockDescription(resolver, info);
      String summary = CommentUtil.getSummary(comment);
      if (!summary.isEmpty()) {
        stream.println("<dd>" + summary);
      }
    }
    stream.println("</dl></table></div>");
    stream.println("</section>");
  }

  private void printInstanceMethods(
      PrintStream stream, Descriptor descriptor, JSTypeRegistry registry) {
    if (!descriptor.isConstructor() && !descriptor.isInterface()) {
      return;
    }

    List<Descriptor> seen = new LinkedList<>();
    StringBuilder methods = new StringBuilder();
    for (String type : descriptor.getAllTypes(registry)) {
      Descriptor typeDescriptor = docRegistry.getType(type);
      if (typeDescriptor == null) {
        continue;
      }

      FluentIterable<Descriptor> unsorted = FluentIterable
          .from(typeDescriptor.getInstanceProperties())
          .filter(isFunction());
      if (typeDescriptor != descriptor) {
        unsorted = unsorted.filter(notOwnPropertyOf(seen));
      }
      List<Descriptor> functions = unsorted.toSortedList(DescriptorNameComparator.INSTANCE);
      seen.add(typeDescriptor);
      if (functions.isEmpty()) {
        continue;
      }
      methods.append("<h3>Defined in <code class=\"type\">");
      if (typeDescriptor != descriptor) {
        methods.append("<a href=\"")
            .append(resolver.getLink(typeDescriptor.getFullName()))
            .append("\">");
      }
      methods.append(typeDescriptor.getFullName());
      if (typeDescriptor != descriptor) {
        methods.append("</a>");
      }
      methods.append("</code></h3>\n");

      for (Descriptor function : functions) {
        List<ArgDescriptor> args = function.getArgs();
        methods.append("<details class=\"function\"><summary>\n")
            .append("<div>").append(getSourceLink(function))
            .append("<a class=\"member");
        if (function.isDeprecated()) {
          methods.append(" deprecation-notice");
        }
        methods.append(String.format("\" name=\"%s$%s\">%s</a>",
            typeDescriptor.getFullName(), function.getName(), function.getName()))
            .append(" <span class=\"args\">(")
            .append(Joiner.on(", ").join(transform(args, new Function<ArgDescriptor, String>() {
              @Override
              public String apply(ArgDescriptor input) {
                return input.getName();
              }
            })))
            .append(")</span>");

        String returnType = getReturnType(function);
        if (returnType == null || "undefined".equals(returnType) || "?".equals(returnType)) {
          returnType = "";
        }
        if (!returnType.isEmpty()) {
          methods.append(" &rArr; <code class=\"type\">").append(returnType).append("</code>");
        }
        methods.append("</div>");

        methods.append(printDeprecation(function));

        JSDocInfo info = function.getInfo();
        if (info != null) {
          String comment = CommentUtil.getBlockDescription(resolver, info);
          if (!comment.isEmpty()) {
            methods.append(comment);
          }
        }
        methods.append("</summary>\n");

        StringBuilder details = new StringBuilder()
            .append(printArgs(args, false))
            .append(printReturns(function))
            .append(printThrows(function));
        if (details.length() > 0) {
          methods.append("<div class=\"info\"><table><tbody>\n")
              .append(details)
              .append("</table></div>\n");
        }
        methods.append("</details>");
      }
      methods.append("\n");
    }

    if (methods.length() > 0) {
      stream.println();
      stream.println();
      stream.println("<section>");
      stream.println("<h2>Instance Methods</h2>");
      stream.println(methods);
      stream.println("</section>");
    }
  }

  private void printInstanceProperties(
      PrintStream stream, Descriptor descriptor, JSTypeRegistry registry) {
    if (!descriptor.isConstructor() && !descriptor.isInterface()) {
      return;
    }

    List<Descriptor> seen = new LinkedList<>();
    StringBuilder builder = new StringBuilder();
    for (String type : descriptor.getAllTypes(registry)) {
      Descriptor typeDescriptor = docRegistry.getType(type);
      if (typeDescriptor == null) {
        continue;
      }

      FluentIterable<Descriptor> unsorted = FluentIterable
          .from(typeDescriptor.getInstanceProperties())
          .filter(isProperty());
      if (typeDescriptor != descriptor) {
        unsorted = unsorted.filter(notOwnPropertyOf(seen));
      }
      List<Descriptor> properties = unsorted.toSortedList(DescriptorNameComparator.INSTANCE);
      seen.add(typeDescriptor);
      if (properties.isEmpty()) {
        continue;
      }

      builder.append("<h3>Defined in <code class=\"type\">");
      if (typeDescriptor != descriptor) {
        builder.append("<a href=\"")
            .append(resolver.getLink(typeDescriptor.getFullName()))
            .append("\">");
      }
      builder.append(typeDescriptor.getFullName());
      if (typeDescriptor != descriptor) {
        builder.append("</a>");
      }
      builder.append("</code></h3>\n");

      for (Descriptor property : properties) {
        builder.append("<details><summary>\n")
            .append("<div>").append(getSourceLink(property))
            .append("<a class=\"member");
        if (property.isDeprecated()) {
          builder.append(" deprecation-notice");
        }
        builder.append(String.format("\" name=\"%s$%s\">%s</a>",
            typeDescriptor.getFullName(), property.getName(), property.getName()));

        JSDocInfo info = property.getInfo();
        if (info != null && info.getType() != null) {
          String typeStr = CommentUtil.formatTypeExpression(info.getType(), resolver);
          builder.append(" : <code class=\"type\">" + typeStr + "</code>");
        } else if (property.getType() != null) {
          Descriptor propertyTypeDescriptor =
              docRegistry.getType(property.getType().toString());
          if (propertyTypeDescriptor == null) {
            propertyTypeDescriptor = docRegistry.getType(property.getType().toString());
          }

          if (propertyTypeDescriptor == null) {
            builder.append(" : <code class=\"type\">" + property.getType() + "</code>");
          } else {
            builder.append(String.format(" : <code class=\"type\"><a href=\"%s\">%s</a></code>",
                resolver.getLink(propertyTypeDescriptor.getFullName()),
                propertyTypeDescriptor.getFullName()));
          }
        }
        builder.append("</div>\n");

        builder.append(printDeprecation(property));

        if (info != null) {
          builder.append(CommentUtil.getBlockDescription(resolver, info));
        }
        builder.append("</summary></details>");
      }
      builder.append("\n");
    }

    if (builder.length() > 0) {
      stream.println();
      stream.println();
      stream.println("<section>");
      stream.println("<h2>Instance Properties</h2>");
      stream.println(builder);
      stream.println("</section>");
    }
  }

  private void printFunctions(PrintStream stream, Descriptor descriptor) {
    List<Descriptor> functions = FluentIterable.from(descriptor.getProperties())
        .filter(isFunction())
        .toSortedList(DescriptorNameComparator.INSTANCE);
    if (functions.isEmpty()) {
      return;
    }

    String header = "Global Functions";
    if (descriptor.isConstructor() || descriptor.isInterface()) {
      header = "Static Functions";
    }

    stream.println();
    stream.println();
    stream.println("<section>");
    stream.println("<h2>" + header + "</h2>");
    for (Descriptor function : functions) {
      List<ArgDescriptor> args = function.getArgs();

      stream.println("<details class=\"function\"><summary>");
      stream.print("<div>" + getSourceLink(function));
      JSDocInfo info = function.getInfo();
      if (info != null) {
        ImmutableList<String> templateNames = info.getTemplateTypeNames();
        if (!templateNames.isEmpty()) {
          stream.print("<code class=\"type\">&lt;");
          stream.print(Joiner.on(", ").join(templateNames));
          stream.print("&gt;</code> ");
        }
      }
      stream.print("<a class=\"member");
      if (function.isDeprecated()) {
        stream.print(" deprecation-notice");
      }
      stream.printf("\" name=\"%s\">%s</a>", function.getFullName(), function.getFullName());
      stream.print(" <span class=\"args\">(");
      stream.print(Joiner.on(", ").join(transform(args, new Function<ArgDescriptor, String>() {
        @Override
        public String apply(ArgDescriptor input) {
          return input.getName();
        }
      })));
      stream.print(")</span>");

      String returnType = getReturnType(function);
      if (returnType == null || "undefined".equals(returnType) || "?".equals(returnType)) {
        returnType = "";
      }
      if (!returnType.isEmpty()) {
        stream.print(" &rArr; <code class=\"type\">" + returnType + "</code>");
      }
      stream.println("</div>");

      stream.println(printDeprecation(function));

      if (info != null) {
        String comment = CommentUtil.getBlockDescription(resolver, info);
        if (!comment.isEmpty()) {
          stream.println(comment);
        }
      }
      stream.println("</summary>");

      StringBuilder details = new StringBuilder()
          .append(printArgs(args, false))
          .append(printReturns(function))
          .append(printThrows(function));
      if (details.length() > 0) {
        stream.println("<div class=\"info\"><table><tbody>\n");
        stream.println(details);
        stream.println("</table></div>");
      }
      stream.println("</details>");
    }
    stream.println("</section>");
  }

  private CharSequence printDeprecation(Descriptor descriptor) {
    if (!descriptor.isDeprecated()) {
      return "";
    }
    return formatDeprecationNotice(descriptor.getDeprecationReason());
  }

  private CharSequence formatDeprecationNotice(String reason) {
    String text = CommentUtil.formatCommentText(resolver, reason);
    if (!text.isEmpty()) {
      text = "<div class=\"deprecation-notice\">Deprecated: " +
          "<span class=\"deprecation-reason\">" + text + "</span></div>";
    }
    return text;
  }

  private CharSequence printThrows(Descriptor function) {
    StringBuilder builder = new StringBuilder();
    JSDocInfo info = function.getInfo();
    if (info == null) {
      return builder;
    }

    // Manually scan the function markers so we can associated thrown types with the
    // document description for why that type would be thrown.
    for (JSDocInfo.Marker marker : info.getMarkers()) {
      if ("throws".equals(marker.getAnnotation().getItem())) {
        if (marker.getType() != null) {
          JSTypeExpression expr = new JSTypeExpression(
              marker.getType().getItem(), info.getSourceName());
          builder.append("<dt><code class=\"type\">")
              .append(CommentUtil.formatTypeExpression(expr, resolver))
              .append("</code>\n");
        } else {
          builder.append("<dt><code class=\"type\">?</code>\n");
        }

        if (marker.getDescription() != null) {
          String desc = marker.getDescription().getItem();
          builder.append("<dd>")
              .append(CommentUtil.formatCommentText(resolver, desc))
              .append("\n");
        }
      }
    }

    if (builder.length() > 0) {
      builder = new StringBuilder("<tr><th>Throws\n<tr><td>\n<dl>\n")
          .append(builder);
    }
    return builder;
  }

  private CharSequence printReturns(Descriptor function) {
    StringBuilder builder = new StringBuilder();
    JSDocInfo info = function.getInfo();
    String desc = null;
    if (info != null) {
      desc = CommentUtil.formatCommentText(resolver, info.getReturnDescription());
    }

    if (Strings.isNullOrEmpty(desc)) {
      return builder;
    }
    return builder.append("<tr><th>Returns\n<tr><td>\n<dl><dd>").append(desc).append("</dl>");
  }

  private CharSequence printArgs(List<ArgDescriptor> args, boolean defaultToNone) {
    StringBuilder builder = new StringBuilder();
    if (args.isEmpty() && !defaultToNone) {
      return builder;
    }
    builder.append("<tr><th>Parameters\n")
        .append("<tr><td>\n")
        .append("<dl>\n");
    if (args.isEmpty() && defaultToNone) {
      builder.append("<dd>None</dd>\n");
    } else {
      for (ArgDescriptor arg : args) {
        builder.append("<dt>").append(arg.getName());
        if (arg.getType() != null) {
          String type = CommentUtil.formatTypeExpression(arg.getType(), resolver);
          builder.append(": <code class=\"type\">")
              .append(type)
              .append("</code>");
        }
        builder.append("\n");
        if (!arg.getDescription().isEmpty()) {
          builder.append("<dd>").append(arg.getDescription());
        }
      }
      builder.append("</dl>");
    }
    return builder;
  }

  @Nullable
  private String getReturnType(Descriptor function) {
    JSDocInfo info = function.getInfo();
    if (info != null) {
      JSTypeExpression expr = info.getReturnType();
      if (expr != null) {
        return CommentUtil.formatTypeExpression(expr, resolver);
      }
    }
    JSType type = ((FunctionType) function.toObjectType()).getReturnType();
    return type == null ? null : type.toString();
  }

  private void printProperties(PrintStream stream, Descriptor descriptor) {
    List<Descriptor> properties = FluentIterable.from(descriptor.getProperties())
        .filter(isProperty())
        .toSortedList(DescriptorNameComparator.INSTANCE);
    if (properties.isEmpty()) {
      return;
    }

    String header = "Global Properties";
    if (descriptor.isConstructor() || descriptor.isInterface()) {
      header = "Static Properties";
    }

    stream.println();
    stream.println();
    stream.println("<section>");
    stream.println("<h2>" + header + "</h2>");
    for (Descriptor property : properties) {
      stream.println("<details><summary>");
      stream.print("<div>" + getSourceLink(property));
      stream.print("<a class=\"member");
      if (property.isDeprecated()) {
        stream.print(" deprecation-notice");
      }
      stream.printf("\" name=\"%s\">%s</a>", property.getFullName(), property.getFullName());

      JSDocInfo info = property.getInfo();
      if (info != null && info.getType() != null) {
        String typeStr = CommentUtil.formatTypeExpression(info.getType(), resolver);
        stream.print(" : <code class=\"type\">" + typeStr + "</code>");
      } else if (property.getType() != null) {
        Descriptor propertyTypeDescriptor =
            docRegistry.getType(property.getType().toString());
        if (propertyTypeDescriptor == null) {
          propertyTypeDescriptor = docRegistry.getType(property.getType().toString());
        }

        if (propertyTypeDescriptor == null) {
          stream.print(" : <code class=\"type\">" + property.getType() + "</code>");
        } else {
          stream.printf(" : <code class=\"type\"><a href=\"%s\">%s</a></code>",
              resolver.getLink(propertyTypeDescriptor.getFullName()),
              propertyTypeDescriptor.getFullName());
        }
      }
      stream.println("</div>");

      stream.println(printDeprecation(property));

      if (info != null) {
        stream.println(CommentUtil.getBlockDescription(resolver, info));
      }
      stream.println("</summary></details>");
    }
    stream.println("</section>");
  }

  private String getTitlePrefix(Descriptor descriptor) {
    if (descriptor.isInterface()) {
      return "interface";
    } else if (descriptor.isConstructor()) {
      return "class";
    } else if (descriptor.isEnum()) {
      return "enum";
    } else {
      return "namespace";
    }
  }

  private void printTopNav(PrintStream stream) {
    stream.println("<div id=\"top\">");
    stream.println("<form id=\"searchbox\">");
    stream.println("<span>Search:</span>");
    stream.println("<input name=\"search\" type=\"search\">");
    stream.println("</form>");
    stream.println("</div>");
  }

  private static Predicate<Descriptor> isTypedef() {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        return input.getInfo() != null && input.getInfo().getTypedefType() != null;
      }
    };
  }

  private static Predicate<Descriptor> isClass() {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        return input.isConstructor();
      }
    };
  }

  private static Predicate<Descriptor> isInterface() {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        return input.isInterface();
      }
    };
  }

  private static Predicate<Descriptor> isEnum() {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        return input.isEnum();
      }
    };
  }

  private static Predicate<Descriptor> isFunction() {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        return input.isFunction() && !input.isConstructor() && !input.isInterface();
      }
    };
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

  private Predicate<Descriptor> isProperty() {
    return new Predicate<Descriptor>() {
      @Override
      public boolean apply(Descriptor input) {
        if (docRegistry.isKnownType(input.getFullName())) {
          return false;
        }

        JSType type = input.getType();
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
    };
  }

  private static enum DescriptorNameComparator implements Comparator<Descriptor> {
    INSTANCE;

    @Override
    public int compare(Descriptor a, Descriptor b) {
      return a.getFullName().compareTo(b.getFullName());
    }
  }
}
