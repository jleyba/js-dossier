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

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Custom soy rendering function for converting a string to lowerCamelCase.
 */
final class ToUpperCamelCaseFunction extends AbstractSoyJavaFunction {
  @Inject
  ToUpperCamelCaseFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    String value = getStringArgument(args, 0);
    value = LOWER_UNDERSCORE.to(UPPER_CAMEL, value);
    return StringData.forValue(value);
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }
}
