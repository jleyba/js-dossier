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

import static com.google.common.truth.Truth.assertAbout;

import com.github.jsdossier.jscomp.Symbol;

public final class LinkedSymbolSubject {

  private final Symbol symbol;

  LinkedSymbolSubject(Symbol symbol) {
    this.symbol = symbol;
  }

  public SymbolSubject that() {
    return assertAbout(SymbolSubject::new).that(symbol);
  }
}
