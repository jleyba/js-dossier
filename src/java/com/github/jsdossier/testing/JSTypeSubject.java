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

package com.github.jsdossier.testing;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.rhino.jstype.JSType;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public final class JSTypeSubject extends Subject<JSTypeSubject, JSType> {

  JSTypeSubject(FailureMetadata md, @Nullable JSType actual) {
    super(md, actual);
  }

  private void check(Predicate<JSType> check) {
    assertWithMessage("%s is a %s", actual(), actual().getClass().getName())
        .that(check.test(actual()))
        .isTrue();
  }

  public void isConstructor() {
    check(JSType::isConstructor);
  }

  public void isInterface() {
    check(JSType::isInterface);
  }

  public void isEnumType() {
    check(JSType::isEnumType);
  }

  public void isFunction() {
    check(JSType::isFunctionType);
  }

  public void isNumber() {
    check(JSType::isNumber);
  }

  public void isString() {
    check(JSType::isString);
  }

  public void isBoolean() {
    check(JSType::isBooleanValueType);
  }

  public void isObject() {
    check(JSType::isObjectType);
  }
}
