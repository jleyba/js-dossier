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

package com.github.jsdossier.jscomp;

import static com.github.jsdossier.testing.DossierTruth.assertThat;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.fail;

import com.github.jsdossier.annotations.Global;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.TypedScope;
import com.google.javascript.jscomp.TypedVar;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(Enclosed.class)
public final class BuildSymbolTablePassTest {

  private abstract static class BaseTest {
    protected final FileSystem fs = Jimfs.newFileSystem();
    protected final Path stdInput = fs.getPath("/foo.js");

    protected final SymbolTable compile(String... lines) {
      return new Scenario().addFile(stdInput, lines).compile();
    }
  }

  @RunWith(JUnit4.class)
  public static final class ModuleDetectionTest extends BaseTest {

    @Test
    public void standardInputIsNotRecordedAsAModule() {
      SymbolTable table = compile("class X {}");
      assertThat(table).hasNoModules();
    }

    @Test
    public void recordsFileAsEs6OnExport() {
      SymbolTable table = compile("export class X {}");
      assertThat(table).containsExactly("module$foo", "X$$module$foo", "module$foo.X");
      assertThat(table).hasEs6Module(stdInput);
    }

    @Test
    public void multipleFiles_oneIsEs6() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "goog.provide('foo');")
              .addFile(two, "export class Bar {}")
              .compile();
      assertThat(table).doesNotHaveModule(one);
      assertThat(table).hasEs6Module(two);
    }

    @Test
    public void recordsFileAsEs6_onImport() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export default class {}")
              .addFile(two, "import one from './one.js'")
              .compile();

      assertThat(table).hasEs6Module(one);
      assertThat(table).hasEs6Module(two);
    }

    @Test
    public void recordsFileAsEs6_onImportWildcard() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export default class {}")
              .addFile(two, "import * as one from './one.js'")
              .compile();

      assertThat(table).hasEs6Module(one);
      assertThat(table).hasEs6Module(two);
    }

    @Test
    public void recordsFileAsEs6_onImportSpec() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export class Foo {}", "export class Bar {}")
              .addFile(two, "import {Foo, Bar as Baz} from './one.js'")
              .compile();

      assertThat(table).hasEs6Module(one);
      assertThat(table).hasEs6Module(two);
    }

    @Test
    public void recordsFileAsClosure_googModuleDeclaration() {
      SymbolTable table = compile("goog.module('foo.bar.baz');");
      assertThat(table).containsExactly("module$exports$foo$bar$baz");

      Module module = assertThat(table).hasGoogModule(stdInput);
      assertThat(module.getHasLegacyNamespace()).isFalse();
    }

    @Test
    public void recordsFileAsClosure_googModuleDeclarationWithLegacyNamespace() {
      SymbolTable table =
          compile("goog.module('foo.bar.baz');", "goog.module.declareLegacyNamespace();");
      assertThat(table).containsExactly("foo.bar.baz");

      Module module = assertThat(table).hasGoogModule(stdInput);
      assertThat(module.getHasLegacyNamespace()).isTrue();
    }

    @Test
    public void recordsFileAsClosure_multipleFiles() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");
      Path three = fs.getPath("three.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "goog.module('a.one');\nexports.x = 1;")
              .addFile(two, "goog.module('a.two');\nexports.x = 2;")
              .addFile(three, "goog.module('a.three');\nexports.x = 3;")
              .compile();
      assertThat(table)
          .containsExactly(
              "module$exports$a$one",
              "module$exports$a$two",
              "module$exports$a$three",
              "module$exports$a$one.x",
              "module$exports$a$two.x",
              "module$exports$a$three.x");

      Module m1 = assertThat(table).hasGoogModule(one);
      Module m2 = assertThat(table).hasGoogModule(two);
      Module m3 = assertThat(table).hasGoogModule(three);

      assertThat(table.getClosureModuleById("a.one")).isSameAs(m1);
      assertThat(table.getClosureModuleById("a.two")).isSameAs(m2);
      assertThat(table.getClosureModuleById("a.three")).isSameAs(m3);
    }

    @Test
    public void recordsFileAsClosure_googModuleLegacyDeclaration() {
      SymbolTable table =
          compile("goog.module('foo.bar.baz');", "goog.module.declareLegacyNamespace();");

      Module module = assertThat(table).hasGoogModule(stdInput);
      assertThat(module.getHasLegacyNamespace()).isTrue();
      assertThat(table).hasOwnSymbol("foo.bar.baz");
    }

    @Test
    public void recordsNodeModules() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");
      Path three = fs.getPath("three.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "var one = 1;")
              .addFile(two, "exports.two = 2;")
              .addFile(three, "exports.three = 3;")
              .setModules(two, three)
              .compile();

      assertThat(table).doesNotHaveModule(one);
      assertThat(table).hasNodeModule(two);
      assertThat(table).hasNodeModule(three);
    }

    @Test
    public void recordsNodeModules_includeNodeLibraryExterns() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");
      Path three = fs.getPath("three.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "var one = 1;")
              .addFile(two, "exports.two = 2;")
              .addFile(three, "exports.three = 3;")
              .setUseNodeLibrary(true)
              .setModules(two, three)
              .compile();

      assertThat(table).doesNotHaveModule(one);
      assertThat(table).hasNodeModule(two);
      assertThat(table).hasNodeModule(three);
    }

    @Test
    public void recordsNodeModuleAsEs6IfThereIsAnExportStatement() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");
      Path three = fs.getPath("three.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "var one = 1;")
              .addFile(two, "exports.two = 2;")
              .addFile(three, "export class X {}")
              .setModules(two, three)
              .compile();

      assertThat(table).doesNotHaveModule(one);
      assertThat(table).hasNodeModule(two);
      assertThat(table).hasEs6Module(three);
    }

    @Test
    public void recordsModuleJsDoc_es6Module() {
      SymbolTable table = compile("/** @fileoverview hello, world! */", "export class X {}");
      Module module = assertThat(table).hasEs6Module(stdInput);
      assertThat(module).hasJsdocCommentThat().isEqualTo("/** @fileoverview hello, world! */");
    }

    @Test
    public void recordsModuleJsDoc_googModule() {
      SymbolTable table =
          compile(
              "/** @fileoverview hello, world! */", "goog.module('one');", "exports.X = class {};");
      Module module = assertThat(table).hasGoogModule(stdInput);
      assertThat(module).hasJsdocCommentThat().isEqualTo("/** @fileoverview hello, world! */");
    }

    @Test
    public void recordsModuleJsDoc_nodeModule() {
      Path one = fs.getPath("one.js");
      SymbolTable table =
          new Scenario()
              .addFile(one, "/** @fileoverview ABC */\nexports.one = 1;")
              .setUseNodeLibrary(false)
              .setModules(one)
              .compile();
      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m).hasJsdocCommentThat().isEqualTo("/** @fileoverview ABC */");
    }
  }

  @RunWith(JUnit4.class)
  public static final class DeclarationsTest extends BaseTest {
    @Test
    public void basicDeclarations_globalScope() {
      SymbolTable table =
          compile(
              "class A {}",
              "function B() { return {}; }",
              "let x;",
              "var y = x;",
              "const {a, b} = B();",
              "const person = {name: {first: 'jane', last: 'doe'}};",
              "const {name} = person, {first} = person.name;",
              "const last = person.name.last;",
              "const [z] = [0];");
      assertThat(table).hasNoModules();
      assertThat(table)
          .containsExactly("A", "B", "x", "y", "a", "b", "person", "name", "first", "last", "z");

      assertThat(table).hasOwnSymbol("A").that().isNotAReference();
      assertThat(table).hasOwnSymbol("B").that().isNotAReference();
      assertThat(table).hasOwnSymbol("x").that().isNotAReference();
      assertThat(table).hasOwnSymbol("y").that().isAReferenceTo("x");
      assertThat(table).hasOwnSymbol("a").that().isNotAReference();
      assertThat(table).hasOwnSymbol("b").that().isNotAReference();
      assertThat(table).hasOwnSymbol("person").that().isNotAReference();
      assertThat(table).hasOwnSymbol("name").that().isAReferenceTo("person.name");
      assertThat(table).hasOwnSymbol("first").that().isAReferenceTo("person.name.first");
      assertThat(table).hasOwnSymbol("last").that().isAReferenceTo("person.name.last");
      assertThat(table).hasOwnSymbol("z").that().isNotAReference();
    }

    @Test
    public void basicDeclarations_inAnEs6Module() {
      SymbolTable table =
          compile(
              "export class A {}",
              "function B() { return {}; }",
              "let x;",
              "var y = x;",
              "const {a, b} = B();",
              "const person = {name: {first: 'jane', last: 'doe'}};",
              "const {name} = person, {first} = person.name;",
              "const [z] = [0];");

      assertThat(table)
          .containsExactly(
              "module$foo",
              "module$foo.A",
              "A$$module$foo",
              "B$$module$foo",
              "x$$module$foo",
              "y$$module$foo",
              "a$$module$foo",
              "b$$module$foo",
              "person$$module$foo",
              "name$$module$foo",
              "first$$module$foo",
              "z$$module$foo");
      assertThat(table).hasOwnSymbol("module$foo.A").that().isAReferenceTo("A$$module$foo");

      Module m = assertThat(table).hasEs6Module(stdInput);
      table = m.getInternalSymbolTable();
      assertThat(table)
          .containsExactly("A", "B", "x", "y", "a", "b", "person", "name", "first", "z");
      assertThat(table)
          .hasOwnSymbolsWithReferences(
              "A", "A$$module$foo",
              "B", "B$$module$foo",
              "x", "x$$module$foo",
              "y", "y$$module$foo",
              "a", "a$$module$foo",
              "b", "b$$module$foo",
              "person", "person$$module$foo",
              "name", "name$$module$foo",
              "first", "first$$module$foo",
              "z", "z$$module$foo");
    }

    @Test
    public void nameInEs6ModuleReferencesGlobalSymbol() {
      SymbolTable table = compile("export const {document} = window;");
      assertThat(table)
          .containsExactly("module$foo", "module$foo.document", "document$$module$foo");
      assertThat(table)
          .hasOwnSymbolsWithReferences(
              "module$foo.document",
              "document$$module$foo",
              "document$$module$foo",
              "window.document");

      Module m = assertThat(table).hasEs6Module(stdInput);
      table = m.getInternalSymbolTable();
      assertThat(table).hasOnlyOneSymbol("document").that().isAReferenceTo("document$$module$foo");
    }

    @Test
    public void basicDeclarations_inAGoogModule() {
      SymbolTable table = compile("goog.module('foo');\nlet x = 1; const y = 2; var z = 3;");

      assertThat(table)
          .containsExactly(
              "module$exports$foo",
              "module$contents$foo_x",
              "module$contents$foo_y",
              "module$contents$foo_z");

      Module m = assertThat(table).hasGoogModule(stdInput);
      table = m.getInternalSymbolTable();
      assertThat(table).containsExactly("x", "y", "z");
      assertThat(table).hasOwnSymbol("x").that().isAReferenceTo("module$contents$foo_x");
      assertThat(table).hasOwnSymbol("y").that().isAReferenceTo("module$contents$foo_y");
      assertThat(table).hasOwnSymbol("z").that().isAReferenceTo("module$contents$foo_z");
    }

    @Test
    public void basicDeclarations_inANodeModule() {
      SymbolTable table =
          new Scenario()
              .setModules(stdInput)
              .setUseNodeLibrary(false)
              .addFile(stdInput, "let x = 1; const y = 2; var z = 3;")
              .compile();

      assertThat(table)
          .containsExactly(
              "module$exports$module$foo",
              "module$contents$module$foo_x",
              "module$contents$module$foo_y",
              "module$contents$module$foo_z");

      Module m = assertThat(table).hasNodeModule(stdInput);
      table = m.getInternalSymbolTable();
      assertThat(table).containsExactly("x", "y", "z");
      assertThat(table).hasOwnSymbol("x").that().isAReferenceTo("module$contents$module$foo_x");
      assertThat(table).hasOwnSymbol("y").that().isAReferenceTo("module$contents$module$foo_y");
      assertThat(table).hasOwnSymbol("z").that().isAReferenceTo("module$contents$module$foo_z");
    }
  }

  @RunWith(JUnit4.class)
  public static final class JsDocTest extends BaseTest {

    private static final String DOCUMENTED_CODE =
        Stream.of(
                "",
                "function fnDeclNoDocs() {}",
                "/** Has docs. */ function fnDeclDocs() {}",
                "/** Var docs. */ var fnExpr = function() {}",
                "/** Let docs. */ let fnExpr2 = function() {}",
                "/** Const docs. */ const fnExpr3 = function() {}",
                "",
                "class clazzNoDocs {}",
                "/** Class docs. */class classWithDocs {}",
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
                "let letNoDocs;")
            .collect(joining("\n"));

    private void checkDocumentedCode(SymbolTable table) {
      assertThat(table).hasOwnSymbol("fnDeclNoDocs").that().hasNoJsDoc();
      assertThat(table).hasOwnSymbol("fnDeclDocs").that().hasJsDoc("/** Has docs. */");
      assertThat(table).hasOwnSymbol("fnExpr").that().hasJsDoc("/** Var docs. */");
      assertThat(table).hasOwnSymbol("fnExpr2").that().hasJsDoc("/** Let docs. */");
      assertThat(table).hasOwnSymbol("fnExpr3").that().hasJsDoc("/** Const docs. */");

      assertThat(table).hasOwnSymbol("clazzNoDocs").that().hasNoJsDoc();
      assertThat(table).hasOwnSymbol("classWithDocs").that().hasJsDoc("/** Class docs. */");
      assertThat(table).hasOwnSymbol("varClass").that().hasJsDoc("/** Var class. */");
      assertThat(table).hasOwnSymbol("letClass").that().hasJsDoc("/** Let class. */");
      assertThat(table).hasOwnSymbol("constClass").that().hasJsDoc("/** Const class. */");

      assertThat(table).hasOwnSymbol("undef1").that().hasJsDoc("/** Undef1. */");
      assertThat(table).hasOwnSymbol("undef2").that().hasJsDoc("/** Undef2. */");
      assertThat(table).hasOwnSymbol("x").that().hasJsDoc("/** Integer. */");
      assertThat(table).hasOwnSymbol("y").that().hasJsDoc("/** Boolean. */");
      assertThat(table).hasOwnSymbol("z").that().hasJsDoc("/** String. */");

      assertThat(table).hasOwnSymbol("varNoDocs").that().hasNoJsDoc();
      assertThat(table).hasOwnSymbol("letNoDocs").that().hasNoJsDoc();
    }

    @Test
    public void collectsJsDoc_globalScope() {
      SymbolTable table = compile(DOCUMENTED_CODE);
      checkDocumentedCode(table);
    }

    @Test
    public void collectsJsDoc_inEs6Module() {
      SymbolTable table = compile(DOCUMENTED_CODE, "export class Trigger {}");
      Module module = assertThat(table).hasEs6Module(stdInput);
      checkDocumentedCode(module.getInternalSymbolTable());
    }

    @Test
    public void collectsJsDoc_inGoogModule() {
      SymbolTable table = compile("goog.module('foo');", DOCUMENTED_CODE);
      Module module = assertThat(table).hasGoogModule(stdInput);
      checkDocumentedCode(module.getInternalSymbolTable());
    }

    @Test
    public void collectsJsDoc_inNodeModule() {
      SymbolTable table =
          new Scenario()
              .setUseNodeLibrary(false)
              .setModules(stdInput)
              .addFile(stdInput, DOCUMENTED_CODE)
              .compile();
      Module module = assertThat(table).hasNodeModule(stdInput);
      checkDocumentedCode(module.getInternalSymbolTable());
    }

    @Test
    public void collectsJsDoc_onGoogModuleExports() {
      SymbolTable table = compile("goog.module('foo'); /** Hi */ exports.Foo = class {};");
      assertThat(table).hasOwnSymbol("module$exports$foo.Foo").that().hasJsDoc("/** Hi */");
    }

    @Test
    public void collectsJsDoc_onEs6ModuleExports() {
      SymbolTable table =
          compile(
              "/** Class one. */ export class One {}",
              "export /** Class one (a) */ class OneA {}",
              "",
              "/** Class two. */ export const Two = class {};",
              "export /** Class two (a) */ const TwoA = class {};",
              "",
              "/** Function one. */ export function one() {}",
              "export /** Function one (a) */ function oneA() {}");
      assertThat(table).hasOwnSymbol("module$foo.One").that().hasJsDoc("/** Class one. */");
      assertThat(table).hasOwnSymbol("module$foo.OneA").that().hasJsDoc("/** Class one (a) */");
      assertThat(table).hasOwnSymbol("module$foo.Two").that().hasJsDoc("/** Class two. */");
      assertThat(table).hasOwnSymbol("module$foo.TwoA").that().hasJsDoc("/** Class two (a) */");
      assertThat(table).hasOwnSymbol("module$foo.one").that().hasJsDoc("/** Function one. */");
      assertThat(table).hasOwnSymbol("module$foo.oneA").that().hasJsDoc("/** Function one (a) */");
    }
  }

  @RunWith(JUnit4.class)
  public static final class Es6ModuleTest extends BaseTest {

    @Test
    public void extractsModuleDocsFromTheScriptNode() {
      SymbolTable table =
          compile(
              "/** @fileoverview Hello, world. */",
              "",
              "/** Class does should not be used. */",
              "export class A {}");
      Module module = assertThat(table).hasEs6Module(stdInput);
      assertThat(module.getJsDoc().getOriginalCommentString())
          .isEqualTo("/** @fileoverview Hello, world. */");
    }

    @Test
    public void modulesWithNoFileOverviewHaveNoDocs() {
      SymbolTable table = compile("/** Class does should not be used. */", "export class A {}");
      Module module = assertThat(table).hasEs6Module(stdInput);
      assertThat(module.getJsDoc().getBlockComment()).isEmpty();
    }

    @Test
    public void exportedClassDeclaration() {
      SymbolTable table = compile("export class X {}");
      assertThat(table).containsExactly("module$foo", "X$$module$foo", "module$foo.X");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("X");
    }

    @Test
    public void exportedFunctionDeclaration() {
      SymbolTable table = compile("export function x() {}");
      assertThat(table).containsExactly("module$foo", "x$$module$foo", "module$foo.x");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("x");
    }

    @Test
    public void exportedConstDeclaration() {
      SymbolTable table = compile("export const x = 1;");
      assertThat(table).containsExactly("module$foo", "x$$module$foo", "module$foo.x");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("x");
    }

    @Test
    public void exportedLetDeclaration() {
      SymbolTable table = compile("export let x = 1;");
      assertThat(table).containsExactly("module$foo", "x$$module$foo", "module$foo.x");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("x");
    }

    @Test
    public void exportedVarDeclaration() {
      SymbolTable table = compile("export var x = 1;");
      assertThat(table).containsExactly("module$foo", "x$$module$foo", "module$foo.x");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("x");
    }

    @Test
    public void exportedCompoundVarDeclaration() {
      SymbolTable table = compile("export var x = 1, y = 2;");
      assertThat(table)
          .containsExactly(
              "module$foo", "x$$module$foo", "module$foo.x", "y$$module$foo", "module$foo.y");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("x", "y");
    }

    @Test
    public void exportsNameAsDefault() {
      SymbolTable table = compile("const x = 1;\nexport default x;");
      assertThat(table).containsExactly("module$foo", "x$$module$foo", "module$foo.default");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("x");
    }

    @Test
    public void exportsAnonymousClassAdDefault() {
      SymbolTable table = compile("export default class {}");
      assertThat(table).containsExactly("module$foo", "module$foo.default");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).isEmpty();
    }

    @Test
    public void exportsClassDeclarationAsDefault() {
      SymbolTable table = compile("export default class X {}");
      assertThat(table).containsExactly("module$foo", "module$foo.default", "X$$module$foo");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("X");
    }

    @Test
    public void exportsAnonymousFunctionAsDefault() {
      SymbolTable table = compile("export default function() {}");
      assertThat(table).containsExactly("module$foo", "module$foo.default");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).isEmpty();
    }

    @Test
    public void exportsFunctionDeclarationAsDefault() {
      SymbolTable table = compile("export default function X() {}");
      assertThat(table).containsExactly("module$foo", "module$foo.default", "X$$module$foo");

      Module m = assertThat(table).hasEs6Module(stdInput);
      assertThat(m.getInternalSymbolTable()).containsExactly("X");
    }

    @Test
    public void exportSpec() {
      SymbolTable table =
          compile(
              "class Foo {}",
              "class Bar {}",
              "const baz = 123;",
              "export {Foo, Bar as Baz, baz as default};");
      assertThat(table)
          .containsExactly(
              "module$foo",
              "Foo$$module$foo",
              "Bar$$module$foo",
              "baz$$module$foo",
              "module$foo.Foo",
              "module$foo.Baz",
              "module$foo.default");
      assertThat(table).hasOwnSymbol("module$foo.Foo").that().isAReferenceTo("Foo$$module$foo");
      assertThat(table).hasOwnSymbol("module$foo.Baz").that().isAReferenceTo("Bar$$module$foo");
      assertThat(table).hasOwnSymbol("module$foo.default").that().isAReferenceTo("baz$$module$foo");

      Module m = assertThat(table).hasEs6Module(stdInput);
      table = m.getInternalSymbolTable();
      assertThat(table).containsExactly("Foo", "Bar", "baz");
      assertThat(table).hasOwnSymbol("Foo").that().isAReferenceTo("Foo$$module$foo");
      assertThat(table).hasOwnSymbol("Bar").that().isAReferenceTo("Bar$$module$foo");
      assertThat(table).hasOwnSymbol("baz").that().isAReferenceTo("baz$$module$foo");
    }

    @Test
    public void exportSpecShortHandObject() {
      SymbolTable table = compile("class Foo {}\nexport {Foo};");
      assertThat(table).containsExactly("module$foo", "module$foo.Foo", "Foo$$module$foo");
      assertThat(table).hasOwnSymbol("module$foo.Foo").that().isAReferenceTo("Foo$$module$foo");

      Module m = assertThat(table).hasEs6Module(stdInput);
      table = m.getInternalSymbolTable();
      assertThat(table).containsExactly("Foo");
      assertThat(table).hasOwnSymbol("Foo").that().isAReferenceTo("Foo$$module$foo");
    }

    @Test
    public void forwardExportFromAnotherModule() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export class Foo {}")
              .addFile(two, "export {Foo, Foo as Baz} from './one.js';")
              .compile();

      assertThat(table)
          .containsExactly(
              "module$one",
              "module$two",
              "Foo$$module$one",
              "module$one.Foo",
              "module$two.Foo",
              "module$two.Baz");
      assertThat(table).hasOwnSymbol("module$two.Foo").that().isAReferenceTo("module$one.Foo");
      assertThat(table).hasOwnSymbol("module$two.Baz").that().isAReferenceTo("module$one.Foo");

      Module m = assertThat(table).hasEs6Module(one);
      assertThat(m.getInternalSymbolTable()).containsExactly("Foo");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("Foo")
          .that()
          .isAReferenceTo("Foo$$module$one");

      m = assertThat(table).hasEs6Module(two);
      assertThat(m.getInternalSymbolTable()).isEmpty();
    }

    @Test
    public void importFromAnotherModule() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export default class {}")
              .addFile(two, "import one from './one.js'")
              .compile();

      assertThat(table).containsExactly("module$one", "module$two", "module$one.default");
      assertThat(table).hasEs6Module(one);

      Module m2 = assertThat(table).hasEs6Module(two);
      table = m2.getInternalSymbolTable();
      assertThat(table).hasOwnSymbol("one").that().isAReferenceTo("module$one.default");
    }

    @Test
    public void importWildcard() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export default class {}")
              .addFile(two, "import * as one from './one.js'")
              .compile();

      assertThat(table).containsExactly("module$one", "module$two", "module$one.default");
      assertThat(table).hasEs6Module(one);

      Module m2 = assertThat(table).hasEs6Module(two);
      table = m2.getInternalSymbolTable();
      assertThat(table).hasOwnSymbol("one").that().isAReferenceTo("module$one");
    }

    @Test
    public void importDefaultAndWildcard() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export default class {}")
              .addFile(two, "import a, * as b from './one.js'")
              .compile();

      assertThat(table).containsExactly("module$one", "module$two", "module$one.default");
      assertThat(table).hasEs6Module(one);

      Module m2 = assertThat(table).hasEs6Module(two);
      table = m2.getInternalSymbolTable();
      assertThat(table).hasOwnSymbol("a").that().isAReferenceTo("module$one.default");
      assertThat(table).hasOwnSymbol("b").that().isAReferenceTo("module$one");
    }

    @Test
    public void importSpec() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export class Foo {}", "export class Bar {}")
              .addFile(two, "import {Foo, Bar as Baz} from './one.js'")
              .compile();

      assertThat(table)
          .containsExactly(
              "module$one",
              "module$two",
              "Foo$$module$one",
              "Bar$$module$one",
              "module$one.Foo",
              "module$one.Bar");
      assertThat(table).hasEs6Module(one);

      Module m2 = assertThat(table).hasEs6Module(two);
      table = m2.getInternalSymbolTable();
      assertThat(table).hasOwnSymbol("Foo").that().isAReferenceTo("module$one.Foo");
      assertThat(table).hasOwnSymbol("Baz").that().isAReferenceTo("module$one.Bar");
    }

    @Test
    public void importDefaultAndSpec() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export class Foo {}", "export class Bar {}")
              .addFile(two, "import one, {Foo, Bar as Baz} from './one.js'")
              .compile();

      assertThat(table)
          .containsExactly(
              "module$one",
              "module$two",
              "Foo$$module$one",
              "Bar$$module$one",
              "module$one.Foo",
              "module$one.Bar");
      assertThat(table).hasEs6Module(one);

      Module m2 = assertThat(table).hasEs6Module(two);
      table = m2.getInternalSymbolTable();
      assertThat(table).hasOwnSymbol("one").that().isAReferenceTo("module$one.default");
      assertThat(table).hasOwnSymbol("Foo").that().isAReferenceTo("module$one.Foo");
      assertThat(table).hasOwnSymbol("Baz").that().isAReferenceTo("module$one.Bar");
    }

    @Test
    public void ignoresImportModuleName() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export default class {}")
              // Imported for side effects; no symbols recorded.
              .addFile(two, "import './one.js'")
              .compile();
      assertThat(table).hasEs6Module(one);
      assertThat(table).hasEs6Module(two);

      Module module = assertThat(table).hasEs6Module(two);
      assertThat(module.getInternalSymbolTable()).isEmpty();
    }

    @Test
    public void exportWildcardFromAnotherModule() {
      Path pone = fs.getPath("one.js");
      Path ptwo = fs.getPath("two.js");
      Scenario scenario =
          new Scenario()
              .addFile(pone, "export class Foo {}")
              .addFile(ptwo, "export * from './one';");
      try {
        scenario.compile();
        fail("test needs to be updated!");
      } catch (RuntimeException expected) {
        assertThat(expected).hasMessageThat().contains("INTERNAL COMPILER ERROR");
        assertThat(expected).hasMessageThat()
            .contains("Type inference doesn't know to handle token EXPORT");
      }
    }

    @Test
    public void worksWithWindowsAbsolutePaths() {
      FileSystem fs = Jimfs.newFileSystem(Configuration.windows());
      Path one = fs.getPath("C:\\foo\\bar\\one.js");
      Path two = fs.getPath("C:\\otherDir\\two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export class Foo {}")
              .addFile(two, "export {Foo} from '../foo/bar/one.js';")
              .compile();

      Module module = assertThat(table).hasEs6Module(two);
      assertThat(module.getInternalSymbolTable()).isEmpty();
      assertThat(table)
          .hasOwnSymbol("module$C_$otherDir$two.Foo")
          .that()
          .isAReferenceTo("module$C_$foo$bar$one.Foo");
    }

    @Test
    public void worksWithWindowsAbsolutePaths_pathHasASpace() {
      FileSystem fs = Jimfs.newFileSystem(Configuration.windows());
      Path one = fs.getPath("C:\\foo bar\\one.js");
      Path two = fs.getPath("C:\\otherDir\\two.js");

      SymbolTable table =
          new Scenario()
              .addFile(one, "export class Foo {}")
              .addFile(two, "export {Foo} from '../foo bar/one.js';")
              .compile();

      Module module = assertThat(table).hasEs6Module(two);
      assertThat(module.getInternalSymbolTable()).isEmpty();
      assertThat(table)
          .hasOwnSymbol("module$C_$otherDir$two.Foo")
          .that()
          .isAReferenceTo("module$C_$foo_bar$one.Foo");
    }

    @Test
    public void buildsExportToInternalNameMap() {
      SymbolTable table =
          compile(
              "",
              "function internalFunction1() {}",
              "var internalFunction2 = function() {}",
              "var internalX = 1234;",
              "var internalObj = {};",
              "",
              "export default function() {}", // Should not be recorded.
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

      Module module = assertThat(table).hasEs6Module(stdInput);
      assertThat(module.getExportedNames().keySet())
          .containsExactly(
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
    public void recordsTypedefs() {
      SymbolTable table = compile("/** @typedef {string} */ var Name; export class Foo {}");
      assertThat(table).hasOwnSymbol("Name$$module$foo").that().isNotAReference();

      table = assertThat(table).hasEs6Module(stdInput).getInternalSymbolTable();
      assertThat(table).hasOwnSymbol("Name").that().isAReferenceTo("Name$$module$foo");
    }
  }

  @RunWith(JUnit4.class)
  public static final class GoogModuleTest extends BaseTest {
    @Test
    public void exportsExprResult_rhsNotAName() {
      SymbolTable table = compile("goog.module('foo.bar.baz');", "exports.Foo = class {};");

      assertThat(table)
          .containsExactly("module$exports$foo$bar$baz", "module$exports$foo$bar$baz.Foo");
      assertThat(table).hasOwnSymbol("module$exports$foo$bar$baz.Foo").that().isNotAReference();

      Module m = assertThat(table).hasGoogModule(stdInput);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$foo$bar$baz.Foo");
    }

    @Test
    public void exportsExprResult_rhsASimpleName() {
      SymbolTable table = compile("goog.module('foo.bar.baz');", "exports.Foo = window;");

      assertThat(table)
          .hasOwnSymbol("module$exports$foo$bar$baz.Foo")
          .that()
          .isAReferenceTo("window");

      Module m = assertThat(table).hasGoogModule(stdInput);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$foo$bar$baz.Foo");
    }

    @Test
    public void exportsExprResult_rhsAGetProp() {
      SymbolTable table = compile("goog.module('foo.bar.baz');", "exports.Foo = window.document;");

      assertThat(table)
          .hasOwnSymbol("module$exports$foo$bar$baz.Foo")
          .that()
          .isAReferenceTo("window.document");

      Module m = assertThat(table).hasGoogModule(stdInput);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$foo$bar$baz.Foo");
    }

    @Test
    public void exportsIsAName() {
      SymbolTable table = compile("goog.module('foo.bar.baz');\nexports = window;");

      Module m = assertThat(table).hasGoogModule(stdInput);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports")
          .that()
          .isAReferenceTo("module$exports$foo$bar$baz");

      assertThat(table)
          .hasOnlyOneSymbol("module$exports$foo$bar$baz")
          .that()
          .isAReferenceTo("window");
    }

    @Test
    public void importAnotherModule() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");
      SymbolTable table =
          new Scenario()
              .addFile(one, "goog.module('one');", "exports.Foo = class {};")
              .addFile(
                  two,
                  "goog.module('two');",
                  "const one = goog.require('one');",
                  "exports.two = 1;")
              .compile();
      assertThat(table).hasOwnSymbol("module$exports$one.Foo").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$two.two").that().isNotAReference();
      assertThat(table)
          .hasOwnSymbol("module$contents$two_one")
          .that()
          .isAReferenceTo("module$exports$one");

      Module m2 = assertThat(table).hasGoogModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("one")
          .that()
          .isAReferenceTo("module$contents$two_one");
    }

    @Test
    public void importSymbolFromAnotherModule() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");
      SymbolTable table =
          new Scenario()
              .addFile(one, "goog.module('one');", "exports.Foo = class {};")
              .addFile(
                  two,
                  "goog.module('two');",
                  "const {Foo} = goog.require('one');",
                  "exports.foo = Foo;")
              .compile();
      assertThat(table).hasOwnSymbol("module$exports$one.Foo").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$two.foo").that().isAReferenceTo("Foo");
      assertThat(table)
          .hasOwnSymbol("module$contents$two_Foo")
          .that()
          .isAReferenceTo("module$exports$one.Foo");

      Module m2 = assertThat(table).hasGoogModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("Foo")
          .that()
          .isAReferenceTo("module$contents$two_Foo");
    }

    @Test
    public void exportsAnotherModule() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");
      SymbolTable table =
          new Scenario()
              .addFile(one, "goog.module('one');", "exports.Foo = class {};")
              .addFile(
                  two, "goog.module('two');", "const one = goog.require('one');", "exports = one;")
              .compile();
      assertThat(table).hasOwnSymbol("module$exports$one.Foo").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$two").that().isAReferenceTo("one");
      assertThat(table)
          .hasOwnSymbol("module$contents$two_one")
          .that()
          .isAReferenceTo("module$exports$one");

      Module m2 = assertThat(table).hasGoogModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("one")
          .that()
          .isAReferenceTo("module$contents$two_one");
    }

    @Test
    public void exportsAnotherModule_asAProperty() {
      Path one = fs.getPath("one.js");
      Path two = fs.getPath("two.js");
      SymbolTable table =
          new Scenario()
              .addFile(one, "goog.module('one');", "exports.Foo = class {};")
              .addFile(
                  two,
                  "goog.module('two');",
                  "const one = goog.require('one');",
                  "exports.two = one;")
              .compile();
      assertThat(table).hasOwnSymbol("module$exports$one.Foo").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$two.two").that().isAReferenceTo("one");
      assertThat(table)
          .hasOwnSymbol("module$contents$two_one")
          .that()
          .isAReferenceTo("module$exports$one");

      Module m2 = assertThat(table).hasGoogModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("one")
          .that()
          .isAReferenceTo("module$contents$two_one");
    }

    @Test
    public void buildsExportToInternalNameMap() {
      SymbolTable table =
          compile(
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
              "exports.constVal = 123;");

      Module module = assertThat(table).hasGoogModule(stdInput);

      assertThat(module.getExportedNames())
          .containsExactlyEntriesIn(
              ImmutableMap.of(
                  "publicFunction1", "internalFunction1",
                  "publicFunction2", "internalFunction2",
                  "publicX", "internalX"));
    }

    @Test
    public void exportsObjectLiteral() {
      SymbolTable table =
          compile(
              "goog.module('foo');",
              "class Person {}",
              "class HappyPerson extends Person {}",
              "Person.Job = class {};",
              "exports = {Person, HappyPerson, Job: Person.Job};");

      assertThat(table)
          .containsExactly(
              "module$exports$foo",
              "module$exports$foo.Person",
              "module$contents$foo_Person",
              "module$contents$foo_Person.Job",
              "module$exports$foo.HappyPerson",
              "module$contents$foo_HappyPerson",
              "module$exports$foo.Job");

      Module m = assertThat(table).hasGoogModule(stdInput);
      assertThat(m.getInternalSymbolTable())
          .containsExactly("exports", "Person", "Person.Job", "HappyPerson");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("exports")
          .that()
          .isAReferenceTo("module$exports$foo");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("Person")
          .that()
          .isAReferenceTo("module$contents$foo_Person");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("Person.Job")
          .that()
          .isAReferenceTo("module$contents$foo_Person.Job");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("HappyPerson")
          .that()
          .isAReferenceTo("module$contents$foo_HappyPerson");
    }

    @Test
    public void recordsTypedefs() {
      SymbolTable table =
          compile("goog.module('foo'); /** @typedef {string} */ var Name; exports.A = class {};");
      assertThat(table).hasOwnSymbol("module$contents$foo_Name").that().isNotAReference();

      table = assertThat(table).hasGoogModule(stdInput).getInternalSymbolTable();
      assertThat(table).hasOwnSymbol("Name").that().isAReferenceTo("module$contents$foo_Name");
    }
  }

  @RunWith(JUnit4.class)
  public static final class NodeModuleTest extends BaseTest {

    private final Path one = stdInput.resolveSibling("one.js");
    private final Path two = stdInput.resolveSibling("two.js");
    private final Scenario scenario = new Scenario().setModules(one, two).setUseNodeLibrary(false);

    @Test
    public void recordsTypedefs() {
      SymbolTable table =
          scenario
              .addFile(one, "/** @typedef {string} */ var Name; exports.A = class {};")
              .compile();
      assertThat(table).hasOwnSymbol("module$contents$module$one_Name").that().isNotAReference();

      table = assertThat(table).hasNodeModule(one).getInternalSymbolTable();
      assertThat(table)
          .hasOwnSymbol("Name")
          .that()
          .isAReferenceTo("module$contents$module$one_Name");
    }

    @Test
    public void exportsExprResult_rhsNotAName() {
      SymbolTable table = scenario.addFile(one, "exports.Foo = class {};").compile();

      assertThat(table)
          .containsExactly("module$exports$module$one", "module$exports$module$one.Foo");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$module$one.Foo");
    }

    @Test
    public void exportsExprResult_rhsASimpleName() {
      SymbolTable table =
          scenario.addFile(one, "exports.Foo = process;").setUseNodeLibrary(true).compile();

      assertThat(table)
          .hasOwnSymbol("module$exports$module$one.Foo")
          .that()
          .isAReferenceTo("process");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$module$one.Foo");
    }

    @Test
    public void exportsExprResult_rhsAGetProp() {
      SymbolTable table =
          scenario
              .addFile(one, "exports.Foo = process.platform;")
              .setUseNodeLibrary(true)
              .compile();

      assertThat(table)
          .hasOwnSymbol("module$exports$module$one.Foo")
          .that()
          .isAReferenceTo("process.platform");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$module$one.Foo");
    }

    @Test
    public void exportsIsAName() {
      SymbolTable table =
          scenario.addFile(one, "exports = process;").setUseNodeLibrary(true).compile();

      assertThat(table).hasOwnSymbol("module$exports$module$one").that().isAReferenceTo("process");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports")
          .that()
          .isAReferenceTo("module$exports$module$one");
    }

    @Test
    public void exportsIsAnEmptyObjectLiteral() {
      SymbolTable table = scenario.addFile(one, "exports = {};").compile();
      assertThat(table).containsExactly("module$exports$module$one");
      assertThat(table).hasOwnSymbol("module$exports$module$one").that().isNotAReference();

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports")
          .that()
          .isAReferenceTo("module$exports$module$one");
    }

    @Test
    public void exportsIsAnObjectLiteralWithPrimitiveProperties() {
      SymbolTable table = scenario.addFile(one, "exports = {a: 1};").compile();
      assertThat(table).containsExactly("module$exports$module$one", "module$exports$module$one.a");

      assertThat(table).hasOwnSymbol("module$exports$module$one").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$one.a").that().isNotAReference();

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable()).containsExactly("exports");
      assertThat(m.getExportedNames()).isEmpty();
    }

    @Test
    public void exportsObjectLiteralWithPropsFromFunctionCall() {
      SymbolTable table =
          scenario
              .addFile(
                  one,
                  "function create() { return {b:1}; }",
                  "exports = {a: create(), b: create().b};")
              .compile();
      assertThat(table)
          .containsExactly(
              "module$contents$module$one_create",
              "module$exports$module$one",
              "module$exports$module$one.a",
              "module$exports$module$one.b");

      assertThat(table).hasOwnSymbol("module$exports$module$one").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$one.a").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$one.b").that().isNotAReference();

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable()).containsExactly("create", "exports");
      assertThat(m.getExportedNames()).isEmpty();
    }

    @Test
    public void exportsObjectLiteralWithNameReference() {
      SymbolTable table = scenario.addFile(one, "class Person {}", "exports = {Person}").compile();

      assertThat(table)
          .containsExactly(
              "module$contents$module$one_Person",
              "module$exports$module$one",
              "module$exports$module$one.Person");
      assertThat(table).hasOwnSymbol("module$contents$module$one_Person").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$one").that().isNotAReference();
      assertThat(table)
          .hasOwnSymbol("module$exports$module$one.Person")
          .that()
          .isAReferenceTo("module$contents$module$one_Person");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable()).containsExactly("Person", "exports");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("exports")
          .that()
          .isAReferenceTo("module$exports$module$one");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("Person")
          .that()
          .isAReferenceTo("module$contents$module$one_Person");
    }

    @Test
    public void exportsObjectLiteralWithPropertyReference() {
      SymbolTable table =
          scenario
              .addFile(
                  one, "class Person {}", "Person.Job = class {};", "exports = {Job: Person.Job}")
              .compile();

      assertThat(table)
          .containsExactly(
              "module$contents$module$one_Person",
              "module$contents$module$one_Person.Job",
              "module$exports$module$one",
              "module$exports$module$one.Job");
      assertThat(table).hasOwnSymbol("module$contents$module$one_Person").that().isNotAReference();
      assertThat(table)
          .hasOwnSymbol("module$contents$module$one_Person.Job")
          .that()
          .isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$one").that().isNotAReference();
      assertThat(table)
          .hasOwnSymbol("module$exports$module$one.Job")
          .that()
          .isAReferenceTo("module$contents$module$one_Person.Job");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable()).containsExactly("Person", "Person.Job", "exports");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("exports")
          .that()
          .isAReferenceTo("module$exports$module$one");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("Person")
          .that()
          .isAReferenceTo("module$contents$module$one_Person");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("Person.Job")
          .that()
          .isAReferenceTo("module$contents$module$one_Person.Job");
    }

    @Test
    public void importAnotherModule() {
      SymbolTable table =
          scenario
              .addFile(one, "exports.Foo = class {};")
              .addFile(two, "const one = require('./one');\nexports.two = 1;")
              .compile();

      assertThat(table).hasOwnSymbol("module$exports$module$one.Foo").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$two.two").that().isNotAReference();
      assertThat(table)
          .hasOwnSymbol("module$contents$module$two_one")
          .that()
          .isAReferenceTo("module$exports$module$one");

      Module m2 = assertThat(table).hasNodeModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("one")
          .that()
          .isAReferenceTo("module$contents$module$two_one");
    }

    @Test
    public void importABuiltInModule() {
      SymbolTable table =
          scenario
              .addFile(two, "const fs = require('fs');\nexports.two = 1;")
              .setUseNodeLibrary(true)
              .compile();

      assertThat(table).hasOwnSymbol("module$exports$module$two.two").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$contents$module$two_fs").that().isAReferenceTo("fs");

      Module m2 = assertThat(table).hasNodeModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("fs")
          .that()
          .isAReferenceTo("module$contents$module$two_fs");
    }

    @Test
    public void importSymbolFromAnotherModule() {
      SymbolTable table =
          scenario
              .addFile(one, "exports.Foo = class {};")
              .addFile(two, "const {Foo} = require('./one');\nexports.foo = Foo;")
              .compile();

      assertThat(table).hasOwnSymbol("module$exports$module$one.Foo").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$two.foo").that().isAReferenceTo("Foo");
      assertThat(table)
          .hasOwnSymbol("module$contents$module$two_Foo")
          .that()
          .isAReferenceTo("module$exports$module$one.Foo");

      Module m2 = assertThat(table).hasNodeModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("Foo")
          .that()
          .isAReferenceTo("module$contents$module$two_Foo");
    }

    @Test
    public void exportsAnotherModule() {
      SymbolTable table =
          scenario
              .addFile(one, "exports.Foo = class {};")
              .addFile(two, "const one = require('./one');\nexports = one;")
              .compile();
      assertThat(table).hasOwnSymbol("module$exports$module$one.Foo").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$two").that().isAReferenceTo("one");
      assertThat(table)
          .hasOwnSymbol("module$contents$module$two_one")
          .that()
          .isAReferenceTo("module$exports$module$one");

      Module m2 = assertThat(table).hasNodeModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("one")
          .that()
          .isAReferenceTo("module$contents$module$two_one");
    }

    @Test
    public void exportsAnotherModule_asAProperty() {
      SymbolTable table =
          scenario
              .addFile(one, "exports.Foo = class {};")
              .addFile(two, "const one = require('./one');\nexports.two = one;")
              .compile();
      assertThat(table).hasOwnSymbol("module$exports$module$one.Foo").that().isNotAReference();
      assertThat(table).hasOwnSymbol("module$exports$module$two.two").that().isAReferenceTo("one");
      assertThat(table)
          .hasOwnSymbol("module$contents$module$two_one")
          .that()
          .isAReferenceTo("module$exports$module$one");

      Module m2 = assertThat(table).hasNodeModule(two);
      assertThat(m2.getInternalSymbolTable())
          .hasOwnSymbol("one")
          .that()
          .isAReferenceTo("module$contents$module$two_one");
    }

    @Test
    public void moduleExportsExprResult_rhsNotAName() {
      SymbolTable table = scenario.addFile(one, "module.exports.Foo = class {};").compile();

      assertThat(table)
          .containsExactly("module$exports$module$one", "module$exports$module$one.Foo");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$module$one.Foo");
    }

    @Test
    public void moduleExportsExprResult_rhsASimpleName() {
      SymbolTable table =
          scenario.addFile(one, "module.exports.Foo = process;").setUseNodeLibrary(true).compile();

      assertThat(table)
          .hasOwnSymbol("module$exports$module$one.Foo")
          .that()
          .isAReferenceTo("process");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$module$one.Foo");
    }

    @Test
    public void moduleExportsExprResult_rhsAGetProp() {
      SymbolTable table =
          scenario
              .addFile(one, "module.exports.Foo = process.platform;")
              .setUseNodeLibrary(true)
              .compile();

      assertThat(table)
          .hasOwnSymbol("module$exports$module$one.Foo")
          .that()
          .isAReferenceTo("process.platform");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports.Foo")
          .that()
          .isAReferenceTo("module$exports$module$one.Foo");
    }

    @Test
    public void moduleExportsIsAName() {
      SymbolTable table =
          scenario.addFile(one, "module.exports = process;").setUseNodeLibrary(true).compile();

      assertThat(table).hasOwnSymbol("module$exports$module$one").that().isAReferenceTo("process");

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOnlyOneSymbol("exports")
          .that()
          .isAReferenceTo("module$exports$module$one");
    }

    @Test
    public void moduleExportsAssignedToAShortHandObject() {
      SymbolTable table =
          scenario
              .addFile(one, "class One {}", "module.exports = {One};")
              .setUseNodeLibrary(true)
              .compile();

      assertThat(table).hasOwnSymbol("module$exports$module$one").that().isNotAReference();

      Module m = assertThat(table).hasNodeModule(one);
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("One")
          .that()
          .isAReferenceTo("module$contents$module$one_One");
      assertThat(m.getInternalSymbolTable())
          .hasOwnSymbol("exports")
          .that()
          .isAReferenceTo("module$exports$module$one");
    }
  }

  @RunWith(JUnit4.class)
  public static final class GoogProvideTest extends BaseTest {
    @Test
    public void googProvide() {
      SymbolTable table =
          compile("goog.provide('foo.bar.baz');\nfoo.bar.baz.quot = function() {};");
      assertThat(table).containsExactly("foo.bar.baz", "foo.bar.baz.quot");
    }

    @Test
    public void googScope() {
      Scenario scenario = new Scenario();
      SymbolTable table =
          scenario
              .addFile(
                  stdInput,
                  "goog.provide('foo.bar.baz');",
                  "goog.scope(function() {",
                  "const fbb = foo.bar.baz;",
                  "fbb.quot = function() {};",
                  "fbb.quot.quux = 123;",
                  "fbb.A = class X {};",
                  "foo.bar.baz.B = class Y {};",
                  "});",
                  "foo.bar.baz.end = 1;")
              .compile();

      assertThat(table)
          .containsExactly(
              "foo.bar.baz",
              "foo.bar.baz.quot",
              "foo.bar.baz.quot.quux",
              "foo.bar.baz.A",
              "foo.bar.baz.B",
              "foo.bar.baz.end");

      // Need to find the node for fbb.quot so we can find the symbol table for the block.
      TypedScope scope = scenario.getCompiler().getTopScope();
      TypedVar var = scope.getVar("foo.bar.baz.quot");
      SymbolTable varTable = table.findTableFor(var.getNode());

      assertThat(varTable).isNotSameAs(table);
      assertThat(varTable.getParentScope()).isSameAs(table);
      assertThat(varTable).containsExactly("fbb", "fbb.quot", "fbb.quot.quux", "fbb.A");
      assertThat(varTable).hasOwnSymbol("fbb").that().isAReferenceTo("foo.bar.baz");
      assertThat(varTable).hasOwnSymbol("fbb.quot").that().isAReferenceTo("foo.bar.baz.quot");
      assertThat(varTable)
          .hasOwnSymbol("fbb.quot.quux")
          .that()
          .isAReferenceTo("foo.bar.baz.quot.quux");
      assertThat(varTable).hasOwnSymbol("fbb.A").that().isAReferenceTo("foo.bar.baz.A");
    }

    @Test
    public void collectsAllProvides() {
      SymbolTable table =
          compile(
              "goog.provide('Foo');",
              "goog.provide('foo.Bar');",
              "goog.provide('foo.bar.Baz');",
              "goog.provide('one.two.three.Four');");
      assertThat(table).containsExactly("Foo", "foo.Bar", "foo.bar.Baz", "one.two.three.Four");
    }

    @Test
    public void recordsAliases() {
      SymbolTable table = compile("goog.provide('a'); a.b = {}; a.c = a.b;");
      assertThat(table).containsExactly("a", "a.b", "a.c");
      assertThat(table).hasOwnSymbol("a").that().isNotAReference();
      assertThat(table).hasOwnSymbol("a.b").that().isNotAReference();
      assertThat(table).hasOwnSymbol("a.c").that().isAReferenceTo("a.b");
    }

    @Test
    public void recordsAliasesWhenLhsWasProvided() {
      SymbolTable table =
          compile(
              "goog.provide('a');",
              "goog.provide('a.b');",
              "goog.provide('a.c');",
              "a.b = {}; a.c = a.b;");
      assertThat(table).containsExactly("a", "a.b", "a.c");
      assertThat(table).hasOwnSymbol("a").that().isNotAReference();
      assertThat(table).hasOwnSymbol("a.b").that().isNotAReference();
      assertThat(table).hasOwnSymbol("a.c").that().isAReferenceTo("a.b");
    }

    @Test
    public void recordsTypedefs() {
      SymbolTable table = compile("goog.provide('foo'); /** @typedef {string} */ foo.Name;");
      assertThat(table).containsExactly("foo", "foo.Name");
    }
  }

  private static final class Scenario {
    private final List<SourceFile> files = new ArrayList<>();
    private final List<Path> modules = new ArrayList<>();
    private CompilerUtil util;
    private boolean useNodeLibrary;
    private FileSystem fs;

    public Scenario addFile(Path path, String... lines) {
      if (files.isEmpty()) {
        fs = path.getFileSystem();
      } else {
        assertThat(fs).isSameAs(path.getFileSystem());
      }
      files.add(CompilerUtil.createSourceFile(path, lines));
      return this;
    }

    public Scenario setModules(Path... paths) {
      modules.addAll(Arrays.asList(paths));
      return this;
    }

    public Scenario setUseNodeLibrary(boolean useLibrary) {
      this.useNodeLibrary = useLibrary;
      return this;
    }

    public DossierCompiler getCompiler() {
      checkState(util != null, "have not compiled in this test yet");
      return util.getCompiler();
    }

    public SymbolTable compile() {
      checkState(!files.isEmpty());
      checkState(util == null, "already compiled in this test");
      Injector injector =
          GuiceRule.builder(new Object())
              .setInputFs(fs)
              .setUseNodeLibrary(useNodeLibrary)
              .setModules(ImmutableSet.copyOf(modules))
              .build()
              .createInjector();
      util = injector.getInstance(CompilerUtil.class);
      util.compile(ImmutableList.of(), files);
      return injector.getInstance(new Key<SymbolTable>(Global.class) {});
    }
  }
}
