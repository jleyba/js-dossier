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

import static com.github.jsdossier.jscomp.NodeModulePass.resolveModuleTypeReference;
import static com.github.jsdossier.jscomp.Types.getModuleId;
import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.inject.Injector;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link NodeModulePass}. */
@RunWith(JUnit4.class)
public class NodeModulePassTest {

  private final FileSystem fs = Jimfs.newFileSystem();

  @Inject TypeRegistry typeRegistry;

  @Test
  public void doesNotModifySourceIfFileIsNotACommonJsModule() {
    CompilerUtil compiler = createCompiler();

    compiler.compile(path("foo/bar.js"), "var x = 123;");
    assertEquals("var x = 123;", compiler.toSource().trim());
  }

  @Test
  public void setsUpCommonJsModulePrimitives_emptyModule() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "");
    assertThat(compiler.toSource()).contains("var module$exports$module$foo$bar = {};");
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void setsUpCommonJsModulePrimitives_useStrictModule() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "'use strict';");
    assertThat(compiler.toSource()).contains("var module$exports$module$foo$bar = {};");
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void setsUpCommonJsModulePrimitives_hasExportsReference() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "exports.x = 123;");
    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$foo$bar = {};",
                "module$exports$module$foo$bar.x = 123;"));
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void hasExportsReferenceAndAnotherScriptDefinesExportsInTheGlobalScope() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        createSourceFile(path("base.js"), "var exports = {};"),
        createSourceFile(path("foo/bar.js"), "exports.x = 123;"));
    assertThat(compiler.toSource())
        .contains(
            lines(
                "var exports = {};",
                "var module$exports$module$foo$bar = {};",
                "module$exports$module$foo$bar.x = 123;"));
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void moduleRebindsExportsVariable() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "exports = 123;");
    assertThat(compiler.toSource()).contains("var module$exports$module$foo$bar = 123;");
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void renamesModuleGlobalVars() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "var x = 123;");
    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar = {};",
                "var module$contents$module$foo$bar_x = 123;"));
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void doesNotRenameNonGlobalVars() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "function x() { var x = 123; }");
    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar = {};",
                "function module$contents$module$foo$bar_x() {",
                "  var x = 123;",
                "}"));
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void renamesModuleGlobalFunctionDeclarations() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "function foo(){}");
    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar = {};",
                "function module$contents$module$foo$bar_foo() {",
                "}"));
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void renamesModuleGlobalFunctionExpressions() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "var foo = function(){}");
    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar = {};",
                "var module$contents$module$foo$bar_foo = function() {",
                "};"));
    assertIsNodeModule("module$exports$module$foo$bar", "foo/bar.js");
  }

  @Test
  public void sortsSingleModuleDep() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    SourceFile root = createSourceFile(path("foo/root.js"), "");
    SourceFile leaf = createSourceFile(path("foo/leaf.js"), "require('./root');");

    compiler.compile(leaf, root); // Should reorder since leaf depends on root.

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$root = {};",
                "var module$exports$module$foo$leaf = {};"));
    assertIsNodeModule("module$exports$module$foo$leaf", "foo/leaf.js");
    assertIsNodeModule("module$exports$module$foo$root", "foo/root.js");
  }

  @Test
  public void sortsWithTwoModuleDeps() {
    CompilerUtil compiler =
        createCompiler(path("foo/one.js"), path("foo/two.js"), path("foo/three.js"));

    SourceFile one = createSourceFile(path("foo/one.js"), "");
    SourceFile two =
        createSourceFile(path("foo/two.js"), "require('./one');", "require('./three');");
    SourceFile three = createSourceFile(path("foo/three.js"));

    compiler.compile(two, one, three); // Should properly reorder inputs.

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$one = {};",
                "var module$exports$module$foo$three = {};",
                "var module$exports$module$foo$two = {};"));
    assertIsNodeModule("module$exports$module$foo$one", "foo/one.js");
    assertIsNodeModule("module$exports$module$foo$two", "foo/two.js");
    assertIsNodeModule("module$exports$module$foo$three", "foo/three.js");
  }

  @Test
  public void rewritesRequireStatementToDirectlyReferenceExportsObject() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    compiler.compile(
        createSourceFile(path("foo/root.js"), "exports.bar = function(value) {};"),
        createSourceFile(
            path("foo/leaf.js"),
            "var foo = require('./root');",
            "var bar = require('./root').bar;",
            "foo.bar(foo);"));

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$root = {};",
                "module$exports$module$foo$root.bar = function(value) {",
                "};",
                "var module$exports$module$foo$leaf = {};",
                "var module$contents$module$foo$leaf_bar = module$exports$module$foo$root.bar;",
                "module$exports$module$foo$root.bar(module$exports$module$foo$root);"));
    assertIsNodeModule("module$exports$module$foo$leaf", "foo/leaf.js");
    assertIsNodeModule("module$exports$module$foo$root", "foo/root.js");
  }

  @Test
  public void rewritesRequireStatementForDirectoryIndex1() {
    CompilerUtil compiler = createCompiler(path("foo/bar/index.js"), path("foo/main.js"));

    compiler.compile(
        createSourceFile(path("foo/bar/index.js"), "exports.a = 123;"),
        createSourceFile(
            path("foo/main.js"), "var bar = require('./bar');", "exports.b = bar.a * 2;"));

    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$foo$bar$index = {};",
                "module$exports$module$foo$bar$index.a = 123;",
                "var module$exports$module$foo$main = {};",
                "module$exports$module$foo$main.b = module$exports$module$foo$bar$index.a * 2;"));
  }

  @Test
  public void rewritesRequireStatementForDirectoryIndex2() throws IOException {
    createDirectories(path("foo"));
    createFile(path("foo/bar.js"));
    CompilerUtil compiler =
        createCompiler(path("foo/bar/index.js"), path("foo/bar.js"), path("foo/main.js"));

    compiler.compile(
        createSourceFile(path("foo/bar/index.js"), "exports.a = 123;"),
        createSourceFile(path("foo/bar.js"), "exports.b = 456;"),
        createSourceFile(
            path("foo/main.js"),
            "var bar1 = require('./bar');",
            "var bar2 = require('./bar/');",
            "exports.c = bar1.b * bar2.a;"));

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar$index = {};",
                "module$exports$module$foo$bar$index.a = 123;",
                "var module$exports$module$foo$bar = {};",
                "module$exports$module$foo$bar.b = 456;",
                "var module$exports$module$foo$main = {};",
                "module$exports$module$foo$main.c ="
                    + " module$exports$module$foo$bar.b * module$exports$module$foo$bar$index.a;"));
  }

  @Test
  public void handlesRequireNodeCoreModule() {
    CompilerUtil compiler = createCompiler(path("foo/module.js"));

    compiler.compile(
        createSourceFile(path("foo/module.js"), "var p = require('path');", "p.join('a', 'b');"));

    assertThat(compiler.toSource().trim())
        .contains(
            lines(
                "var module$exports$module$foo$module = {};",
                "module$exports$path.join(\"a\", \"b\");"));
  }

  @Test
  public void canReferToClassesExportedByNodeCoreModule() {
    CompilerUtil compiler = createCompiler(path("foo/module.js"));

    compiler.compile(
        createSourceFile(
            path("foo/module.js"),
            "var stream = require('stream');",
            "",
            "/** @type {!stream.Stream} */",
            "exports.s = new stream.Stream;"));

    assertThat(compiler.toSource().trim())
        .contains(
            lines(
                "var module$exports$module$foo$module = {};",
                "module$exports$module$foo$module.s = new module$exports$stream.Stream;"));
  }

  @Test
  public void leavesRequireStatementsForUnrecognizedModuleIds() {
    CompilerUtil compiler = createCompiler(path("foo/module.js"));

    compiler.compile(
        createSourceFile(path("foo/module.js"), "var foo = require('foo');", "foo.doSomething();"));

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$module = {};",
                "var module$contents$module$foo$module_foo = require(\"foo\");",
                "module$contents$module$foo$module_foo.doSomething();"));
    assertIsNodeModule("module$exports$module$foo$module", "foo/module.js");
  }

  @Test
  public void rewritesRequireStatementToDirectlyReferenceExportsObject_compoundStatement() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    compiler.compile(
        createSourceFile(path("foo/root.js"), "exports.bar = function(value) {};"),
        createSourceFile(
            path("foo/leaf.js"),
            "var foo = require('./root');",
            "var bar = require('./root').bar;",
            "foo.bar(foo);"));

    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$foo$root = {};",
                "module$exports$module$foo$root.bar = function(value) {",
                "};",
                "var module$exports$module$foo$leaf = {};",
                "var module$contents$module$foo$leaf_bar = module$exports$module$foo$root.bar;",
                "module$exports$module$foo$root.bar(module$exports$module$foo$root);"));
  }

  @Test
  public void handlesRequiringModulesFromASubDirectory() {
    CompilerUtil compiler = createCompiler(path("foo/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"), "require('./bar/two');"),
        createSourceFile(path("foo/bar/two.js"), ""));

    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$foo$bar$two = {};",
                "var module$exports$module$foo$one = {};"));
  }

  @Test
  public void handlesRequiringModulesFromAParentDirectory() {
    CompilerUtil compiler = createCompiler(path("foo/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('../one');"));

    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$foo$one = {};",
                "var module$exports$module$foo$bar$two = {};"));
  }

  @Test
  public void handlesRequiringModulesFromAParentsSibling() {
    CompilerUtil compiler = createCompiler(path("foo/baz/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/baz/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('../baz/one');"));

    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$foo$baz$one = {};",
                "var module$exports$module$foo$bar$two = {};"));
  }

  @Test
  public void handlesRequiringAbsoluteModule() {
    CompilerUtil compiler =
        createCompiler(path("/absolute/foo/baz/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("/absolute/foo/baz/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('/absolute/foo/baz/one');"));

    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$$absolute$foo$baz$one = {};",
                "var module$exports$module$foo$bar$two = {};"));
  }

  @Test
  public void nonGlobalRequireCallsAreNotRegisteredAsInputRequirements() {
    CompilerUtil compiler =
        createCompiler(path("foo/one.js"), path("foo/two.js"), path("foo/three.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"), "var x = require('./two');", "x.go();"),
        createSourceFile(
            path("foo/two.js"),
            "var go = function() {",
            "  var x = require('./three');",
            "};",
            "exports.go = go;"),
        createSourceFile(path("foo/three.js"), "var x = require('./one');"));

    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$foo$two = {};",
                "var module$contents$module$foo$two_go = function() {",
                "  var x = module$exports$module$foo$three;",
                "};",
                "module$exports$module$foo$two.go = module$contents$module$foo$two_go;",
                "var module$exports$module$foo$one = {};",
                "module$exports$module$foo$two.go();",
                "var module$exports$module$foo$three = {};"));
  }

  @Test
  public void maintainsInternalTypeCheckingConsistency() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        path("foo/bar.js"),
        "/** @constructor */",
        "var Bar = function() {};",
        "",
        "/** @constructor */",
        "Bar.Baz = function() {};",
        "",
        "/** @type {!Bar} */",
        "var x = new Bar();",
        "",
        "/** @type {!Bar.Baz} */",
        "var y = new Bar.Baz();",
        "");

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar = {};",
                "var module$contents$module$foo$bar_Bar = function() {",
                "};",
                "module$contents$module$foo$bar_Bar.Baz = function() {",
                "};",
                "var module$contents$module$foo$bar_x = new module$contents$module$foo$bar_Bar;",
                "var module$contents$module$foo$bar_y = new module$contents$module$foo$bar_Bar.Baz;"));
  }

  @Test
  public void canReferenceInternalTypes() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        path("foo/bar.js"),
        "/** @constructor */",
        "var One = function() {};",
        "",
        "/**",
        " * @constructor",
        " * @extends {One}",
        " */",
        "exports.Two = function() {};",
        // Assignment tests.
        "/** @type {!One} */",
        "var testOne = new One();",
        "/** @type {!One} */",
        "var testTwo = new exports.Two();",
        "");
    // OK if compiles without error.
  }

  @Test
  public void canReferenceTypesDefinedOnOwnModuleExports() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        path("foo/bar.js"),
        "/** @constructor */",
        "var One = function() {};",
        "",
        "/**",
        " * @constructor",
        " * @extends {One}",
        " */",
        "exports.Two = function() {};",
        "",
        "/**",
        " * @param {!exports.Two} p .",
        " * @constructor",
        " */",
        "exports.Three = function(p) {};",
        "");
    // OK if compiles without error.
  }

  @Test
  public void canReferenceRequiredModuleTypesUsingImportAlias() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"), path("foo/baz.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"), "/** @constructor */", "exports.Bar = function(){};"),
        createSourceFile(
            path("foo/baz.js"),
            "var bar = require('./bar');",
            "var bBar = bar.Bar;",
            "var Bar = require('./bar').Bar;",
            "",
            "/** @type {!bar.Bar} */",
            "var one = new bar.Bar();",
            "",
            "/** @type {!bar.Bar} */",
            "var two = new bBar;",
            "",
            "/** @type {!Bar} */",
            "var three = new Bar();",
            "",
            "/** @type {!Bar} */",
            "var four = new bar.Bar;",
            ""));
    // OK if compiles without error.
  }

  @Test
  public void canReferenceCastedTypeThroughModuleImportAlias() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"), path("foo/baz.js"));

    compiler.compile(
        createSourceFile(
            path("index.js"),
            "/**",
            " * @param {number} a .",
            " * @constructor",
            " */",
            "function NotACommonJsModuleCtor(a) {};"),
        createSourceFile(
            path("foo/bar.js"),
            "/** @constructor */",
            "exports.NotACommonJsModuleCtor = NotACommonJsModuleCtor;",
            "/** @constructor */",
            "exports.Bar = NotACommonJsModuleCtor;"),
        createSourceFile(
            path("foo/baz.js"),
            "var bar = require('./bar');",
            "",
            "/** @type {!bar.NotACommonJsModuleCtor} */",
            "var one = new bar.NotACommonJsModuleCtor(1);",
            "",
            "/** @type {!bar.Bar} */",
            "var two = new bar.Bar(2);",
            ""));
    // OK if compiles without error.
  }

  @Test
  public void canReferenceTypeExportedAsAlias() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"), path("foo/baz.js"));

    compiler.compile(
        createSourceFile(
            path("foo/bar.js"),
            "/**",
            " * @param {number} a .",
            " * @constructor",
            " */",
            "var Greeter = function(a) {};",
            "",
            "/** @constructor */",
            "exports.Bar = Greeter;"),
        createSourceFile(
            path("foo/baz.js"),
            "var bar = require('./bar');",
            "",
            "/** @type {!bar.Bar} */",
            "var b = new bar.Bar(1);",
            ""));
    // OK if compiles without error.
  }

  @Test
  public void exportedInternalVarInheritsJsDocInfo() {
    CompilerUtil compiler = createCompiler(path("foo.js"));

    compiler.compile(
        createSourceFile(
            path("foo.js"),
            "/**",
            " * @constructor",
            " */",
            "var Greeter = function(){};",
            "/**",
            " * @param {string} name .",
            " * @return {string} .",
            " */",
            "Greeter.prototype.sayHi = function(name) {",
            "  return 'Hello, ' + name;",
            "};",
            "",
            "exports.Greeter = Greeter"));

    JSType exportedGreeter =
        compiler
            .getCompiler()
            .getTopScope()
            .getVar("module$exports$module$foo")
            .getType()
            .findPropertyType("Greeter");
    assertTrue(exportedGreeter.isConstructor());
  }

  @Test
  public void savesOriginalTypeNameInJsDoc() {
    CompilerUtil compiler = createCompiler(path("foo.js"));

    compiler.compile(
        createSourceFile(
            path("foo.js"),
            "/** @constructor */",
            "var Builder = function(){};",
            "/** @return {!Builder} . */",
            "Builder.prototype.returnThis = function() { return this; };",
            "exports.Builder = Builder"));

    Scope scope = compiler.getCompiler().getTopScope();
    Var var = scope.getVar("module$exports$module$foo");
    JSType type = var.getInitialValue().getJSType().findPropertyType("Builder");
    assertTrue(type.isConstructor());

    type = type.toObjectType().getTypeOfThis();
    assertEquals("module$contents$module$foo_Builder", type.toString());

    type = type.toObjectType().getPropertyType("returnThis");
    assertTrue(type.toString(), type.isFunctionType());

    JSDocInfo info = type.getJSDocInfo();
    assertNotNull(info);

    Node node = getOnlyElement(info.getTypeNodes());
    assertEquals(Token.BANG, node.getToken());

    node = node.getFirstChild();
    assertTrue(node.isString());
    assertEquals("module$contents$module$foo_Builder", node.getString());
    assertEquals("Builder", node.getProp(Node.ORIGINALNAME_PROP));
  }

  @Test
  public void canReferenceConstructorExportedByAnotherModule() {
    CompilerUtil compiler = createCompiler(path("x/foo.js"), path("x/bar.js"));

    compiler.compile(
        createSourceFile(path("x/foo.js"), "/** @constructor */", "exports.Foo = function(){};"),
        createSourceFile(
            path("x/bar.js"),
            "var foo = require('./foo');",
            "/** @type {function(new: foo.Foo)} */",
            "exports.Foo = foo.Foo;"));

    Scope scope = compiler.getCompiler().getTopScope();
    Var var = scope.getVar("module$exports$module$x$bar");

    JSType type = var.getInitialValue().getJSType().findPropertyType("Foo");
    assertTrue(type.isConstructor());

    type = type.toObjectType().getTypeOfThis();
    assertEquals("module$exports$module$x$foo.Foo", type.toString());
  }

  @Test
  public void canReferenceConstructorDefinedInTheGlobalScope() {
    CompilerUtil compiler = createCompiler(path("x/bar.js"));

    compiler.compile(
        createSourceFile(path("x/foo.js"), "/** @constructor */", "function Foo() {}"),
        createSourceFile(
            path("x/bar.js"), "/** @type {function(new: Foo)} */", "exports.Foo = Foo;"));

    Scope scope = compiler.getCompiler().getTopScope();
    Var var = scope.getVar("module$exports$module$x$bar");

    JSType type = var.getInitialValue().getJSType().findPropertyType("Foo");
    assertTrue(type.isConstructor());

    type = type.toObjectType().getTypeOfThis();
    assertEquals("Foo", type.toString());
  }

  @Test
  public void canUseModuleInternalTypedefsInJsDoc() {
    CompilerUtil compiler = createCompiler(path("foo.js"));

    compiler.compile(
        createSourceFile(
            path("foo.js"),
            "/** @typedef {{x: number}} */",
            "var Variable;",
            "",
            "/**",
            " * @param {Variable} a .",
            " * @param {Variable} b .",
            " * @return {Variable} .",
            " */",
            "exports.add = function(a, b) {",
            "  return {x: a.x + b.x};",
            "};"));

    Scope scope = compiler.getCompiler().getTopScope();
    Var var = scope.getVar("module$exports$module$foo");
    JSType type = var.getInitialValue().getJSType().toObjectType().getPropertyType("add");
    assertTrue(type.isFunctionType());

    JSDocInfo info = type.getJSDocInfo();
    Node node = info.getTypeNodes().iterator().next();
    assertTrue(node.isString());
    assertEquals("module$contents$module$foo_Variable", node.getString());
    assertEquals("Variable", node.getProp(Node.ORIGINALNAME_PROP));
  }

  @Test
  public void renamesExportsWhenUsedAsParameter() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(createSourceFile(path("foo/bar.js"), "function go(e) {}", "go(exports);"));

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar = {};",
                "function module$contents$module$foo$bar_go(e) {",
                "}",
                "module$contents$module$foo$bar_go(module$exports$module$foo$bar);"));
  }

  @Test
  public void handlesCompoundVarDeclarations() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(createSourceFile(path("foo/bar.js"), "var x = 1,", "    y = 2;"));

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar = {};",
                "var module$contents$module$foo$bar_x = 1, module$contents$module$foo$bar_y = 2;"));
  }

  @Test
  public void canReferenceExportedTypeReferences() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"), path("foo/baz.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"), "/** @constructor */", "exports.foo = function(){};"),
        createSourceFile(
            path("foo/baz.js"),
            "var bar = require('./bar');",
            "var foo = require('./bar').foo;",
            "/** @type {!bar.foo} */",
            "var a = new bar.foo();",
            "/** @type {!foo} */",
            "var b = new foo();"));

    assertThat(compiler.toSource())
        .startsWith(
            lines(
                "var module$exports$module$foo$bar = {};",
                "module$exports$module$foo$bar.foo = function() {",
                "};",
                "var module$exports$module$foo$baz = {};",
                "var module$contents$module$foo$baz_foo = module$exports$module$foo$bar.foo;",
                "var module$contents$module$foo$baz_a = new module$exports$module$foo$bar.foo;",
                "var module$contents$module$foo$baz_b = new module$contents$module$foo$baz_foo;"));

    JSType objA =
        compiler.getCompiler().getTopScope().getVar("module$contents$module$foo$baz_a").getType();
    assertThat(objA.getDisplayName()).isEqualTo("module$exports$module$foo$bar.foo");

    JSType objB =
        compiler.getCompiler().getTopScope().getVar("module$contents$module$foo$baz_b").getType();
    assertThat(objB.getDisplayName()).isEqualTo("module$exports$module$foo$bar.foo");
  }

  @Test
  public void doesNotNpeOnScriptThatAccessesPropertyOfReturnValue() {
    CompilerUtil util = createCompiler(path("foo/bar.js"));

    util.compile(
        path("foo/bar.js"),
        "function createCallback() {",
        " return function(y) { this.doIt().x = y; };",
        "}");
  }

  @Test
  public void multipleModuleExportAssignmentsAreNotPermitted() {
    CompilerUtil util = createCompiler(path("module.js"));

    try {
      util.compile(path("module.js"), "module.exports = 1;", "module.exports = 2;");
      fail();
    } catch (CompilerUtil.CompileFailureException expected) {
      assertThat(expected.getMessage())
          .contains("Multiple assignments to module.exports are not permitted");
    }
  }

  @Test
  public void requireAnEmptyModuleIdIsNotPermitted() {
    CompilerUtil util = createCompiler(path("module.js"));

    try {
      util.compile(path("module.js"), "require('');");
      fail();
    } catch (CompilerUtil.CompileFailureException expected) {
      assertThat(expected.getMessage()).contains("Invalid module ID passed to require()");
    }
  }

  @Test
  public void rewritesModuleExportAssignments() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(path("module.js"), "module.exports = 1234;");

    assertThat(util.toSource()).contains("module$exports$module$module = 1234;");
  }

  @Test
  public void ignoresLocalModuleExportReferences() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(
        path("module.js"),
        "function x() {",
        "  var module = {};",
        "  module.exports = 1234;",
        "}",
        "module.exports = {};");

    assertThat(util.toSource())
        .startsWith(
            lines(
                "function module$contents$module$module_x() {",
                "  var module = {};",
                "  module.exports = 1234;",
                "}",
                "var module$exports$module$module = {};"));
  }

  @Test
  public void ignoresModuleVarReferencesWhenDefinedAsTopLevelOfModule() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(
        path("module.js"),
        "var module = {};",
        "module.exports = {};",
        "module.exports.x = function() {};");

    assertThat(util.toSource())
        .startsWith(
            lines(
                "var module$exports$module$module = {};",
                "var module$contents$module$module_module = {};",
                "module$contents$module$module_module.exports = {};",
                "module$contents$module$module_module.exports.x = function() {",
                "};"));
  }

  @Test
  public void canAssignAdditionalPropertiesToModuleExports() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(path("module.js"), "module.exports = {};", "module.exports.x = function() {};");

    assertThat(util.toSource())
        .contains(
            lines(
                "var module$exports$module$module = {};",
                "module$exports$module$module.x = function() {",
                "};"));
  }

  @Test
  public void rewritesModuleExportReferences() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(path("module.js"), "module.exports.x = function() {};");

    assertThat(util.toSource())
        .contains(
            lines(
                "var module$exports$module$module = {};",
                "module$exports$module$module.x = function() {",
                "};"));
  }

  @Test
  public void canRewritePropertyReferenceAttachedToRequireStatement() {
    Path bar = fs.getPath("/src/modules/foo/bar.js");
    Path baz = fs.getPath("/src/modules/foo/baz.js");
    CompilerUtil util = createCompiler(bar, baz);
    util.compile(
        createSourceFile(bar, "/** @constructor */", "exports.Baz = function() {}"),
        createSourceFile(baz, "exports.AliasedBaz = require('./bar').Baz;"));

    assertThat(util.toSource())
        .contains(
            lines(
                "var module$exports$module$$src$modules$foo$bar = {};",
                "module$exports$module$$src$modules$foo$bar.Baz = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$baz = {};",
                "module$exports$module$$src$modules$foo$baz.AliasedBaz"
                    + " = module$exports$module$$src$modules$foo$bar.Baz"));
  }

  @Test
  public void resolveModuleTypeReference_nonRelativePath() {
    try {
      resolveModuleTypeReference(path("context.js"), "/abs/path");
      fail();
    } catch (IllegalArgumentException expected) {
      // Do nothing.
    }
  }

  @Test
  public void testResolveModuleTypeReference_pathResolvesToModuleDirectly() throws IOException {
    Path ref = path("a/b/c.js");
    Path file = ref.resolveSibling("d/e.js");
    createDirectories(file.getParent());
    createFile(file);

    assertThat(resolveModuleTypeReference(ref, "./d/e")).isEqualTo(getModuleId(file));
    assertThat(resolveModuleTypeReference(ref, "./d/../d/e")).isEqualTo(getModuleId(file));
    assertThat(resolveModuleTypeReference(ref, "../b/d/e")).isEqualTo(getModuleId(file));
  }

  @Test
  public void testResolveModuleTypeReference_pathResolvesToModuleWithIndex() throws IOException {
    Path ref = path("a/b/c.js");
    Path dir = ref.resolveSibling("d/e");
    Path file = dir.resolve("index.js");
    createDirectories(dir);
    createFile(file);

    assertThat(resolveModuleTypeReference(ref, "./d/e")).isEqualTo(getModuleId(file));
    assertThat(resolveModuleTypeReference(ref, "./d/../d/e")).isEqualTo(getModuleId(file));
    assertThat(resolveModuleTypeReference(ref, "../b/d/e")).isEqualTo(getModuleId(file));

    assertThat(resolveModuleTypeReference(ref, "./d/e/index")).isEqualTo(getModuleId(file));
    assertThat(resolveModuleTypeReference(ref, "./d/../d/e/index")).isEqualTo(getModuleId(file));
    assertThat(resolveModuleTypeReference(ref, "../b/d/e/index")).isEqualTo(getModuleId(file));
  }

  @Test
  public void testResolveModuleTypeReference_pathResolvesToExportedType() throws IOException {
    Path ref = path("a/b/c.js");
    Path dir = ref.resolveSibling("d/e");
    createDirectories(dir);

    Path indexFile = dir.resolve("index.js");
    createFile(indexFile);

    Path otherFile = dir.resolve("foo.bar.js");
    createFile(otherFile);

    assertThat(resolveModuleTypeReference(ref, "./d/e.Foo"))
        .isEqualTo(getModuleId(indexFile) + ".Foo");
    assertThat(resolveModuleTypeReference(ref, "./d/../d/e.Foo"))
        .isEqualTo(getModuleId(indexFile) + ".Foo");
    assertThat(resolveModuleTypeReference(ref, "../b/d/e.Foo"))
        .isEqualTo(getModuleId(indexFile) + ".Foo");
    assertThat(resolveModuleTypeReference(ref, "./d/e.Foo.Bar"))
        .isEqualTo(getModuleId(indexFile) + ".Foo.Bar");

    assertThat(resolveModuleTypeReference(ref, "./d/e/index.Foo"))
        .isEqualTo(getModuleId(indexFile) + ".Foo");
    assertThat(resolveModuleTypeReference(ref, "./d/../d/e/index.Foo"))
        .isEqualTo(getModuleId(indexFile) + ".Foo");
    assertThat(resolveModuleTypeReference(ref, "../b/d/e/index.Foo"))
        .isEqualTo(getModuleId(indexFile) + ".Foo");
    assertThat(resolveModuleTypeReference(ref, "./d/e/index.Foo.Bar"))
        .isEqualTo(getModuleId(indexFile) + ".Foo.Bar");

    assertThat(resolveModuleTypeReference(ref, "./d/e/foo.bar.Baz"))
        .isEqualTo(getModuleId(otherFile) + ".Baz");
    assertThat(resolveModuleTypeReference(ref, "./d/../d/e/foo.bar.Baz"))
        .isEqualTo(getModuleId(otherFile) + ".Baz");
    assertThat(resolveModuleTypeReference(ref, "../b/d/e/foo.bar.Baz"))
        .isEqualTo(getModuleId(otherFile) + ".Baz");
  }

  @Test
  public void testResolveModuleTypeReference_pathDoesNotREsolve() throws IOException {
    Path ref = path("a/b/c.js");
    assertThat(resolveModuleTypeReference(ref, "./d/e")).isEqualTo("./d/e");
  }

  @Test
  public void handlesReferencesToOtherModulesTypesEvenIfNotExplicitlyRequired() throws IOException {
    Path root = path("root.js");
    Path bar = path("foo/bar.js");
    Path baz = path("foo/baz.js");

    createFile(root);
    createDirectories(bar.getParent());
    createFile(bar);
    createFile(baz);

    CompilerUtil compiler = createCompiler(root, bar, baz);
    compiler.compile(
        createSourceFile(root, "/** @param {!./foo/bar.Person} p . */", "function inRoot(p) {}"),
        createSourceFile(bar, "/** @constructor */", "exports.Person = function(){};"),
        createSourceFile(baz, "/** @param {!./bar.Person} p . */", "function inBaz(p) {}"));
  }

  @Test
  public void processesExternModules() throws IOException {
    Path extern = path("root/externs/xml.js");
    Path module = path("root/source/foo.js");

    createDirectories(extern.getParent());
    write(
        extern,
        lines(
                "/** @const */",
                "var xml = {};",
                "/** @param {string} str",
                " *  @return {!Object}",
                " */",
                "xml.parse = function(str) {};",
                "module.exports = xml;")
            .getBytes(StandardCharsets.UTF_8));

    CompilerUtil compiler = createCompiler(ImmutableSet.of(extern), ImmutableSet.of(module));
    compiler.compile(module, "var xml = require('xml');", "xml.parse('abc');");

    assertThat(compiler.toSource())
        .contains(
            lines(
                "var module$exports$module$root$source$foo = {};",
                "module$exports$xml.parse(\"abc\");"));
  }

  @Test
  public void splitsMultipleRequireDeclarations() {
    Path foo = fs.getPath("/src/modules/foo/index.js");
    Path bar = fs.getPath("/src/modules/foo/bar.js");
    Path baz = fs.getPath("/src/modules/foo/baz.js");

    CompilerUtil util = createCompiler(foo, bar, baz);

    util.compile(
        createSourceFile(
            foo,
            "var bar = require('./bar'),",
            "    baz = require('./baz');",
            "",
            "exports.bar = bar;",
            "exports.baz = baz;"),
        createSourceFile(bar, "/** @constructor */", "exports.Bar = function() {}"),
        createSourceFile(baz, "/** @constructor */", "exports.Baz = function() {}"));

    assertThat(util.toSource())
        .contains(
            lines(
                "var module$exports$module$$src$modules$foo$bar = {};",
                "module$exports$module$$src$modules$foo$bar.Bar = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$baz = {};",
                "module$exports$module$$src$modules$foo$baz.Baz = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$index = {};",
                "module$exports$module$$src$modules$foo$index.bar ="
                    + " module$exports$module$$src$modules$foo$bar;",
                "module$exports$module$$src$modules$foo$index.baz ="
                    + " module$exports$module$$src$modules$foo$baz;"));
  }

  @Test
  public void splitsMultipleRequireDeclarations_const() {
    Path foo = fs.getPath("/src/modules/foo/index.js");
    Path bar = fs.getPath("/src/modules/foo/bar.js");
    Path baz = fs.getPath("/src/modules/foo/baz.js");

    CompilerUtil util = createCompiler(foo, bar, baz);

    util.compile(
        createSourceFile(
            foo,
            "const bar = require('./bar'),",
            "    baz = require('./baz');",
            "",
            "exports.bar = bar;",
            "exports.baz = baz;"),
        createSourceFile(bar, "/** @constructor */", "exports.Bar = function() {}"),
        createSourceFile(baz, "/** @constructor */", "exports.Baz = function() {}"));

    assertThat(util.toSource())
        .contains(
            lines(
                "var module$exports$module$$src$modules$foo$bar = {};",
                "module$exports$module$$src$modules$foo$bar.Bar = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$baz = {};",
                "module$exports$module$$src$modules$foo$baz.Baz = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$index = {};",
                "module$exports$module$$src$modules$foo$index.bar ="
                    + " module$exports$module$$src$modules$foo$bar;",
                "module$exports$module$$src$modules$foo$index.baz ="
                    + " module$exports$module$$src$modules$foo$baz;"));
  }

  @Test
  public void splitsMultipleRequireDeclarations_immediatePropertyAccess() {
    Path foo = fs.getPath("/src/modules/foo/index.js");
    Path bar = fs.getPath("/src/modules/foo/bar.js");
    Path baz = fs.getPath("/src/modules/foo/baz.js");

    CompilerUtil util = createCompiler(foo, bar, baz);
    util.compile(
        createSourceFile(
            foo,
            "let Bar = require('./bar').Bar,",
            "    baz = require('./baz');",
            "",
            "exports.Bar = Bar;",
            "exports.baz = baz;"),
        createSourceFile(bar, "/** @constructor */", "exports.Bar = function() {}"),
        createSourceFile(baz, "/** @constructor */", "exports.Baz = function() {}"));

    assertThat(util.toSource())
        .contains(
            lines(
                "var module$exports$module$$src$modules$foo$bar = {};",
                "module$exports$module$$src$modules$foo$bar.Bar = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$baz = {};",
                "module$exports$module$$src$modules$foo$baz.Baz = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$index = {};",
                "var module$contents$module$$src$modules$foo$index_Bar ="
                    + " module$exports$module$$src$modules$foo$bar.Bar;",
                "module$exports$module$$src$modules$foo$index.Bar ="
                    + " module$contents$module$$src$modules$foo$index_Bar;",
                "module$exports$module$$src$modules$foo$index.baz ="
                    + " module$exports$module$$src$modules$foo$baz;"));
  }

  @Test
  public void handlesDestructuredImport() {
    Path foo = fs.getPath("/src/modules/foo/index.js");
    Path bar = fs.getPath("/src/modules/foo/bar.js");

    CompilerUtil util = createCompiler(foo, bar);
    util.compile(
        createSourceFile(
            foo,
            "let {Bar} = require('./bar');",
            "",
            "/** @return {!Bar} */",
            "exports.createBar = function() { return new Bar; };"),
        createSourceFile(bar, "/** @constructor */", "exports.Bar = function() {}"));

    assertThat(util.toSource())
        .contains(
            lines(
                "var module$exports$module$$src$modules$foo$bar = {};",
                "module$exports$module$$src$modules$foo$bar.Bar = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$index = {};",
                "var module$contents$module$$src$modules$foo$index_Bar = "
                    + "module$exports$module$$src$modules$foo$bar.Bar;",
                "module$exports$module$$src$modules$foo$index.createBar = function() {",
                "  return new module$contents$module$$src$modules$foo$index_Bar;",
                "};"));
  }

  @Test
  public void handlesDestructuredImportInCompoundStatement() {
    Path foo = fs.getPath("/src/modules/foo/index.js");
    Path bar = fs.getPath("/src/modules/foo/bar.js");

    CompilerUtil util = createCompiler(foo, bar);
    util.compile(
        createSourceFile(
            foo,
            "let {Bar} = require('./bar'),",
            "    bar = require('./bar');",
            "",
            "/** @return {!bar.Bar} */",
            "exports.newBar = function() { return new bar.Bar; };",
            "",
            "/** @return {!Bar} */",
            "exports.createBar = function() { return new Bar; };"),
        createSourceFile(bar, "/** @constructor */", "exports.Bar = function() {}"));

    assertThat(util.toSource())
        .contains(
            lines(
                "var module$exports$module$$src$modules$foo$bar = {};",
                "module$exports$module$$src$modules$foo$bar.Bar = function() {",
                "};",
                "var module$exports$module$$src$modules$foo$index = {};",
                "var module$contents$module$$src$modules$foo$index_Bar = "
                    + "module$exports$module$$src$modules$foo$bar.Bar;",
                "module$exports$module$$src$modules$foo$index.newBar = function() {",
                "  return new module$exports$module$$src$modules$foo$bar.Bar;",
                "};",
                "module$exports$module$$src$modules$foo$index.createBar = function() {",
                "  return new module$contents$module$$src$modules$foo$index_Bar;",
                "};"));
  }

  private void assertIsNodeModule(String id, String path) {
    Module module = typeRegistry.getModule(id);
    assertThat(module.getPath().toString()).isEqualTo(path);
    assertThat(module.getType()).isEqualTo(Module.Type.NODE);
  }

  private CompilerUtil createCompiler(final Path... modules) {
    return createCompiler(ImmutableSet.<Path>of(), ImmutableSet.copyOf(modules));
  }

  private CompilerUtil createCompiler(ImmutableSet<Path> externs, ImmutableSet<Path> modules) {
    for (Path path : concat(externs, modules)) {
      Path parent = path.getParent();
      if (parent != null) {
        try {
          Files.createDirectories(parent);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    Injector injector =
        GuiceRule.builder(new Object())
            .setInputFs(fs)
            .setModuleExterns(externs)
            .setModules(modules)
            .setLanguageIn(LanguageMode.ECMASCRIPT6_STRICT)
            .build()
            .createInjector();
    injector.injectMembers(this);
    CompilerUtil util = injector.getInstance(CompilerUtil.class);
    util.getOptions().setCheckTypes(true);
    return util;
  }

  private Path path(String first, String... remaining) {
    return fs.getPath(first, remaining);
  }

  private static String lines(String... lines) {
    return Joiner.on('\n').join(lines);
  }
}
