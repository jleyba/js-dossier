package com.google.javascript.jscomp;

import static com.github.jleyba.dossier.CompilerUtil.createSourceFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.jleyba.dossier.CompilerUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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
  public void setsUpCommonJsModulePrimitives() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "");
    assertEquals(
        module("dossier$$module__foo$bar"),
        compiler.toSource().trim());
  }

  @Test
  public void renamesModuleGlobalVars() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "var x = 123;");
    assertEquals(
        module("dossier$$module__foo$bar",
            "var x$$_dossier$$module__foo$bar = 123;"),
        compiler.toSource().trim());
  }

  @Test
  public void doesNotRenameNonGlobalVars() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "function x() { var x = 123; }");
    assertEquals(
        module("dossier$$module__foo$bar",
            "function x$$_dossier$$module__foo$bar() {\n  var x = 123;\n}\n;"),
        compiler.toSource().trim());
  }

  @Test
  public void renamesModuleGlobalFunctionDeclarations() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "function foo(){}");
    assertEquals(
        module("dossier$$module__foo$bar",
            "function foo$$_dossier$$module__foo$bar() {\n}\n;"),
        compiler.toSource().trim());
  }

  @Test
  public void renamesModuleGlobalFunctionExpressions() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"), "var foo = function(){}");
    assertEquals(
        module("dossier$$module__foo$bar",
            "var foo$$_dossier$$module__foo$bar = function() {\n};"),
        compiler.toSource().trim());
  }

  @Test
  public void handlesReferencesToFilenameAndDirnameFreeVariables() {
    CompilerUtil compiler = createCompiler(path("foo/bar.js"));

    compiler.compile(path("foo/bar.js"),
        "var x = __filename;",
        "var y = __dirname;",
        "var z = module.filename");

    assertEquals(
        module("dossier$$module__foo$bar", lines(
            "var x$$_dossier$$module__foo$bar = dossier$$module__foo$bar.__filename;",
            "var y$$_dossier$$module__foo$bar = dossier$$module__foo$bar.__dirname;",
            "var z$$_dossier$$module__foo$bar = dossier$$module__foo$bar.filename;")),
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
            module("dossier$$module__foo$root"),
            module("dossier$$module__foo$leaf", "dossier$$module__foo$root.exports;")),
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
            module("dossier$$module__foo$one"),
            module("dossier$$module__foo$three"),
            module("dossier$$module__foo$two", lines(
                "dossier$$module__foo$one.exports;",
                "dossier$$module__foo$three.exports;"))),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesRequireStatementToDirectlyReferenceExportsObject() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    compiler.compile(
        createSourceFile(path("foo/root.js"), ""),
        createSourceFile(path("foo/leaf.js"),
            "var foo = require('./root');",
            "var bar = require('./root').bar"));

    assertEquals(
        lines(
            module("dossier$$module__foo$root"),
            module("dossier$$module__foo$leaf", lines(
                "var foo$$_dossier$$module__foo$leaf = dossier$$module__foo$root.exports;",
                "var bar$$_dossier$$module__foo$leaf = dossier$$module__foo$root.exports.bar;"))),
        compiler.toSource().trim());
  }

  @Test
  public void rewritesRequireStatementToDirectlyReferenceExportsObject_compoundStatement() {
    CompilerUtil compiler = createCompiler(path("foo/leaf.js"), path("foo/root.js"));

    compiler.compile(
        createSourceFile(path("foo/root.js"), ""),
        createSourceFile(path("foo/leaf.js"),
            "var foo = require('./root'),",
            "    bar = require('./root').bar"));

    assertEquals(
        lines(
            module("dossier$$module__foo$root"),
            module("dossier$$module__foo$leaf",
                "var foo$$_dossier$$module__foo$leaf = dossier$$module__foo$root.exports, " +
                    "bar$$_dossier$$module__foo$leaf = dossier$$module__foo$root.exports.bar;")),
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
            module("dossier$$module__foo$bar$two"),
            module("dossier$$module__foo$one",
                "dossier$$module__foo$bar$two.exports;")),
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
            module("dossier$$module__foo$one"),
            module("dossier$$module__foo$bar$two",
                "dossier$$module__foo$one.exports;")),
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
            module("dossier$$module__foo$baz$one"),
            module("dossier$$module__foo$bar$two",
                "dossier$$module__foo$baz$one.exports;")),
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
            module("dossier$$module__$absolute$foo$baz$one"),
            module("dossier$$module__foo$bar$two",
                "dossier$$module__$absolute$foo$baz$one.exports;")),
        compiler.toSource().trim());
  }

  @Test
  public void nonGlobalRequireCallsAreNotRegisteredAsInputRequirements() {
    CompilerUtil compiler = createCompiler(
        path("foo/one.js"), path("foo/two.js"), path("foo/three.js"));

    compiler.compile(
        createSourceFile(path("foo/one.js"),
            "var x = require('./two');"),
        createSourceFile(path("foo/two.js"),
            "var go = function() {",
            "  var x = require('./three');",
            "};"),
        createSourceFile(path("foo/three.js"),
            "var x = require('./one');"));

    assertEquals(
        lines(
            module("dossier$$module__foo$two", lines(
                "var go$$_dossier$$module__foo$two = function() {",
                "  var x = dossier$$module__foo$three.exports;",
                "};")),
            module("dossier$$module__foo$one",
                "var x$$_dossier$$module__foo$one = dossier$$module__foo$two.exports;"),
            module("dossier$$module__foo$three",
                "var x$$_dossier$$module__foo$three = dossier$$module__foo$one.exports;")),
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
        module("dossier$$module__foo$bar", lines(
            "var Bar$$_dossier$$module__foo$bar = function() {",
            "};",
            "Bar$$_dossier$$module__foo$bar.Baz = function() {",
            "};",
            "var x$$_dossier$$module__foo$bar = new Bar$$_dossier$$module__foo$bar;",
            "var y$$_dossier$$module__foo$bar = new Bar$$_dossier$$module__foo$bar.Baz;")),
        compiler.toSource().trim());
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
            "var Bar = require('./bar').Bar;",
            "var x = {y: {}};",
            "x.Bar = require('./bar').Bar;",
            "x.y.Bar = require('./bar').Bar;",
            "",
            "/** @type {!bar.Bar} */",
            "var one = new bar.Bar();",
            "",
            "/** @type {!Bar} */",
            "var two = new Bar();",
            "",
            "/** @type {!x.Bar} */",
            "var three = new x.Bar();",
            "",
            "/** @type {!x.y.Bar} */",
            "var four = new x.y.Bar();",
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

    JSType greeterType = compiler.getCompiler()
        .getTypeRegistry()
        .getType("Greeter$$_dossier$$module__foo");
    assertNotNull(greeterType);

    JSDocInfo greeterInfo = greeterType.getJSDocInfo();
    assertNotNull(greeterInfo);

    ObjectType exportsObj = compiler.getCompiler().getTopScope()
        .getVar("dossier$$module__foo.exports")
        .getType()
        .toObjectType();
    assertNotNull(exportsObj);

    JSType exportedGreeter = exportsObj.getPropertyType("Greeter");
    assertTrue(exportedGreeter.isConstructor());
    assertEquals(greeterType, exportedGreeter.toObjectType().getTypeOfThis());
    assertEquals(greeterInfo, exportedGreeter.getJSDocInfo());
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
    Scope.Var var = scope.getVar("dossier$$module__foo");
    ObjectType module = var.getType().toObjectType();
    ObjectType exports = module.getPropertyType("exports").toObjectType();

    JSType type = exports.getPropertyType("Builder");
    assertTrue(type.isConstructor());

    type = type.toObjectType().getTypeOfThis();
    assertEquals("Builder$$_dossier$$module__foo", type.toString());

    type = type.toObjectType().getPropertyType("returnThis");
    assertTrue(type.toString(), type.isFunctionType());

    JSDocInfo info = type.getJSDocInfo();
    assertNotNull(info);

    Node node = Iterables.getOnlyElement(info.getTypeNodes());
    assertEquals(Token.BANG, node.getType());

    node = node.getFirstChild();
    assertTrue(node.isString());
    assertEquals("Builder$$_dossier$$module__foo", node.getString());
    assertEquals("Builder", node.getProp(Node.ORIGINALNAME_PROP));
  }

  @Test
  public void savesOriginalVarNameOnNameNode() {
    CompilerUtil compiler = createCompiler(path("foo.js"));

    compiler.compile(
        createSourceFile(path("foo.js"),
            "var Foo = 1;"));

    Scope scope = compiler.getCompiler().getTopScope();
    Scope.Var var = scope.getVar("Foo$$_dossier$$module__foo");
    Node nameNode = var.getNameNode();
    assertEquals("Foo", nameNode.getProp(Node.ORIGINALNAME_PROP));
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
    Scope.Var var = scope.getVar("dossier$$module__foo");
    JSType type = var.getType().toObjectType()
        .getPropertyType("exports")
        .toObjectType()
        .getPropertyType("add");
    assertTrue(type.isFunctionType());

    JSDocInfo info = type.getJSDocInfo();
    Node node = info.getTypeNodes().iterator().next();
    assertTrue(node.isString());
    assertEquals("Variable$$_dossier$$module__foo", node.getString());
    assertEquals("Variable", node.getProp(Node.ORIGINALNAME_PROP));
  }

  private static String module(String name) {
    return module(name, Optional.<String>absent());
  }

  private static String module(String name, String contents) {
    return module(name, Optional.of(contents));
  }

  private static String module(String name, Optional<String> contents) {
    ImmutableList.Builder<String> builder = ImmutableList.<String>builder().add(
        "var " + name + " = {__filename:\"\", __dirname:\"\", filename:\"\", exports:{}};");
    if (contents.isPresent()) {
      builder.add(contents.get());
    }
    return Joiner.on("\n").join(builder.build());
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
