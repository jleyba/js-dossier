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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.rhino.jstype.JSType;
import javax.annotation.Nullable;

public final class JSTypeSubject extends Subject<JSTypeSubject, JSType> {

  JSTypeSubject(FailureMetadata md, @Nullable JSType actual) {
    super(md, actual);
  }

  public void isConstructor() {
    if (!actual().isConstructor()) {
      fail("is a constructor; it is " + actual().getClass().getName());
    }
  }

  public void isInterface() {
    if (!actual().isInterface()) {
      fail("is an interface");
    }
  }

  public void isEnumType() {
    if (!actual().isEnumType()) {
      fail("is an enum type");
    }
  }

  public void isFunction() {
    if (!actual().isFunctionType()) {
      fail("is a function");
    }
  }

  public void isNumber() {
    if (!actual().isNumber()) {
      fail("is a number");
    }
  }

  public void isString() {
    if (!actual().isString()) {
      fail("is a string");
    }
  }

  public void isBoolean() {
    if (!actual().isBooleanValueType()) {
      fail("is a boolean");
    }
  }

  public void isObject() {
    if (!actual().isObjectType()) {
      fail("is an object");
    }
  }
}
