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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
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
class Linker {

  private final Config config;
  private final DocRegistry docRegistry;

  /**
   * @param config The current runtime configuration.
   * @param docRegistry The documented type registry.
   */
  Linker(Config config, DocRegistry docRegistry) {
    this.config = checkNotNull(config);
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
   * will always be relative to this linker's output directory.
   */
  Path getFilePath(Descriptor descriptor) {
    String name = descriptor.getFullName().replace('.', '_') + ".html";
    return config.getOutput().resolve(getTypePrefix(descriptor) + name);
  }

  /**
   * Returns the path of the generated documentation for the given source file. The generated path
   * will always be relative to this instance's output directory.
   */
  Path getFilePath(Path sourceFile) {
    Iterator<Path> parts = config.getSrcPrefix()
        .relativize(sourceFile.toAbsolutePath().normalize())
        .iterator();
    String name = "source_" + Joiner.on('_').join(parts) + ".src.html";
    return config.getOutput().resolve(name);
  }

  /**
   * @see #getFilePath(Path)
   */
  Path getFilePath(String sourceFile) {
    return getFilePath(config.getOutput().getFileSystem().getPath(sourceFile));
  }

  /**
   * Computes the URL path, relative to the output directory, for the source file definition of
   * the given {@code descriptor}.  If the source file for the {@code descriptor} is not known,
   * this method will return {@code null}.
   */
  @Nullable
  String getSourcePath(Descriptor descriptor) {
    String strPath = descriptor.getSource();
    if (strPath == null) {
      return null;
    }

    Iterator<Path> parts = config.getOutput()
        .relativize(getFilePath(strPath))
        .iterator();
    strPath = Joiner.on('/').join(parts);

    int lineNum = descriptor.getLineNum();
    if (lineNum > 1) {
      return strPath + "#l" + (lineNum - 1);
    }
    return strPath;
  }

  /**
   * Computes the URL path from one descriptor to the definition of another type. The referenced
   * type may be specified as:
   * <ul>
   *   <li>A fully qualified type: {@code foo.bar.Baz}
   *   <li>A fully qualified type with property qualified: {@code foo.Bar#baz} or
   *       {@code foo.Bar$baz} (this method does not differentiate between "#" and "$" being used
   *       as instance vs. static property qualifiers).
   * </ul>
   *
   * <p>If the referenced type is recognized, the returned path will be relative to the output
   * directory, or an external URL (if referencing an extern symbol). If the type's definition
   * could not be found, {@code null} is returned.
   */
  @Nullable
  String getLink(String to) {
    // Trim down the target symbol to something that would be indexable.
    int index = to.indexOf("(");
    if (index != -1) {
      to = to.substring(0, index);
    }

    // We don't explicitly document class prototypes, so link to the main type.
    if (to.endsWith(".prototype")) {
      to = to.substring(0, to.length() - ".prototype".length());
    }

    String fragment = "";
    String path;
    index = to.indexOf("#");
    if (index != -1) {
      String typeName = to.substring(0, index);
      String propertyName = "$" + to.substring(index + 1);
      path = getTypeFile(typeName);
      if (path != null) {
        fragment = "#" + typeName + propertyName;
      }
    } else {
      path = getTypeFile(to);
      if (path == null) {
        index = to.lastIndexOf('.');
        if (index != -1) {
          path = getTypeFile(to.substring(0, index));
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
  private String getTypeFile(String name) {
    Descriptor descriptor = docRegistry.getType(name);
    if (descriptor != null) {
      return getFilePath(descriptor).getFileName().toString();
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

  /**
   * Attempts to find a link to an extern type definition. Primitive types (null, undefined,
   * string, number, boolean) will be linked to their definitions on the
   * <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/">Mozilla Developer
   * Network</a>. For all other symbols, this method will scan the jsdoc annotations for an
   * {@literal @see} annotation containing a valid URI.
   *
   * @param name The name of the extern to find a link for.
   * @return A link to the extern's type definition, or {@code null} if one could not be found.
   */
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
}
