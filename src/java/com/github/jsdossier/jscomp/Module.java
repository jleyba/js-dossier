/*
 Copyright 2013-2016 Jason Leyba

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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.JSDocInfo;

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
    return new AutoValue_Module.Builder()
        .setExportedDocs(ImmutableMap.<String, JSDocInfo>of())
        .setExportedNames(ImmutableMap.<String, String>of())
        .setInternalVarDocs(ImmutableMap.<String, JSDocInfo>of())
        .setHasLegacyNamespace(false);
  }

  // Package private to prevent extensions.
  Module() {}

  /**
   * Returns this module's original name before the compiler applied any code transformations. For
   * Node and
   */
  public abstract String getOriginalName();

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
   * Returns a map of exported names to the JSDoc attached to the <em>export</em> keyword in the
   * AST.
   */
  public abstract ImmutableMap<String, JSDocInfo> getExportedDocs();

  /**
   * Returns a map of exported names. Keys will be the exported symbol and values will be the
   * name of the exported value, as seen within the module. This map will only include exported
   * names:
   * <pre><code>
   *   function foo() {}
   *   exports.bar = foo;  // Will record (foo, bar)
   * </code></pre>
   */
  public abstract ImmutableMap<String, String> getExportedNames();

  /**
   * Returns the JSDoc for symbols defined within this module.
   */
  public abstract ImmutableMap<String, JSDocInfo> getInternalVarDocs();

  /**
   * Returns the alias region encompassing this module.
   */
  public abstract AliasRegion getAliases();

  /**
   * Returns whether this module has a legacy namespace equal to its
   * {@link #getOriginalName() original name}. This will always return false
   * for non-closure modules.
   */
  public abstract boolean getHasLegacyNamespace();

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
    public abstract Builder setOriginalName(String name);
    public abstract String getOriginalName();
    public abstract Builder setId(String id);
    public abstract String getId();
    public abstract Builder setPath(Path path);
    public abstract Path getPath();
    public abstract Builder setType(Type type);
    public abstract Type getType();
    public abstract Builder setJsDoc(JsDoc doc);
    public abstract Builder setExportedNames(ImmutableMap<String, String> names);
    public abstract Builder setExportedDocs(ImmutableMap<String, JSDocInfo> docs);
    public abstract Builder setInternalVarDocs(ImmutableMap<String, JSDocInfo> docs);
    public abstract Builder setAliases(AliasRegion region);
    public abstract AliasRegion getAliases();
    public abstract Builder setHasLegacyNamespace(boolean legacy);

    abstract Module autoBuild();

    public Module build() {
      Module m = autoBuild();
      checkArgument(m.getPath().equals(m.getAliases().getPath()),
          "Module path does not match alias region path: %s != %s",
          m.getPath(), m.getAliases().getPath());
      checkArgument(!m.getHasLegacyNamespace() || m.getType() == Type.CLOSURE,
          "Only Closure modules may have a legacy namespace: %s", m.getId());
      checkArgument(!m.getHasLegacyNamespace() || m.getId().equals(m.getOriginalName()),
          "Module ID and legacy namespace must be the same: %s != %s",
          m.getOriginalName(), m.getId());
      return m;
    }
  }
}
