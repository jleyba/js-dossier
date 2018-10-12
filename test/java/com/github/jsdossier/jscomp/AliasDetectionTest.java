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
import static org.junit.Assume.assumeNoException;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import java.nio.file.FileSystem;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for tracking aliases created by the compiler. */
@RunWith(JUnit4.class)
public class AliasDetectionTest {

  @Rule public GuiceRule guice = GuiceRule.builder(this).setUseNodeLibrary(false).build();

  @Inject @Input private FileSystem inputFs;
  @Inject private TypeRegistry typeRegistry;
  @Inject private CompilerUtil util;

  @Test
  public void doesNotResolveAliasIfThereWereNoTransformations() {
    util.compile(
        inputFs.getPath("foo/bar.js"),
        "/** @constructor */ function X() {};",
        "/** @constructor */ function Y() {};");

    NominalType x = typeRegistry.getType("X");
    assertThat(typeRegistry.resolveAlias(x, "Y")).isNull();
  }

  @Test
  public void resolveAliasFromGoogScopeBlock() {
    util.compile(
        inputFs.getPath("foo/bar.js"),
        "goog.provide('foo');",
        "goog.scope(function() {",
        "  var x = foo;",
        "  x.A = class {};",
        "});");

    NominalType a = typeRegistry.getType("foo.A");
    assertThat(typeRegistry.resolveAlias(a, "x")).isEqualTo("foo");
    assertThat(typeRegistry.resolveAlias(a, "y")).isNull();
    assertThat(typeRegistry.resolveAlias(a, "foo")).isNull();
  }

  @Test
  public void resolveAliasFromGoogModule() {
    util.compile(
        inputFs.getPath("foo/bar.js"),
        "goog.module('foo');",
        "class X {}",
        "class Y extends X {}",
        "exports.Z = Y;");

    NominalType z = typeRegistry.getType("module$exports$foo.Z");
    assertThat(typeRegistry.resolveAlias(z, "X")).isEqualTo("module$contents$foo_X");
    assertThat(typeRegistry.resolveAlias(z, "Y")).isEqualTo("module$contents$foo_Y");
  }

  @Test
  public void resolveAliasFromNodeModule() {
    defineInputModules("module", "foo.js", "bar.js");
    util.compile(
        createSourceFile(inputFs.getPath("module/foo.js"), "exports.X = class {};"),
        createSourceFile(
            inputFs.getPath("module/bar.js"),
            "const f = require('./foo');",
            "class Y extends f.X {}",
            "exports.Z = Y;"));

    NominalType z = typeRegistry.getType("module$exports$module$module$bar.Z");
    assertThat(typeRegistry.resolveAlias(z, "Y")).isEqualTo("module$contents$module$module$bar_Y");
  }

