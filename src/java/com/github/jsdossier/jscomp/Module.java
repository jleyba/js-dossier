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
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.deps.ModuleNames.fileToModuleName;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.nio.file.Path;

/** Describes a JavaScript module. */
@AutoValue
public abstract class Module {

  /** See {@link com.google.javascript.jscomp.ClosureRewriteModule} */
  private static final String MODULE_EXPORTS_PREFIX = "module$exports$";

  /** See {@link com.google.javascript.jscomp.ClosureRewriteModule} */
  private static final String MODULE_CONTENTS_PREFIX = "module$contents$";

  /** Returns a new builder. */
  public static Builder builder() {
    return new AutoValue_Module.Builder().setHasLegacyNamespace(false);
  }

  // Package private to prevent extensions.
  Module() {}

  /** Returns the ID for this module. */
  public abstract Id getId();

  /**
   * Returns the ID used to reference this module in code after any transformations applied by the
   * compiler. For Closure module's, this will simply be the ID specified in the "goog.module"
   * declaration. For Node and ES6 modules, this will be derived by the compiler from the module's
   * file path.
   */
  public final String getOriginalName() {
    return getId().getOriginalName();
  }

  /** Returns the input file that defines this module. */
  public final Path getPath() {
    return getId().getPath();
  }

  /** Returns whether this is a Closure module. */
  public boolean isClosure() {
    return getId().getType() == Type.CLOSURE;
  }

  /** Returns whether this is an ES6 module. */
  public boolean isEs6() {
    return getId().getType() == Type.ES6;
  }

  /** Returns whether this is a node-style commonjs module. */
  public boolean isNode() {
    return getId().getType() == Type.NODE;
  }

  /** Returns the file-level JSDoc for this module. */
  public abstract JsDoc getJsDoc();

  /**
   * Returns a map of exported names to the JSDoc attached to the <em>export</em> keyword in the
   * AST.
   */
  public abstract ImmutableMap<String, JSDocInfo> getExportedDocs();

  /**
   * Returns a map of exported names. Keys will be the exported symbol and values will be the name
   * of the exported value, as seen within the module. This map will only include exported names:
   *
   * <pre><code>
   *   function foo() {}
   *   exports.bar = foo;  // Will record (foo, bar)
   * </code></pre>
   */
  public abstract ImmutableMap<String, String> getExportedNames();

  /**
   * Returns the IDs for modules that are exported in full form this module:
   *
   * <pre><code>
   *   export * from './foo.js';
   * </code></pre>
   */
  public abstract ImmutableSet<Id> getExportedModules();

  /** Returns the alias region encompassing this module. */
  public abstract AliasRegion getAliases();

  /**
   * Returns whether this module has a legacy namespace equal to its {@link #getOriginalName()
   * original name}. This will always return false for non-closure modules.
   */
  public abstract boolean getHasLegacyNamespace();

  /** Returns the root node in the AST for this module. */
  public abstract Node getRoot();

  /** Returns the internal symbol table for this module. */
  public abstract SymbolTable getInternalSymbolTable();

  /** The recognized module types. */
  public enum Type {
    /** A module defined using the Closure library's "goog.module" syntax. */
    CLOSURE() {
      @Override
      public boolean isModuleId(String name) {
        return name.startsWith(MODULE_EXPORTS_PREFIX);
      }

      @Override
      public String stripModulePrefix(String name) {
        checkArgument(isModuleId(name), "not a module ID: %s", name);
        return name.substring(MODULE_EXPORTS_PREFIX.length());
      }

      @Override
      public Id newId(Path path) {
        throw new UnsupportedOperationException(
            "cannot create a closure module ID from just the file path");
      }

      @Override
      public Id newId(String name, Path path) {
        return new AutoValue_Module_Id(
            MODULE_EXPORTS_PREFIX + name.replace('.', '$'), name, path, this);
      }
    },

    /** A standard ES6 module. */
    ES6() {
      @Override
      public boolean isModuleId(String name) {
        return name.startsWith("module$");
      }

      @Override
      public String stripModulePrefix(String name) {
        return name;
      }

      @Override
      public Id newId(Path path) {
        return new AutoValue_Module_Id(
            fileToModuleName(path.normalize().toString()), path.toString(), path, this);
      }

      @Override
      public Id newId(String ignored, Path path) {
        throw new UnsupportedOperationException(
            "can only create and ES6 module ID from the file path");
      }
    },

