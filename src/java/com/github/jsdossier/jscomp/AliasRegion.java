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

import com.google.auto.value.AutoValue;
import com.google.common.collect.Range;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.SourcePosition;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Describes a region of a source file that contains variable aliases created by the compiler. A
 * region may represent a module rewritten for the global scope, or a goog.scope block.
 */
@AutoValue
public abstract class AliasRegion implements CompilerOptions.AliasTransformation {

  private static final Logger log = Logger.getLogger(AliasRegion.class.getName());

  private final Map<String, String> aliases = new HashMap<>();

  /**
   * Returns a new region builder.
   */
  public static Builder builder() {
    return new AutoValue_AliasRegion.Builder();
  }

  /**
   * Creates a new alias region for an entire file.
   *
   * @param path the path to the file to define a new region for.
   * @return the new AliasRegion.
   */
  public static AliasRegion forFile(Path path) {
    return AliasRegion.builder()
        .setPath(path)
        .setRange(Range.<Position>all())
        .build();
  }

  // Package-private to prevent extensions.
  AliasRegion() {}

  @Override
  public void addAlias(String alias, String definition) {
    log.fine(String.format("In %s, %s = %s (%s)", getPath(), alias, definition, getRange()));
    aliases.put(alias, definition);
  }

  /**
   * Returns the definition for the given alias, or null if there is no such alias defined in this
   * region.
   */
  @Nullable
  @CheckReturnValue
  public String resolveAlias(String alias) {
    return aliases.get(alias);
  }

  /**
   * Returns all aliases defined in this region.
   */
  public Iterable<String> getAliases() {
    return Collections.unmodifiableCollection(aliases.keySet());
  }

  /**
   * Returns the path to the file defines this region.
   */
  public abstract Path getPath();

  /**
   * Returns the bounded region in the file that defines the region.
   */
  public abstract Range<Position> getRange();

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder setPath(Path p);
    public abstract Builder setRange(Range<Position> r);

    public Builder setRange(SourcePosition<?> position) {
      return setRange(Range.closed(Position.fromStart(position), Position.fromEnd(position)));
    }

    public abstract AliasRegion build();
  }
}
