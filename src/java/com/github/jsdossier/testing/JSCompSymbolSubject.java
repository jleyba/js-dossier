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

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.javascript.jscomp.SymbolTable.Symbol;
import javax.annotation.Nullable;

public final class JSCompSymbolSubject extends Subject<JSCompSymbolSubject, Symbol> {

  static final SubjectFactory<JSCompSymbolSubject, Symbol> FACTORY =
      new SubjectFactory<JSCompSymbolSubject, Symbol>() {
        @Override
        public JSCompSymbolSubject getSubject(FailureStrategy fs, Symbol that) {
          return new JSCompSymbolSubject(fs, that);
        }
      };

  public JSCompSymbolSubject(FailureStrategy failureStrategy, @Nullable Symbol actual) {
    super(failureStrategy, actual);
  }
}
