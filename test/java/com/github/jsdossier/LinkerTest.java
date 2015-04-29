/*
 Copyright 2013-2015 Jason Leyba

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.github.jsdossier;

import static com.github.jsdossier.CompilerUtil.createSourceFile;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.Property;

import com.github.jsdossier.jscomp.DossierCompiler;
import com.github.jsdossier.jscomp.DossierModule;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.SourceLink;
import com.github.jsdossier.proto.TypeLink;
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
  private Path sourcePrefix;
  private Path modulePrefix;
  private Predicate<NominalType> typeFilter;
  private TypeRegistry typeRegistry;
  private CompilerUtil util;
  private Linker linker;

  @Before
  public void setUp() {
    fileSystem = Jimfs.newFileSystem();
    outputDir = fileSystem.getPath("/root/output");
    sourcePrefix = fileSystem.getPath("/sources");
    modulePrefix = fileSystem.getPath("/modules");

    @SuppressWarnings("unchecked")
    Predicate<NominalType> mockTypeFilter = mock(Predicate.class);
    typeFilter = mockTypeFilter;

    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.<Path>of());
    typeRegistry = new TypeRegistry(compiler.getTypeRegistry());

    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    linker = new Linker(
        outputDir,
        sourcePrefix,
        modulePrefix,
        typeFilter,
        typeRegistry);
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
    assertEquals(
        outputDir.resolve("source/one/two/three.js.src.html"),
        linker.getFilePath(sourcePrefix.resolve("one/two/three.js")));
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
  public void testGetLink_filteredType() {
    JSType jsType = mock(JSType.class);
    when(jsType.isConstructor()).thenReturn(true);

    NominalType filtered = createType("foo.Filtered", jsType);
    when(typeFilter.apply(filtered)).thenReturn(true);

    typeRegistry.addType(filtered);
    assertNull("Type is filtered from documentation", linker.getLink("foo.Filtered"));
  }

  @Test
  public void testGetLink_filteredTypeWithUnfilteredAlias() {
    JSType jsType = mock(JSType.class);
    when(jsType.isConstructor()).thenReturn(true);

    NominalType filtered = createType("foo.Filtered", jsType);
    NominalType alias = new NominalType(
        null, "foo.Alias", filtered.getTypeDescriptor(), mock(Node.class), null, null);

    when(typeFilter.apply(filtered)).thenReturn(true);

    typeRegistry.addType(filtered);
    typeRegistry.addType(alias);
    checkLink("foo.Alias", "class_foo_Alias.html", linker.getLink("foo.Filtered"));
  }

  @Test
  public void testGetLink_filteredJsType() {
    JSType jsType = mock(JSType.class);
    when(jsType.isConstructor()).thenReturn(true);

    NominalType filtered = createType("foo.Filtered", jsType);
    when(typeFilter.apply(filtered)).thenReturn(true);

    typeRegistry.addType(filtered);
    assertNull("Type is filtered from documentation", linker.getLink(jsType));
  }

  @Test
  public void testGetLink_filteredJsTypeWithUnfilteredAlias() {
    JSType jsType = mock(JSType.class);
    when(jsType.isConstructor()).thenReturn(true);

    NominalType filtered = createType("foo.Filtered", jsType);
    NominalType alias = new NominalType(
        null, "foo.Alias", filtered.getTypeDescriptor(), mock(Node.class), null, null);

    when(typeFilter.apply(filtered)).thenReturn(true);

    typeRegistry.addType(filtered);
    typeRegistry.addType(alias);
    checkLink("foo.Alias", "class_foo_Alias.html", linker.getLink(jsType));
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
    Path module = modulePrefix.resolve("foo.js");
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module, "exports = {bar: function() {}};");
    assertThat((Iterable) typeRegistry.getModules()).isNotEmpty();

    checkLink("foo", "module_foo.html", linker.getLink("dossier$$module__$modules$foo"));
    checkLink("foo.bar", "module_foo.html#bar",
        linker.getLink("dossier$$module__$modules$foo.bar"));
  }

  @Test
  public void testGetLink_unknownModuleProperty() {
    Path module = modulePrefix.resolve("foo.js");
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module, "exports = {bar: function() {}};");
    assertThat((Iterable) typeRegistry.getModules()).isNotEmpty();

    checkLink("foo.Name", "module_foo.html",
        linker.getLink("dossier$$module__$modules$foo.Name"));
  }

  @Test
  public void testGetLink_enum() {
    util.compile(
        fileSystem.getPath("/src/foo/bar.js"),
        "goog.provide('foo');",
        "/** @enum {string} */",
        "foo.Bar = {yes: 'yes', no: 'no'};",
        "foo.Bar.valueOf = function (x) { return x ? foo.Bar.yes : foo.Bar.no; };");

    assertNotNull(typeRegistry.getNominalType("foo.Bar"));

    checkLink("foo.Bar", "enum_foo_Bar.html", linker.getLink("foo.Bar"));
    checkLink("foo.Bar.yes", "enum_foo_Bar.html#yes", linker.getLink("foo.Bar#yes"));
    checkLink("foo.Bar.valueOf", "enum_foo_Bar.html#Bar.valueOf",
        linker.getLink("foo.Bar.valueOf"));
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

    checkLink("foo.Bar.x", "enum_foo_Bar.html#x", linker.getLink("#x"));
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
    Path module = modulePrefix.resolve("foo.js");
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module, "exports = {bar: function() {}};");
    assertThat((Iterable) typeRegistry.getModules()).isNotEmpty();

    NominalType context = typeRegistry.getModuleType("dossier$$module__$modules$foo");
    assertNotNull(context);
    linker.pushContext(context);

    checkLink("foo.bar", "module_foo.html#bar", linker.getLink("#bar"));
  }

  @Test
  public void testGetLink_referenceToContextModuleExportedType() {
    Path module = modulePrefix.resolve("foo.js");
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module,
        "/** @constructor */",
        "var InternalClass = function() {};",
        "InternalClass.staticFunc = function() {};",
        "InternalClass.prototype.method = function() {};",
        "exports.ExternalClass = InternalClass");

    assertThat((Iterable) typeRegistry.getModules()).isNotEmpty();

    NominalType context = typeRegistry.getModuleType("dossier$$module__$modules$foo");
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
  public void testGetLink_toCommonJsModule() {
    Path module = modulePrefix.resolve("foo.js");
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(module));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(module,
        "/** @constructor */",
        "var InternalClass = function() {};",
        "InternalClass.staticFunc = function() {};",
        "InternalClass.prototype.method = function() {};",
        "exports.ExternalClass = InternalClass");

    checkLink("foo", "module_foo.html", linker.getLink("foo"));
    checkLink("foo.ExternalClass",
        "module_foo_class_ExternalClass.html",
        linker.getLink("foo.ExternalClass"));
    checkLink("foo.ExternalClass.staticFunc",
        "module_foo_class_ExternalClass.html#ExternalClass.staticFunc",
        linker.getLink("foo.ExternalClass.staticFunc"));
    checkLink("foo.ExternalClass#method",
        "module_foo_class_ExternalClass.html#method",
        linker.getLink("foo.ExternalClass#method"));
  }

  @Test
  public void testGetSourcePath_nullNode() {
    assertEquals(
        SourceLink.newBuilder()
            .setPath("")
            .build(),
        linker.getSourceLink(null));
  }

  @Test
  public void testGetSourcePath_externNode() {
    Node node = mock(Node.class);
    when(node.isFromExterns()).thenReturn(true);
    assertEquals(
        SourceLink.newBuilder()
            .setPath("")
            .build(),
        linker.getSourceLink(node));
  }

  @Test
  public void testGetSourcePath() {
    Node node = mock(Node.class);
    when(node.getSourceFileName()).thenReturn(sourcePrefix.resolve("a/b/c").toString());
    when(node.getLineno()).thenReturn(123);
    assertEquals(
        SourceLink.newBuilder()
            .setPath("source/a/b/c.src.html")
            .setLine(123)
            .build(),
        linker.getSourceLink(node));
  }

  @Test
  public void formatTypeExpression_moduleContextWillHideGlobalTypeNames() {
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(
        modulePrefix.resolve("a.js"),
        modulePrefix.resolve("b.js")));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(
        createSourceFile(sourcePrefix.resolve("/globals.js"),
            "goog.provide('ns');",
            "/** @typedef {string} */",
            "ns.Name;"),
        createSourceFile(modulePrefix.resolve("a.js"), ""),
        createSourceFile(modulePrefix.resolve("b.js"),
            "var ns = require('./a');",
            "/** @param {ns.Name} name The name. */",
            "exports.greet = function(name) {};"));

    NominalType type = typeRegistry.getModuleType("dossier$$module__$modules$b");
    assertNotNull(type);

    Property property = type.getOwnSlot("greet");
    assertNotNull(property);

    JSTypeExpression expression = property.getJSDocInfo().getParameterType("name");
    Comment comment = linker.formatTypeExpression(expression);
    Comment.Token token = getOnlyElement(comment.getTokenList());
    assertEquals(
        "ns is defined as an alias to module/a, so any type reference will hide the global " +
            "ns variable",
        "a.Name",
        token.getText());
    assertEquals("module_a.html", token.getHref());
  }

  @Test
  public void formatTypeExpression_removesInternalModuleNameFromUnrecognizedSymbols() {
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableList.of(
        modulePrefix.resolve("a.js")));
    CompilerOptions options = Main.createOptions(fileSystem, typeRegistry, compiler);
    util = new CompilerUtil(compiler, options);

    util.compile(
        createSourceFile(modulePrefix.resolve("a.js"),
            "var http = require('http');",
            "/** @param {!http.Agent} agent The agent to use. */",
            "exports.createClient = function(agent) {};"));

    NominalType type = typeRegistry.getModuleType("dossier$$module__$modules$a");
    assertNotNull(type);

    Property property = type.getOwnSlot("createClient");
    assertNotNull(property);

    JSTypeExpression expression = property.getJSDocInfo().getParameterType("agent");
    Comment comment = linker.formatTypeExpression(expression);
    Comment.Token token = getOnlyElement(comment.getTokenList());
    assertEquals("http.Agent", token.getText());
    assertFalse(token.hasHref());
  }

  private static void checkLink(String text, String href, TypeLink link) {
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
