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
import com.google.common.io.Files;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Utilities for generating links to {@link Descriptor types} in generated documentation.
 */
public class Linker {

  private final Config config;
  private final Path outputRoot;
  private final DocRegistry docRegistry;

  /**
   * @param config The current runtime configuration.
   * @param docRegistry The documented type registry.
   */
  public Linker(Config config, DocRegistry docRegistry) {
    this.config = checkNotNull(config);
    this.outputRoot = config.getOutput();
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
   * Returns the display name for the given {@code descriptor}.
   */
  public String getDisplayName(ModuleDescriptor descriptor) {
    if (descriptor.getAttribute("displayName") != null) {
      return descriptor.getAttribute("displayName");
    }

    Path modulePath = stripExtension(descriptor.getPath());

    Path displayPath = config.getModulePrefix().relativize(modulePath);
    if (displayPath.getFileName().toString().equals("index")
        && displayPath.getParent() != null) {
      displayPath = displayPath.getParent();
    }

    String displayName = displayPath.toString()
        .replace(modulePath.getFileSystem().getSeparator(), "/");  // Oh windows...

    descriptor.setAttribute("displayName", displayName);
    return displayName;
  }

  private static Path stripExtension(Path path) {
    String name = path.getFileName().toString();
    return path.resolveSibling(Files.getNameWithoutExtension(name));
  }

  /**
   * Returns the display name for the given {@code descriptor}. If this is the descriptor for the
   * {@link Descriptor#isModuleExports() exported API} of a CommonJS module, this will return
   * that module's {@link #getDisplayName(ModuleDescriptor) display name}. Otherwise, this simply
   * returns the descriptor's {@link Descriptor#getFullName() fully qualified name}.
   */
  public String getDisplayName(Descriptor descriptor) {
    if (descriptor.isModuleExports()) {
      return getDisplayName(descriptor.getModule().get());
    }
    return descriptor.getFullName();
  }

  /**
   * Returns the path of the generated document file for the given module.
   */
  public Path getFilePath(ModuleDescriptor module) {
    String name = getDisplayName(module).replace('/', '_') + ".html";
    return outputRoot.resolve("module_" + name);
  }

  /**
   * Returns the path of the generated document file for the given descriptor.
   */
  public Path getFilePath(Descriptor descriptor) {
    String name = descriptor.getFullName().replace('.', '_') + ".html";
    name = getTypePrefix(descriptor) + name;

    if (descriptor.getModule().isPresent()) {
      ModuleDescriptor module = descriptor.getModule().get();
      name = stripExtension(getFilePath(module)).getFileName().toString() + "_" + name;
    }

    return outputRoot.resolve(name);
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
   * Computes the path from the given module's {@link #getFilePath(ModuleDescriptor) file} to the
   * rendered source file.
   */
  public String getSourcePath(ModuleDescriptor descriptor) {
    Iterator<Path> parts = config.getOutput()
        .relativize(getFilePath(descriptor.getSource()))
        .iterator();
    return Joiner.on('/').join(parts);
  }

  /**
   * Computes the URL path, relative to the output directory, for the source file definition of
   * the given {@code descriptor}.  If the source file for the {@code descriptor} is not known,
   * this method will return {@code null}.
   */
  @Nullable
  public String getSourcePath(Descriptor descriptor) {
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
      return strPath + "#l" + lineNum;
    }
    return strPath;
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
   * directory, or an external URL (if referencing an extern symbol). If the type's definition
   * could not be found, {@code null} is returned.
   */
  @Nullable
  public String getLink(String to) {
    // Trim down the target symbol to something that would be indexable.
    int index = to.indexOf("(");
    if (index != -1) {
      to = to.substring(0, index);
    }

    Descriptor descriptor = docRegistry.resolve(to);
    if (descriptor != null && !docRegistry.isExtern(descriptor.getFullName())) {
      return getLink(descriptor);
    }

    // If we get here, make one last attempt to resolve the referenced path
    // by checking for an extern type.
    return getExternLink(to);
  }

  @Nullable
  public String getLink(Descriptor descriptor) {
    if (docRegistry.isExtern(descriptor.getFullName())) {
      return getExternLink(descriptor);
    }

    if (!docRegistry.isDocumentedType(descriptor)) {
      return null;
    }

    if (descriptor.getFullName().contains(".prototype.")) {
      return getPrototypeLink(descriptor);
    }

    if (descriptor.isModuleExports()) {
      return getFilePath(descriptor.getModule().get()).getFileName().toString();
    }

    String filePath = getFilePath(descriptor).getFileName().toString();

    // Check if the fully qualified name refers directly to a type.
    if (docRegistry.isKnownType(descriptor.getFullName())) {
      return filePath;
    }

    int index = descriptor.getFullName().lastIndexOf('.');
    if (index != -1) {
      filePath = getLink(descriptor.getFullName().substring(0, index));
      if (filePath != null) {
        return filePath + "#" + descriptor.getFullName();
      }
    }

    return filePath;
  }

  @Nullable
  private String getPrototypeLink(Descriptor descriptor) {
    checkArgument(descriptor.getFullName().contains(".prototype."));

    String parentName = descriptor.getFullName()
        .substring(0, descriptor.getFullName().indexOf(".prototype."));
    String name = descriptor.getSimpleName();

    Descriptor parent = docRegistry.resolve(parentName);
    if (parent != null) {
      return getFilePath(parent).getFileName().toString() + "#" + name;
    }
    return null;
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
  private String getExternLink(String name) {
    if (BUILTIN_TO_MDN_LINK.containsKey(name)) {
      return BUILTIN_TO_MDN_LINK.get(name);
    }

    Descriptor descriptor = docRegistry.getExtern(name);
    if (descriptor != null) {
      return getExternLink(descriptor);
    }
    return null;
  }

  /**
   * @see #getExternLink(String)
   */
  @Nullable
  private String getExternLink(Descriptor descriptor) {
    if (BUILTIN_TO_MDN_LINK.containsKey(descriptor.getFullName())) {
      return BUILTIN_TO_MDN_LINK.get(descriptor.getFullName());
    }

    JsDoc jsDoc = descriptor.getJsDoc();
    if (jsDoc != null) {
      for (String see : jsDoc.getSeeClauses()) {
        try {
          return new URI(see).toString();
        } catch (URISyntaxException ignored) {
          // Do nothing.
        }
      }
    }
    return null;
  }
}
