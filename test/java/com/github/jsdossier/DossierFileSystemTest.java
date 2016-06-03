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

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.jscomp.Module;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;
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
  @Input
  private FileSystem fs;

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
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo.bar.Baz');",
        "foo.bar.Baz = class {};");

    NominalType type = typeRegistry.getType("foo.bar.Baz");
    assertThat(sut.getPath(type).toString()).isEqualTo(
        outputRoot.resolve("foo.bar.Baz.html").toString());
  }

  @Test
  public void canGetThePathToAClosureModule() {
    util.compile(fs.getPath("/input/src/foo.js"),
        "goog.module('a.b.c');",
        "exports.D = class {};");

    Module module = typeRegistry.getModule("module$exports$a$b$c");
    assertThat(sut.getPath(module).toString())
        .isEqualTo(outputRoot.resolve("a.b.c.html").toString());

    NominalType type = typeRegistry.getType(module.getId());
    assertThat(sut.getPath(type).toString())
        .isEqualTo(outputRoot.resolve("a.b.c.html").toString());

    type = typeRegistry.getType(module.getId() + ".D");
    assertThat(sut.getPath(type).toString())
        .isEqualTo(outputRoot.resolve("a.b.c.D.html").toString());
  }

  @Test
  public void canGetThePathToAModule_index() {
    Path path = fs.getPath("/input/module/foo/bar/index.js");
    util.compile(path, "exports.x = 1;");

    Module module = typeRegistry.getModule(path);
    assertThat(sut.getPath(module).toString())
        .isEqualTo(outputRoot.resolve("module/foo/bar/index.html").toString());
  }

  @Test
  public void canGetThePathToAModule() {
    Path path = fs.getPath("/input/module/foo/bar.js");
    util.compile(path, "exports.x = 1;");

    Module module = typeRegistry.getModule(path);
    assertThat(sut.getPath(module).toString())
        .isEqualTo(outputRoot.resolve("module/foo/bar.html").toString());
  }

  @Test
  public void canGetThePathToAModuleExportedType() {
    util.compile(fs.getPath("/input/module/foo/bar.js"),
        "exports.Clazz = class {};");

    NominalType type = typeRegistry.getType("module$exports$module$$input$module$foo$bar.Clazz");
    Path path = sut.getPath(type);
    assertThat(path.toString())
        .isEqualTo(outputRoot.resolve("module/foo/bar_exports_Clazz.html").toString());
  }

  @Test
  public void canGetThePathToAModuleExportedType_exportedFromIndex() {
    util.compile(fs.getPath("/input/module/foo/bar/index.js"),
        "exports.Clazz = class {};");

    NominalType type =
        typeRegistry.getType("module$exports$module$$input$module$foo$bar$index.Clazz");
    Path path = sut.getPath(type);
    assertThat(path.toString())
        .isEqualTo(outputRoot.resolve("module/foo/bar/index_exports_Clazz.html").toString());
  }

  @Test
  public void getModuleDisplayName_indexWithNodeConventions() {
    setNamingConvention(ModuleNamingConvention.NODE);

    Path path = fs.getPath("/input/module/foo/bar/index.js");
    util.compile(path, "exports.Clazz = class {};");

    Module module = typeRegistry.getModule(path);
    assertThat(sut.getDisplayName(module)).isEqualTo("foo/bar");
  }

  @Test
  public void getModuleDisplayName_indexWithEs6Conventions() {
    setNamingConvention(ModuleNamingConvention.ES6);

    Path path = fs.getPath("/input/module/foo/bar/index.js");
    util.compile(path, "export class Clazz {}");

    Module module = typeRegistry.getModule(path);
    assertThat(sut.getDisplayName(module)).isEqualTo("foo/bar/index");
  }

  @Test
  public void getModuleDisplayName_notIndex_nodeConventions() {
    setNamingConvention(ModuleNamingConvention.NODE);

    Path path = fs.getPath("/input/module/foo/bar.js");
    util.compile(path, "exports.Clazz = class {};");

    Module module = typeRegistry.getModule(path);
    assertThat(sut.getDisplayName(module)).isEqualTo("foo/bar");
  }

  @Test
  public void getModuleDisplayName_notIndex_es6Conventions() {
    setNamingConvention(ModuleNamingConvention.ES6);

    Path path = fs.getPath("/input/module/foo/bar.js");
    util.compile(path, "export class Clazz {}");

    Module module = typeRegistry.getModule(path);
    assertThat(sut.getDisplayName(module)).isEqualTo("foo/bar");
  }

  @Test
  public void getModuleDisplayName_indexClashesWithSiblingInParentDir_es6Conventions() {
    setNamingConvention(ModuleNamingConvention.ES6);

    Path p1 = fs.getPath("/input/module/foo/bar.js");
    Path p2 = fs.getPath("/input/module/foo/bar/index.js");
    util.compile(
        createSourceFile(p1, "export class X {}"),
        createSourceFile(p2, "export class Y {}"));

    assertThat(sut.getDisplayName(typeRegistry.getModule(p1))).isEqualTo("foo/bar");
    assertThat(sut.getDisplayName(typeRegistry.getModule(p2))).isEqualTo("foo/bar/index");
  }

  @Test
  public void getModuleDisplayName_indexClashesWithSiblingInParentDir_nodeConventions() {
    setNamingConvention(ModuleNamingConvention.NODE);

    Path p1 = fs.getPath("/input/module/foo/bar.js");
    Path p2 = fs.getPath("/input/module/foo/bar/index.js");
    util.compile(
        createSourceFile(p1, "export class X {}"),
        createSourceFile(p2, "export class Y {}"));

    assertThat(sut.getDisplayName(typeRegistry.getModule(p1))).isEqualTo("foo/bar");
    assertThat(sut.getDisplayName(typeRegistry.getModule(p2))).isEqualTo("foo/bar/");
  }

  @Test
  public void getGoogModuleDisplayName() {
    util.compile(fs.getPath("foo.js"),
        "goog.module('foo.bar.baz');");

    Module module = typeRegistry.getModule("module$exports$foo$bar$baz");
    assertThat(sut.getDisplayName(module)).isEqualTo("foo.bar.baz");
  }

  @Test
  public void getGoogModuleDisplayName_withLegacyNamespace_1() {
    util.compile(fs.getPath("foo.js"),
        "goog.module('foo.bar');",
        "goog.module.declareLegacyNamespace();");

    NominalType type = typeRegistry.getType("foo.bar");
    assertThat(sut.getDisplayName(type)).isEqualTo("foo.bar");
  }

  @Test
  public void getGoogModuleDisplayName_withLegacyNamespace_2() {
    util.compile(fs.getPath("foo.js"),
        "goog.module('foo.bar.baz');",
        "goog.module.declareLegacyNamespace();");

    NominalType type = typeRegistry.getType("foo.bar.baz");
    assertThat(sut.getDisplayName(type)).isEqualTo("foo.bar.baz");
  }

  @Test
  public void getModuleExportedTypeDisplayName_es6Module() {
    Path path = fs.getPath("/input/module/foo/bar.js");
    util.compile(path, "export class Foo {}");

    NominalType type = typeRegistry.getType("module$$input$module$foo$bar.Foo");
    assertThat(sut.getDisplayName(type)).isEqualTo("Foo");
  }

  @Test
  public void getModuleExportedTypeDisplayName_nodeModule() {
    Path path = fs.getPath("/input/module/foo/bar.js");
    util.compile(path, "exports.Foo = class {};");

    NominalType type = typeRegistry.getType("module$exports$module$$input$module$foo$bar.Foo");
    assertThat(sut.getDisplayName(type)).isEqualTo("Foo");
  }

  @Test
  public void getGoogModuleExportedTypeDisplayName() {
    util.compile(fs.getPath("foo.js"),
        "goog.module('foo.bar');",
        "exports.Baz = class {};");
    NominalType type = typeRegistry.getType("module$exports$foo$bar.Baz");
    assertThat(sut.getDisplayName(type)).isEqualTo("Baz");
  }

  @Test
  public void getGoogModuleExportedTypeDisplayName_moduleHasLegacyNamespace() {
    util.compile(fs.getPath("foo.js"),
        "goog.module('foo.bar');",
        "goog.module.declareLegacyNamespace();",
        "exports.Baz = class {};");
    NominalType type = typeRegistry.getType("foo.bar.Baz");
    assertThat(sut.getDisplayName(type)).isEqualTo("Baz");
  }

  @Test
  public void getRelativePath_fromGlobalType() {
    util.compile(fs.getPath("foo.js"),
        "class Baz {}");
    NominalType type = typeRegistry.getType("Baz");
    Path path = sut.getPath(srcPrefix.resolve("foo/bar/baz.js"));
    assertThat(sut.getRelativePath(type, path).toString()).isEqualTo(
        "source/foo/bar/baz.js.src.html");
  }

  @Test
  public void getRelativePath_fromModuleType() {
    util.compile(fs.getPath("/input/module/foo/bar/baz.js"),
        "export class Baz {}");
    NominalType type = typeRegistry.getType("module$$input$module$foo$bar$baz.Baz");
    Path path = sut.getPath(srcPrefix.resolve("foo/bar/baz.js"));
    assertThat(sut.getRelativePath(type, path).toString()).isEqualTo(
        "../../../source/foo/bar/baz.js.src.html");
  }

  @Test
  public void getRelativePath_betweenTypesExportedByTheSameModule() {
    util.compile(fs.getPath("/input/module/foo/bar/baz.js"),
        "export class One {}",
        "export class Two {}");
    NominalType a = typeRegistry.getType("module$$input$module$foo$bar$baz.One");
    NominalType b = typeRegistry.getType("module$$input$module$foo$bar$baz.Two");
    assertThat(sut.getRelativePath(a, b).toString()).isEqualTo("baz_exports_Two.html");
    assertThat(sut.getRelativePath(b, a).toString()).isEqualTo("baz_exports_One.html");
  }

  @Test
  public void getRelativePath_betweenTypesExportedByModulesInTheSameDirectory() {
    util.compile(
        createSourceFile(fs.getPath("/input/module/one.js"), "export class One {}"),
        createSourceFile(fs.getPath("/input/module/two.js"), "export class Two {}"));

    NominalType a = typeRegistry.getType("module$$input$module$one.One");
    NominalType b = typeRegistry.getType("module$$input$module$two.Two");
    assertThat(sut.getRelativePath(a, b).toString()).isEqualTo("two_exports_Two.html");
    assertThat(sut.getRelativePath(b, a).toString()).isEqualTo("one_exports_One.html");
  }

  @Test
  public void getRelativePath_betweenTypesExportedByModulesInDifferentDirectories() {
    util.compile(
        createSourceFile(fs.getPath("/input/module/one.js"), "export class One {}"),
        createSourceFile(fs.getPath("/input/module/bar/two.js"), "export class Two {}"));

    NominalType a = typeRegistry.getType("module$$input$module$one.One");
    NominalType b = typeRegistry.getType("module$$input$module$bar$two.Two");
    assertThat(sut.getRelativePath(a, b).toString()).isEqualTo("bar/two_exports_Two.html");
    assertThat(sut.getRelativePath(b, a).toString()).isEqualTo("../one_exports_One.html");
  }

  @Test
  public void getQualifiedDisplayName_globalType() {
    util.compile(fs.getPath("foo.js"),
        "class One {}");
    NominalType type = typeRegistry.getType("One");
    assertThat(sut.getDisplayName(type)).isEqualTo("One");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("One");
  }

  @Test
  public void getQualifiedDisplayName_namespacedType() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('one.two.Three');",
        "one.two.Three = class {};");
    NominalType type = typeRegistry.getType("one.two.Three");
    assertThat(sut.getDisplayName(type)).isEqualTo("one.two.Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("one.two.Three");
  }

  @Test
  public void getQualifiedDisplayName_closureModuleType() {
    util.compile(fs.getPath("foo.js"),
        "goog.module('one.two');",
        "exports.Three = class {};");
    NominalType type = typeRegistry.getType("module$exports$one$two.Three");
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("one.two.Three");
  }

  @Test
  public void getQualifiedDisplayName_nodeModuleType() {
    util.compile(fs.getPath("/input/module/foo/bar.js"),
        "exports.Three = class {};");
    NominalType type = typeRegistry.getType("module$exports$module$$input$module$foo$bar.Three");
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("foo/bar.Three");
  }

  @Test
  public void getQualifiedDisplayName_nodeIndexModuleType() {
    setNamingConvention(ModuleNamingConvention.NODE);

    util.compile(fs.getPath("/input/module/foo/bar/index.js"),
        "exports.Three = class {};");
    NominalType type =
        typeRegistry.getType("module$exports$module$$input$module$foo$bar$index.Three");
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("foo/bar.Three");
  }

  @Test
  public void getQualifiedDisplayName_es6ModuleType() {
    util.compile(fs.getPath("/input/module/foo/bar.js"),
        "export class Three {}");
    NominalType type = typeRegistry.getType("module$$input$module$foo$bar.Three");
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("foo/bar.Three");
  }

  @Test
  public void getQualifiedDisplayName_es6IndexModuleType() {
    util.compile(fs.getPath("/input/module/foo/bar/index.js"),
        "export class Three {}");
    NominalType type = typeRegistry.getType("module$$input$module$foo$bar$index.Three");
    assertThat(sut.getDisplayName(type)).isEqualTo("Three");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("foo/bar/index.Three");
  }

  @Test
  public void moduleExportDefault_hasOtherName() {
    util.compile(modulePrefix.resolve("a/b/c.js"),
        "export default class Foo {}");
    NominalType type = typeRegistry.getType("module$$input$module$a$b$c.default");
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
    NominalType type = typeRegistry.getType("module$$input$module$a$b$c.default");
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
    NominalType type = typeRegistry.getType("module$$input$module$a$b$c.default");
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
    NominalType type = typeRegistry.getType("module$$input$module$a$b$c.default");
    Module module = type.getModule().get();
    assertThat(module.getExportedNames()).isEmpty();

    assertThat(sut.getDisplayName(type)).isEqualTo("default");
    assertThat(sut.getQualifiedDisplayName(type)).isEqualTo("a/b/c.default");
    assertThat(sut.getPath(type).toString())
        .isEqualTo(outputRoot.resolve("module/a/b/c_exports_default.html").toString());
  }

  private void setNamingConvention(ModuleNamingConvention convention) {
    guice.toBuilder()
        .setModuleNamingConvention(convention)
        .build()
        .createInjector()
        .injectMembers(this);
  }

  public static SourceFile createSourceFile(Path path, String... lines) {
    return SourceFile.fromCode(path.toString(), Joiner.on("\n").join(lines));
  }
}
