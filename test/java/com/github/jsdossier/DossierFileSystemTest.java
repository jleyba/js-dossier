/*
 Copyright 2013-2016 Jason Leyba

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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.jscomp.JsDoc;
import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.Position;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.base.Optional;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.jstype.JSType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.Path;

import javax.inject.Inject;

/**
 * Tests for {@link DossierFileSystem}.
 */
@RunWith(JUnit4.class)
public class DossierFileSystemTest {

  @Rule
  public final GuiceRule guice = GuiceRule.builder(this)
      .setSourcePrefix("/input/src")
      .setModulePrefix("/input/module")
      .setModules("one.js", "two.js", "foo/bar.js", "foo/bar/index.js")
      .setOutputDir("/out")
      .setModuleNamingConvention(ModuleNamingConvention.ES6)
      .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
      .build();

  @Inject
  @SourcePrefix
  private Path srcPrefix;

  @Inject
  @ModulePrefix
  private Path modulePrefix;

  @Inject
  private TypeRegistry typeRegistry;

  @Inject
  @Output
  private Path outputRoot;

  @Inject
  private CompilerUtil util;

  @Inject
  private DossierFileSystem sut;

  @Test
  public void canGetThePathToARenderedSourceFile() {
    Path path = sut.getPath(srcPrefix.resolve("foo/bar/baz.js"));
    assertThat(path.toString()).isEqualTo(
        outputRoot.resolve("source/foo/bar/baz.js.src.html").toString());
  }

  @Test
  public void getSourceRelativePath() {
    Path path = sut.getSourceRelativePath(srcPrefix.resolve("foo/bar/baz.js"));
    assertThat(path.toString()).isEqualTo("foo/bar/baz.js");
  }

  @Test
  public void canGetThePathToANominalType() {
    NominalType type = createType("foo.bar.Baz");
    assertThat(sut.getPath(type).toString()).isEqualTo(
        outputRoot.resolve("foo.bar.Baz.html").toString());
  }

  @Test
  public void canGetThePathToAModule_index() {
    Path path = sut.getPath(commonJsModule("foo/bar/index.js"));
    assertThat(path.toString()).isEqualTo(
        outputRoot.resolve("module/foo/bar/index.html").toString());
  }

  @Test
  public void canGetThePathToAModule() {
    Path path = sut.getPath(commonJsModule("foo/bar/baz.js"));
    assertThat(path.toString()).isEqualTo(
        outputRoot.resolve("module/foo/bar/baz.html").toString());
  }

  @Test
  public void canGetThePathToAModuleExportedType() {
    NominalType type = createType("Clazz", commonJsModule("foo/bar.js"));
    Path path = sut.getPath(type);
    assertThat(path.toString()).isEqualTo(
        outputRoot.resolve("module/foo/bar_exports_Clazz.html").toString());
  }

  @Test
  public void canGetThePathToAModuleExportedType_exportedFromIndex() {
    NominalType type = createType("Clazz", commonJsModule("foo/bar/index.js"));
    Path path = sut.getPath(type);
    assertThat(path.toString()).isEqualTo(
        outputRoot.resolve("module/foo/bar/index_exports_Clazz.html").toString());
  }

  @Test
  public void getModuleDisplayName_indexWithNodeConventions() {
    sut = createFileSystem(ModuleNamingConvention.NODE);
    assertThat(sut.getDisplayName(commonJsModule("foo/index.js"))).isEqualTo("foo");
  }

  @Test
  public void getModuleDisplayName_indexWithEs6Conventions() {
    sut = createFileSystem(ModuleNamingConvention.ES6);
    assertThat(sut.getDisplayName(commonJsModule("foo/index.js"))).isEqualTo("foo/index");
  }

  @Test
  public void getModuleDisplayName_notIndex() {
    assertThat(sut.getDisplayName(commonJsModule("foo/bar/baz.js"))).isEqualTo("foo/bar/baz");
  }

  @Test
  public void getModuleDisplayName_indexClashesWithSiblingInParentDir_es6Conventions() {
    sut = createFileSystem(ModuleNamingConvention.ES6);

    typeRegistry.addModule(commonJsModule("foo/bar.js"));
    typeRegistry.addModule(commonJsModule("foo/bar/index.js"));

    assertThat(sut.getDisplayName(commonJsModule("foo/bar.js"))).isEqualTo("foo/bar");
    assertThat(sut.getDisplayName(commonJsModule("foo/bar/index.js"))).isEqualTo("foo/bar/index");
  }