  @Test
  public void es6Module_internalModuleAlias() {
    defineInputModules("module", "foo.js", "bar.js");
    try {
      util.compile(
          createSourceFile(inputFs.getPath("module/foo.js"), "class X {}", "export {X as Y};"),
          createSourceFile(
              inputFs.getPath("module/bar.js"),
              "import * as f from './foo.js';",
              "export class Y extends f.Y {}"));
    } catch (CompilerUtil.CompileFailureException e) {
      if (e.getMessage()
          .contains("Bad type annotation. Unknown type module$module$foo.Y module/bar.js:-1")) {
        assumeNoException("Compiler does not properly handle types exported under an alias", e);
      }
      throw e;
    }

    NominalType type = typeRegistry.getType("module$module$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "Y")).isEqualTo("Y$$module$module$bar");
  }

  @Test
  public void es6Module_aliasForImportedStar1() {
    try {
      util.compile(
          createSourceFile(inputFs.getPath("module/foo.js"), "class X {}", "export {X};"),
          createSourceFile(
              inputFs.getPath("module/bar.js"),
              "import * as f from './foo.js';",
              "export class Y extends f.X {}"));
    } catch (CompilerUtil.CompileFailureException e) {
      if (e.getMessage()
          .contains("Bad type annotation. Unknown type module$module$foo.X module/bar.js:-1")) {
        assumeNoException("Compiler does not properly handle types exported under an alias", e);
      }
      throw e;
    }

    NominalType type = typeRegistry.getType("module$module$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "f")).isEqualTo("module$module$foo");
  }

  @Test
  public void es6Module_aliasForImportedStar2() {
    try {
      util.compile(
          createSourceFile(inputFs.getPath("module/a/b/c.js"), "class X {}", "export {X};"),
          createSourceFile(
              inputFs.getPath("module/bar.js"),
              "import * as f from './a/b/c.js';",
              "export class Y extends f.X {}"));
    } catch (CompilerUtil.CompileFailureException e) {
      if (e.getMessage()
          .contains("Bad type annotation. Unknown type module$module$a$b$c.X module/bar.js:-1")) {
        assumeNoException("Compiler does not properly handle types exported under an alias", e);
      }
      throw e;
    }

    NominalType type = typeRegistry.getType("module$module$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "f")).isEqualTo("module$module$a$b$c");
  }

  @Test
  public void es6Module_aliasForImportedStar3() {
    try {
      util.compile(
          createSourceFile(inputFs.getPath("module/a/foo.js"), "class X {}", "export {X};"),
          createSourceFile(
              inputFs.getPath("module/a/b/c.js"),
              "import * as f from '../foo.js';",
              "export class Y extends f.X {}"));
    } catch (CompilerUtil.CompileFailureException e) {
      if (e.getMessage()
          .contains("Bad type annotation. Unknown type module$module$a$foo.X module/a/b/c.js:-1")) {
        assumeNoException("Compiler does not properly handle types exported under an alias", e);
      }
      throw e;
    }

    NominalType type = typeRegistry.getType("module$module$a$b$c.Y");
    assertThat(typeRegistry.resolveAlias(type, "f")).isEqualTo("module$module$a$foo");
  }

  @Test
  public void es6Module_aliasForImportedName() {
    util.compile(
        createSourceFile(inputFs.getPath("module/a/foo.js"), "export class X {}"),
        createSourceFile(
            inputFs.getPath("module/a/bar.js"),
            "import {X} from './foo.js';",
            "export class Y extends X {}"));

    NominalType type = typeRegistry.getType("module$module$a$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "X")).isEqualTo("module$module$a$foo.X");
  }

  @Test
  public void es6Module_aliasForMultipleImportedNames() {
    util.compile(
        createSourceFile(
            inputFs.getPath("module/a/foo.js"), "export class X {}", "export class Y {}"),
        createSourceFile(
            inputFs.getPath("module/a/bar.js"),
            "import {X, Y} from './foo.js';",
            "export class Z extends Y {}"));

    NominalType type = typeRegistry.getType("module$module$a$bar.Z");
    assertThat(typeRegistry.resolveAlias(type, "X")).isEqualTo("module$module$a$foo.X");
    assertThat(typeRegistry.resolveAlias(type, "Y")).isEqualTo("module$module$a$foo.Y");
  }

  @Test
  public void es6Module_aliasForRenamedImportedName1() {
    util.compile(
        createSourceFile(inputFs.getPath("module/a/foo.js"), "export class X {}"),
        createSourceFile(
            inputFs.getPath("module/a/bar.js"),
            "import {X as A} from './foo.js';",
            "export class Y extends A {}"));

    NominalType type = typeRegistry.getType("module$module$a$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "A")).isEqualTo("module$module$a$foo.X");
  }

  @Test
  public void es6Module_aliasForRenamedImportedName2() {
    util.compile(
        createSourceFile(
            inputFs.getPath("module/a/foo.js"), "export class X {}", "export class Y {}"),
        createSourceFile(
            inputFs.getPath("module/a/bar.js"),
            "import {X as A, Y as B} from './foo.js';",
            "export class Y extends A {}"));

    NominalType type = typeRegistry.getType("module$module$a$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "A")).isEqualTo("module$module$a$foo.X");
    assertThat(typeRegistry.resolveAlias(type, "B")).isEqualTo("module$module$a$foo.Y");
  }

  @Test
  public void es6Module_importedDefault() {
    try {
      util.compile(
          createSourceFile(inputFs.getPath("module/a/foo.js"), "export default class {}"),
          createSourceFile(
              inputFs.getPath("module/a/bar.js"),
              "import A from './foo.js';",
              "export class Y extends A {}"));
    } catch (CompilerUtil.CompileFailureException e) {
      if (e.getMessage()
          .contains(
              "Bad type annotation. Unknown type module$module$a$foo.default module/a/bar.js:-1")) {
        assumeNoException("The compiler no longer properly parses default exports :(", e);
      }
      throw e;
    }

    NominalType type = typeRegistry.getType("module$module$a$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "A")).isEqualTo("module$module$a$foo.default");
  }

  @Test
  public void es6Module_aliasForImportedDefault() {
    try {
      util.compile(
          createSourceFile(inputFs.getPath("module/a/foo.js"), "export default class {}"),
          createSourceFile(
              inputFs.getPath("module/a/bar.js"),
              "import {default as A} from './foo.js';",
              "export class Y extends A {}"));
    } catch (CompilerUtil.CompileFailureException e) {
      if (e.getMessage()
          .contains(
              "Bad type annotation. Unknown type module$module$a$foo.default module/a/bar.js:-1")) {
        assumeNoException("The compiler no longer properly parses default exports :(", e);
      }
      throw e;
    }

    NominalType type = typeRegistry.getType("module$module$a$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "A")).isEqualTo("module$module$a$foo.default");
  }

  @Test
  public void es6Module_importDefaultAndOthers() {
    try {
      util.compile(
          createSourceFile(inputFs.getPath("module/a/foo.js"), "export default class {}"),
          createSourceFile(
              inputFs.getPath("module/a/bar.js"),
              "import A, {B as C} from './foo.js';",
              "export class Y extends A {}"));
    } catch (CompilerUtil.CompileFailureException e) {
      if (e.getMessage()
          .contains(
              "Bad type annotation. Unknown type module$module$a$foo.default module/a/bar.js:-1")) {
        assumeNoException("The compiler no longer properly parses default exports :(", e);
      }
      throw e;
    }

    NominalType type = typeRegistry.getType("module$module$a$bar.Y");
    assertThat(typeRegistry.resolveAlias(type, "A")).isEqualTo("module$module$a$foo.default");
    assertThat(typeRegistry.resolveAlias(type, "C")).isEqualTo("module$module$a$foo.B");
  }

  @Test
  public void closureModule_recordsAliasedImports() {
    util.compile(
        createSourceFile(
            inputFs.getPath("one.js"),
            "goog.module('one');",
            "",
            "exports.One = class {};",
            "exports.Two = class {};",
            "exports.Three = class {};",
            "class X {}",
            "class Y {}",
            "exports.Four = {X, Y};"),
        createSourceFile(
            inputFs.getPath("two.js"),
            "goog.module('two');",
            "",
            "var a = goog.require('one');",
            "var {One, Two, Three} = goog.require('one');"));

    NominalType type = typeRegistry.getType("module$exports$two");
    assertThat(typeRegistry.resolveAlias(type, "a")).isEqualTo("module$exports$one");
    assertThat(typeRegistry.resolveAlias(type, "One")).isEqualTo("module$exports$one.One");
    assertThat(typeRegistry.resolveAlias(type, "Two")).isEqualTo("module$exports$one.Two");
    assertThat(typeRegistry.resolveAlias(type, "Three")).isEqualTo("module$exports$one.Three");
  }

  @Test
  public void closureModule_recordsContentAliases() {
    util.compile(
        createSourceFile(
            inputFs.getPath("one.js"),
            "goog.module('one');",
            "",
            "exports.One = class {};",
            "exports.Two = class {};",
            "exports.Three = class {};",
            "class X {}",
            "class Y {}",
            "exports.Four = {X, Y};"),
        createSourceFile(
            inputFs.getPath("two.js"), "goog.module('two');", "var a = goog.require('one');"));

    NominalType type = typeRegistry.getType("module$exports$two");
    assertThat(typeRegistry.resolveAlias(type, "module$contents$two_a"))
        .isEqualTo("module$exports$one");
  }

  @Test
  public void closureModule_recordsContentAliases_withLegacyNamspace() {
    util.compile(
        createSourceFile(
            inputFs.getPath("one.js"),
            "goog.module('a.b');",
            "goog.module.declareLegacyNamespace();",
            "",
            "exports.One = class {};",
            "",
            "function X() {}",
            "class Y {}",
            "var Z = function() {}"));

    NominalType type = typeRegistry.getType("a.b");
    assertThat(typeRegistry.resolveAlias(type, "X")).isEqualTo("module$contents$a$b_X");
    assertThat(typeRegistry.resolveAlias(type, "Y")).isEqualTo("module$contents$a$b_Y");
    assertThat(typeRegistry.resolveAlias(type, "Z")).isEqualTo("module$contents$a$b_Z");
  }

  @Test
  public void closureModule_recordsContentAliases_multipleLevels() {
    util.compile(
        createSourceFile(
            inputFs.getPath("/modules/one.js"), "goog.module('one');", "exports.One = class {};"),
        createSourceFile(
            inputFs.getPath("/modules/two.js"),
            "goog.module('two');",
            "const a = goog.require('one');",
            "const b = a;",
            "const c = b;",
            "const One = a.One;",
            "const Two = b.One;",
            "const Three = c.One;",
            "exports.Two = One;"));

    NominalType type = typeRegistry.getType("module$exports$two");
    assertThat(typeRegistry.resolveAlias(type, "module$contents$two_a"))
        .isEqualTo("module$exports$one");
    assertThat(typeRegistry.resolveAlias(type, "module$contents$two_b"))
        .isEqualTo("module$exports$one");
    assertThat(typeRegistry.resolveAlias(type, "module$contents$two_c"))
        .isEqualTo("module$exports$one");
    assertThat(typeRegistry.resolveAlias(type, "module$contents$two_One"))
        .isEqualTo("module$exports$one.One");
    assertThat(typeRegistry.resolveAlias(type, "module$contents$two_Two"))
        .isEqualTo("module$exports$one.One");
    assertThat(typeRegistry.resolveAlias(type, "module$contents$two_Three"))
        .isEqualTo("module$exports$one.One");
  }

  @SuppressWarnings("SameParameterValue")
  private void defineInputModules(String prefix, String... modules) {
    guice
        .toBuilder()
        .setModulePrefix(prefix)
        .setModules(modules)
        .build()
        .createInjector()
        .injectMembers(this);
  }
}
