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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.stream.Collectors.toList;

import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.Symbol;
import com.github.jsdossier.jscomp.SymbolTable;
import com.google.common.collect.Iterables;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class SymbolTableSubject extends Subject<SymbolTableSubject, SymbolTable> {

  public SymbolTableSubject(FailureMetadata md, SymbolTable actual) {
    super(md, actual);

    if (actual.getParentScope() == null) {
      named("global symbol table");
    } else {
      named("symbol table (root = %s)", actual.getRootNode());
    }
  }

  private List<String> getSymbolNames() {
    return actual().getAllSymbols().stream().map(Symbol::getName).collect(toList());
  }

  public void isEmpty() {
    if (actual().getAllSymbols().iterator().hasNext()) {
      failWithoutActual("is empty; it has " + getSymbolNames());
    }
  }

  public LinkedSymbolSubject hasOwnSymbol(String name) {
    Symbol symbol = actual().getOwnSlot(name);
    if (symbol == null) {
      failWithoutActual("has own symbol \"" + name + "\"; has " + getSymbolNames());
    }
    return new LinkedSymbolSubject(symbol);
  }

  public LinkedSymbolSubject hasOnlyOneSymbol(String name) {
    Symbol s = Iterables.getOnlyElement(actual().getAllSymbols());
    assertWithMessage("only symbol is not %s", name).that(s.getName()).isEqualTo(name);
    return new LinkedSymbolSubject(s);
  }

  public void containsExactly(String... names) {
    containsExactly(Arrays.asList(names));
  }

  public void containsExactly(Iterable<String> names) {
    assertThat(getSymbolNames()).containsExactlyElementsIn(names);
  }

  public void hasOwnSymbolsWithReferences(String... args) {
    checkArgument(args.length > 0 && args.length % 2 == 0, "expect even number of args");
    for (int i = 0; i < args.length; i += 2) {
      hasOwnSymbol(args[i]).that().isAReferenceTo(args[i + 1]);
    }
  }

  public void hasNoModules() {
    assertThat(actual().getAllModules()).isEmpty();
  }

  public void doesNotHaveModule(Path path) {
    Module module = actual().getModule(path);
    if (module != null) {
      failWithoutActual("does not have module with path = " + path);
    }
  }

  private void verifyId(Module module) {
    Truth.assertWithMessage("did not properly track ID for module @ path %s", module.getPath())
        .that(actual().getModuleById(module.getId().toString()))
        .isSameAs(module);
  }

  public Module hasEs6Module(Path path) {
    Module module = getModuleByPath(path);
    Truth.assertWithMessage("%s is not an ES6 module", path)
        .that(module.getId().getType())
        .isEqualTo(Module.Type.ES6);
    verifyId(module);
    return module;
  }

  public Module hasGoogModule(Path path) {
    Module module = getModuleByPath(path);
    Truth.assertWithMessage("%s is not a CLOSURE module", path)
        .that(module.getId().getType())
        .isEqualTo(Module.Type.CLOSURE);
    verifyId(module);
    return module;
  }

  public Module hasGoogModule(String id) {
    Module module = actual().getModuleById(id);
    if (module == null) {
      failWithoutActual(internalCustomName() + " does not have a module with ID " + id);
    }
    assert module != null;
    Truth.assertWithMessage("%s is not a CLOSURE module", id)
        .that(module.getId().getType())
        .isEqualTo(Module.Type.CLOSURE);
    verifyId(module);
    return module;
  }

  public Module hasNodeModule(Path path) {
    Module module = getModuleByPath(path);
    Truth.assertWithMessage("%s is not a NODE module", path)
        .that(module.getId().getType())
        .isEqualTo(Module.Type.NODE);
    verifyId(module);
    return module;
  }

  private Module getModuleByPath(Path path) {
    Module module = actual().getModule(path);
    if (module == null) {
      failWithoutActual(internalCustomName() + " does not have a module from path " + path);
      throw new AssertionError("unreachable statement");
    }
    return module;
  }
}
