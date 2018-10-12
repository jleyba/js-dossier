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

import com.github.jsdossier.jscomp.Symbol;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.javascript.rhino.JSDocInfo;

public final class SymbolSubject extends Subject<SymbolSubject, Symbol> {

  static final SubjectFactory<SymbolSubject, Symbol> FACTORY =
      new SubjectFactory<SymbolSubject, Symbol>() {
        @Override
        public SymbolSubject getSubject(FailureStrategy fs, Symbol that) {
          return new SymbolSubject(fs, that);
        }
      };

  public SymbolSubject(FailureStrategy failureStrategy, Symbol actual) {
    super(failureStrategy, actual);
  }

  public void isNotAReference() {
    String ref = actual().getReferencedSymbol();
    assertWithMessage("%s is an unexpected reference to %s", actual(), ref).that(ref).isNull();
  }

  public void isAReferenceTo(String name) {
    String ref = actual().getReferencedSymbol();
    assertWithMessage("%s is not a reference", actual()).that(ref).isNotNull();
    assertWithMessage("%s is an unexpected reference to %s", actual(), ref)
        .that(ref)
        .isEqualTo(name);
  }

  public void hasNoJsDoc() {
    JSDocInfo info = actual().getJSDocInfo();
    if (info != null && !info.getOriginalCommentString().isEmpty()) {
      failWithRawMessage("%s has unexpected jsdoc: %s", actual(), info.getOriginalCommentString());
    }
  }

  public void hasJsDoc(String text) {
    JSDocInfo info = actual().getJSDocInfo();
    assertWithMessage("%s does not have jsdoc", actual()).that(info).isNotNull();
    assertWithMessage("wrong jsdoc for %s", actual())
        .that(info.getOriginalCommentString())
        .isEqualTo(text);
  }
}