  @Test
  public void getModuleDisplayName_indexClashesWithSiblingInParentDir_nodeConventions() {
    sut = createFileSystem(ModuleNamingConvention.NODE);

    typeRegistry.addModule(commonJsModule("foo/bar.js"));
    typeRegistry.addModule(commonJsModule("foo/bar/index.js"));

    assertThat(sut.getDisplayName(commonJsModule("foo/bar.js"))).isEqualTo("foo/bar");
    assertThat(sut.getDisplayName(commonJsModule("foo/bar/index.js"))).isEqualTo("foo/bar/");
  }

  @Test
  public void getGoogModuleDisplayName() {
    assertThat(sut.getDisplayName(googModule("foo.bar.baz"))).isEqualTo("foo.bar.baz");
  }

  @Test
  public void getModuleExportedTypeDisplayName() {
    NominalType type = createType("Foo", commonJsModule("foo/bar.js"));
    assertThat(sut.getDisplayName(type)).isEqualTo("Foo");
  }

  @Test
  public void getGoogModuleExportedTypeDisplayName() {
    NominalType type = createType("Baz", googModule("foo.bar"));
    assertThat(sut.getDisplayName(type)).isEqualTo("Baz");
  }

  @Test
  public void getRelativePath_fromGlobalType() {
    NominalType type = createType("Baz");
    Path path = sut.getPath(srcPrefix.resolve("foo/bar/baz.js"));
    assertThat(sut.getRelativePath(type, path).toString()).isEqualTo(
        "source/foo/bar/baz.js.src.html");
  }

  @Test
  public void getRelativePath_fromModuleType() {
    NominalType type = createType("Baz", commonJsModule("foo/bar/baz.js"));
    Path path = sut.getPath(srcPrefix.resolve("foo/bar/baz.js"));
    assertThat(sut.getRelativePath(type, path).toString()).isEqualTo(
        "../../../source/foo/bar/baz.js.src.html");
  }

  @Test
  public void getRelativePath_betweenTypesExportedByTheSameModule() {
    NominalType a = createType("One", commonJsModule("foo/bar/baz.js"));
    NominalType b = createType("Two", commonJsModule("foo/bar/baz.js"));
    assertThat(sut.getRelativePath(a, b).toString()).isEqualTo("baz_exports_Two.html");
    assertThat(sut.getRelativePath(b, a).toString()).isEqualTo("baz_exports_One.html");
  }

  @Test
  public void getRelativePath_betweenTypesExportedModulesInTheSameDirectory() {
    NominalType a = createType("One", commonJsModule("foo/bar/one.js"));
    NominalType b = createType("Two", commonJsModule("foo/bar/two.js"));
    assertThat(sut.getRelativePath(a, b).toString()).isEqualTo("two_exports_Two.html");
    assertThat(sut.getRelativePath(b, a).toString()).isEqualTo("one_exports_One.html");
  }

  @Test
  public void getRelativePath_betweenTypesExportedByDifferentModules() {
    NominalType a = createType("One", commonJsModule("foo/one.js"));
    NominalType b = createType("Two", commonJsModule("foo/bar/two.js"));
    assertThat(sut.getRelativePath(a, b).toString()).isEqualTo("bar/two_exports_Two.html");
    assertThat(sut.getRelativePath(b, a).toString()).isEqualTo("../one_exports_One.html");
  }

  @Test
  public void getQualifiedDisplayName_globalType() {
    NominalType type = createType("One");
    assertThat(sut.getDisplayName(type)).isEqualTo("One");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("One");
  }

