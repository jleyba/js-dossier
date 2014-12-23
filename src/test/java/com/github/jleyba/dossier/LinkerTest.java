package com.github.jleyba.dossier;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DossierCompiler;
import com.google.javascript.jscomp.DossierModule;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
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
  private TypeRegistry typeRegistry;
  private CompilerUtil util;
  private Linker linker;

  @Before
  public void setUp() {
    fileSystem = Jimfs.newFileSystem();
    outputDir = fileSystem.getPath("/root/output");

    mockConfig = mock(Config.class);
    when(mockConfig.getModulePrefix()).thenReturn(fileSystem.getPath("/modules"));
    when(mockConfig.getOutput()).thenReturn(outputDir);

    ErrorReporter errorReporter = mock(ErrorReporter.class);
    JSTypeRegistry jsTypeRegistry = new JSTypeRegistry(errorReporter);
    typeRegistry = new TypeRegistry(jsTypeRegistry);

    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.<Path>of());
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);

    util = new CompilerUtil(compiler, options);

    linker = new Linker(mockConfig, typeRegistry);
  }

  @Test
  public void testGetDisplayName() {
    NominalType type = createType("foo.bar.Baz");
    assertThat(linker.getDisplayName(type)).isEqualTo("foo.bar.Baz");
  }

  @Test
  public void testGetDisplayName_moduleExports() {
    NominalType type = createType(
        "dossier$$module__$modules$foo$bar$baz",
        createModule(fileSystem.getPath("/modules/foo/bar/baz.js")));

    assertThat(linker.getDisplayName(type)).isEqualTo("foo/bar/baz");
  }

  @Test
  public void testGetDisplayName_moduleExportsAsIndexFile() {
    NominalType type = createType(
        "dossier$$module__$modules$foo$bar",
        createModule(fileSystem.getPath("/modules/foo/bar/index.js")));

    assertThat(linker.getDisplayName(type)).isEqualTo("foo/bar");
  }

  @Test
  public void testFilePath_nominalType() {
    JSType jsType = mock(JSType.class);
    NominalType type = createType("foo.Bar", jsType);

    when(jsType.isInterface()).thenReturn(true);
    assertEquals(
        outputDir.resolve("interface_foo_Bar.html"),
        linker.getFilePath(type));

    when(jsType.isInterface()).thenReturn(false);
    when(jsType.isConstructor()).thenReturn(true);
    assertEquals(
        outputDir.resolve("class_foo_Bar.html"),
        linker.getFilePath(type));

    when(jsType.isConstructor()).thenReturn(false);
    when(jsType.isEnumType()).thenReturn(true);
    assertEquals(
        outputDir.resolve("enum_foo_Bar.html"),
        linker.getFilePath(type));

    when(jsType.isEnumType()).thenReturn(false);
    assertEquals(
        outputDir.resolve("namespace_foo_Bar.html"),
        linker.getFilePath(type));
  }

  @Test
  public void testGetFilePath_moduleExports() {
    NominalType type = createType(
        "dossier$$module__$modules$foo$bar$baz",
        createModule(fileSystem.getPath("/modules/foo/bar/baz.js")));

    assertEquals(
        outputDir.resolve("module_foo_bar_baz.html"),
        linker.getFilePath(type));
  }

  @Test
  public void testGetFilePath_moduleType() {
    JSType jsType = mock(JSType.class);
    when(jsType.isConstructor()).thenReturn(true);

    NominalType type = createType("Baz", jsType,
        createModule(fileSystem.getPath("/modules/foo/bar/index.js")));

    assertEquals(
        outputDir.resolve("module_foo_bar_class_Baz.html"),
        linker.getFilePath(type));
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
  public void testGetLink_unknownType() {
    assertNull("No types are known", linker.getLink("goog.Foo"));
  }

  @Test
  public void testGetLink_namespace() {
    NominalType type = createType("foo.bar");
    typeRegistry.addType(type);
    assertEquals("namespace_foo_bar.html", linker.getLink("foo.bar"));
  }

  @Test
  public void testGetLink_externs() {
    String link =
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String";

    assertEquals(link, linker.getLink("string"));
    assertEquals(link, linker.getLink("String"));
    assertEquals(link, linker.getLink("String.prototype.indexOf"));
    assertEquals(link, linker.getLink("String#indexOf"));
    assertEquals(link, linker.getLink("String#"));
    assertEquals(link, linker.getLink("String.fromCharCode"));
  }

  @Test
  public void testGetLink_staticProperty() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo.bar');",
        "",
        "foo.bar.baz = function() {};");

    assertNotNull(typeRegistry.getNominalType("foo.bar"));
    assertEquals("namespace_foo_bar.html#bar.baz", linker.getLink("foo.bar.baz"));
    assertEquals("namespace_foo_bar.html#bar.unknown", linker.getLink("foo.bar.unknown"));
    assertEquals("namespace_foo_bar.html", linker.getLink("foo.bar.prototype.baz"));
  }

  @Test
  public void testGetLink_prototype() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @constructor */",
        "foo.Bar = function() {};",
        "foo.Bar.baz = function() {};",
        "foo.Bar.prototype.bar = function() {}");

    assertNotNull(typeRegistry.getNominalType("foo.Bar"));

    assertEquals("class_foo_Bar.html", linker.getLink("foo.Bar"));
    assertEquals("class_foo_Bar.html", linker.getLink("foo.Bar#"));
    assertEquals("class_foo_Bar.html#bar", linker.getLink("foo.Bar#bar"));
    assertEquals("class_foo_Bar.html#bar", linker.getLink("foo.Bar#bar()"));
    assertEquals("class_foo_Bar.html", linker.getLink("foo.Bar.prototype"));
    assertEquals("class_foo_Bar.html#bar", linker.getLink("foo.Bar.prototype.bar"));
    assertEquals("class_foo_Bar.html#bar", linker.getLink("foo.Bar.prototype.bar()"));
    assertEquals("class_foo_Bar.html#unknown", linker.getLink("foo.Bar.prototype.unknown()"));
  }

  @Test
  public void testGetSourcePath_nullNode() {
    assertEquals("", linker.getSourcePath(null));
  }

  @Test
  public void testGetSourcePath_externNode() {
    Node node = mock(Node.class);
    when(node.isFromExterns()).thenReturn(true);
    assertEquals("", linker.getSourcePath(node));
  }

  @Test
  public void testGetSourcePath_noLineNumber() {
    Path srcPrefix = fileSystem.getPath("/alphabet/soup");
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Node node = mock(Node.class);
    when(node.getSourceFileName()).thenReturn("/alphabet/soup/a/b/c");
    assertEquals("source/a/b/c.src.html", linker.getSourcePath(node));
  }

  @Test
  public void testGetSourcePath_withLineNumber() {
    Path srcPrefix = fileSystem.getPath("/alphabet/soup");
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Node node = mock(Node.class);
    when(node.getSourceFileName()).thenReturn("/alphabet/soup/a/b/c");
    when(node.getLineno()).thenReturn(123);
    assertEquals("source/a/b/c.src.html#l123", linker.getSourcePath(node));
  }

  @Test
  public void testGetSourcePath_onLineZero() {
    Path srcPrefix = fileSystem.getPath("/alphabet/soup");
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Node node = mock(Node.class);
    when(node.getSourceFileName()).thenReturn("/alphabet/soup/a/b/c");
    when(node.getLineno()).thenReturn(0);
    assertEquals("source/a/b/c.src.html", linker.getSourcePath(node));
  }

  private static NominalType createType(String name) {
    return createType(name, mock(JSType.class), null);
  }

  private static NominalType createType(String name, DossierModule module) {
    return createType(name, mock(JSType.class), module);
  }

  private static NominalType createType(String name, JSType type) {
    return createType(name, type, null);
  }

  private static NominalType createType(String name, JSType type, DossierModule module) {
    return new NominalType(
        null,
        name,
        new NominalType.TypeDescriptor(type),
        mock(Node.class),
        null,
        module);
  }

  private static DossierModule createModule(Path path) {
    Node node = mock(Node.class);
    when(node.isScript()).thenReturn(true);
    when(node.getSourceFileName()).thenReturn(path.toString());
    return new DossierModule(node, path);
  }
}
