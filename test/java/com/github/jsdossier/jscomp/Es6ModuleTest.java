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

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;

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
  @Inject private TypeRegistry typeRegistry;
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
        "Bar", "AClass", "publicFunction1", "publicFunction2", "publicX", "x", "y");
    assertThat(module.getExportedNames()).containsEntry("Bar", "AClass");
    assertThat(module.getExportedNames()).containsEntry("AClass", "AClass");
    assertThat(module.getExportedNames()).containsEntry("publicFunction1", "internalFunction1");
    assertThat(module.getExportedNames()).containsEntry("publicFunction2", "internalFunction2");
    assertThat(module.getExportedNames()).containsEntry("publicX", "internalX");
    assertThat(module.getExportedNames()).containsEntry("x", "x");
    assertThat(module.getExportedNames()).containsEntry("y", "y");
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
    assertThat(module.getExportedNames().keySet()).containsExactly("default");
    assertThat(module.getExportedNames()).containsEntry("default", "foo");
  }

  @Test
  public void reportsACompilerErrorIfAFileThatDoesNotExistIsImported() {
    assertThat(Files.exists(fs.getPath("does_not_exist.js"))).isFalse();
    try {
      util.compile(
          createSourceFile(fs.getPath("one.js"),
              "import * as dne from './does_not_exist';"));
      fail("should fail to compile!");
    } catch (CompilerUtil.CompileFailureException expected) {
      assertThat(expected.getMessage()).contains(
          "Failed to load module \"./does_not_exist\" one.js:1");
    }
  }

  @Test
  public void reportsACompilerErrorIfImportedModuleIsNotACompilerInput() throws IOException {
    Files.createFile(fs.getPath("two.js"));
    try {
      util.compile(
          createSourceFile(fs.getPath("one.js"),
              "import * as dne from './two';"));
      fail("should fail to compile!");
    } catch (CompilerUtil.CompileFailureException expected) {
      assertThat(expected.getMessage()).contains(
          "Failed to load module \"./two\" one.js:1");
    }
  }

  @Test
  public void identifiesModulesWithAbsolutePaths() {
    util.compile(fs.getPath("/one/two.js"),
        "export default function() {}");
    assertThat(typeRegistry.isModule("module$one$two")).isTrue();
  }

  @Test
  public void identifiesModulesWithAbsolutePaths_windows() {
    FileSystem inputFs = Jimfs.newFileSystem(Configuration.windows());
    guiceRule.toBuilder()
        .setInputFs(inputFs)
        .build()
        .createInjector()
        .injectMembers(this);

    util.compile(fs.getPath("C:\\one\\two\\three.js"),
        "export default function() {}");
    assertThat(typeRegistry.isModule("module$one$two$three")).isTrue();
  }

  @Test
  public void recordsInternalDocsForExportExpressions() {
    util.compile(fs.getPath("/one/two.js"),
        "/** Class one. */ export class One {}",
        "export /** Class one (a) */ class OneA {}",
        "",
        "/** Class two. */ export const Two = class {};",
        "export /** Class two (a) */ const TwoA = class {};",
        "",
        "/** Function one. */ export function one() {}",
        "export /** Function one (a) */ function oneA() {}");

    Module module = typeRegistry.getModule("module$one$two");

    assertThat(module.getInternalVarDocs().keySet())
        .containsExactly("One", "OneA", "Two", "TwoA", "one", "oneA");

    checkInternalDocs(module, "One", "Class one.");
    checkInternalDocs(module, "OneA", "Class one (a)");

    checkInternalDocs(module, "Two", "Class two.");
    checkInternalDocs(module, "TwoA", "Class two (a)");

    checkInternalDocs(module, "one", "Function one.");
    checkInternalDocs(module, "oneA", "Function one (a)");
  }

  @Test
  public void declaredModulesThatUseAnImportStatementAreFlaggedAsEs6() {
    guiceRule.toBuilder()
        .setModulePrefix("/modules")
        .setModules("bar.js", "baz.js")
        .build()
        .createInjector()
        .injectMembers(this);

    util.compile(
        createSourceFile(
            fs.getPath("/globals/foo.js"),
            "class Foo {}"),
        createSourceFile(
            fs.getPath("/modules/bar.js"),
            "class Bar {}",
            "export default Bar;"),
        createSourceFile(
            fs.getPath("/modules/baz.js"),
            "import Bar from './bar';",
            "class Baz extends Bar {}"));

    Module bar = typeRegistry.getModule("module$modules$bar");
    assertThat(bar.getType()).isEqualTo(Module.Type.ES6);

    Module baz = typeRegistry.getModule("module$modules$baz");
    assertThat(baz.getType()).isEqualTo(Module.Type.ES6);
  }

  @Test
  public void undeclaredModulesThatUseAnImportStatementAreFlaggedAsEs6() {
    guiceRule.toBuilder()
        .setModulePrefix("/modules")
        .setModules("bar.js")
        .build()
        .createInjector()
        .injectMembers(this);

    util.compile(
        createSourceFile(
            fs.getPath("/globals/foo.js"),
            "class Foo {}"),
        createSourceFile(
            fs.getPath("/modules/bar.js"),
            "class Bar {}",
            "export default Bar;"),
        createSourceFile(
            fs.getPath("/modules/baz.js"),
            "import Bar from './bar';",
            "class Baz extends Bar {}"));

    Module bar = typeRegistry.getModule("module$modules$bar");
    assertThat(bar.getType()).isEqualTo(Module.Type.ES6);

    Module baz = typeRegistry.getModule("module$modules$baz");
    assertThat(baz.getType()).isEqualTo(Module.Type.ES6);
  }

  @Test
  public void extractsModuleDocsFromTheScriptNode() {
    util.compile(fs.getPath("/one/two.js"),
        "/** @fileoverview The file overview comment should be used for module docs. */",
        "",
        "/** Class does should not be used. */",
        "export class A {}");

    Module module = typeRegistry.getModule("module$one$two");

    assertThat(module.getJsDoc().getBlockComment())
        .isEqualTo("The file overview comment should be used for module docs.");
  }

  @Test
  public void modulesWithNoFileOverviewHaveNoDocs() {
    util.compile(fs.getPath("/one/two.js"),
        "/** Class does should not be used. */",
        "export class A {}");

    Module module = typeRegistry.getModule("module$one$two");
    assertThat(module.getJsDoc().getBlockComment()).isEmpty();
  }

  @Test
  public void modulesWithNoFileOverviewHaveNoDocs_moduleHasDefaultExportClass() {
    util.compile(fs.getPath("/one/two.js"),
        "/** @fileoverview Fileoverview should not be used when there is a default export */",
        "/** This class is the default export */",
        "export default class A {}");

    Module module = typeRegistry.getModule("module$one$two");
    assertThat(module.getJsDoc().getBlockComment()).isEqualTo("This class is the default export");
  }

  private static void checkInternalDocs(Module module, String name, String comment) {
    assertThat(module.getInternalVarDocs().get(name).getBlockDescription().trim())
        .isEqualTo(comment);
  }
}
