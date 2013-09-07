package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.JSDocInfo;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Utilities for generating links to {@link Descriptor types} in generated documentation.
 */
class LinkResolver {

  private final Path outputDir;
  private final DocRegistry docRegistry;
  @Nullable private final Descriptor descriptor;

  /**
   * @param outputDir The base output directory.
   * @param docRegistry The documented type registry.
   */
  LinkResolver(Path outputDir, DocRegistry docRegistry) {
    this(outputDir, docRegistry, null);
  }

  private LinkResolver(Path outputDir, DocRegistry docRegistry, @Nullable Descriptor descriptor) {
    this.outputDir = checkNotNull(outputDir);
    this.docRegistry = checkNotNull(docRegistry);
    this.descriptor = descriptor;
  }

  /**
   * Creates a new link resolver specifically for the given descriptor.
   */
  LinkResolver createResolver(Descriptor descriptor) {
    return new LinkResolver(outputDir, docRegistry, descriptor);
  }

  /**
   * Returns the path of the generated document, relative to this instance's output directory,
   * for the descriptor managed by this resolver.
   *
   * @throws IllegalStateException If this resolver does not have a descriptor.
   */
  Path getFilePath() {
    checkState(descriptor != null);
    return getFilePath(descriptor);
  }

  /**
   * Returns the path of the generated document file for the given descriptor. The generated path
   * will always be relative to this resolver's output directory.
   */
  private Path getFilePath(Descriptor descriptor) {
    String name = descriptor.getFullName().replace('.', '_') + ".html";
    if (descriptor.isConstructor()
        || descriptor.isInterface()
        || descriptor.isEnum()) {
      return outputDir.resolve("type").resolve(name);
    }
    return outputDir.resolve("ns").resolve(name);
  }

  /**
   * Returns the relative path from the descriptor managed by this resolver to the referenced
   * type symbol. If this instance does not have a descriptor, the returned path will be
   * relative to the output directory.
   */
  @Nullable
  String getRelativeTypeLink(String to) {
    // Trim down the target symbol to something that would be indexable.
    int index = to.indexOf("(");
    if (index != -1) {
      to = to.substring(0, index);
    }

    String fragment = "";
    Path toPath;
    index = to.indexOf("#");
    if (index != -1) {
      fragment = to.substring(index);
      toPath = getTypePath(to.substring(0, index));
      if (toPath != null) {
        toPath = toPath.resolveSibling(
            toPath.getFileName() + to.substring(index));
      }
    } else {
      toPath = getTypePath(to);
      if (toPath == null) {
        index = to.lastIndexOf('.');
        if (index != -1) {
          toPath = getTypePath(to.substring(0, index));
        }
      }
    }

    if (toPath == null) {
      return getExternLink(to);
    }

    return getRelativePath(getFilePath(), toPath) + fragment;
  }

  @Nullable
  Path getTypePath(String name) {
    Descriptor descriptor = docRegistry.getType(name);
    if (descriptor != null) {
      return getFilePath(descriptor);
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

  /**
   * Resolves a path relative to the root directory against the output file for the descriptor
   * managed by this instance. If this instance does not have a descriptor, the root relative path
   * will be trivially returned.
   */
  Path resolveRootRelativePath(String path) {
    Path p = outputDir.resolve(path);
    if (descriptor == null) {
      return p;
    }
    return getRelativePath(getFilePath(), p);
  }

  @Nullable
  String getPathToSourceFile() {
    if (descriptor == null) {
      return null;
    }
    String strPath = descriptor.getSource();
    if (strPath == null) {
      return null;
    }
    Path path = outputDir.getFileSystem().getPath(strPath);
    Iterator<Path> parts = path.iterator();
    Path output = outputDir.resolve("file");
    while (parts.hasNext()) {
      Path part = parts.next();
      if (!part.toString().equals(".") && !part.toString().equals("..")) {
        output = output.resolve(part);
      }
    }
    output = output.resolveSibling(output.getFileName() + ".src.html");

    String relPath = getRelativePath(getFilePath(), output).toString();
    int lineNum = descriptor.getLineNum();
    if (lineNum > 1) {
      relPath += "#l" + (lineNum - 1);
    }
    return relPath;
  }

  /**
   * Computes the relative path from one file to another. Assumes both files are located under
   * {@link #outputDir}.
   *
   * @param from The source file.
   * @param to The destination file.
   * @return The relative path between the two files.
   */
  private Path getRelativePath(Path from, Path to) {
    Path fromDir = Files.isDirectory(from) ? from : from.getParent();
    return fromDir.relativize(to);
  }
}
