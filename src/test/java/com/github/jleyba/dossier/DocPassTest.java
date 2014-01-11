package com.github.jleyba.dossier;

import static com.github.jleyba.dossier.CompilerUtil.createSourceFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DossierCompiler;
import com.google.javascript.jscomp.SourceFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for {@link DocPass}.
 */
@RunWith(JUnit4.class)
public class DocPassTest {

  private DocRegistry docRegistry;
  private CompilerUtil util;

  @Before
  public void setUp() {
    docRegistry = new DocRegistry();

    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.<Path>of());
    CompilerOptions options = Main.createOptions(compiler, docRegistry);

    util = new CompilerUtil(compiler, options);
  }

  @Test
  public void recordsFileOverviewComments() throws IOException {
    SourceFile bar = createSourceFile(path("foo/bar.js"),
        "/** @fileoverview This is a file overview",
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
    assertFalse(Iterables.isEmpty(docRegistry.getTypes()));
  }

  @Test
  public void recordsGlobalConstructors() throws IOException {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "function Foo() {}");
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertConstructor(descriptor);
    assertTrue(descriptor.getArgs().isEmpty());
  }

  @Test
  public void recordsGlobalInterfaces() {
    util.compile(path("foo/bar.js"),
        "/** @interface */",
        "function Foo() {}");
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertInterface(descriptor);
    assertTrue(descriptor.getArgs().isEmpty());
  }

  @Test
  public void recordsGlobalEnums() {
    util.compile(path("foo/bar.js"),
        "/** @enum */",
        "var Foo = {};");
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
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

    List<Descriptor> descriptors = getDescriptors();
    assertEquals(
        ImmutableList.of("foo", "foo.bar", "foo.baz"),
        getNames(descriptors));
    assertNamespace(descriptors.get(0));
    assertNamespace(descriptors.get(1));
    assertNamespace(descriptors.get(2));
  }

  @Test
  public void functionInstancesAreNotDocumentedAsConstructors() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo');",
        "/** @type {!Function} */",
        "var foo = Function;");

    List<Descriptor> descriptors = getDescriptors();
    assertEquals(ImmutableList.of("foo"), getNames(descriptors));
    assertNamespace(descriptors.get(0));
  }

  @Test
  public void canGetConstructorArgs_functionExpression_undocumented() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "function Foo(a, b) {}");
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
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
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
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
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
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
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertInterface(descriptor);

    List<ArgDescriptor> args = descriptor.getArgs();
    assertEquals(2, args.size());
    assertArg(args.get(0), "a", "is for apples.");
    assertArg(args.get(1), "b", "is for bananas.");
  }

  @Test
  public void documentsClassDefinedInAnonymousFunction() {
    util.compile(path("foo.js"),
        "var foo = foo || {};",
        "(function(foo) {",
        "  /**",
        "   * @param {string} a .",
        "   * @param {string} b .",
        "   */",
        "  foo.bar = function(a, b) {};",
        "})(foo);");

    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
    assertEquals("foo", descriptor.getFullName());
    assertNamespace(descriptor);

    descriptor = Iterables.getOnlyElement(descriptor.getProperties());
    assertEquals("foo.bar", descriptor.getFullName());
    assertTrue(descriptor.isFunction());
  }

  private void assertArg(ArgDescriptor arg, String name, String description) {
    assertEquals(name, arg.getName());
    assertEquals(description, arg.getDescription());
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

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }
}