  @Test
  public void getQualifiedDisplayName_namespacedType() {
    NominalType type = createType("one.two.Three");
    assertThat(sut.getDisplayName(type)).isEqualTo("one.two.Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("one.two.Three");
  }

  @Test
  public void getQualifiedDisplayName_closureModuleType() {
    NominalType type = createType("Three", googModule("one.two"));
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("one.two.Three");
  }

  @Test
  public void getQualifiedDisplayName_nodeModuleType() {
    NominalType type = createType("Three", commonJsModule("one/two.js"));
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("one/two.Three");
  }

  @Test
  public void getQualifiedDisplayName_nodeIndexModuleType() {
    NominalType type = createType("Three", commonJsModule("one/two/index.js"));
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("one/two/index.Three");
  }

  @Test
  public void getQualifiedDisplayName_es6ModuleType() {
    NominalType type = createType("Three", es6Module("one/two.js"));
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("one/two.Three");
  }

  @Test
  public void getQualifiedDisplayName_es6IndexModuleType() {
    NominalType type = createType("Three", es6Module("one/two/index.js"));
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("one/two/index.Three");
  }

  @Test
  public void moduleExportDefault_hasOtherName() {
    util.compile(modulePrefix.resolve("a/b/c.js"),
        "export default class Foo {}");
    NominalType type = typeRegistry.getType("module$input$module$a$b$c.default");
    Module module = type.getModule().get();
    assertThat(module.getExportedNames()).containsEntry("default", "Foo");

    assertThat(sut.getDisplayName(type)).isEqualTo("default");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("a/b/c.default");
    assertThat(sut.getPath(type).toString())
        .isEqualTo(outputRoot.resolve("module/a/b/c_exports_default.html").toString());
  }

  @Test
  public void moduleExportDefault_isInternalSymbol1() {
    util.compile(modulePrefix.resolve("a/b/c.js"),
        "class Foo {}",
        "export default Foo");
    NominalType type = typeRegistry.getType("module$input$module$a$b$c.default");
    Module module = type.getModule().get();
    assertThat(module.getExportedNames()).containsEntry("default", "Foo");

    assertThat(sut.getDisplayName(type)).isEqualTo("default");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("a/b/c.default");
    assertThat(sut.getPath(type).toString())
        .isEqualTo(outputRoot.resolve("module/a/b/c_exports_default.html").toString());
  }

  @Test
  public void moduleExportDefault_isInternalSymbol2() {
    util.compile(modulePrefix.resolve("a/b/c.js"),
        "class Foo {}",
        "export {Foo as default}");
    NominalType type = typeRegistry.getType("module$input$module$a$b$c.default");
    Module module = type.getModule().get();
    assertThat(module.getExportedNames()).containsEntry("default", "Foo");

    assertThat(sut.getDisplayName(type)).isEqualTo("default");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("a/b/c.default");
    assertThat(sut.getPath(type).toString())
        .isEqualTo(outputRoot.resolve("module/a/b/c_exports_default.html").toString());
  }

  @Test
  public void moduleExportDefault_isAnonymousClass() {
    util.compile(modulePrefix.resolve("a/b/c.js"),
        "export default class {}");
    NominalType type = typeRegistry.getType("module$input$module$a$b$c.default");
    Module module = type.getModule().get();
    assertThat(module.getExportedNames()).isEmpty();

    assertThat(sut.getDisplayName(type)).isEqualTo("default");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("a/b/c.default");
    assertThat(sut.getPath(type).toString())
        .isEqualTo(outputRoot.resolve("module/a/b/c_exports_default.html").toString());
  }

  private Module es6Module(String path) {
    return Module.builder()
        .setId(path.substring(0, path.length() - 3).replace('/', '.'))
        .setJsDoc(JsDoc.from(null))
        .setType(Module.Type.ES6)
        .setPath(modulePrefix.resolve(path))
        .build();
  }

  private Module commonJsModule(String path) {
    return Module.builder()
        .setId(path.substring(0, path.length() - 3).replace('/', '.'))
        .setJsDoc(JsDoc.from(null))
        .setType(Module.Type.NODE)
        .setPath(modulePrefix.resolve(path))
        .build();
  }

  private Module googModule(String name) {
    return Module.builder()
        .setId(name)
        .setJsDoc(JsDoc.from(null))
        .setType(Module.Type.CLOSURE)
        .setPath(modulePrefix.resolve("unused"))
        .build();
  }

  private NominalType createType(String name) {
    return createType(name, mock(JSType.class), null);
  }

  private NominalType createType(String name, Module module) {
    return createType(name, mock(JSType.class), module);
  }

  private NominalType createType(String name, JSType type, Module module) {
    return NominalType.builder()
        .setName(module == null ? name : (module.getId() + "." + name))
        .setSourcePosition(Position.of(0, 0))
        .setSourceFile(srcPrefix.resolve(name))
        .setJsDoc(JsDoc.from(null))
        .setType(type)
        .setModule(module)
        .build();
  }

  private DossierFileSystem createFileSystem(ModuleNamingConvention convention) {
    return new DossierFileSystem(outputRoot, srcPrefix, modulePrefix, typeRegistry, convention);
  }
}
