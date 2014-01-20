package com.github.jleyba.dossier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
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
    Linker linker = new Linker(mockConfig, new DocRegistry());

    Descriptor descriptor = object("foo.Bar")
        .setType(TestDescriptorBuilder.Type.INTERFACE)
        .build();
    assertEquals(
        Paths.get("output/interface_foo_Bar.html"),
        linker.getFilePath(descriptor));

    descriptor = object("foo.Bar")
        .setType(TestDescriptorBuilder.Type.CLASS)
        .build();
    assertEquals(
        Paths.get("output/class_foo_Bar.html"),
        linker.getFilePath(descriptor));

    descriptor = object("foo.Bar")
        .setType(TestDescriptorBuilder.Type.ENUM)
        .build();
    assertEquals(
        Paths.get("output/enum_foo_Bar.html"),
        linker.getFilePath(descriptor));

    descriptor = object("foo.Bar").build();
    assertEquals(
        Paths.get("output/namespace_foo_Bar.html"),
        linker.getFilePath(descriptor));
  }

  @Test
  public void testGetFilePath_module() {
    Path modulePrefix = Paths.get("src/foo");
    Path outputDir = Paths.get("output");

    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(outputDir);
    when(mockConfig.getModulePrefix()).thenReturn(modulePrefix);

    Linker linker = new Linker(mockConfig, new DocRegistry());

    ModuleDescriptor mockModule = mock(ModuleDescriptor.class);

    when(mockModule.getPath()).thenReturn(modulePrefix.resolve("bar/baz.js"));
    assertEquals(
        Paths.get("output/module_bar_baz.html"),
        linker.getFilePath(mockModule));

    when(mockModule.getPath()).thenReturn(modulePrefix.resolve("bar/baz/index.js"));
    assertEquals(
        Paths.get("output/module_bar_baz.html"),
        linker.getFilePath(mockModule));
  }

  @Test
  public void testGetFilePath_source() {
    Path outputDir = Paths.get("output");
    Path srcPrefix = Paths.get("/apples/oranges");

    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(outputDir);
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Linker linker = new Linker(mockConfig, new DocRegistry());

    assertEquals(
        outputDir.resolve("source/one/two/three.js.src.html"),
        linker.getFilePath(srcPrefix.resolve("one/two/three.js")));
  }

  @Test
  public void testGetFilePath_exportedDescriptor() {
    Path srcPrefix = Paths.get("/root/src");
    Path modulePrefix = srcPrefix.resolve("foo");
    Path outputDir = Paths.get("/root/output");

    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(outputDir);
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);
    when(mockConfig.getModulePrefix()).thenReturn(modulePrefix);

    Linker linker = new Linker(mockConfig, new DocRegistry());

    ModuleDescriptor module = mock(ModuleDescriptor.class);
    when(module.getPath()).thenReturn(modulePrefix.resolve("bar/baz.js"));

    Descriptor descriptor = object("foo.Bar")
        .setType(TestDescriptorBuilder.Type.CLASS)
        .setModule(module)
        .build();

    assertEquals(
        Paths.get("/root/output/module_bar_baz_class_foo_Bar.html"),
        linker.getFilePath(descriptor));
  }

  @Test
  public void testGetLink() {
    Path outputDir = Paths.get("");
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(outputDir);
    DocRegistry registry = new DocRegistry();
    Linker linker = new Linker(mockConfig, registry);

    assertNull("No types are known", linker.getLink("goog.Foo"));

    Descriptor googFoo = object("goog.Foo")
        .addStaticProperty(object("goog.Foo.bar"))
        .build();
    Descriptor goog = object("goog").addStaticProperty(googFoo).build();

    registry.addType(goog);
    assertEquals("namespace_goog.html#goog.Foo", linker.getLink("goog.Foo"));
    assertEquals("namespace_goog.html#goog.Foo", linker.getLink("goog.Foo()"));

    registry.addType(googFoo);
    assertEquals("namespace_goog_Foo.html", linker.getLink("goog.Foo"));
    assertEquals("namespace_goog_Foo.html", linker.getLink("goog.Foo()"));

    assertEquals("namespace_goog_Foo.html#goog.Foo.bar", linker.getLink("goog.Foo.bar"));
    assertEquals("namespace_goog_Foo.html#goog.Foo.bar", linker.getLink("goog.Foo.bar()"));
  }

  @Test
  public void testGetLink_global() {
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(Paths.get(""));

    DocRegistry registry = new DocRegistry();
    Linker linker = new Linker(mockConfig, registry);

    Descriptor goog = object("goog")
        .addStaticProperty(object("goog.Foo"))
        .build();
    registry.addType(goog);

    assertEquals("namespace_goog.html", linker.getLink("goog"));
    assertEquals("namespace_goog.html#goog.Foo", linker.getLink("goog.Foo"));
    assertNull(linker.getLink("goog.Foo.bar"));
  }

  @Test
  public void testGetLink_externs() {
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(Paths.get(""));

    DocRegistry registry = new DocRegistry();
    Linker linker = new Linker(mockConfig, registry);

    Descriptor element = object("Element")
        .addInstanceProperty(object("Element.prototype.nodeType"))
        .build();
    registry.addExtern(element);

    assertEquals(
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String",
        linker.getLink("string"));
    assertNull(linker.getLink("Element"));
    assertNull(linker.getLink("Element.prototype.nodeType"));
    assertNull(linker.getLink("Element#nodeType"));
  }

  @Test
  public void testGetLink_byDescriptor() {
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(Paths.get(""));

    DocRegistry registry = new DocRegistry();
    Linker linker = new Linker(mockConfig, registry);

    Descriptor str = object("String").build();
    registry.addExtern(str);

    assertEquals(
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String",
        linker.getLink(str));
  }

  @Test
  public void testGetLink_prototype() {
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(Paths.get(""));

    Descriptor googFoo = object("goog.Foo")
        .setType(TestDescriptorBuilder.Type.CLASS)
        .addInstanceProperty(object("goog.Foo.prototype.bar"))
        .build();

    DocRegistry registry = new DocRegistry();
    registry.addType(googFoo);

    Linker linker = new Linker(mockConfig, registry);

    assertEquals("class_goog_Foo.html", linker.getLink("goog.Foo"));
    assertEquals("class_goog_Foo.html", linker.getLink("goog.Foo.prototype"));
    assertEquals("class_goog_Foo.html#bar", linker.getLink("goog.Foo#bar"));
    assertEquals("class_goog_Foo.html#bar", linker.getLink("goog.Foo#bar()"));
    assertEquals("class_goog_Foo.html#bar", linker.getLink("goog.Foo.prototype.bar"));
    assertEquals("class_goog_Foo.html#bar", linker.getLink("goog.Foo.prototype.bar()"));
  }

  @Test
  public void testGetLink_module() {
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(Paths.get(""));
    when(mockConfig.getModulePrefix()).thenReturn(Paths.get(""));

    ModuleDescriptor module = object("foo.bar.exports")
        .setSource("foo/bar.js")
        .buildModule();
    DocRegistry registry = new DocRegistry();
    registry.addModule(module);

    Linker linker = new Linker(mockConfig, registry);
    assertEquals("module_foo_bar.html", linker.getLink("foo.bar.exports"));
    assertEquals("module_foo_bar.html", linker.getLink("foo.bar"));
  }

  @Test
  public void testGetLink_moduleExportedClass() {
    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(Paths.get(""));
    when(mockConfig.getModulePrefix()).thenReturn(Paths.get(""));

    ModuleDescriptor module = object("foo.bar.exports")
        .setSource("foo/bar.js")
        .buildModule();
    module.addExportedProperty(object("SomeClass")
        .setType(TestDescriptorBuilder.Type.CLASS)
        .build());

    DocRegistry registry = new DocRegistry();
    registry.addModule(module);

    Linker linker = new Linker(mockConfig, registry);
    assertEquals("module_foo_bar_class_SomeClass.html",
        linker.getLink("foo.bar.exports.SomeClass"));
    assertEquals("module_foo_bar_class_SomeClass.html", linker.getLink("foo.bar.SomeClass"));
  }

  @Test
  public void testGetSourcePath_sourceInfoNotFound() {
    Linker linker = new Linker(mock(Config.class), new DocRegistry());

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

    Descriptor descriptor = object("Foo")
        .setSource("/alphabet/soup/a/b/c")
        .build();

    Linker linker = new Linker(mockConfig, new DocRegistry());
    assertEquals("source/a/b/c.src.html", linker.getSourcePath(descriptor));
  }

  @Test
  public void testGetSourcePath_withLineNumber() {
    Path output = FileSystems.getDefault().getPath("foo/bar/baz");
    Path srcPrefix = FileSystems.getDefault().getPath("/alphabet/soup");

    Config mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(output);
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Descriptor descriptor = object("foo")
        .setSource("/alphabet/soup/a/b/c")
        .setLineNum(123)
        .build();

    Linker linker = new Linker(mockConfig, new DocRegistry());
    assertEquals("source/a/b/c.src.html#l123", linker.getSourcePath(descriptor));
  }

  @Test
  public void getExternLink_primitiveType() {
    Linker linker = new Linker(mock(Config.class), new DocRegistry());
    assertEquals(
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String",
        linker.getLink("string"));
    assertEquals(
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String",
        linker.getLink("String"));
    assertEquals(
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Null",
        linker.getLink("null"));
  }

  @Test
  public void getExternLink_noSeeTags() {
    Descriptor descriptor = object("Foo").build();

    DocRegistry registry = new DocRegistry();
    registry.addExtern(descriptor);

    Linker linker = new Linker(mock(Config.class), registry);

    assertNull(linker.getLink("Foo"));
  }

  @Test
  public void getExternLink() {
    JsDoc jsDoc = mock(JsDoc.class);
    when(jsDoc.getSeeClauses()).thenReturn(ImmutableList.of(
        "not a valid URL",
        "http://www.example.com"));

    Descriptor descriptor = object("Foo").setJsDoc(jsDoc).build();

    DocRegistry registry = new DocRegistry();
    registry.addExtern(descriptor);

    Linker linker = new Linker(mock(Config.class), registry);

    assertEquals("http://www.example.com", linker.getLink("Foo"));
  }

  private static TestDescriptorBuilder object(String name) {
    return new TestDescriptorBuilder(name);
  }
}
