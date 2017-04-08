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

package com.github.jsdossier.jscomp;

import static com.github.jsdossier.jscomp.ProvidedSymbolPass.nameToId;
import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.jscomp.CompilerOptions;
import java.nio.file.FileSystem;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ProvidedSymbolPass}. */
@RunWith(JUnit4.class)
public class ProvidedSymbolPassTest {

  @Rule
  public GuiceRule guiceRule =
      GuiceRule.builder(this)
          .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
          .build();

  @Inject @Input private FileSystem fs;
  @Inject private TypeRegistry typeRegistry;
  @Inject private CompilerUtil util;

  private void declareNodeModules() {
    guiceRule
        .toBuilder()
        .setModulePrefix("/modules")
        .setModules("foo/bar.js")
        .build()
        .createInjector()
        .injectMembers(this);
  }

  @Test
  public void collectsProvidedSymbols() {
    util.compile(
        fs.getPath("foo/bar.js"),
        "goog.provide('Foo');",
        "goog.provide('foo.Bar');",
        "goog.provide('foo.bar.Baz');",
        "goog.provide('one.two.three.Four');");

    assertThat(typeRegistry.getProvidedSymbols())
        .containsExactly("Foo", "foo.Bar", "foo.bar.Baz", "one.two.three.Four");
    assertThat(typeRegistry.getImplicitNamespaces())
        .containsExactly(
            "Foo",
            "foo",
            "foo.Bar",
            "foo.bar",
            "foo.bar.Baz",
            "one",
            "one.two",
            "one.two.three",
            "one.two.three.Four");

    assertThat(typeRegistry.isProvided("one.two.three")).isFalse();
    assertThat(typeRegistry.isProvided("one.two.three.Four")).isTrue();
  }

  @Test
  public void collectsClosureModules() {
    util.compile(
        fs.getPath("/foo/bar/module.js"),
        "goog.module('sample.module');",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {};",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.publicFunction1 = internalFunction1;",
        "exports.publicFunction2 = internalFunction2;",
        "exports.publicX = internalX;");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).isEmpty();

