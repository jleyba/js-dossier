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

import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.Position;
import com.github.jsdossier.jscomp.Symbol;
import com.github.jsdossier.jscomp.SymbolTable;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSType;

public final class DossierTruth {
  private DossierTruth() {}

  public static JSTypeSubject assertThat(JSType type) {
    return assertAbout(JSTypeSubject::new).that(type);
  }

  public static JSDocInfoSubject assertThat(JSDocInfo info) {
    return assertAbout(JSDocInfoSubject::new).that(info);
  }

  public static ModuleSubject assertThat(Module module) {
    return assertAbout(ModuleSubject::new).that(module);
  }

  public PositionSubject assertThat(Position position) {
    return assertAbout(PositionSubject::new).that(position);
  }

  public static JSCompSymbolSubject assertThat(
      com.google.javascript.jscomp.SymbolTable.Symbol symbol) {
    return assertAbout(JSCompSymbolSubject::new).that(symbol);
  }

  public static SymbolSubject assertThat(Symbol symbol) {
    return assertAbout(SymbolSubject::new).that(symbol);
  }

  public static SymbolTableSubject assertThat(SymbolTable table) {
    return assertAbout(SymbolTableSubject::new).that(table);
  }
}