    /**
     * CommonJS modules, as implemented for Node. Modules support "require(id)" syntax for imports.
     */
    NODE() {
      @Override
      public boolean isModuleId(String name) {
        return CLOSURE.isModuleId(name);
      }

      @Override
      public String stripModulePrefix(String name) {
        return CLOSURE.stripModulePrefix(name);
      }

      @Override
      public Id newId(Path path) {
        Id es6 = ES6.newId(path);
        return newId(es6.getCompiledName(), path);
      }

      @Override
      public Id newId(String name, Path path) {
        return new AutoValue_Module_Id(
            MODULE_EXPORTS_PREFIX + name.replace('.', '$'), name, path, this);
      }
    };

    /** Returns whether the given variable name could be ID for this type of module. */
    public abstract boolean isModuleId(String name);

    /**
     * Strips the module prefix used on the given name to generate a module ID from another JS
     * identifier. In the case of ES6 modules, the initial value will always be returned unchanged.
     */
    public abstract String stripModulePrefix(String name);

    /** Creates a new ID from the path to a module file. */
    public abstract Id newId(Path path);

    /** Creates a new ID for this type of module. */
    public abstract Id newId(String name, Path path);
  }

  /** An identifier for a JavaScript module. */
  @AutoValue
  public abstract static class Id {
    Id() {}

    @Override
    public String toString() {
      return getCompiledName();
    }

    String getContentsVar(String name) {
      if (Type.ES6.equals(getType())) {
        int index = name.indexOf('.');
        if (index > 0) {
          return name.substring(0, index) + "$$" + getCompiledName() + name.substring(index);
        }
        return name + "$$" + getCompiledName();
      }

      String base = getCompiledName();
      if (base.startsWith(MODULE_EXPORTS_PREFIX)) {
        base = base.substring(MODULE_EXPORTS_PREFIX.length());
      } else {
        base = base.replace('.', '$');
      }
      return MODULE_CONTENTS_PREFIX + base + "_" + name;
    }

    Id toLegacyId() {
      checkState(
          getType() == Type.CLOSURE,
          "A legacy ID may only be created for a closure module: %s",
          getOriginalName());
      return new AutoValue_Module_Id(getOriginalName(), getOriginalName(), getPath(), getType());
    }

    /** Returns the module ID as it will appear in code generated by the Closure Compiler. */
    public abstract String getCompiledName();

    /**
     * Returns the module's original name. For a closure module, this will be the string provided in
     * the call to {@code goog.module()}. For a node or ES6 module, this will be the module file's
     * path.
     */
    public abstract String getOriginalName();

    /** Returns the path to the file that declared this module. */
    public abstract Path getPath();

    /** Returns the type of module this is an ID for. */
    public abstract Type getType();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder setId(Id id);

    public abstract Id getId();

    final Path getPath() {
      return getId().getPath();
    }

    final boolean isClosure() {
      return Type.CLOSURE.equals(getType());
    }

    final boolean isNode() {
      return Type.NODE.equals(getType());
    }

    final boolean isEs6() {
      return Type.ES6.equals(getType());
    }

    final Type getType() {
      return getId().getType();
    }

    public abstract Builder setJsDoc(JsDoc doc);

    public abstract ImmutableMap.Builder<String, String> exportedNamesBuilder();

    public abstract ImmutableMap.Builder<String, JSDocInfo> exportedDocsBuilder();

    public abstract ImmutableSet.Builder<Id> exportedModulesBuilder();

    public abstract Builder setAliases(AliasRegion region);

    public abstract AliasRegion getAliases();

    public abstract Builder setHasLegacyNamespace(boolean legacy);

    public abstract Builder setRoot(Node node);

    public abstract Builder setInternalSymbolTable(SymbolTable symbolTable);

    public abstract SymbolTable getInternalSymbolTable();

    abstract Module autoBuild();

    public Module build() {
      Module m = autoBuild();
      checkArgument(
          m.getId().getPath().equals(m.getAliases().getPath()),
          "Module path does not match alias region path: %s != %s",
          m.getId().getPath(),
          m.getAliases().getPath());
      checkArgument(
          !m.getHasLegacyNamespace() || m.isClosure(),
          "Only Closure modules may have a legacy namespace: %s",
          m.getId());
      return m;
    }
  }
}
