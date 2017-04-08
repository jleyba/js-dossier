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

package com.github.jsdossier.soy;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.SoyString;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.util.List;

/** Base class for custom Soy rendering functions. */
abstract class AbstractSoyJavaFunction implements SoyJavaFunction {
  /** Returns the string value for the nth argument this function was called with. */
  String getStringArgument(List<SoyValue> args, int position) {
    SoyValue arg = args.get(position);
    checkArgument(arg != null, "Argument %s to %s must be a string; was null", position, getName());
    checkArgument(
        arg instanceof SoyString,
        "Argument %s to %s must be of type string (found type %s)",
        position,
        getName(),
        arg.getClass().getName());
    return arg.stringValue();
  }

  @Override
  public final String getName() {
    String name = getClass().getSimpleName();
    checkState(name.endsWith("Function"), "%s must end with 'Function'", name);

    name = name.substring(0, name.length() - "Function".length());
    name = UPPER_CAMEL.to(LOWER_CAMEL, name);
    return name;
  }
}