    Module module = typeRegistry.getModule(nameToId("sample.module"));
    assertThat(module.getHasLegacyNamespace()).isFalse();
    assertThat(module.getPath().toString()).isEqualTo("/foo/bar/module.js");
    assertThat(module.getType()).isEqualTo(Module.Type.CLOSURE);
  }

  @Test
  public void collectsClosureModules_withLegacyNamespace() {
    util.compile(
        fs.getPath("/foo/bar/module.js"),
        "goog.module('sample.module');",
        "goog.module.declareLegacyNamespace();",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {};",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.publicFunction1 = internalFunction1;",
        "exports.publicFunction2 = internalFunction2;",
        "exports.publicX = internalX;");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).containsExactly("sample", "sample.module");

    Module module = typeRegistry.getModule("sample.module");
    assertThat(module.getHasLegacyNamespace()).isTrue();
    assertThat(module.getPath().toString()).isEqualTo("/foo/bar/module.js");
    assertThat(module.getType()).isEqualTo(Module.Type.CLOSURE);
  }

  @Test
  public void collectsJsDocForModuleInternalVars_closureModule() {
    util.compile(
        fs.getPath("module.js"),
        "goog.module('sample.module');",
        "",
        "function fnDeclNoDocs() {}",
        "/** Has docs. */ function fnDeclDocs() {}",
        "/** Var docs. */ var fnExpr = function() {}",
        "/** Let docs. */ let fnExpr2 = function() {}",
        "/** Const docs. */ const fnExpr3 = function() {}",
        "",
        "class clazzNoDocs {}",
        "/** Var class. */ var varClass = class {};",
        "/** Let class. */ let letClass = class {};",
        "/** Const class. */ const constClass = class {};",
        "",
        "/** Undef1. */ var undef1;",
        "/** Undef2. */ let undef2;",
        "/** Integer. */ var x = 123;",
        "/** Boolean. */ let y = true;",
        "/** String. */ const z = 'hi';",
        "",
        "var varNoDocs;",
        "let letNoDocs;");

    Module module = typeRegistry.getModule(nameToId("sample.module"));
    assertThat(module.getInternalVarDocs().keySet())
        .containsExactly(
            "fnDeclDocs",
            "fnExpr",
            "fnExpr2",
            "fnExpr3",
            "varClass",
            "letClass",
            "constClass",
            "undef1",
            "undef2",
            "x",
            "y",
            "z");
  }

  @Test
  public void collectsJsDocForModuleInternalVars_nodeModule() {
    declareNodeModules();

    util.compile(
        fs.getPath("/modules/foo/bar.js"),
        "",
        "function fnDeclNoDocs() {}",
        "/** Has docs. */ function fnDeclDocs() {}",
        "/** Var docs. */ var fnExpr = function() {}",
        "/** Let docs. */ let fnExpr2 = function() {}",
        "/** Const docs. */ const fnExpr3 = function() {}",
        "",
        "class clazzNoDocs {}",
        "/** Var class. */ var varClass = class {};",
        "/** Let class. */ let letClass = class {};",
        "/** Const class. */ const constClass = class {};",
        "",
        "/** Undef1. */ var undef1;",
        "/** Undef2. */ let undef2;",
        "/** Integer. */ var x = 123;",
        "/** Boolean. */ let y = true;",
        "/** String. */ const z = 'hi';",
        "",
        "var varNoDocs;",
        "let letNoDocs;");

    Module module = typeRegistry.getModule(nameToId("module$$modules$foo$bar"));
    assertThat(module.getInternalVarDocs().keySet())
        .containsExactly(
            "fnDeclDocs",
            "fnExpr",
            "fnExpr2",
            "fnExpr3",
            "varClass",
            "letClass",
            "constClass",
            "undef1",
            "undef2",
            "x",
            "y",
            "z");
  }

  @Test
  public void buildsExportToInternalNameMap_closureModule() {
    util.compile(
        fs.getPath("module.js"),
        "goog.module('sample.module');",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.publicFunction1 = internalFunction1",
        "exports.publicFunction2 = internalFunction2",
        "exports.publicX = internalX");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).isEmpty();

    Module module = typeRegistry.getModule(nameToId("sample.module"));
    assertThat(module.getExportedNames().keySet())
        .containsExactly("publicFunction1", "publicFunction2", "publicX");
    assertThat(module.getExportedNames()).containsEntry("publicFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("publicFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("publicX", "internalX");
  }

  @Test
  public void buildsExportToInternalNameMap_exportsObjectLit_closureModule() {
    util.compile(
        fs.getPath("module.js"),
        "goog.module('sample.module');",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "class A {}",
        "A.B = class {}",
        "",
        "exports = {publicFunction1: internalFunction1,",
        "           publicFunction2: internalFunction2,",
        "           publicX: internalX,",
        "           publicB: A.B",
        "};");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).isEmpty();

    Module module = typeRegistry.getModule(nameToId("sample.module"));
    assertThat(module.getExportedNames().keySet())
        .containsExactly("publicFunction1", "publicFunction2", "publicX", "publicB");
    assertThat(module.getExportedNames()).containsEntry("publicFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("publicFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("publicX", "internalX");
    assertThat(module.getExportedNames()).containsEntry("publicB", "A.B");
  }

  @Test
  public void buildsExportToInternalNameMap_exportsDestructuredObjectLit_closureModule() {
    util.compile(
        fs.getPath("module.js"),
        "goog.module('sample.module');",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports = {internalFunction1,",
        "           internalFunction2,",
        "           internalX};");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).isEmpty();

    Module module = typeRegistry.getModule(nameToId("sample.module"));
    assertThat(module.getExportedNames().keySet())
        .containsExactly("internalFunction1", "internalFunction2", "internalX");
    assertThat(module.getExportedNames()).containsEntry("internalFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("internalFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("internalX", "internalX");
  }

  @Test
  public void buildsExportToInternalNameMap_exportsNameAssignedToObjectLit_closureModule() {
    util.compile(
        fs.getPath("module.js"),
        "goog.module('sample.module');",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.Data = {internalFunction1,",
        "                internalFunction2,",
        "                internalX};");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).isEmpty();

    Module module = typeRegistry.getModule(nameToId("sample.module"));
    assertThat(module.getExportedNames().keySet()).isEmpty();
  }

  @Test
  public void buildsExportToInternalNameMap_nodeModule() {
    declareNodeModules();

    util.compile(
        fs.getPath("/modules/foo/bar.js"),
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.publicFunction1 = internalFunction1",
        "exports.publicFunction2 = internalFunction2",
        "exports.publicX = internalX");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).isEmpty();

    Module module = typeRegistry.getModule(nameToId("module$$modules$foo$bar"));
    assertThat(module.getPath().toString()).isEqualTo("/modules/foo/bar.js");
    assertThat(module.getType()).isEqualTo(Module.Type.NODE);
    assertThat(module.getExportedNames().keySet())
        .containsExactly("publicFunction1", "publicFunction2", "publicX");
    assertThat(module.getExportedNames()).containsEntry("publicFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("publicFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("publicX", "internalX");
  }

  @Test
  public void buildsExportToInternalNameMap_exportsObjectLit_nodeModule() {
    declareNodeModules();

    util.compile(
        fs.getPath("/modules/foo/bar.js"),
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "class A {}",
        "A.B = class {}",
        "",
        "module.exports = {publicFunction1: internalFunction1,",
        "                  publicFunction2: internalFunction2,",
        "                  publicX: internalX,",
        "                  publicB: A.B",
        "};");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).isEmpty();

    Module module = typeRegistry.getModule(nameToId("module$$modules$foo$bar"));
    assertThat(module.getPath().toString()).isEqualTo("/modules/foo/bar.js");
    assertThat(module.getType()).isEqualTo(Module.Type.NODE);
    assertThat(module.getExportedNames().keySet())
        .containsExactly("publicFunction1", "publicFunction2", "publicX", "publicB");
    assertThat(module.getExportedNames()).containsEntry("publicFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("publicFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("publicX", "internalX");
    assertThat(module.getExportedNames()).containsEntry("publicB", "A.B");
  }
}
