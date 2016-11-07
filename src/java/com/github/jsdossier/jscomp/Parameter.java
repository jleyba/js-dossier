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

import static com.google.common.base.Strings.nullToEmpty;

import com.google.javascript.rhino.JSTypeExpression;
import javax.annotation.Nullable;

/**
 * Describes a function parameter.
 */
public final class Parameter {

  private final String name;
  @Nullable private final JSTypeExpression type;
  private final String description;

  public Parameter(
      @Nullable String name,
      @Nullable JSTypeExpression type,
      @Nullable String description) {
    this.name = nullToEmpty(name);
    this.type = type;
    this.description = nullToEmpty(description).trim();
  }

  public String getName() {
    return name;
  }

  @Nullable
  public JSTypeExpression getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }
}
