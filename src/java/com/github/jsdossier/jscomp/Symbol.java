/*
Copyright 2013-2018 Jason Leyba

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
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticRef;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.StaticSourceFile;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Represents a symbol found in JavaScript source that may be referenced from JSDoc. */
@AutoValue
public abstract class Symbol implements StaticSlot, StaticRef {

  @Nullable private SymbolTable table;

  Symbol() {}

  static Symbol forExports(Module module) {
    return new AutoValue_Symbol.Builder()
        .setName(module.getId().toString())
        .setJSDocInfo(module.getJsDoc().getInfo())
        .setPosition(Position.of(module.getRoot()))
        .setFile(module.getPath())
        .setNode(module.getRoot())
        .build();
  }

  static Builder builder(FileSystem fs, Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if (info == null
        && n.isName()
        && n.getParent() != null
        && (n.getParent().isVar() || n.getParent().isLet() || n.getParent().isConst())
        && n == n.getParent().getFirstChild()) {
      info = n.getParent().getJSDocInfo();
    }
    return new AutoValue_Symbol.Builder()
        .setJSDocInfo(info)
        .setPosition(Position.of(n))
        .setFile(fs.getPath(n.getSourceFileName()))
        .setNode(n);
  }

  @Override
  public final String toString() {
    return "Symbol<"
        + getName()
        + (getReferencedSymbol() == null ? "" : ", " + getReferencedSymbol())
        + ">";
  }

  // StaticSlot

  /**
   * Returns the name of this symbol as it appeared in source. This is always the last component in
   * a dot delimited name (e.g. "foo.x" -> "x").
   */
  @Override
  public abstract String getName();

  @Override
  public StaticRef getDeclaration() {
    return this;
  }

  /**
   * Returns the JSDoc attached to the node that defined this symbol; this is saved just in case the
   * compiler decides to remove the node/jsdoc prior to Dossier's custom documentation passes
   * running.
   */
  @Nullable
  @CheckReturnValue
  @Override
  public abstract JSDocInfo getJSDocInfo();

  @Override
  public final SymbolTable getScope() {
    return table;
  }

  final void setScope(SymbolTable table) {
    this.table = table;
  }

  // StaticRef

  @Override
  public Symbol getSymbol() {
    return this;
  }

  @Override
  public abstract Node getNode();

  @Override
  public StaticSourceFile getSourceFile() {
    throw new UnsupportedOperationException();
  }

  // Custom

  /** Returns the file in which this symbol was defined. */
  public abstract Path getFile();

  /** Returns the position in the file where this symbol was defined. */
  public abstract Position getPosition();

  /**
   * Returns the name of another symbol that this symbol is a reference to. This symbol is not
   * guaranteed to be a global name and should be resolved from this symbol's originating scope.
   */
  @Nullable
  @CheckReturnValue
  public abstract String getReferencedSymbol();

  /**
   * Whether this symbol was only seen in a goog.provide statement, or if it was actually found in
   * code.
   */
  abstract boolean isGoogProvide();

  /** Converts this symbol back to a builder. */
  abstract Builder toBuilder();

  /** Builds {@link Symbol} objects. */
  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {
      setGoogProvide(false);
    }

    public abstract String getName();

    public abstract Builder setName(String name);

    public abstract Builder setJSDocInfo(@Nullable JSDocInfo info);

    public abstract Builder setFile(Path p);

    public abstract Builder setPosition(Position p);

    public abstract Builder setReferencedSymbol(@Nullable String s);

    @Nullable
    public abstract String getReferencedSymbol();

    public abstract Builder setNode(Node n);

    public abstract Builder setGoogProvide(boolean provide);

    public abstract Symbol build();
  }
}
