package com.github.jleyba.dossier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(JUnit4.class)
public class LinkerTest {

  @Test
  public void testFilePath_descriptor() {
    Path outputDir = Paths.get("output");
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(outputDir);
    DocRegistry mockRegistry = mock(DocRegistry.class);
    Linker linker = new Linker(mockConfig, mockRegistry);

    Descriptor descriptor = mock(Descriptor.class);
    when(descriptor.getFullName()).thenReturn("foo.Bar");
    when(descriptor.isInterface()).thenReturn(true);
    assertEquals(
        Paths.get("output/interface_foo_Bar.html"),
        linker.getFilePath(descriptor));

    when(descriptor.isInterface()).thenReturn(false);
    when(descriptor.isConstructor()).thenReturn(true);
    assertEquals(
        Paths.get("output/class_foo_Bar.html"),
        linker.getFilePath(descriptor));

    when(descriptor.isConstructor()).thenReturn(false);
    when(descriptor.isEnum()).thenReturn(true);
    assertEquals(
        Paths.get("output/enum_foo_Bar.html"),
        linker.getFilePath(descriptor));

    when(descriptor.isEnum()).thenReturn(false);
    assertEquals(
        Paths.get("output/namespace_foo_Bar.html"),
        linker.getFilePath(descriptor));
  }

  @Test
  public void testGetFilePath_source() {
    Path outputDir = Paths.get("output");
    Path srcPrefix = Paths.get("/apples/oranges");

    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(outputDir);
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    DocRegistry mockRegistry = mock(DocRegistry.class);
    Linker linker = new Linker(mockConfig, mockRegistry);

    assertEquals(
        outputDir.resolve("source_one_two_three.js.src.html"),
        linker.getFilePath(srcPrefix.resolve("one/two/three.js")));
  }

  @Test
  public void testGetLink() {
    Path outputDir = Paths.get("");
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(outputDir);
    DocRegistry mockRegistry = mock(DocRegistry.class);
    Linker linker = new Linker(mockConfig, mockRegistry);

    assertNull("No types are known", linker.getLink("goog.Foo"));

    Descriptor mockGoog = mock(Descriptor.class);
    when(mockRegistry.getType("goog")).thenReturn(mockGoog);
    when(mockGoog.getFullName()).thenReturn("goog");
    assertEquals("namespace_goog.html#goog.Foo", linker.getLink("goog.Foo"));
    assertEquals("namespace_goog.html#goog.Foo", linker.getLink("goog.Foo()"));

    Descriptor mockGoogFoo = mock(Descriptor.class);
    when(mockRegistry.getType("goog.Foo")).thenReturn(mockGoogFoo);
    when(mockGoogFoo.getFullName()).thenReturn("goog.Foo");
    assertEquals("namespace_goog_Foo.html", linker.getLink("goog.Foo"));
    assertEquals("namespace_goog_Foo.html", linker.getLink("goog.Foo()"));

    assertEquals("namespace_goog_Foo.html#goog.Foo.bar", linker.getLink("goog.Foo.bar"));
    assertEquals("namespace_goog_Foo.html#goog.Foo.bar", linker.getLink("goog.Foo.bar()"));
    assertEquals("namespace_goog_Foo.html#goog.Foo$bar", linker.getLink("goog.Foo#bar"));
    assertEquals("namespace_goog_Foo.html#goog.Foo$bar", linker.getLink("goog.Foo#bar()"));

    assertEquals("namespace_goog_Foo.html", linker.getLink("goog.Foo.prototype"));
  }

  @Test
  public void testGetSourcePath_sourceInfoNotFound() {
    Linker linker = new Linker(mock(Config.class), mock(DocRegistry.class));

    Descriptor mockDescriptor = mock(Descriptor.class);
    assertNull(linker.getSourcePath(mockDescriptor));
  }

  @Test
  public void testGetSourcePath_noLineNumber() {
    Path output = FileSystems.getDefault().getPath("foo/bar/baz");
    Path srcPrefix = FileSystems.getDefault().getPath("/alphabet/soup");

    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(output);
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Descriptor mockDescriptor = mock(Descriptor.class);
    when(mockDescriptor.getSource()).thenReturn("/alphabet/soup/a/b/c");

    Linker linker = new Linker(mockConfig, mock(DocRegistry.class));
    assertEquals(
        "source_a_b_c.src.html",
        linker.getSourcePath(mockDescriptor));
  }

  @Test
  public void testGetSourcePath_withLineNumber() {
    Path output = FileSystems.getDefault().getPath("foo/bar/baz");
    Path srcPrefix = FileSystems.getDefault().getPath("/alphabet/soup");

    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(output);
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Descriptor mockDescriptor = mock(Descriptor.class);
    when(mockDescriptor.getSource()).thenReturn("/alphabet/soup/a/b/c");
    when(mockDescriptor.getLineNum()).thenReturn(123);

    Linker linker = new Linker(mockConfig, mock(DocRegistry.class));
    assertEquals(
        "source_a_b_c.src.html#l122",
        linker.getSourcePath(mockDescriptor));
  }
}
