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

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

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
 * Tests for ES6 module handling.
 */
@RunWith(JUnit4.class)
public class Es6ModuleTest {

  @Rule
  public GuiceRule guiceRule = GuiceRule.builder(this)
      .setModulePrefix("/modules")
      .setModules("foo/bar.js")
      .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
      .build();

  @Inject @Input private FileSystem fs;
  @Inject private TypeRegistry2 typeRegistry;
  @Inject private CompilerUtil util;

  @Test
  public void collectsJsDocForModuleInternalVars() {
    util.compile(fs.getPath("module.js"),
        "export default function () {};",  // Trigger ES6 identificatio.
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

    Module module = typeRegistry.getModule("module$module");
    assertThat(module.getInternalVarDocs().keySet()).containsExactly(
        "fnDeclDocs", "fnExpr", "fnExpr2", "fnExpr3",
        "varClass", "letClass", "constClass",
        "undef1", "undef2", "x", "y", "z");
  }

  @Test
  public void buildsExportToInternalNameMap() {
    util.compile(fs.getPath("module.js"),
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "export default function() {}",  // Should not be recorded.
        "export function x() {}",
        "export class y {}",
        "",
        "class AClass {}",
        "export {AClass};",
        "export {AClass as Bar}",
        "export {",
        "  internalFunction1 as publicFunction1,",
        "  internalFunction2 as publicFunction2,",
        "  internalX as publicX",
        "}");

    Module module = typeRegistry.getModule("module$module");
    assertThat(module.getExportedNames().keySet()).containsExactly(
        "Bar", "AClass", "publicFunction1", "publicFunction2", "publicX");
    assertThat(module.getExportedNames()).containsEntry("Bar", "AClass");
    assertThat(module.getExportedNames()).containsEntry("AClass", "AClass");
    assertThat(module.getExportedNames()).containsEntry("publicFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("publicFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("publicX", "internalX");
  }
  
  @Test
  public void buildsExportToInternalNameMap_wildcardExports() {
    try {
      util.compile(
          createSourceFile(fs.getPath("one.js"),
              "export class Foo {}",
              "export class Bar {}"),
          createSourceFile(fs.getPath("two.js"),
              "export * from './one';"));
      fail("Update this test");
    } catch (CompilerUtil.CompileFailureException expected) {
      if (expected.getMessage().contains(
          "ES6 transpilation of 'Wildcard export' is not yet implemented.")) {
        assumeNoException("wildcard exports not yet implemented", expected);
      }
      throw expected;
    }
  }
  
  @Test
  public void buildsExportToInternalNameMap_forwardOtherModuleExport() {
    util.compile(
        createSourceFile(fs.getPath("one.js"),
            "export class Foo {}",
            "export class Bar {}"),
        createSourceFile(fs.getPath("two.js"),
            "export {Foo} from './one';",
            "export {Foo as One, Bar as Two} from './one';",
            "export {Bar as Three, Bar} from './one';"));

    Module module = typeRegistry.getModule("module$two");
    assertThat(module.getExportedNames().keySet()).containsExactly(
        "Foo", "Bar", "One", "Two", "Three");
    assertThat(module.getExportedNames()).containsEntry("Foo", "module$one.Foo");
    assertThat(module.getExportedNames()).containsEntry("One", "module$one.Foo");
    assertThat(module.getExportedNames()).containsEntry("Two", "module$one.Bar");
    assertThat(module.getExportedNames()).containsEntry("Three", "module$one.Bar");
    assertThat(module.getExportedNames()).containsEntry("Bar", "module$one.Bar");
  }
  
  @Test
  public void buildsExportToInternalNameMap_handlesDefaultExports() {
    util.compile(
        createSourceFile(fs.getPath("one.js"),
            "function foo() {}",
            "export default foo;"));

    Module module = typeRegistry.getModule("module$one");
    assertThat(module.getExportedNames().keySet()).containsExactly("foo");
    assertThat(module.getExportedNames()).containsEntry("foo", "foo");
  }
  
  // TODO: report import of unknown module as an error.
}