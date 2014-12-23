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

import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.javascript.jscomp.DossierModule;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

import java.nio.file.Path;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Utilities for generating links to {@link Descriptor types} in generated documentation.
 */
public class Linker {

  private final Config config;
  private final Path outputRoot;
  private final TypeRegistry typeRegistry;

  /**
   * @param config The current runtime configuration.
   * @param typeRegistry The type registry.
   */
  public Linker(Config config, TypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    this.config = checkNotNull(config);
    this.outputRoot = config.getOutput();
  }

  private static String getTypePrefix(JSType type) {
    if (type.isInterface()) {
      return "interface_";
    } else if (type.isConstructor()) {
      return "class_";
    } else if (type.isEnumType()) {
      return "enum_";
    } else {
      return "namespace_";
    }
  }

  /**
   * Returns the display name for the given type.
   */
  public String getDisplayName(NominalType type) {
    if (!type.isModuleExports()) {
      return type.getQualifiedName();
    }
    String displayName = getDisplayName(type.getModule());
    type.setAttribute("displayName", displayName);
    return displayName;
  }

  /**
   * Returns the display name for the given module.
   */
  public String getDisplayName(DossierModule module) {
    Path modulePath = stripExtension(module.getModulePath());

    Path displayPath = config.getModulePrefix().relativize(modulePath);
    if (displayPath.getFileName().toString().equals("index")
        && displayPath.getParent() != null) {
      displayPath = displayPath.getParent();
    }
    return displayPath.toString()
        .replace(modulePath.getFileSystem().getSeparator(), "/");  // Oh windows...
  }

  private static Path stripExtension(Path path) {
    String name = path.getFileName().toString();
    return path.resolveSibling(Files.getNameWithoutExtension(name));
  }

  /**
   * Returns the path of the generated document file for the given type.
   */
  public Path getFilePath(NominalType type) {
    String name = "";
    if (type.getModule() != null) {
      name = "module_" + getDisplayName(type.getModule()).replace('/', '_');
    }
    if (!type.isModuleExports()) {
      if (!name.isEmpty()) {
        name += "_";
      }
      name += getTypePrefix(type.getJsType()) + getDisplayName(type).replace('.', '_');
    }
    return outputRoot.resolve(name + ".html");
  }

  /**
   * Returns the path of the generated documentation for the given source file.
   */
  public Path getFilePath(Path sourceFile) {
    Path path = config.getSrcPrefix()
        .relativize(sourceFile.toAbsolutePath().normalize())
        .resolveSibling(sourceFile.getFileName() + ".src.html");
    return outputRoot.resolve("source").resolve(path);
  }

  /**
   * @see #getFilePath(Path)
   */
  public Path getFilePath(String sourceFile) {
    return getFilePath(outputRoot.getFileSystem().getPath(sourceFile));
  }

  /**
   * Returns the path to the rendered source file for the given node.
   */
  public Dossier.SourceLink getSourceLink(@Nullable Node node) {
    if (node == null || node.isFromExterns()) {
      return Dossier.SourceLink.newBuilder().setPath("").build();
    }
    Iterator<Path> parts = config.getOutput()
        .relativize(getFilePath(node.getSourceFileName()))
        .iterator();
    return Dossier.SourceLink.newBuilder()
        .setPath(Joiner.on('/').join(parts))
        .setLine(node.getLineno())
        .build();
  }

