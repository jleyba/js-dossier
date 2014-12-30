package com.github.jleyba.dossier;

import static com.github.jleyba.dossier.CompilerUtil.createSourceFile;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DossierCompiler;
import com.google.javascript.jscomp.DossierModule;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.Property;
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
  public void testGetDisplayName_closureModuleExports() {
    NominalType type = createType("foo.bar.baz", createModule("foo.bar.baz"));
    assertThat(linker.getDisplayName(type)).isEqualTo("foo.bar.baz");
  }

  @Test
  public void testGetDisplayName_moduleExports() {
    NominalType type = createType(
        "dossier$$module__$modules$foo$bar$baz",
        createCommonJsModule(fileSystem.getPath("/modules/foo/bar/baz.js")));

    assertThat(linker.getDisplayName(type)).isEqualTo("foo/bar/baz");
  }

  @Test
  public void testGetDisplayName_moduleExportsAsIndexFile() {
    NominalType type = createType(
        "dossier$$module__$modules$foo$bar",
        createCommonJsModule(fileSystem.getPath("/modules/foo/bar/index.js")));

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
  public void testGetFilePath_closureModuleExports() {
    NominalType type = createType("foo.bar", createModule("foo.bar"));
    assertEquals(outputDir.resolve("namespace_foo_bar.html"), linker.getFilePath(type));
  }

  @Test
  public void testGetFilePath_moduleExports() {
    NominalType type = createType(
        "dossier$$module__$modules$foo$bar$baz",
        createCommonJsModule(fileSystem.getPath("/modules/foo/bar/baz.js")));

    assertEquals(
        outputDir.resolve("module_foo_bar_baz.html"),
        linker.getFilePath(type));
  }

  @Test
  public void testGetFilePath_closureModuleType() {
    JSType jsType = mock(JSType.class);
    when(jsType.isConstructor()).thenReturn(true);

    NominalType type = createType("bar", jsType, createModule("bar"));
    assertEquals(
        outputDir.resolve("class_bar.html"),
        linker.getFilePath(type));
  }

  @Test
  public void testGetFilePath_moduleType() {
    JSType jsType = mock(JSType.class);
    when(jsType.isConstructor()).thenReturn(true);

    NominalType type = createType("Baz", jsType,
        createCommonJsModule(fileSystem.getPath("/modules/foo/bar/index.js")));

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
    checkLink("foo.bar", "namespace_foo_bar.html", linker.getLink("foo.bar"));
  }

  @Test
  public void testGetLink_externs() {
    String href =
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String";

    checkLink("string", href, linker.getLink("string"));
    checkLink("String", href, linker.getLink("String"));
    checkLink("String", href, linker.getLink("String.prototype.indexOf"));
    checkLink("String", href, linker.getLink("String#indexOf"));
    checkLink("String", href, linker.getLink("String#"));
    checkLink("String", href, linker.getLink("String.fromCharCode"));
  }

  @Test
  public void testGetLink_staticProperty() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo.bar');",
        "",
        "foo.bar.baz = function() {};");

    assertNotNull(typeRegistry.getNominalType("foo.bar"));
    checkLink("foo.bar.baz", "namespace_foo_bar.html#baz", linker.getLink("foo.bar.baz"));
    checkLink("foo.bar.unknown", "namespace_foo_bar.html", linker.getLink("foo.bar.unknown"));
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

    checkLink("foo.Bar", "class_foo_Bar.html", linker.getLink("foo.Bar"));
    checkLink("foo.Bar", "class_foo_Bar.html", linker.getLink("foo.Bar#"));
    checkLink("foo.Bar#bar", "class_foo_Bar.html#bar", linker.getLink("foo.Bar#bar"));
    checkLink("foo.Bar#bar", "class_foo_Bar.html#bar", linker.getLink("foo.Bar#bar()"));
    checkLink("foo.Bar#bar", "class_foo_Bar.html#bar", linker.getLink("foo.Bar.prototype.bar"));
    checkLink("foo.Bar#bar", "class_foo_Bar.html#bar", linker.getLink("foo.Bar.prototype.bar()"));
    checkLink("foo.Bar.unknown", "class_foo_Bar.html", linker.getLink("foo.Bar.prototype.unknown"));
  }

  @Test
  public void testGetLink_module() {
    Path module = fileSystem.getPath("/src/module/foo.js");

    when(mockConfig.getModulePrefix()).thenReturn(fileSystem.getPath("/src/module"));
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module, "exports = {bar: function() {}};");
    assertThat(typeRegistry.getModules()).isNotEmpty();

    checkLink("foo", "module_foo.html", linker.getLink("dossier$$module__$src$module$foo"));
    checkLink("foo.bar", "module_foo.html#bar",
        linker.getLink("dossier$$module__$src$module$foo.bar"));
  }

  @Test
  public void testGetLink_unknownModuleProperty() {
    Path module = fileSystem.getPath("/src/module/foo.js");

    when(mockConfig.getModulePrefix()).thenReturn(fileSystem.getPath("/src/module"));
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module, "exports = {bar: function() {}};");
    assertThat(typeRegistry.getModules()).isNotEmpty();

    checkLink("foo.Name", "module_foo.html",
        linker.getLink("dossier$$module__$src$module$foo.Name"));
  }

  @Test
  public void testGetLink_contextHash_contextIsClass() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @constructor */",
        "foo.Bar = function() { this.x = 123; };",
        "foo.Bar.baz = function() {};",
        "foo.Bar.prototype.bar = function() {}");

    NominalType context = typeRegistry.getNominalType("foo.Bar");
    assertNotNull(context);
    linker.pushContext(context);

    checkLink("foo.Bar#bar", "class_foo_Bar.html#bar", linker.getLink("#bar"));
    checkLink("foo.Bar#x", "class_foo_Bar.html#x", linker.getLink("#x"));
    checkLink("foo.Bar.baz", "class_foo_Bar.html#Bar.baz", linker.getLink("#baz"));
  }

  @Test
  public void testGetLink_contextHash_contextIsInterface() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @interface */",
        "foo.Bar = function() {};",
        "foo.Bar.baz = function() {};",
        "foo.Bar.prototype.bar = function() {}");

    NominalType context = typeRegistry.getNominalType("foo.Bar");
    assertNotNull(context);
    linker.pushContext(context);

    checkLink("foo.Bar#bar", "interface_foo_Bar.html#bar", linker.getLink("#bar"));
    checkLink("foo.Bar.baz", "interface_foo_Bar.html#Bar.baz", linker.getLink("#baz"));
  }

  @Test
  public void testGetLink_contextHash_contextIsEnum() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @enum {number} */",
        "foo.Bar = {x: 1, y: 2};",
        "foo.Bar.baz = function() {};");

    NominalType context = typeRegistry.getNominalType("foo.Bar");
    assertNotNull(context);
    linker.pushContext(context);

    checkLink("foo.Bar.x", "enum_foo_Bar.html#Bar.x", linker.getLink("#x"));
    checkLink("foo.Bar.baz", "enum_foo_Bar.html#Bar.baz", linker.getLink("#baz"));
  }

  @Test
  public void testGetLink_contextHash_contextIsNamespace() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo');",
        "foo.bar = function() {};");

    NominalType context = typeRegistry.getNominalType("foo");
    assertNotNull(context);
    linker.pushContext(context);

    checkLink("foo.bar", "namespace_foo.html#bar", linker.getLink("#bar"));
  }

  @Test
  public void testGetLink_contextHash_contextIsModule() {
    Path module = fileSystem.getPath("/src/module/foo.js");

    when(mockConfig.getModulePrefix()).thenReturn(fileSystem.getPath("/src/module"));
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module, "exports = {bar: function() {}};");
    assertThat(typeRegistry.getModules()).isNotEmpty();

    NominalType context = typeRegistry.getModuleType("dossier$$module__$src$module$foo");
    assertNotNull(context);
    linker.pushContext(context);

    checkLink("foo.bar", "module_foo.html#bar", linker.getLink("#bar"));
  }

  @Test
  public void testGetLink_referenceToContextModuleExportedType() {
    Path module = fileSystem.getPath("/src/module/foo.js");

    when(mockConfig.getModulePrefix()).thenReturn(fileSystem.getPath("/src/module"));
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module,
        "/** @constructor */",
        "var InternalClass = function() {};",
        "InternalClass.staticFunc = function() {};",
        "InternalClass.prototype.method = function() {};",
        "exports.ExternalClass = InternalClass");

    assertThat(typeRegistry.getModules()).isNotEmpty();

    NominalType context = typeRegistry.getModuleType("dossier$$module__$src$module$foo");
    assertNotNull(context);
    linker.pushContext(context);

    checkLink("ExternalClass", "module_foo_class_ExternalClass.html",
        linker.getLink("InternalClass"));
    checkLink("ExternalClass.staticFunc",
        "module_foo_class_ExternalClass.html#ExternalClass.staticFunc",
        linker.getLink("InternalClass.staticFunc"));
    checkLink("ExternalClass#method",
        "module_foo_class_ExternalClass.html#method",
        linker.getLink("InternalClass#method"));
  }

  @Test
  public void testGetSourcePath_nullNode() {
    assertEquals(
        Dossier.SourceLink.newBuilder()
            .setPath("")
            .build(),
        linker.getSourceLink(null));
  }

  @Test
  public void testGetSourcePath_externNode() {
    Node node = mock(Node.class);
    when(node.isFromExterns()).thenReturn(true);
    assertEquals(
        Dossier.SourceLink.newBuilder()
            .setPath("")
            .build(),
        linker.getSourceLink(node));
  }

  @Test
  public void testGetSourcePath() {
    Path srcPrefix = fileSystem.getPath("/alphabet/soup");
    when(mockConfig.getSrcPrefix()).thenReturn(srcPrefix);

    Node node = mock(Node.class);
    when(node.getSourceFileName()).thenReturn("/alphabet/soup/a/b/c");
    when(node.getLineno()).thenReturn(123);
    assertEquals(
        Dossier.SourceLink.newBuilder()
            .setPath("source/a/b/c.src.html")
            .setLine(123)
            .build(),
        linker.getSourceLink(node));
  }

  @Test
  public void formatTypeExpression_moduleContextWillHideGlobalTypeNames() {
    when(mockConfig.getModulePrefix()).thenReturn(fileSystem.getPath("/src/module"));
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(
        fileSystem.getPath("/src/module/a.js"),
        fileSystem.getPath("/src/module/b.js")));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(
        createSourceFile(fileSystem.getPath("/globals.js"),
            "goog.provide('ns');",
            "/** @typedef {string} */",
            "ns.Name;"),
        createSourceFile(fileSystem.getPath("/src/module/a.js"), ""),
        createSourceFile(fileSystem.getPath("/src/module/b.js"),
            "var ns = require('./a');",
            "/** @param {ns.Name} name The name. */",
            "exports.greet = function(name) {};"));

    NominalType type = typeRegistry.getModuleType("dossier$$module__$src$module$b");
    assertNotNull(type);

    Property property = type.getProperty("greet");
    assertNotNull(property);

    JSTypeExpression expression = property.getJSDocInfo().getParameterType("name");
    Dossier.Comment comment = linker.formatTypeExpression(expression);
    Dossier.Comment.Token token = getOnlyElement(comment.getTokenList());
    assertEquals(
        "ns is defined as an alias to module/a, so any type reference will hide the global " +
            "ns variable",
        "a.Name",
        token.getText());
    assertEquals("module_a.html", token.getHref());
  }


  private static void checkLink(String text, String href, Dossier.TypeLink link) {
    assertEquals(text, link.getText());
    assertEquals(href, link.getHref());
  }

  private static NominalType createType(String name) {
    return createType(name, mock(JSType.class), null);
  }

  private static NominalType createType(String name, ModuleDescriptor module) {
    return createType(name, mock(JSType.class), module);
  }

  private static NominalType createType(String name, JSType type) {
    return createType(name, type, null);
  }

  private static NominalType createType(String name, JSType type, ModuleDescriptor module) {
    return new NominalType(
        null,
        name,
        new NominalType.TypeDescriptor(type),
        mock(Node.class),
        null,
        module);
  }

  private ModuleDescriptor createModule(String name) {
    return new ModuleDescriptor(name, fileSystem.getPath("/unused"), false);
  }

  private static ModuleDescriptor createCommonJsModule(Path path) {
    Node node = mock(Node.class);
    when(node.isScript()).thenReturn(true);
    when(node.getSourceFileName()).thenReturn(path.toString());
    DossierModule module = new DossierModule(node, path);
    return new ModuleDescriptor(module.getVarName(), path, true);
  }
}
