/*
 Copyright 2013-2015 Jason Leyba
 
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

import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.jscomp.CompilerOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;

import javax.inject.Inject;

/**
 * Tests for {@link ProvidedSymbolPass}.
 */
@RunWith(JUnit4.class)
public class ProvidedSymbolPassTest {
  
  @Rule 
  public GuiceRule guiceRule = GuiceRule.builder(this)
      .setModulePrefix("/modules")
      .setModules("foo/bar.js")
      .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
      .build();

  @Inject @Input private FileSystem fs; 
  @Inject private TypeRegistry typeRegistry;
  @Inject private CompilerUtil util;

  @Test
  public void collectsProvidedSymbols() {
    util.compile(fs.getPath("foo/bar.js"),
        "goog.provide('Foo');",
        "goog.provide('foo.Bar');",
        "goog.provide('foo.bar.Baz');",
        "goog.provide('one.two.three.Four');");

    assertThat(typeRegistry.getProvidedSymbols()).containsExactly(
        "Foo", "foo.Bar", "foo.bar.Baz", "one.two.three.Four");
    assertThat(typeRegistry.getImplicitNamespaces()).containsExactly(
        "Foo", "foo", "foo.Bar", "foo.bar", "foo.bar.Baz",
        "one", "one.two", "one.two.three", "one.two.three.Four");
  }

  @Test
  public void collectsClosureModules() {
    util.compile(fs.getPath("/foo/bar/module.js"),
        "goog.module('sample.module');",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {};",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.publicFunction1 = internalFunction1;",
        "exports.publicFunction2 = internalFunction2;",
        "exports.publicX = internalX;",
        "exports = internalObj;",
        "",
        "module.exports = 'foo';"
    );
    
    assertThat(typeRegistry.getProvidedSymbols()).containsExactly("sample.module");
    assertThat(typeRegistry.getImplicitNamespaces()).containsExactly("sample", "sample.module");
    
    Module module = typeRegistry.getModule("sample.module");
    assertThat(module.getPath().toString()).isEqualTo("/foo/bar/module.js");
    assertThat(module.getType()).isEqualTo(Module.Type.CLOSURE);
  }

  @Test
  public void collectsJsDocForModuleInternalVars_closureModule() {
    util.compile(fs.getPath("module.js"),
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

    Module module = typeRegistry.getModule("sample.module");
    assertThat(module.getInternalVarDocs().keySet()).containsExactly(
        "fnDeclDocs", "fnExpr", "fnExpr2", "fnExpr3",
        "varClass", "letClass", "constClass",
        "undef1", "undef2", "x", "y", "z");
  }

  @Test
  public void collectsJsDocForModuleInternalVars_nodeModule() {
    util.compile(fs.getPath("/modules/foo/bar.js"),
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

    Module module = typeRegistry.getModule("module$$modules$foo$bar");
    assertThat(module.getInternalVarDocs().keySet()).containsExactly(
        "fnDeclDocs", "fnExpr", "fnExpr2", "fnExpr3",
        "varClass", "letClass", "constClass",
        "undef1", "undef2", "x", "y", "z");
  }

  @Test
  public void buildsExportToInternalNameMap_closureModule() {
    util.compile(fs.getPath("module.js"),
        "goog.module('sample.module');",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.publicFunction1 = internalFunction1",
        "exports.publicFunction2 = internalFunction2",
        "exports.publicX = internalX",
        "exports = internalObj");

    assertThat(typeRegistry.getProvidedSymbols()).containsExactly("sample.module");
    assertThat(typeRegistry.getImplicitNamespaces()).containsExactly("sample", "sample.module");
    
    Module module = typeRegistry.getModule("sample.module");
    assertThat(module.getExportedNames().keySet()).containsExactly(
        "publicFunction1", "publicFunction2", "publicX");
    assertThat(module.getExportedNames()).containsEntry("publicFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("publicFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("publicX", "internalX");
  }

  @Test
  public void buildsExportToInternalNameMap_nodeModule() {
    util.compile(fs.getPath("/modules/foo/bar.js"),
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.publicFunction1 = internalFunction1",
        "exports.publicFunction2 = internalFunction2",
        "exports.publicX = internalX",
        "module.exports = internalObj");

    assertThat(typeRegistry.getProvidedSymbols()).isEmpty();
    assertThat(typeRegistry.getImplicitNamespaces()).isEmpty();

    Module module = typeRegistry.getModule("module$$modules$foo$bar");
    assertThat(module.getPath().toString()).isEqualTo("/modules/foo/bar.js");
    assertThat(module.getType()).isEqualTo(Module.Type.NODE);
    assertThat(module.getExportedNames().keySet()).containsExactly(
        "publicFunction1", "publicFunction2", "publicX");
    assertThat(module.getExportedNames()).containsEntry("publicFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("publicFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("publicX", "internalX");
  }
}