  /**
   * Computes the URL path from one descriptor to the definition of another type. The referenced
   * type may be specified as:
   * <ul>
   *   <li>A fully qualified type: {@code foo.bar.Baz}
   *   <li>A fully qualified type with instance property qualifier: {@code foo.Bar#baz}. This is
   *       treated the same as {@code foo.Bar.prototype.baz}.
   * </ul>
   *
   * <p>If the referenced type is recognized, the returned path will be relative to the output
   * directory, otherwise {@code null} is returned.
   */
  @Nullable
  public String getLink(String to) {
    // Trim down the target symbol to something that would be indexable.
    int index = to.indexOf("(");
    if (index != -1) {
      to = to.substring(0, index);
    }

    String typeName = to;
    String propertyName = "";
    boolean instanceProperty = false;

    if (to.endsWith("#")) {
      typeName = to.substring(0, to.length() - 1);

    } else if (to.endsWith(".prototype")) {
      typeName = to.substring(0, to.length() - ".prototype".length());

    } else if (to.contains("#")) {
      String[] parts = to.split("#");
      typeName = parts[0];
      propertyName = parts[1];
      instanceProperty = true;

    } else if (to.contains(".prototype.")) {
      String[] parts = to.split(".prototype.");
      typeName = parts[0];
      propertyName = parts[1];
      instanceProperty = true;
    }

    NominalType type = typeRegistry.getNominalType(typeName);

    // Link might be a qualified path to a property.
    if (type == null && propertyName.isEmpty()) {
      index = typeName.lastIndexOf(".");
      if (index != -1) {
        instanceProperty = false;
        propertyName = typeName.substring(index + 1);
        typeName = typeName.substring(0, index);
        type = typeRegistry.getNominalType(typeName);
      }
    }

    if (type == null) {
      // If we get here, make one last attempt to resolve the referenced path
      // by checking for an extern type.
      return getExternLink(typeName);
    }

    String filePath = getFilePath(type).getFileName().toString();
    if (!propertyName.isEmpty()) {
      if (instanceProperty) {
        if (type.getJsdoc().isConstructor() || type.getJsdoc().isInterface()) {
          filePath += "#" + propertyName;
        }
      } else {
        filePath += "#" + type.getName() + "." + propertyName;
      }
    }
    return filePath;
  }

  private static final String MDN_PREFIX =
      "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/";

  /**
   * Maps built-in objects to a link to their definition on the Mozilla Develper Network.
   * The Closure compiler's externs do not provide links for these types in its externs.
   */
  private static final ImmutableMap<String, String> BUILTIN_TO_MDN_LINK =
      ImmutableMap.<String, String>builder()
          .put("Arguments", MDN_PREFIX + "Functions/arguments")
          .put("Array", MDN_PREFIX + "Global_Objects/Array")
          .put("Boolean", MDN_PREFIX + "Global_Objects/Boolean")
          .put("Date", MDN_PREFIX + "Global_Objects/Date")
          .put("Error", MDN_PREFIX + "Global_Objects/Error")
          .put("Function", MDN_PREFIX + "Global_Objects/Function")
          .put("Infinity", MDN_PREFIX + "Global_Objects/Infinity")
          .put("Math", MDN_PREFIX + "Global_Objects/Math")
          .put("NaN", MDN_PREFIX + "Global_Objects/NaN")
          .put("Number", MDN_PREFIX + "Global_Objects/Number")
          .put("Object", MDN_PREFIX + "Global_Objects/Object")
          .put("RangeError", MDN_PREFIX + "Global_Objects/RangeError")
          .put("ReferenceError", MDN_PREFIX + "Global_Objects/ReferenceError")
          .put("RegExp", MDN_PREFIX + "Global_Objects/RegExp")
          .put("String", MDN_PREFIX + "Global_Objects/String")
          .put("SyntaxError", MDN_PREFIX + "Global_Objects/SyntaxError")
          .put("TypeError", MDN_PREFIX + "Global_Objects/TypeError")
          .put("URIError", MDN_PREFIX + "Global_Objects/URIError")
          .put("arguments", MDN_PREFIX + "Functions/arguments")
          .put("boolean", MDN_PREFIX + "Global_Objects/Boolean")
          .put("null", MDN_PREFIX + "Global_Objects/Null")
          .put("number", MDN_PREFIX + "Global_Objects/Number")
          .put("string", MDN_PREFIX + "Global_Objects/String")
          .put("undefined", MDN_PREFIX + "Global_Objects/Undefined")
          .build();

  /**
   * Returns a for one of the builtin extern types to its definition on the
   * <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/">Mozilla Developer
   * Network</a>.
   *
   * @param name The name of the extern to find a link for.
   * @return A link to the extern's type definition, or {@code null} if one could not be found.
   */
  @Nullable
  public String getExternLink(String name) {
    if (BUILTIN_TO_MDN_LINK.containsKey(name)) {
      return BUILTIN_TO_MDN_LINK.get(name);
    }
    return null;
  }
}
