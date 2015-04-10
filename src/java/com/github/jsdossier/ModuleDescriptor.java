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
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.javascript.rhino.JSDocInfo;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Describes a JavaScript module, which may be a CommonJS or Closure module.
 */
final class ModuleDescriptor {

  private final String name;
  private final Path path;
  private final boolean isCommonJsModule;
  private final Map<String, JSDocInfo> internalVarDocs = new HashMap<>();
  private final BiMap<String, String> exportedNames = HashBiMap.create();

  public ModuleDescriptor(String name, Path path, boolean isCommonJsModule) {
    this.name = name;
    this.path = path;
    this.isCommonJsModule = isCommonJsModule;
  }

  public String getName() {
    return name;
  }

  public Path getPath() {
    return path;
  }

  public boolean isCommonJsModule() {
    return isCommonJsModule;
  }

  /**
   * Records a symbol exported as part of this module's public API.
   *
   * @param internalName the internal name.
   * @param publicName the fully qualified public name.
   */
  public void addExportedName(String internalName, String publicName) {
    checkArgument(!isNullOrEmpty(internalName));
    checkArgument(!isNullOrEmpty(publicName));
    if (!exportedNames.containsKey(internalName)) {
      exportedNames.put(internalName, publicName);
    }
  }

  @VisibleForTesting
  Map<String, String> getExportedNames() {
    return Collections.unmodifiableMap(exportedNames);
  }

  /**
   * Returns the fully qualified public name of an internal symbol exported by this module.
   */
  @Nullable
  public String getExportedName(String internalName) {
    return exportedNames.get(internalName);
  }

  /**
   * Given a qualified name for a property exported by this module, returns the internal variable
   * name, if any.
   */
  @Nullable
  public String getInternalName(String qualifiedName) {
    return exportedNames.inverse().get(qualifiedName);
  }

  /**
   * Saves a reference to the JSDocs for a variable declared within this module. This is used to
   * forward the docs to aliases exported as part of the module's public API.
   *
   *     /** Comment here. *\
   *     function foo() {}
   *     exports.foo = foo;
   *
   * @param name the variable name.
   * @param info the variable's JS docs.
   */
  public void addInternalVarDocs(String name, JSDocInfo info) {
    checkArgument(!internalVarDocs.containsKey(name),
        "Function already registered in module %s: %s", this.name, name);
    internalVarDocs.put(name, info);
  }

  @VisibleForTesting
  Map<String, JSDocInfo> getInternalVarDocs() {
    return Collections.unmodifiableMap(internalVarDocs);
  }

  @Nullable
  public JSDocInfo getInternalVarDocs(String name) {
    return internalVarDocs.get(name);
  }
}
