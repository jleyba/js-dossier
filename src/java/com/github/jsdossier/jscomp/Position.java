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
import com.google.javascript.rhino.SourcePosition;

import javax.annotation.Nonnull;

/**
 * Represents a position in a file.
 */
@AutoValue
public abstract class Position implements Comparable<Position> {

  public static Position fromStart(SourcePosition<?> pos) {
    return new AutoValue_Position.Builder()
        .setLine(pos.getStartLine())
        .setOffset(pos.getPositionOnStartLine())
        .build();
  }

  public static Position fromEnd(SourcePosition<?> pos) {
    return new AutoValue_Position.Builder()
        .setLine(pos.getEndLine())
        .setOffset(pos.getPositionOnEndLine())
        .build();
  }
  
  public static Position of(int line, int offset) {
    return new AutoValue_Position.Builder()
        .setLine(line)
        .setOffset(offset)
        .build();
  }
  
  // Package-private to prevent extensions.
  Position() {}

  /**
   * The line in the file.
   */
  public abstract int getLine();

  /**
   * The offset on the line in the file.
   */
  public abstract int getOffset();

  @Override
  public int compareTo(@Nonnull Position that) {
    int diff = this.getLine() - that.getLine();
    if (diff < 0) {
      return diff;
    } else if (diff > 0) {
      return diff;
    }
    return this.getOffset() - that.getOffset();
  }

  /**
   * Builds {@link Position} objects.
   */
  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder setLine(int line);
    public abstract Builder setOffset(int offset);
    public abstract Position build();
  }
}
