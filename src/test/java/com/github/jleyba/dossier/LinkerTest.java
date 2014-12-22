package com.github.jleyba.dossier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class LinkerTest {

  private FileSystem fileSystem;

  private Path outputDir;
  private Config mockConfig;
  private DocRegistry docRegistry;

  private Linker linker;

  @Before
  public void setUp() {
    fileSystem = Jimfs.newFileSystem();
    outputDir = fileSystem.getPath("/root/output");

    mockConfig = mock(Config.class);
    when(mockConfig.getOutput()).thenReturn(outputDir);

    ErrorReporter errorReporter = mock(ErrorReporter.class);
    JSTypeRegistry typeRegistry = new JSTypeRegistry(errorReporter);
    docRegistry = new DocRegistry(typeRegistry);
    linker = new Linker(mockConfig, docRegistry);
  }

  @Test
  public void testFilePath_descriptor() {
    Descriptor descriptor = object("foo.Bar")
        .setType(TestDescriptorBuilder.Type.INTERFACE)
        .build();
    assertEquals(
        outputDir.resolve("interface_foo_Bar.html"),
        linker.getFilePath(descriptor));

    descriptor = object("foo.Bar")
        .setType(TestDescriptorBuilder.Type.CLASS)
        .build();
    assertEquals(
        outputDir.resolve("class_foo_Bar.html"),
        linker.getFilePath(descriptor));

    descriptor = object("foo.Bar")
        .setType(TestDescriptorBuilder.Type.ENUM)
        .build();
    assertEquals(
        outputDir.resolve("enum_foo_Bar.html"),
        linker.getFilePath(descriptor));

    descriptor = object("foo.Bar").build();
    assertEquals(
        outputDir.resolve("namespace_foo_Bar.html"),
        linker.getFilePath(descriptor));
  }

  @Test
  public void testGetFilePath_module() {
    Path modulePrefix = fileSystem.getPath("src/foo");
    when(mockConfig.getModulePrefix()).thenReturn(modulePrefix);

    ModuleDescriptor mockModule = mock(ModuleDescriptor.class);

    when(mockModule.getPath()).thenReturn(modulePrefix.resolve("bar/baz.js"));
    assertEquals(
        outputDir.resolve("module_bar_baz.html"),
        linker.getFilePath(mockModule));

    when(mockModule.getPath()).thenReturn(modulePrefix.resolve("bar/baz/index.js"));
    assertEquals(
        outputDir.resolve("module_bar_baz.html"),
        linker.getFilePath(mockModule));
  }

  @Test
  public void testGetFilePath_source() {
    Path srcPrefix = fileSystem.getPath("/apples/oranges");
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    assertEquals(
        outputDir.resolve("source/one/two/three.js.src.html"),
        linker.getFilePath(srcPrefix.resolve("one/two/three.js")));
  }

  @Test
  public void testGetFilePath_exportedDescriptor() {
    Path srcPrefix = fileSystem.getPath("/root/src");
    Path modulePrefix = srcPrefix.resolve("foo");

    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);
    when(mockConfig.getModulePrefix()).thenReturn(modulePrefix);

    ModuleDescriptor module = mock(ModuleDescriptor.class);
    when(module.getPath()).thenReturn(modulePrefix.resolve("bar/baz.js"));

    Descriptor descriptor = object("foo.Bar")
        .setType(TestDescriptorBuilder.Type.CLASS)
        .setModule(module)
        .build();

    assertEquals(
        fileSystem.getPath("/root/output/module_bar_baz_class_foo_Bar.html"),
        linker.getFilePath(descriptor));
  }

  @Test
  public void testGetLink() {
    assertNull("No types are known", linker.getLink("goog.Foo"));

    Descriptor googFoo = object("goog.Foo")
        .addStaticProperty(object("goog.Foo.bar"))
        .build();
    Descriptor goog = object("goog").addStaticProperty(googFoo).build();

    docRegistry.addType(goog);
    assertEquals("namespace_goog.html#goog.Foo", linker.getLink("goog.Foo"));
    assertEquals("namespace_goog.html#goog.Foo", linker.getLink("goog.Foo()"));

    docRegistry.addType(googFoo);
    assertEquals("namespace_goog_Foo.html", linker.getLink("goog.Foo"));
    assertEquals("namespace_goog_Foo.html", linker.getLink("goog.Foo()"));

    assertEquals("namespace_goog_Foo.html#goog.Foo.bar", linker.getLink("goog.Foo.bar"));
    assertEquals("namespace_goog_Foo.html#goog.Foo.bar", linker.getLink("goog.Foo.bar()"));
  }

  @Test
  public void testGetLink_global() {
    Descriptor goog = object("goog")
        .addStaticProperty(object("goog.Foo"))
        .build();
    docRegistry.addType(goog);

    assertEquals("namespace_goog.html", linker.getLink("goog"));
    assertEquals("namespace_goog.html#goog.Foo", linker.getLink("goog.Foo"));
    assertNull(linker.getLink("goog.Foo.bar"));
  }

  @Test
  public void testGetLink_externs() {
    Descriptor element = object("Element")
        .addInstanceProperty(object("Element.prototype.nodeType"))
        .build();
    docRegistry.addExtern(element);

    assertEquals(
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String",
        linker.getLink("string"));
    assertNull(linker.getLink("Element"));
    assertNull(linker.getLink("Element.prototype.nodeType"));
    assertNull(linker.getLink("Element#nodeType"));
  }

  @Test
  public void testGetLink_byDescriptor() {
    when(mockConfig.getOutput()).thenReturn(fileSystem.getPath(""));

    Descriptor str = object("String").build();
    docRegistry.addExtern(str);

    assertEquals(
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String",
        linker.getLink(str));
  }

  @Test
  public void testGetLink_prototype() {
    when(mockConfig.getOutput()).thenReturn(fileSystem.getPath(""));

    Descriptor googFoo = object("goog.Foo")
        .setType(TestDescriptorBuilder.Type.CLASS)
        .addInstanceProperty(object("goog.Foo.prototype.bar"))
        .build();

    docRegistry.addType(googFoo);

    assertEquals("class_goog_Foo.html", linker.getLink("goog.Foo"));
    assertEquals("class_goog_Foo.html", linker.getLink("goog.Foo.prototype"));
    assertEquals("class_goog_Foo.html#bar", linker.getLink("goog.Foo#bar"));
    assertEquals("class_goog_Foo.html#bar", linker.getLink("goog.Foo#bar()"));
    assertEquals("class_goog_Foo.html#bar", linker.getLink("goog.Foo.prototype.bar"));
    assertEquals("class_goog_Foo.html#bar", linker.getLink("goog.Foo.prototype.bar()"));
  }

  @Test
  public void testGetLink_module() {
    when(mockConfig.getModulePrefix()).thenReturn(fileSystem.getPath(""));

    ModuleDescriptor module = object("foo.bar")
        .setSource("foo/bar.js")
        .buildModule();
    docRegistry.addModule(module);

    assertEquals("module_foo_bar.html", linker.getLink("foo.bar"));
  }

  @Test
  public void testGetLink_moduleExportedClass() {
    when(mockConfig.getModulePrefix()).thenReturn(fileSystem.getPath(""));

    ModuleDescriptor module = object("foo.bar")
        .setSource("foo/bar.js")
        .addStaticProperty(object("SomeClass")
            .setType(TestDescriptorBuilder.Type.CLASS)
            .build())
        .buildModule();

    docRegistry.addModule(module);

    assertEquals("module_foo_bar_class_SomeClass.html", linker.getLink("foo.bar.SomeClass"));
  }

  @Test
  public void testGetSourcePath_sourceInfoNotFound() {
    Descriptor mockDescriptor = mock(Descriptor.class);
    assertNull(linker.getSourcePath(mockDescriptor));
  }

  @Test
  public void testGetSourcePath_noLineNumber() {
    Path srcPrefix = fileSystem.getPath("/alphabet/soup");

    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Descriptor descriptor = object("Foo")
        .setSource("/alphabet/soup/a/b/c")
        .build();

    assertEquals("source/a/b/c.src.html", linker.getSourcePath(descriptor));
  }

  @Test
  public void testGetSourcePath_withLineNumber() {
    Path srcPrefix = fileSystem.getPath("/alphabet/soup");

    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Descriptor descriptor = object("foo")
        .setSource("/alphabet/soup/a/b/c")
        .setLineNum(123)
        .build();

    assertEquals("source/a/b/c.src.html#l123", linker.getSourcePath(descriptor));
  }

  @Test
  public void getExternLink_primitiveType() {
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

    docRegistry.addExtern(descriptor);

    assertNull(linker.getLink("Foo"));
  }

  @Test
  public void getExternLink() {
    JsDoc jsDoc = mock(JsDoc.class);
    when(jsDoc.getSeeClauses()).thenReturn(ImmutableList.of(
        "not a valid URL",
        "http://www.example.com"));

    Descriptor descriptor = object("Foo").setJsDoc(jsDoc).build();

    docRegistry.addExtern(descriptor);

    assertEquals("http://www.example.com", linker.getLink("Foo"));
  }

  private TestDescriptorBuilder object(String name) {
    return new TestDescriptorBuilder(name, fileSystem);
  }
}
