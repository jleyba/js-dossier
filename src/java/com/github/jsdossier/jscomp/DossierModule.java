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

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.rhino.Node;

/**
 * Describes a CommonJS module.
 *
 * @deprecated Use {@link Module} instead.
 */
@Deprecated
public class DossierModule {

  private static final String EXTERN_PREFIX = "dossier$$extern__";

  private final Node scriptNode;
  private final Module module;

  /**
   * Creates a new module descriptor.
   *
   * @param script the SCRIPT node for the module.
   * @param module the new container to delegate to for module information.
   */
  public DossierModule(Node script, Module module) {
    checkArgument(script.isScript());
    this.scriptNode = script;
    this.module = module;
  }

  public Node getScriptNode() {
    return scriptNode;
  }

  public String getVarName() {
    return module.getId();
  }

  public static boolean isExternModule(String name) {
    return name.startsWith(EXTERN_PREFIX);
  }

  public static String externToOriginalName(String name) {
    checkArgument(isExternModule(name));
    return name.substring(EXTERN_PREFIX.length());
  }

  /**
   * Mangles an extern module's ID so it may be used as a global variable with Closure's type
   * system.
   */
  static String externModuleName(String id) {
    return EXTERN_PREFIX + id;
  }
}
