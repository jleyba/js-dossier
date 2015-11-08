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
import com.google.common.base.Optional;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSType;

import java.nio.file.Path;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Describes a named JavaScript type that should be documented.
 */
@AutoValue
public abstract class NominalType2 {
  
  public static Builder builder() {
    return new AutoValue_NominalType2.Builder();
  }

  /**
   * Returns this type's name.
   */
  public abstract String getName();

  /**
   * Returns this JSType as used by the compiler.
   */
  public abstract JSType getType();

  /**
   * Returns the path to the file that defines this type.
   */
  public abstract Path getSourceFile();

  /**
   * Returns the position in the file where this type is defined.
   */
  public abstract Position getSourcePosition();

  /**
   * Returns the JSDoc attached to this type.
   */
  public abstract JsDoc getJsDoc();

  /**
   * Returns the module this type is defined in, if any.
   */
  public abstract Optional<Module> getModule();

  /**
   * Returns whether this is the main exports object for this type's containing module.
   */
  public boolean isModuleExports() {
    return getModule().isPresent() && getModule().get().getId().equals(getName());
  }

  /**
   * Resolves a type name relative to this type. Returns {@code null} if the name cannot be
   * resolved.
   */
  @Nullable
  @CheckReturnValue
  public NominalType2 resolveType(String name, TypeRegistry typeRegistry) {
    String def = typeRegistry.resolveAlias(this, name);
    if (def != null) {
      name = def;
    }
    if (typeRegistry.isType(name)) {
      return typeRegistry.getType(name);
    }
    return null;
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder setName(String name);
    public abstract Builder setType(JSType type);
    public abstract Builder setSourceFile(Path path);
    public abstract Builder setSourcePosition(Position position);

    public abstract Builder setJsDoc(JsDoc docs);
    public Builder setJsDoc(@Nullable JSDocInfo info) {
      return setJsDoc(JsDoc.from(info));
    }

    public abstract Builder setModule(Optional<Module> module);
    public Builder setModule(@Nullable Module module) {
      return setModule(Optional.fromNullable(module));
    }

    public abstract NominalType2 build();
  }
}
