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

import com.google.auto.value.AutoValue;

import java.nio.file.Path;

/**
 * Describes a JavaScript module.
 */
@AutoValue
public abstract class Module {

  /**
   * Returns a new builder.
   */
  public static Builder builder() {
    return new AutoValue_Module.Builder();
  }

  // Package private to prevent extensions.
  Module() {}

  /**
   * Returns the ID used to reference this module in code after any transformations
   * applied by the compiler. For Closure module's, this will simply be the ID specified in the
   * "goog.module" declaration. For Node and ES6 modules, this will be derived by the compiler from
   * the module's file path.
   */
  public abstract String getId();

  /**
   * Returns the input file that defines this module.
   */
  public abstract Path getPath();

  /**
   * Returns which syntactic type of module this is.
   */
  public abstract Type getType();

  /**
   * Returns the file-level JSDoc for this module.
   */
  public abstract JsDoc getJsDoc();

  /**
   * The recognized module types.
   */
  public enum Type {
    /**
     * A module defined using the Closure library's "goog.module" syntax.
     */
    CLOSURE,

    /**
     * A standard ES6 module.
     */
    ES6,

    /**
     * CommonJS modules, as implemented for Node. Modules support "require(id)" syntax for imports.
     */
    NODE
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder setId(String id);
    public abstract Builder setPath(Path path);
    public abstract Builder setType(Type type);
    public abstract Builder setJsDoc(JsDoc doc);
    public abstract Module build();
  }
}
