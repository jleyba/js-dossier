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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.javascript.rhino.JSDocInfo;
import javax.annotation.Nullable;

public final class JSDocInfoSubject extends Subject<JSDocInfoSubject, JSDocInfo> {

  static final SubjectFactory<JSDocInfoSubject, JSDocInfo> FACTORY = new Factory();

  public JSDocInfoSubject(FailureStrategy failureStrategy, @Nullable JSDocInfo actual) {
    super(failureStrategy, actual);
  }

  public void hasOriginalCommentString(String comment) {
    JSDocInfo info = actual();
    assertThat(info.getOriginalCommentString()).isEqualTo(comment);
  }

  public static final class Factory extends SubjectFactory<JSDocInfoSubject, JSDocInfo> {
    @Override
    public JSDocInfoSubject getSubject(FailureStrategy fs, JSDocInfo that) {
      return new JSDocInfoSubject(fs, that);
    }
  }
}
