package com.github.jleyba.dossier;

import static com.github.jleyba.dossier.CompilerUtil.createSourceFile;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DossierCompiler;
import com.google.javascript.jscomp.SourceFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;

/**
 * Tests for {@link DocPass}.
 */
@RunWith(JUnit4.class)
public class DocPassTest {

  private FileSystem fs;
  private TypeRegistry typeRegistry;
  private CompilerUtil util;

  @Before
  public void setUp() {
    fs = Jimfs.newFileSystem();

    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.<Path>of());
    typeRegistry = new TypeRegistry(compiler.getTypeRegistry());
    CompilerOptions options = Main.createOptions(fs, typeRegistry, compiler);

    util = new CompilerUtil(compiler, options);
  }

  @Test
  public void recordsFileOverviewComments() throws IOException {
    SourceFile bar = createSourceFile(path("foo/bar.js"),
        "/** @" +
            "fileoverview This is a file overview",
        " *     It is on multiple lines.",
        " *Here is a pre tag:",
        " *<pre>",
        " *    adfadfafd",
        " *       </pre>",
        " * @see another widget",
        " */",
        "function Foo() {}");
    SourceFile baz = createSourceFile(path("foo/baz.js"),
        "/** @fileoverview Single line overview. */",
        "function doWork(){}");

    util.compile(bar, baz);

    assertEquals(
        Joiner.on('\n').join(
            "This is a file overview",
            "     It is on multiple lines.",
            "Here is a pre tag:",
            "<pre>",
            "    adfadfafd",
            "       </pre>"),
        typeRegistry.getFileOverview(path("foo/bar.js")).getFileoverview()
    );
    assertEquals("Single line overview.",
        typeRegistry.getFileOverview(path("foo/baz.js")).getFileoverview());
  }

  @Test
  public void ignoresUndocumentedFunctions() {
    util.compile(path("foo/bar.js"), "function Foo() {}");
    assertThat(typeRegistry.getNominalTypes()).isEmpty();
  }

  @Test
  public void recordsGlobalConstructors() throws IOException {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "function Foo() {}");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("Foo", type.getName());
    assertConstructor(type);
  }

  @Test
  public void recordsGlobalInterfaces() {
    util.compile(path("foo/bar.js"),
        "/** @interface */",
        "function Foo() {}");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("Foo", type.getName());
    assertInterface(type);
  }

  @Test
  public void recordsGlobalEnums() {
    util.compile(path("foo/bar.js"),
        "/** @enum */",
        "var Foo = {};");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("Foo", type.getName());
    assertEnum(type);
  }

  @Test
  public void recordsGlobalTypedefs() {
    util.compile(path("foo/bar.js"),
        "/** @typedef {string} */",
        "var Foo;");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("Foo", type.getName());
    assertTypedef(type);
  }

  @Test
  public void documentsNamespacedTypes() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo.bar');",
        "/** @constructor */",
        "foo.bar.Baz = function() {};");

    Map<String, NominalType> types = typeRegistry.getNominalTypeMap();
    assertThat(types.keySet()).containsExactly("foo", "foo.bar", "foo.bar.Baz");
    assertNamespace(types.get("foo"));
    assertNamespace(types.get("foo.bar"));
    assertConstructor(types.get("foo.bar.Baz"));
  }

  @Test
  public void documentsNestedTypes() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo.bar');",
        "/** @constructor */",
        "foo.bar.Bim = function() {};",
        "/** @constructor */",
        "foo.bar.Bim.Baz = function() {};");

    Map<String, NominalType> types = typeRegistry.getNominalTypeMap();
    assertThat(types.keySet()).containsExactly("foo", "foo.bar", "foo.bar.Bim", "foo.bar.Bim.Baz");
    assertNamespace(types.get("foo"));
    assertNamespace(types.get("foo.bar"));
    assertConstructor(types.get("foo.bar.Bim"));
    assertConstructor(types.get("foo.bar.Bim.Baz"));
  }

  @Test
  public void functionVariablesAreNotDocumentedAsConstructors() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo');",
        "/** @type {!Function} */",
        "foo.bar = function() {};",
        "/** @type {!Function} */",
        "foo.baz = function() {};");

    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertNamespace(type);
    assertEquals("foo", type.getName());
  }

  @Test
  public void functionInstancesAreNotDocumentedAsConstructors() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo');",
        "/** @type {!Function} */",
        "foo.bar = Function;");

    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertNamespace(type);
    assertEquals("foo", type.getName());
  }

  @Test
  public void canGetConstructorArgs_functionExpression_undocumented() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "function Foo(a, b) {}");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("Foo", type.getName());
    assertConstructor(type);
  }

  @Test
  public void canGetConstructorArgs_functionExpression_documented() {
    util.compile(path("foo/bar.js"),
        "/**",
        " * @param {string} a is for",
        " *     apples.",
        " * @param {string} b is for bananas.",
        " * @param {(string|Object)=} opt_c is for an optional",
        " *     parameter.",
        " * @constructor */",
        "function Foo(a, b, opt_c) {}");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("Foo", type.getName());
    assertConstructor(type);
  }

  @Test
  public void canGetInterfaceArgs_functionExpression_undocumented() {
    util.compile(path("foo/bar.js"),
        "/** @interface */",
        "function Foo(a, b) {}");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("Foo", type.getName());
    assertInterface(type);
  }

  @Test
  public void canGetInterfaceArgs_functionExpression_documented() {
    util.compile(path("foo/bar.js"),
        "/**",
        " * @param {string} a is for apples.",
        " * @param {string} b is for bananas.",
        " * @interface */",
        "function Foo(a, b) {}");
    NominalType type = getOnlyElement(typeRegistry.getNominalTypes());
    assertEquals("Foo", type.getName());
    assertInterface(type);
  }

  public DocPassTest() {
    super();
  }

  @Test
  public void onlyDocumentsAModulesExportedClass() {
    util.compile(path("foo.js"),
        "goog.module('foo');",
        "",
        "/** @constructor */",
        "function InternalClass() {}",
        "",
        "/** @constructor */",
        "exports.Foo = function() {};");

    Map<String, NominalType> types = typeRegistry.getNominalTypeMap();
    assertThat(types.keySet()).containsExactly("foo", "foo.Foo");
    assertNamespace(types.get("foo"));
    assertConstructor(types.get("foo.Foo"));
  }

  @Test
  public void documentsFunctionExportedByCommonJsModule() {
    createCompiler(ImmutableList.of(path("module.js")));
    util.compile(path("module.js"),
        "/**",
        " * @param {string} name a name.",
        " * @return {string} a greeting.",
        " */",
        "exports.greet = function(name) { return 'hello, ' + name; };");

    assertNamespace(getOnlyElement(typeRegistry.getModules()));
  }

  @Test
  public void documentsTypesAfterModuleExportsAssignment_assignedToFunction() {
    createCompiler(ImmutableList.of(path("module.js")));
    util.compile(path("module.js"),
        "/**",
        " * @param {string} name a name.",
        " * @return {string} a greeting.",
        " */",
        "module.exports = function(name) { return 'hello, ' + name; };");

    assertFunction(getOnlyElement(typeRegistry.getModules()));
  }

  @Test
  public void doesNotRecordExternAliasAsAType() {
    util.compile(path("script.js"),
        "/** @type {function(new: Date)} */",
        "var DateClone = Date;");

    assertThat(typeRegistry.getNominalTypes()).isEmpty();
  }

  @Test
  public void recordsReferencedExterns() {
    util.compile(
        ImmutableList.of(
            createSourceFile(path("externs.js"),
                "/** @constructor */",
                "function GlobalCtor() {}",
                "",
                "/** @interface */",
                "var GlobalIface = function() {};",
                "",
                "/** @enum {string} */",
                "var GlobalEnum = {};",
                "",
                "/** @const */",
                "var ns = {};",
                "",
                "/** @constructor */",
                "ns.Ctor = function(e) {};",
                "",
                "/** @enum {number} */",
                "ns.Enum = {foo:1};")
        ),
        ImmutableList.of(
            createSourceFile(path("script.js"),
                "var x = new GlobalCtor();",
                "/**",
                " * @param {GlobalEnum} a .",
                " * @param {ns.Ctor} b .",
                " * @param {ns.Enum} c .",
                " * @constructor",
                " * @implements {GlobalIface}",
                " */",
                "var y = function(a, b, c) {};"
            )));
    assertThat(typeRegistry.getExternNames()).containsExactly(
        "GlobalCtor",
        "GlobalIface",
        "GlobalEnum",
        "ns.Ctor",
        "ns.Enum");
  }

  @Test
  public void doesNotTraverseGlobalObjectAsExtern() {
    util.compile(
        ImmutableList.of(
            createSourceFile(path("externs.js"),
                "/** @const */",
                "var global = this;")
        ),
        ImmutableList.of(
            createSourceFile(path("script.js"),
                "/** @constructor */",
                "var x = function() {};",
                "/** @constructor */",
                "global.y = function() {};")
        )
    );
    assertThat(typeRegistry.getExternNames()).containsExactly("global");
  }

  @Test
  public void identifiesTypesFromAClosureModule() {
    util.compile(path("module.js"),
        "goog.module('foo.bar');",
        "",
        "/** @constructor */",
        "var Internal = function() {};",
        "",
        "/** @constructor */",
        "exports.Foo = function() {};",
        "exports.Clazz = Internal;");
    assertThat(typeRegistry.getNominalTypeMap().keySet())
        .containsExactly("foo", "foo.bar", "foo.bar.Foo", "foo.bar.Clazz");
  }

  @Test
  public void doesNotDocumentCtorReferencesAsNestedTypes() {
    util.compile(path("module.js"),
        "goog.provide('foo');",
        "",
        "/** @constructor */",
        "foo.Bar = function() {};",
        "",
        "/** @type {function(new: foo.Bar)} */",
        "foo.Baz = foo.Bar",
        "",
        "/** @private {function(new: foo.Bar)} */",
        "foo.PrivateBar = foo.Bar",
        "",
        "/** @protected {function(new: foo.Bar)} */",
        "foo.ProtectedBar = foo.Bar",
        "",
        "/** @public {function(new: foo.Bar)} */",
        "foo.PublicBar = foo.Bar");
    assertThat(typeRegistry.getNominalTypeMap().keySet()).containsExactly("foo", "foo.Bar");
  }

  @Test
  public void doesNotDocumentCtorReferencesAsNestedTypes_closureModule() {
    util.compile(path("module.js"),
        "goog.module('foo');",
        "",
        "/** @constructor */",
        "var Internal = function() {};",
        "",
        "/** @type {function(new: Internal)} */",
        "exports.createInternal = Internal;");
    assertThat(typeRegistry.getNominalTypeMap().keySet()).containsExactly("foo");
    assertThat(typeRegistry.getNominalType("foo").getProperty("createInternal")).isNotNull();
  }

  private Path path(String first, String... remaining) {
    return fs.getPath(first, remaining);
  }

  private void createCompiler(Iterable<Path> modules) {
    DossierCompiler compiler = new DossierCompiler(System.err, modules);
    CompilerOptions options = Main.createOptions(
        fs, typeRegistry, compiler);

    util = new CompilerUtil(compiler, options);
  }

  private static void assertTypedef(NominalType type) {
    assertNotNull(type.getJsdoc());
    assertTrue(type.getJsdoc().isTypedef());
  }

  private static void assertConstructor(NominalType type) {
    assertTrue(type.getJsType().isConstructor());
    assertFalse(type.getJsType().isInterface());
    assertFalse(type.getJsType().isEnumType());
  }

  private static void assertInterface(NominalType type) {
    assertFalse(type.getJsType().isConstructor());
    assertTrue(type.getJsType().isInterface());
    assertFalse(type.getJsType().isEnumType());
  }

  private static void assertEnum(NominalType type) {
    assertFalse(type.getJsType().isConstructor());
    assertFalse(type.getJsType().isInterface());
    assertTrue(type.getJsType().isEnumType());
  }

  private static void assertFunction(NominalType type) {
    assertTrue(type.getJsType().isFunctionType());
  }

  private static void assertNamespace(NominalType type) {
    assertFalse(type.getJsType().isConstructor());
    assertFalse(type.getJsType().isInterface());
    assertFalse(type.getJsType().isEnumType());
    assertTrue(type.getJsType().isObject());
  }
}
