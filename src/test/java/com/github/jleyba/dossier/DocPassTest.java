package com.github.jleyba.dossier;

import static com.github.jleyba.dossier.CompilerUtil.createSourceFile;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DossierCompiler;
import com.google.javascript.jscomp.SourceFile;
import com.google.protobuf.Descriptors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for {@link DocPass}.
 */
@RunWith(JUnit4.class)
public class DocPassTest {

  private FileSystem fs;
  private DocRegistry docRegistry;
  private CompilerUtil util;

  @Before
  public void setUp() {
    fs = Jimfs.newFileSystem();

    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.<Path>of());
    docRegistry = new DocRegistry(compiler.getTypeRegistry());
    CompilerOptions options = Main.createOptions(fs, compiler, docRegistry);

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

    assertEquals(Joiner.on('\n').join(
        "This is a file overview",
        "     It is on multiple lines.",
        "Here is a pre tag:",
        "<pre>",
        "    adfadfafd",
        "      </pre>"), docRegistry.getFileOverview(path("foo/bar.js")));
    assertEquals("Single line overview. ", docRegistry.getFileOverview(path("foo/baz.js")));
  }

  @Test
  public void ignoresUndocumentedFunctions() {
    util.compile(path("foo/bar.js"), "function Foo() {}");
    assertTrue(Iterables.isEmpty(docRegistry.getTypes()));
  }

  @Test
  public void recordsGlobalConstructors() throws IOException {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "function Foo() {}");
    Descriptor descriptor = getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertConstructor(descriptor);
    assertTrue(descriptor.getArgs().isEmpty());
  }

  @Test
  public void recordsGlobalInterfaces() {
    util.compile(path("foo/bar.js"),
        "/** @interface */",
        "function Foo() {}");
    Descriptor descriptor = getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertInterface(descriptor);
    assertTrue(descriptor.getArgs().isEmpty());
  }

  @Test
  public void recordsGlobalEnums() {
    util.compile(path("foo/bar.js"),
        "/** @enum */",
        "var Foo = {};");
    Descriptor descriptor = getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertEnum(descriptor);
  }

  @Test
  public void documentsNamespacedTypes() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo.bar');",
        "/** @constructor */",
        "foo.bar.Baz = function() {};");

    List<Descriptor> descriptors = getDescriptors();
    assertEquals(
        ImmutableList.of("foo", "foo.bar", "foo.bar.Baz"),
        getNames(descriptors));
    assertNamespace(descriptors.get(0));
    assertNamespace(descriptors.get(1));
    assertConstructor(descriptors.get(2));
  }

  @Test
  public void documentsNestedTypes() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo.bar');",
        "/** @constructor */",
        "foo.bar.Bim = function() {};",
        "/** @constructor */",
        "foo.bar.Bim.Baz = function() {};");

    List<Descriptor> descriptors = getDescriptors();
    assertEquals(
        ImmutableList.of("foo", "foo.bar", "foo.bar.Bim", "foo.bar.Bim.Baz"),
        getNames(descriptors));
    assertNamespace(descriptors.get(0));
    assertNamespace(descriptors.get(1));
    assertConstructor(descriptors.get(2));
    assertConstructor(descriptors.get(3));
  }

  @Test
  public void functionVariablesAreNotDocumentedAsConstructors() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo');",
        "/** @type {!Function} */",
        "foo.bar = function() {};",
        "/** @type {!Function} */",
        "foo.baz = function() {};");

    Descriptor descriptor = getOnlyElement(getDescriptors());
    assertNamespace(descriptor);

    List<Descriptor> descriptors = Descriptor.sortByName(descriptor.getProperties());
    assertEquals(2, descriptors.size());
    assertEquals("foo.bar", descriptors.get(0).getFullName());
    assertEquals("foo.baz", descriptors.get(1).getFullName());
  }

  @Test
  public void functionInstancesAreNotDocumentedAsConstructors() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo');",
        "/** @type {!Function} */",
        "var foo = Function;");

    Descriptor descriptor = getOnlyElement(getDescriptors());
    assertNamespace(descriptor);
  }

  @Test
  public void canGetConstructorArgs_functionExpression_undocumented() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "function Foo(a, b) {}");
    Descriptor descriptor = getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertConstructor(descriptor);

    List<ArgDescriptor> args = descriptor.getArgs();
    assertEquals(2, args.size());
    assertArg(args.get(0), "a", "");
    assertArg(args.get(1), "b", "");
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
    Descriptor descriptor = getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertConstructor(descriptor);

    List<ArgDescriptor> args = descriptor.getArgs();
    assertEquals(3, args.size());
    assertArg(args.get(0), "a", "is for\n     apples.");
    assertArg(args.get(1), "b", "is for bananas.");
  }

  @Test
  public void canGetInterfaceArgs_functionExpression_undocumented() {
    util.compile(path("foo/bar.js"),
        "/** @interface */",
        "function Foo(a, b) {}");
    Descriptor descriptor = getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertInterface(descriptor);

    List<ArgDescriptor> args = descriptor.getArgs();
    assertEquals(2, args.size());
    assertArg(args.get(0), "a", "");
    assertArg(args.get(1), "b", "");
  }

  @Test
  public void canGetInterfaceArgs_functionExpression_documented() {
    util.compile(path("foo/bar.js"),
        "/**",
        " * @param {string} a is for apples.",
        " * @param {string} b is for bananas.",
        " * @interface */",
        "function Foo(a, b) {}");
    Descriptor descriptor = getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertInterface(descriptor);

    List<ArgDescriptor> args = descriptor.getArgs();
    assertEquals(2, args.size());
    assertArg(args.get(0), "a", "is for apples.");
    assertArg(args.get(1), "b", "is for bananas.");
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

    List<Descriptor> descriptors = getDescriptors();
    assertEquals(
        ImmutableList.of("foo", "foo.Foo"),
        getNames(descriptors));
    assertNamespace(descriptors.get(0));
    assertConstructor(descriptors.get(1));
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

    ModuleDescriptor module = getOnlyElement(docRegistry.getModules());

    Descriptor greet = getOnlyElement(module.getExportedProperties());
    assertEquals("greet", greet.getFullName());
    assertTrue(greet.isFunction());

    ArgDescriptor arg = getOnlyElement(greet.getArgs());
    assertEquals("name", arg.getName());
    assertEquals("a name.", arg.getDescription());

    JsDoc jsdoc = greet.getJsDoc();
    assertNotNull(jsdoc);
    assertEquals("a greeting.", jsdoc.getReturnDescription());
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

    ModuleDescriptor module = getOnlyElement(docRegistry.getModules());
    Descriptor descriptor = module.getDescriptor();
    assertTrue(descriptor.isFunction());

    ArgDescriptor arg = getOnlyElement(descriptor.getArgs());
    assertEquals("name", arg.getName());
    assertEquals("a name.", arg.getDescription());

    JsDoc jsdoc = descriptor.getJsDoc();
    assertNotNull(jsdoc);
    assertEquals("a greeting.", jsdoc.getReturnDescription());
  }

  @Test
  public void documentsTypesAfterModuleExportsAssignment_assignedToFunctionVar() {
    createCompiler(ImmutableList.of(path("module.js")));
    util.compile(path("module.js"),
        "/**",
        " * @param {string} name a name.",
        " * @return {string} a greeting.",
        " */",
        "var greet = function(name) { return 'hello, ' + name; };",
        "",
        "/**",
        " * A number.",
        " * @type {number}",
        " */",
        "greet.x = 1234;",
        "",
        "module.exports = greet;");

    ModuleDescriptor module = getOnlyElement(docRegistry.getModules());

    Descriptor descriptor = module.getDescriptor();
    assertTrue(descriptor.isFunction());

    ArgDescriptor arg = getOnlyElement(descriptor.getArgs());
    assertEquals("name", arg.getName());
    assertEquals("a name.", arg.getDescription());

    JsDoc jsdoc = descriptor.getJsDoc();
    assertNotNull(jsdoc);
    assertEquals("a greeting.", jsdoc.getReturnDescription());

    Descriptor x = getOnlyElement(descriptor.getProperties());
    assertEquals("dossier$$module__module.x", x.getFullName());

    jsdoc = x.getJsDoc();
    assertNotNull(jsdoc);
    assertEquals("A number.", jsdoc.getBlockComment());
  }

  private void assertArg(ArgDescriptor arg, String name, String description) {
    assertEquals(name, arg.getName());
    assertEquals(description, arg.getDescription());
  }

  private Path path(String first, String... remaining) {
    return fs.getPath(first, remaining);
  }

  private void createCompiler(Iterable<Path> modules) {
    DossierCompiler compiler = new DossierCompiler(System.err, modules);
    CompilerOptions options = Main.createOptions(fs, compiler, docRegistry);

    util = new CompilerUtil(compiler, options);
  }

  private List<String> getGlobalTypes() {
    return FluentIterable.from(docRegistry.getTypes())
        .filter(new Predicate<Descriptor>() {
          @Override
          public boolean apply(Descriptor input) {
            return docRegistry.isExtern(input.getFullName());
          }
        })
        .transform(new Function<Descriptor, String>() {
          @Override
          public String apply(Descriptor input) {
            return input.getFullName();
          }
        })
        .toList();
  }

  private static void assertConstructor(Descriptor descriptor) {
    assertTrue(descriptor.isConstructor());
    assertFalse(descriptor.isInterface());
    assertFalse(descriptor.isEnum());
  }

  private static void assertInterface(Descriptor descriptor) {
    assertFalse(descriptor.isConstructor());
    assertTrue(descriptor.isInterface());
    assertFalse(descriptor.isEnum());
  }

  private static void assertEnum(Descriptor descriptor) {
    assertFalse(descriptor.isConstructor());
    assertFalse(descriptor.isInterface());
    assertTrue(descriptor.isEnum());
  }

  private static void assertNamespace(Descriptor descriptor) {
    assertFalse(descriptor.isConstructor());
    assertFalse(descriptor.isInterface());
    assertFalse(descriptor.isEnum());
  }

  private List<Descriptor> getDescriptors() {
    return Descriptor.sortByName(docRegistry.getTypes());
  }

  private static List<String> getNames(List<Descriptor> descriptors) {
    return Lists.transform(descriptors, new Function<Descriptor, String>() {
      @Override
      public String apply(Descriptor input) {
        return input.getFullName();
      }
    });
  }
}
