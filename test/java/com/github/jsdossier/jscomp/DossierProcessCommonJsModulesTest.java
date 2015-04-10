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

import static com.github.jsdossier.CompilerUtil.createSourceFile;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.jsdossier.CompilerUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Tests for {@link DossierProcessCommonJsModules}.
 */
@RunWith(JUnit4.class)
public class DossierProcessCommonJsModulesTest {

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
    assertEquals(
        "var dossier$$module__foo$bar = {};",
        compiler.toSource().trim());
  }

  @Test
  public void setsUpCommonJsModulePrimitives_hasExportsReference() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "exports.x = 123;");
    assertEquals(
        lines(
            "var dossier$$module__foo$bar = {};",
            "dossier$$module__foo$bar.x = 123;"),
        compiler.toSource().trim());
  }

  @Test
  public void hasExportsReferenceAndAnotherScriptDefinesExportsInTheGlobalScope() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        createSourceFile(path("base.js"), "var exports = {};"),
        createSourceFile(path("foo/bar.js"), "exports.x = 123;"));
    assertEquals(
        lines(
            "var exports = {};",
            "var dossier$$module__foo$bar = {};",
            "dossier$$module__foo$bar.x = 123;"),
        compiler.toSource().trim());
  }

  @Test
  public void moduleRebindsExportsVariable() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "exports = 123;");
    assertEquals(
        "var dossier$$module__foo$bar = 123;",
        compiler.toSource().trim());
  }

  @Test
  public void renamesModuleGlobalVars() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "var x = 123;");
    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$bar = {};",
            "$jscomp.scope.x = 123;"),
        compiler.toSource().trim());
  }

  @Test
  public void doesNotRenameNonGlobalVars() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "function x() { var x = 123; }");
    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$bar = {};",
            "$jscomp.scope.x = function() {",
            "  var x = 123;",
            "};"),
        compiler.toSource().trim());
  }

  @Test
  public void renamesModuleGlobalFunctionDeclarations() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "function foo(){}");
    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$bar = {};",
            "$jscomp.scope.foo = function() {",
            "};"),
        compiler.toSource().trim());
  }

  @Test
  public void renamesModuleGlobalFunctionExpressions() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "var foo = function(){}");
    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$bar = {};",
            "$jscomp.scope.foo = function() {",
            "};"),
        compiler.toSource().trim());
  }

  @Test
  public void sortsSingleModuleDep() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    SourceFile root = createSourceFile(path("foo/root.js"), "");
    SourceFile leaf = createSourceFile(path("foo/leaf.js"), "require('./root');");

    compiler.compile(leaf, root);  // Should reorder since leaf depends on root.

    assertEquals(
        lines(
            "var dossier$$module__foo$root = {};",
            "var dossier$$module__foo$leaf = {};",
            "dossier$$module__foo$root;"),
        compiler.toSource().trim());
  }

  @Test
  public void sortsWithTwoModuleDeps() {
    CompilerUtil compiler = createCompiler(
        path("foo/one.js"), path("foo/two.js"), path("foo/three.js"));

    SourceFile one = createSourceFile(path("foo/one.js"), "");
    SourceFile two = createSourceFile(path("foo/two.js"),
        "require('./one');",
        "require('./three');");
    SourceFile three = createSourceFile(path("foo/three.js"));

    compiler.compile(two, one, three);  // Should properly reorder inputs.

    assertEquals(
        lines(
            "var dossier$$module__foo$one = {};",
            "var dossier$$module__foo$three = {};",
            "var dossier$$module__foo$two = {};",
            "dossier$$module__foo$one;",
            "dossier$$module__foo$three;"),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesRequireStatementToDirectlyReferenceExportsObject() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    compiler.compile(
        createSourceFile(path("foo/root.js"), ""),
        createSourceFile(path("foo/leaf.js"),
            "var foo = require('./root');",
            "var bar = require('./root').bar;",
            "foo.bar(foo);"));

    assertEquals(
        lines(
            "var dossier$$module__foo$root = {};",
            "var dossier$$module__foo$leaf = {};",
            "dossier$$module__foo$root.bar(dossier$$module__foo$root);"),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesRequireStatementsForExternModuleDefinitions() {
    Compiler compiler = new DossierCompiler(System.err, ImmutableSet.of(path("foo/module.js")));
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setPrettyPrint(true);
    CompilerUtil util = new CompilerUtil(compiler, options);

    util.compile(
        createSourceFile(path("foo/module.js"),
            "var http = require('http');",
            "http.doSomething();"));

    assertEquals(
        lines(
            "var dossier$$module__foo$module = {};",
            "dossier$$extern__http.doSomething();"),
        util.toSource().trim());
  }

  @Test
  public void rewritesRequireStatementToDirectlyReferenceExportsObject_compoundStatement() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    compiler.compile(
        createSourceFile(path("foo/root.js"), ""),
        createSourceFile(path("foo/leaf.js"),
            "var foo = require('./root'),",
            "    bar = require('./root').bar;",
            "foo.bar(foo);"));

    assertEquals(
        lines(
            "var dossier$$module__foo$root = {};",
            "var dossier$$module__foo$leaf = {};",
            "dossier$$module__foo$root.bar(dossier$$module__foo$root);"),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringModulesFromASubDirectory() {
    CompilerUtil compiler = createCompiler(path("foo/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"), "require('./bar/two');"),
        createSourceFile(path("foo/bar/two.js"), ""));

    assertEquals(
        lines(
            "var dossier$$module__foo$bar$two = {};",
            "var dossier$$module__foo$one = {};",
            "dossier$$module__foo$bar$two;"),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringModulesFromAParentDirectory() {
    CompilerUtil compiler = createCompiler(path("foo/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('../one');"));

    assertEquals(
        lines(
            "var dossier$$module__foo$one = {};",
            "var dossier$$module__foo$bar$two = {};",
            "dossier$$module__foo$one;"),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringModulesFromAParentsSibling() {
    CompilerUtil compiler = createCompiler(
        path("foo/baz/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("foo/baz/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('../baz/one');"));

    assertEquals(
        lines(
            "var dossier$$module__foo$baz$one = {};",
            "var dossier$$module__foo$bar$two = {};",
            "dossier$$module__foo$baz$one;"),
        compiler.toSource().trim());
  }

  @Test
  public void handlesRequiringAbsoluteModule() {
    CompilerUtil compiler = createCompiler(
        path("/absolute/foo/baz/one.js"), path("foo/bar/two.js"));

    compiler.compile(
        createSourceFile(path("/absolute/foo/baz/one.js"), ""),
        createSourceFile(path("foo/bar/two.js"), "require('/absolute/foo/baz/one');"));

    assertEquals(
        lines(
            "var dossier$$module__$absolute$foo$baz$one = {};",
            "var dossier$$module__foo$bar$two = {};",
            "dossier$$module__$absolute$foo$baz$one;"),
        compiler.toSource().trim());
  }

  @Test
  public void nonGlobalRequireCallsAreNotRegisteredAsInputRequirements() {
    CompilerUtil compiler = createCompiler(
        path("foo/one.js"), path("foo/two.js"), path("foo/three.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"),
            "var x = require('./two');",
            "x.go();"),
        createSourceFile(path("foo/two.js"),
            "var go = function() {",
            "  var x = require('./three');",
            "};",
            "exports.go = go;"),
        createSourceFile(path("foo/three.js"),
            "var x = require('./one');"));

    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$two = {};",
            "$jscomp.scope.go = function() {",
            "  var x = dossier$$module__foo$three;",
            "};",
            "dossier$$module__foo$two.go = $jscomp.scope.go;",
            "var dossier$$module__foo$one = {};",
            "dossier$$module__foo$two.go();",
            "var dossier$$module__foo$three = {};"),
        compiler.toSource().trim());
  }

  @Test
  public void maintainsInternalTypeCheckingConsistency() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"),
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

    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$bar = {};",
            "$jscomp.scope.Bar = function() {",
            "};",
            "$jscomp.scope.Bar.Baz = function() {",
            "};",
            "$jscomp.scope.x = new $jscomp.scope.Bar;",
            "$jscomp.scope.y = new $jscomp.scope.Bar.Baz;"),
        compiler.toSource().trim());
  }

  @Test
  public void canReferenceInternalTypes() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"),
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

    compiler.compile(path("foo/bar.js"),
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
    CompilerUtil compiler = createCompiler(
        path("foo/bar.js"), path("foo/baz.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"),
            "/** @constructor */",
            "exports.Bar = function(){};"),
        createSourceFile(path("foo/baz.js"),
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
    CompilerUtil compiler = createCompiler(
        path("foo/bar.js"), path("foo/baz.js"));

    compiler.compile(
        createSourceFile(path("index.js"),
            "/**",
            " * @param {number} a .",
            " * @constructor",
            " */",
            "function NotACommonJsModuleCtor(a) {};"),
        createSourceFile(path("foo/bar.js"),
            "/** @constructor */",
            "exports.NotACommonJsModuleCtor = NotACommonJsModuleCtor;",
            "/** @constructor */",
            "exports.Bar = NotACommonJsModuleCtor;"),
        createSourceFile(path("foo/baz.js"),
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
        createSourceFile(path("foo/bar.js"),
            "/**",
            " * @param {number} a .",
            " * @constructor",
            " */",
            "var Greeter = function(a) {};",
            "",
            "/** @constructor */",
            "exports.Bar = Greeter;"),
        createSourceFile(path("foo/baz.js"),
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
        createSourceFile(path("foo.js"),
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

    JSType exportedGreeter = compiler.getCompiler().getTopScope()
        .getVar("dossier$$module__foo")
        .getType()
        .findPropertyType("Greeter");
    assertTrue(exportedGreeter.isConstructor());
  }

  @Test
  public void savesOriginalTypeNameInJsDoc() {
    CompilerUtil compiler = createCompiler(path("foo.js"));

    compiler.compile(
        createSourceFile(path("foo.js"),
            "/** @constructor */",
            "var Builder = function(){};",
            "/** @return {!Builder} . */",
            "Builder.prototype.returnThis = function() { return this; };",
            "exports.Builder = Builder"));

    Scope scope = compiler.getCompiler().getTopScope();
    Var var = scope.getVar("dossier$$module__foo");
    JSType type = var.getInitialValue().getJSType().findPropertyType("Builder");
    assertTrue(type.isConstructor());

    type = type.toObjectType().getTypeOfThis();
    assertEquals("$jscomp.scope.Builder", type.toString());

    type = type.toObjectType().getPropertyType("returnThis");
    assertTrue(type.toString(), type.isFunctionType());

    JSDocInfo info = type.getJSDocInfo();
    assertNotNull(info);

    Node node = getOnlyElement(info.getTypeNodes());
    assertEquals(Token.BANG, node.getType());

    node = node.getFirstChild();
    assertTrue(node.isString());
    assertEquals("$jscomp.scope.Builder", node.getString());
    assertEquals("Builder", node.getProp(Node.ORIGINALNAME_PROP));
  }

  @Test
  public void canReferenceConstructorExportedByAnotherModule() {
    CompilerUtil compiler = createCompiler(path("x/foo.js"), path("x/bar.js"));

    compiler.compile(
        createSourceFile(path("x/foo.js"),
            "/** @constructor */",
            "exports.Foo = function(){};"),
        createSourceFile(path("x/bar.js"),
            "var foo = require('./foo');",
            "/** @type {function(new: foo.Foo)} */",
            "exports.Foo = foo.Foo;"));

    Scope scope = compiler.getCompiler().getTopScope();
    Var var = scope.getVar("dossier$$module__x$bar");

    JSType type = var.getInitialValue().getJSType().findPropertyType("Foo");
    assertTrue(type.isConstructor());

    type = type.toObjectType().getTypeOfThis();
    assertEquals("dossier$$module__x$foo.Foo", type.toString());
  }

  @Test
  public void canUseModuleInternalTypedefsInJsDoc() {
    CompilerUtil compiler = createCompiler(path("foo.js"));

    compiler.compile(
        createSourceFile(path("foo.js"),
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
    Var var = scope.getVar("dossier$$module__foo");
    JSType type = var.getInitialValue().getJSType().toObjectType()
        .getPropertyType("add");
    assertTrue(type.isFunctionType());

    JSDocInfo info = type.getJSDocInfo();
    Node node = info.getTypeNodes().iterator().next();
    assertTrue(node.isString());
    assertEquals("$jscomp.scope.Variable", node.getString());
    assertEquals("Variable", node.getProp(Node.ORIGINALNAME_PROP));
  }

  @Test
  public void renamesExportsWhenUsedAsParameter() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"),
            "function go(e) {}",
            "go(exports);"));

    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$bar = {};",
            "$jscomp.scope.go = function(e) {",
            "};",
            "(0,$jscomp.scope.go)(dossier$$module__foo$bar);"),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesNamespaceAssignments() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"),
            "/** @type {foo.} */",
            "exports.foo = {};"),
        createSourceFile(path("foo.js"),
            "goog.provide('foo');"));

    assertEquals(
        lines(
            "var foo = {};",
            "var dossier$$module__foo$bar = {};",
            "dossier$$module__foo$bar.foo = foo;"),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesNamespaceTypeDeclarations() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"),
            "/** @type {foo.} */",
            "exports.bar;"),
        createSourceFile(path("foo.js"),
            "goog.provide('foo');"));

    assertEquals(
        lines(
            "var foo = {};",
            "var dossier$$module__foo$bar = {};",
            "dossier$$module__foo$bar.bar = foo;"),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesNamespaceTypeGetters() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"),
            "/** @type {foo.} */",
            "(exports.__defineGetter__('bar', function(){}));",
            "/** @type {foo.} */",
            "(exports.bar.__defineGetter__('baz', function(){}));"),
        createSourceFile(path("foo.js"),
            "goog.provide('foo');"));

    assertEquals(
        lines(
            "var foo = {};",
            "var dossier$$module__foo$bar = {};",
            "dossier$$module__foo$bar.bar = foo;",
            "dossier$$module__foo$bar.bar.baz = foo;"),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesCompoundVarDeclarations() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"),
            "var x = 1,",
            "    y = 2;"));

    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$bar = {};",
            "$jscomp.scope.x = 1;",
            "$jscomp.scope.y = 2;"),
        compiler.toSource().trim()
    );
  }

  @Test
  public void canReferenceExportedTypeReferences() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"), path("foo/baz.js"));

    compiler.compile(
        createSourceFile(path("foo/bar.js"),
            "/** @constructor */",
            "exports.foo = function(){};"),
        createSourceFile(path("foo/baz.js"),
            "var bar = require('./bar');",
            "var foo = require('./bar').foo;",
            "/** @type {!bar.foo} */",
            "var a = new bar.foo();",
            "/** @type {!foo} */",
            "var b = new foo();"));

    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__foo$bar = {};",
            "dossier$$module__foo$bar.foo = function() {",
            "};",
            "var dossier$$module__foo$baz = {};",
            "$jscomp.scope.a = new dossier$$module__foo$bar.foo;",
            "$jscomp.scope.b = new dossier$$module__foo$bar.foo;"),
        compiler.toSource().trim());

    ObjectType scope = compiler.getCompiler().getTopScope()
        .getVar("$jscomp")
        .getType()
        .findPropertyType("scope")
        .toObjectType();

    assertEquals("dossier$$module__foo$bar.foo", scope.getPropertyType("a").getDisplayName());
    assertEquals("dossier$$module__foo$bar.foo", scope.getPropertyType("b").getDisplayName());
  }

  @Test
  public void doesNotNpeOnScriptThatAccessesPropertyOfReturnValue() {
    CompilerUtil util = createCompiler(path("foo/bar.js"));

    util.compile(path("foo/bar.js"),
        "function createCallback() {",
        " return function(y) { this.doIt().x = y; };",
        "}");
  }

  @Test
  public void multipleModuleExportAssignmentsAreNotPermitted() {
    CompilerUtil util = createCompiler(path("module.js"));

    try {
      util.compile(path("module.js"),
          "module.exports = 1;",
          "module.exports = 2;");
      fail();
    } catch (CompilerUtil.CompileFailureException expected) {
      assertTrue(expected.getMessage(),
          expected.getMessage().contains(
              "Multiple assignments to module.exports are not permitted"));
    }
  }

  @Test
  public void rewritesModuleExportAssignments() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(path("module.js"), "module.exports = 1234;");

    assertEquals(
        "var dossier$$module__module = 1234;",
        util.toSource().trim());
  }

  @Test
  public void ignoresLocalModuleExportReferences() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(path("module.js"),
        "function x() {",
        "  var module = {};",
        "  module.exports = 1234;",
        "}",
        "module.exports = {};");

    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "$jscomp.scope.x = function() {",
            "  var module = {};",
            "  module.exports = 1234;",
            "};",
            "var dossier$$module__module = {};"),
        util.toSource().trim());
  }

  @Test
  public void ignoresModuleVarReferencesWhenDefinedAsTopLevelOfModule() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(path("module.js"),
        "var module = {};",
        "module.exports = {};",
        "module.exports.x = function() {};");

    assertEquals(
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "var dossier$$module__module = {};",
            "$jscomp.scope.module = {};",
            "$jscomp.scope.module.exports = {};",
            "$jscomp.scope.module.exports.x = function() {",
            "};"),
        util.toSource().trim());
  }

  @Test
  public void canAssignAdditionalPropertiesToModuleExports() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(path("module.js"),
        "module.exports = {};",
        "module.exports.x = function() {};");

    assertEquals(
        lines(
            "var dossier$$module__module = {};",
            "dossier$$module__module.x = function() {",
            "};"),
        util.toSource().trim()
    );
  }

  @Test
  public void rewritesModuleExportReferences() {
    CompilerUtil util = createCompiler(path("module.js"));

    util.compile(path("module.js"),
        "module.exports.x = function() {};");

    assertEquals(
        lines(
            "var dossier$$module__module = {};",
            "dossier$$module__module.x = function() {",
            "};"),
        util.toSource().trim()
    );
  }

  private static CompilerUtil createCompiler(final Path... commonJsModules) {
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new ClosureCodingConvention());
    options.setIdeMode(true);
    options.setClosurePass(true);
    options.setPrettyPrint(true);
    options.setCheckTypes(true);
    options.setCheckSymbols(true);
    options.setAggressiveVarCheck(CheckLevel.ERROR);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    Compiler compiler = new DossierCompiler(System.err, ImmutableSet.copyOf(commonJsModules));

    return new CompilerUtil(compiler, options);
  }

  private static String lines(String... lines) {
    return Joiner.on('\n').join(lines);
  }

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }
}
