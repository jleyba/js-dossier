package com.github.jleyba.dossier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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

    Compiler compiler = new Compiler(System.err);
    CompilerOptions options = Main.createOptions(compiler, docRegistry);

    util = new CompilerUtil(compiler, options);
  }

  @Test
  public void ignoresUndocumentedFunctions() {
    util.compile(path("foo/bar.js"), "function Foo() {}");
    assertFalse(Iterables.isEmpty(docRegistry.getTypes()));
  }

  @Test
  public void recordsGlobalConstructors() {
    util.compile(path("foo/bar.js"),
        "/** @constructor */",
        "function Foo() {}");
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertConstructor(descriptor);
  }

  @Test
  public void recordsGlobalInterfaces() {
    util.compile(path("foo/bar.js"),
        "/** @interface */",
        "function Foo() {}");
    Descriptor descriptor = Iterables.getOnlyElement(docRegistry.getTypes());
    assertEquals("Foo", descriptor.getFullName());
    assertInterface(descriptor);
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
  public void factoriesAreNotDocumentedAsConstructors() {
    util.compile(path("foo/bar.js"),
        "goog.provide('foo');",
        "/** @constructor */",
        "foo.Bar = function() {};",
        "/** @type {function(new: foo.Bar)} */",
        "foo.createBar = function() { return new foo.Bar(); };");

    List<Descriptor> descriptors = getDescriptors();
    assertEquals(
        ImmutableList.of("foo", "foo.Bar", "foo.createBar"),
        getNames(descriptors));
    assertNamespace(descriptors.get(0));
    assertConstructor(descriptors.get(1));
    assertNamespace(descriptors.get(2));
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
