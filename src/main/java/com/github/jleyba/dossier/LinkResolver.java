package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.JSDocInfo;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Utilities for generating links to {@link Descriptor types} in generated documentation.
 */
class LinkResolver {

  private final Path outputDir;
  private final DocRegistry docRegistry;

  /**
   * @param outputDir The base output directory.
   * @param docRegistry The documented type registry.
   */
  LinkResolver(Path outputDir, DocRegistry docRegistry) {
    this.outputDir = checkNotNull(outputDir);
    this.docRegistry = checkNotNull(docRegistry);
  }

  private static String getTypePrefix(Descriptor descriptor) {
    if (descriptor.isInterface()) {
      return "interface_";
    } else if (descriptor.isConstructor()) {
      return "class_";
    } else if (descriptor.isEnum()) {
      return "enum_";
    } else {
      return "namespace_";
    }
  }

  /**
   * Returns the path of the generated document file for the given descriptor. The generated path
   * will always be relative to this resolver's output directory.
   */
  Path getFilePath(Descriptor descriptor) {
    String name = descriptor.getFullName().replace('.', '_') + ".html";
    return outputDir.resolve(getTypePrefix(descriptor) + name);
  }

  /**
   * Returns the relative path from the descriptor managed by this resolver to the referenced
   * type symbol. If this instance does not have a descriptor, the returned path will be
   * relative to the output directory.
   */
  @Nullable
  String getLink(String to) {
    // Trim down the target symbol to something that would be indexable.
    int index = to.indexOf("(");
    if (index != -1) {
      to = to.substring(0, index);
    }

    String fragment = "";
    Path path;
    index = to.indexOf("#");
    if (index != -1) {
      String typeName = to.substring(0, index);
      String propertyName = "$" + to.substring(index + 1);
      path = getTypePath(typeName);
      if (path != null) {
        fragment = "#" + typeName + propertyName;
      }
    } else {
      path = getTypePath(to);
      if (path == null) {
        index = to.lastIndexOf('.');
        if (index != -1) {
          path = getTypePath(to.substring(0, index));
          if (path != null) {
            fragment = "#" + to;
          }
        }
      }
    }

    if (path == null) {
      return getExternLink(to);
    }

    return path + fragment;
  }

  @Nullable
  private Path getTypePath(String name) {
    Descriptor descriptor = docRegistry.getType(name);
    if (descriptor != null) {
      return getFilePath(descriptor).getFileName();
    }
    return null;
  }

  private static final String MDN_PREFIX =
      "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/";

  /**
   * Maps primitives to a link to their definition on the Mozilla Develper Network. The Closure
   * compiler does not provide predefined externs for these types (it does have the object
   * equivalents (e.g. number vs Number).
   */
  private static final ImmutableMap<String, String> PRIMITIVES_TO_MDN_LINK = ImmutableMap.of(
      "null", MDN_PREFIX + "Global_Objects/Null",
      "undefined", MDN_PREFIX + "Global_Objects/Undefined",
      "string", MDN_PREFIX + "Global_Objects/String",
      "number", MDN_PREFIX + "Global_Objects/Number",
      "boolean", MDN_PREFIX + "Global_Objects/Boolean");

  @Nullable
  String getExternLink(String name) {
    if (PRIMITIVES_TO_MDN_LINK.containsKey(name)) {
      return PRIMITIVES_TO_MDN_LINK.get(name);
    }

    Descriptor descriptor = docRegistry.getExtern(name);
    if (descriptor != null) {
      JSDocInfo info = descriptor.getInfo();
      if (info != null) {
        for (JSDocInfo.Marker marker : info.getMarkers()) {
          if ("see".equals(marker.getAnnotation().getItem())) {
            try {
              return new URI(marker.getDescription().getItem()).toString();
            } catch (URISyntaxException e) {
              continue;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  String getSourcePath(Descriptor descriptor) {
    String strPath = descriptor.getSource();
    if (strPath == null) {
      return null;
    }
    Iterator<Path> parts = outputDir.getFileSystem().getPath(strPath).iterator();
    Path output = outputDir.getFileSystem().getPath("source");
    while (parts.hasNext()) {
      Path part = parts.next();
      if (!part.toString().equals(".") && !part.toString().equals("..")) {
        output = output.resolve(part);
      }
    }
    output = output.resolveSibling(output.getFileName() + ".src.html");

    String relPath = output.toString();
    int lineNum = descriptor.getLineNum();
    if (lineNum > 1) {
      relPath += "#l" + (lineNum - 1);
    }
    return relPath;
  }
}
