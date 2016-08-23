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

import static com.github.jsdossier.ProtoTruth.assertMessage;
import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.SourceLink;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.jscomp.CompilerOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Tests for {@link LinkFactory}.
 */
@RunWith(JUnit4.class)
public class LinkFactoryTest {

  @Rule
  public GuiceRule guice = GuiceRule.builder(this)
      .setOutputDir("out")
      .setSourcePrefix("source")
      .setModulePrefix("source/modules")
      .setModules("one.js", "two.js", "three.js")
      .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
      .build();

  @Inject @Input private FileSystem fs;
  @Inject private TypeRegistry typeRegistry;
  @Inject private CompilerUtil util;
  @Inject private LinkFactoryBuilder linkFactoryBuilder;

  @Test
  public void createLinkFromPathAndPosition() {
    util.compile(fs.getPath("source/foo.js"),
        "class Foo {}");

    NominalType type = typeRegistry.getType("Foo");
    SourceLink link = createFactory()
        .createLink(type.getSourceFile(), type.getSourcePosition());
    checkLink(link, "out/source/foo.js.src.html", 1);
  }

  @Test
  public void createLinkFromPathAndPosition_withSourceUrlTemplate() {
    guice.toBuilder()
        .setSourceUrlTemplate("http://www.example.com/%path%#l%line%")
        .build()
        .createInjector()
        .injectMembers(this);

    util.compile(fs.getPath("source/foo/bar/baz.js"),
        "class Foo {}");

    NominalType type = typeRegistry.getType("Foo");
    SourceLink link = createFactory()
        .createLink(type.getSourceFile(), type.getSourcePosition());
    checkLink(link,
        "http://www.example.com/foo/bar/baz.js#l1",
        "foo/bar/baz.js", 1);
  }

  @Test
  public void createLinkFromPathAndPosition_withSourceUrlTemplate_fromModule() {
    guice.toBuilder()
        .setSourceUrlTemplate("http://www.example.com/%path%#l%line%")
        .build()
        .createInjector()
        .injectMembers(this);

    util.compile(
        createSourceFile(
            fs.getPath("source/foo/bar/baz.js"),
            "class Foo {}"),
        createSourceFile(
            fs.getPath("source/some/closure/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"));

    NominalType type = typeRegistry.getType("Foo");
    NominalType ref = typeRegistry.getType("module$exports$a$b.X");

    SourceLink link = createFactory(ref)
        .createLink(type.getSourceFile(), type.getSourcePosition());
    checkLink(link,
        "http://www.example.com/foo/bar/baz.js#l1",
        "foo/bar/baz.js", 1);
  }

  @Test
  public void generateLinkToGlobalType_fromGlobalScope() {
    util.compile(fs.getPath("source/foo.js"),
        "class Foo {}");

    NominalType type = typeRegistry.getType("Foo");
    NamedType link = createFactory().createLink(type);
    checkLink(link, "Foo", "Foo.html");
  }

  @Test
  public void generateLinkToGlobalType_fromGlobalType() {
    util.compile(fs.getPath("source/foo.js"),
        "class Foo {}",
        "class Bar {}");

    NominalType foo = typeRegistry.getType("Foo");
    NominalType bar = typeRegistry.getType("Bar");

    NamedType link = createFactory().withTypeContext(bar).createLink(foo);
    checkLink(link, "Foo", "Foo.html");
  }

  @Test
  public void generateLinkToGlobalType_fromClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/global.js"),
            "class Foo {}"),
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"));

    NominalType foo = typeRegistry.getType("Foo");
    NominalType ref = typeRegistry.getType("module$exports$a$b.X");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "Foo", "Foo.html");
  }

  @Test
  public void generateLinkToGlobalType_fromNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/global.js"),
            "class Foo {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"));

    NominalType foo = typeRegistry.getType("Foo");
    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "Foo", "../Foo.html");
  }

  @Test
  public void generateLinkToGlobalType_fromEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/global.js"),
            "class Foo {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"));

    NominalType foo = typeRegistry.getType("Foo");
    NominalType ref = typeRegistry.getType("module$source$modules$one.X");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "Foo", "../Foo.html");
  }

  @Test
  public void generateLinkToNamespacedType_fromGlobalScope() {
    util.compile(fs.getPath("source/foo.js"),
        "goog.provide('foo.bar');",
        "foo.bar.Baz = class {};");

    NominalType type = typeRegistry.getType("foo.bar.Baz");
    NamedType link = createFactory().createLink(type);
    checkLink(link, "foo.bar.Baz", "foo.bar.Baz.html");
  }

  @Test
  public void generateLinkToNamespacedType_fromGlobalType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/global.js"),
            "goog.provide('foo');",
            "foo.Bar = class Bar {};",
            "class Foo {}"));

    NominalType foo = typeRegistry.getType("foo.Bar");
    NominalType ref = typeRegistry.getType("Foo");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "foo.Bar", "foo.Bar.html");
  }

  @Test
  public void generateLinkToNamespacedType_fromClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/global.js"),
            "goog.provide('foo');",
            "foo.Bar = class Bar {};"),
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"));

    NominalType foo = typeRegistry.getType("foo.Bar");
    NominalType ref = typeRegistry.getType("module$exports$a$b.X");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "foo.Bar", "foo.Bar.html");
  }

  @Test
  public void generateLinkToNamespacedType_fromNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/global.js"),
            "goog.provide('foo');",
            "foo.Bar = class Bar {};"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {};"));

    NominalType foo = typeRegistry.getType("foo.Bar");
    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "foo.Bar", "../foo.Bar.html");
  }

  @Test
  public void generateLinkToNamespacedType_fromEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/global.js"),
            "goog.provide('foo');",
            "foo.Bar = class Bar {};"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"));

    NominalType foo = typeRegistry.getType("foo.Bar");
    NominalType ref = typeRegistry.getType("module$source$modules$one.X");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "foo.Bar", "../foo.Bar.html");
  }

  @Test
  public void generateLinkToClosureModule_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"));

    NominalType type = typeRegistry.getType("module$exports$a$b");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "a.b", "a.b.html");
  }

  @Test
  public void generateLinkToClosureModule_fromNamespacedType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "class Bar {}"));

    NominalType type = typeRegistry.getType("module$exports$a$b");
    NominalType ref = typeRegistry.getType("Bar");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "a.b", "a.b.html");
  }

  @Test
  public void generateLinkToClosureModule_fromClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.module('x');",
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$exports$a$b");
    NominalType ref = typeRegistry.getType("module$exports$x");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "a.b", "a.b.html");
  }

  @Test
  public void generateLinkToClosureModule_fromNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$exports$a$b");
    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "a.b", "../a.b.html");
  }

  @Test
  public void generateLinkToClosureModule_fromEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class Bar {}"));

    NominalType type = typeRegistry.getType("module$exports$a$b");
    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "a.b", "../a.b.html");
  }

  @Test
  public void generateLinkToClosureModuleExportedType_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"));

    NominalType type = typeRegistry.getType("module$exports$a$b.X");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "X", "a.b.X", "a.b.X.html");
  }

  @Test
  public void generateLinkToClosureModuleExportedType_fromNamespacedType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "class Bar {}"));

    NominalType type = typeRegistry.getType("module$exports$a$b.X");
    NominalType ref = typeRegistry.getType("Bar");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "a.b.X", "a.b.X.html");
  }

  @Test
  public void generateLinkToClosureModuleExportedType_fromClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.module('x');",
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$exports$a$b.X");
    NominalType ref = typeRegistry.getType("module$exports$x");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "a.b.X", "a.b.X.html");
  }

  @Test
  public void generateLinkToClosureModuleExportedType_fromNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$exports$a$b.X");
    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "a.b.X", "../a.b.X.html");
  }

  @Test
  public void generateLinkToClosureModuleExportedType_fromEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class Bar {}"));

    NominalType type = typeRegistry.getType("module$exports$a$b.X");
    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "a.b.X", "../a.b.X.html");
  }

  @Test
  public void generateLinkToNodeModule_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "one", "module/one.html");
  }

  @Test
  public void generateLinkToNodeModule_fromNamespacedType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "class Bar {}"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one");
    NominalType ref = typeRegistry.getType("Bar");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "module/one.html");
  }

  @Test
  public void generateLinkToNodeModule_fromClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.module('x');",
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one");
    NominalType ref = typeRegistry.getType("module$exports$x");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "module/one.html");
  }

  @Test
  public void generateLinkToNodeModule_fromNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one");
    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "one.html");
  }

  @Test
  public void generateLinkToNodeModule_fromEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "export class Bar {}"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one");
    NominalType ref = typeRegistry.getType("module$source$modules$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "one.html");
  }

  @Test
  public void generateLinkToNodeModuleExportedType_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one.X");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "X", "one.X", "module/one_exports_X.html");
  }

  @Test
  public void generateLinkToNodeModuleExportedType_fromNamespacedType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "class Bar {}"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one.X");
    NominalType ref = typeRegistry.getType("Bar");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one.X", "module/one_exports_X.html");
  }

  @Test
  public void generateLinkToNodeModuleExportedType_fromClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.module('x');",
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one.X");
    NominalType ref = typeRegistry.getType("module$exports$x");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one.X", "module/one_exports_X.html");
  }

  @Test
  public void generateLinkToNodeModuleExportedType_fromNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one.X");
    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$two");

    checkLink(createFactory(ref).withTypeContext(ref).createLink(type),
        "X", "one.X", "one_exports_X.html");
  }

  @Test
  public void generateLinkToNodeModuleExportedType_fromEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "export class Bar {}"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one.X");
    NominalType ref = typeRegistry.getType("module$source$modules$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one.X", "one_exports_X.html");
  }

  @Test
  public void generateLinkToEs6Module_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"));

    NominalType type = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "one", "module/one.html");
  }

  @Test
  public void generateLinkToEs6Module_fromNamespacedType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "class Bar {}"));

    NominalType type = typeRegistry.getType("module$source$modules$one");
    NominalType ref = typeRegistry.getType("Bar");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "module/one.html");
  }

  @Test
  public void generateLinkToEs6Module_fromClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.module('x');",
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$source$modules$one");
    NominalType ref = typeRegistry.getType("module$exports$x");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "module/one.html");
  }

  @Test
  public void generateLinkToEs6Module_fromNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$source$modules$one");
    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "one.html");
  }

  @Test
  public void generateLinkToEs6Module_fromEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "export class Bar {}"));

    NominalType type = typeRegistry.getType("module$source$modules$one");
    NominalType ref = typeRegistry.getType("module$source$modules$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "one.html");
  }

  @Test
  public void generateLinkToEs6ModuleExportedType_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"));

    NominalType type = typeRegistry.getType("module$source$modules$one.X");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "X", "one.X", "module/one_exports_X.html");
  }

  @Test
  public void generateLinkToEs6ModuleExportedType_fromNamespacedType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "class Bar {}"));

    NominalType type = typeRegistry.getType("module$source$modules$one.X");
    NominalType ref = typeRegistry.getType("Bar");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one.X", "module/one_exports_X.html");
  }

  @Test
  public void generateLinkToEs6ModuleExportedType_fromClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.module('x');",
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$source$modules$one.X");
    NominalType ref = typeRegistry.getType("module$exports$x");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one.X", "module/one_exports_X.html");
  }

  @Test
  public void generateLinkToEs6ModuleExportedType_fromNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "exports.Bar = class {};"));

    NominalType type = typeRegistry.getType("module$source$modules$one.X");
    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one.X", "one_exports_X.html");
  }

  @Test
  public void generateLinkToEs6ModuleExportedType_fromEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "export class Bar {}"));

    NominalType type = typeRegistry.getType("module$source$modules$one.X");
    NominalType ref = typeRegistry.getType("module$source$modules$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one.X", "one_exports_X.html");
  }

  @Test
  public void generateLinkToGlobalTypedef_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "/** @typedef {string} */",
        "var AString;");

    NominalType type = typeRegistry.getType("AString");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "AString", ".globals.html#AString");
  }

  @Test
  public void generateLinkToGlobalTypedef_fromEs6Modules() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "/** @typedef {string} */",
            "var AString;"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType type = typeRegistry.getType("AString");
    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "AString", "../.globals.html#AString");
  }

  // TODO: @Test
  public void generateLinkToGlobalCompilerConstant_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "/** @define {boolean} */",
        "var DEBUG=true;");

    NominalType type = typeRegistry.getType("DEBUG");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "DEBUG", ".globals.html#DEBUG");
  }

  // TODO: @Test
  public void generateLinkToGlobalCompilerConstant_fromEs6Modules() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "/** @define {boolean} */",
            "var DEBUG=true;"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType type = typeRegistry.getType("DEBUG");
    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "DEBUG", "../.globals.html#DEBUG");
  }

  @Test
  public void generateLinkToNamespacedTypedef_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo');",
        "/** @typedef {string} */",
        "foo.AString;");

    NominalType type = typeRegistry.getType("foo.AString");

    NamedType link = createFactory().createLink(type);
    checkLink(link, "foo.AString", "foo.html#foo.AString");
  }

  @Test
  public void generateLinkToNamespacedTypedef_fromEs6Modules() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.provide('foo');",
            "/** @typedef {string} */",
            "foo.AString;"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType type = typeRegistry.getType("foo.AString");
    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "foo.AString", "../foo.html#foo.AString");
  }

  @Test
  public void createLink_unresolvedSymbol() {
    util.compile(fs.getPath("foo.js"),
        "class Foo {}");

    NamedType link = createFactory().createLink("Bar");
    checkLink(link, "Bar", "");
  }

  @Test
  public void createGlobalNamedType_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "class Foo {}");

    NamedType link = createFactory().createLink("Foo");
    checkLink(link, "Foo", "Foo.html");
  }

  @Test
  public void createGlobalNamedType_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "class Foo {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("Foo");
    checkLink(link, "Foo", "../Foo.html");
  }

  @Test
  public void createNamespacedNamedType_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo');",
        "foo.Bar = class {}");

    NamedType link = createFactory().createLink("foo.Bar");
    checkLink(link, "foo.Bar", "foo.Bar.html");
  }

  @Test
  public void createNamespacedNamedType_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.provide('foo');",
            "foo.Bar = class {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("foo.Bar");
    checkLink(link, "foo.Bar", "../foo.Bar.html");
  }

  @Test
  public void createModuleNamedType_fromGlobalScope() {
    util.compile(fs.getPath("source/modules/foo/bar/baz.js"),
        "export default class {}");

    NamedType link = createFactory().createLink("foo/bar/baz");
    checkLink(link, "foo/bar/baz", "module/foo/bar/baz.html");
  }

  @Test
  public void createModuleNamedType_fromGlobalScopeToTopLevelModule() {
    util.compile(fs.getPath("source/modules/foo.js"),
        "export default class {}");

    NamedType link = createFactory().createLink("foo");
    checkLink(link, "foo", "module/foo.html");
  }

  @Test
  public void createModuleNamedType_fromGlobalScopeToIndexModuleWithEs6NamingConventions() {
    util.compile(fs.getPath("source/modules/foo/index.js"),
        "export default class {}");

    NamedType link = createFactory().createLink("foo");
    checkLink(link, "foo", "");
  }

  @Test
  public void createModuleNamedType_fromGlobalScopeToIndexModuleWithNodeNamingConventions() {
    guice.toBuilder()
        .setModuleNamingConvention(ModuleNamingConvention.NODE)
        .build()
        .createInjector()
        .injectMembers(this);

    util.compile(fs.getPath("source/modules/foo/index.js"),
        "export default class {}");

    NamedType link = createFactory().createLink("foo");
    checkLink(link, "foo", "module/foo/index.html");
  }

  @Test
  public void createModuleNamedType_fromModule_withFullModulePath() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/foo/bar/baz.js"),
            "export default class {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("foo/bar/baz");
    checkLink(link, "foo/bar/baz", "foo/bar/baz.html");
  }

  @Test
  public void createModuleNamedType_fromModule_withRelativeModulePath1() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/foo/bar/baz.js"),
            "export class A {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("./foo/bar/baz");
    checkLink(link, "foo/bar/baz", "foo/bar/baz.html");

    link = createFactory(ref).withTypeContext(ref).createLink("./foo/bar/baz.A");
    checkLink(link, "A", "foo/bar/baz.A", "foo/bar/baz_exports_A.html");
  }

  @Test
  public void createModuleNamedType_fromModule_withRelativeModulePath2() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/foo/bar/baz.js"),
            "export class A {}"),
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/baz");
    checkLink(link, "foo/bar/baz", "../foo/bar/baz.html");

    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/baz.A");
    checkLink(link, "A", "foo/bar/baz.A", "../foo/bar/baz_exports_A.html");
  }

  @Test
  public void createModuleNamedType_fromModule_withRelativeModulePath3() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {};"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "exports.Y = class {}"));

    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("./one");
    checkLink(link, "one", "one.html");

    link = createFactory(ref).withTypeContext(ref).createLink("./one.X");
    checkLink(link, "X", "one.X", "one_exports_X.html");
  }

  @Test
  public void createIndexModuleNamedType_withRelativeModulePath_es6Conventions() {
    guice.toBuilder()
        .setModuleNamingConvention(ModuleNamingConvention.ES6)
        .build()
        .createInjector()
        .injectMembers(this);

    util.compile(
        createSourceFile(
            fs.getPath("source/modules/foo/bar/index.js"),
            "export default class {}"),
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/index");
    checkLink(link, "foo/bar/index", "../foo/bar/index.html");

    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/");
    checkLink(link, "../foo/bar/", "");

    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar");
    checkLink(link, "../foo/bar", "");
  }

  @Test
  public void createIndexModuleNamedType_withRelativeModulePath_nodeConventions() {
    guice.toBuilder()
        .setModuleNamingConvention(ModuleNamingConvention.NODE)
        .build()
        .createInjector()
        .injectMembers(this);

    util.compile(
        createSourceFile(
            fs.getPath("source/modules/foo/bar/index.js"),
            "export default class {}"),
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one$two");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/index");
    checkLink(link, "foo/bar", "../foo/bar/index.html");

    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/");
    checkLink(link, "foo/bar", "../foo/bar/index.html");

    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar");
    checkLink(link, "foo/bar", "../foo/bar/index.html");
  }

  @Test
  public void createModuleExportedNamedType_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NamedType link = createFactory().createLink("module$source$modules$one$two.B");
    checkLink(link, "B", "one/two.B", "module/one/two_exports_B.html");
  }

  @Test
  public void createModuleExportedNamedType_fromGlobalScopeWithModuleDisplayName() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NamedType link = createFactory().createLink("one/two.B");
    checkLink(link, "B", "one/two.B", "module/one/two_exports_B.html");
  }

  @Test
  public void createModuleExportedNamedType_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    NamedType link = factory.createLink("module$source$modules$one$two.B");
    checkLink(link, "B", "one/two.B", "two_exports_B.html");

    link = factory.createLink("B");
    checkLink(link, "B", "one/two.B", "two_exports_B.html");
  }

  @Test
  public void createModuleExportedNamedType_fromModuleWithModuleDisplayName() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$one$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("one/two.B"), "B", "one/two.B", "two_exports_B.html");
    checkLink(factory.createLink("./two.B"), "B", "one/two.B", "two_exports_B.html");
    checkLink(factory.createLink("B"), "B", "one/two.B", "two_exports_B.html");
  }

  @Test
  public void createModuleExportedTypePropertyLink_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B { go() {} }"));

    LinkFactory factory = createFactory();

    checkLink(factory.createLink("one/two.B#go"),
        "B#go", "one/two.B", "module/one/two_exports_B.html#go");
    checkLink(factory.createLink("./two.B#go"), "./two.B#go", "");
    checkLink(factory.createLink("B#go"), "B#go", "");
  }

  @Test
  public void createModuleExportedTypePropertyLink_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B { go() {} }"));

    NominalType ref = typeRegistry.getType("module$source$modules$one$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("one/two.B#go"),
        "B#go", "one/two.B", "two_exports_B.html#go");
    checkLink(factory.createLink("./two.B#go"),
        "B#go", "one/two.B", "two_exports_B.html#go");
    checkLink(factory.createLink("B#go"),
        "B#go", "one/two.B", "two_exports_B.html#go");
  }

  @Test
  public void createAliasedNamedType_forTypeInsideGoogScopeBlock() {
    util.compile(
        fs.getPath("foo.js"),
        "goog.provide('foo.bar');",
        "goog.scope(function() {",
        "  var fb = foo.bar;",
        "  fb.Baz = class {};",
        "});");

    NominalType ref = typeRegistry.getType("foo.bar.Baz");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("fb.Baz");
    checkLink(link, "foo.bar.Baz", "foo.bar.Baz.html");
  }

  @Test
  public void createAliasedNamedType_forTypeInsideGoogScopeBlock_aliasHidesAnotherType() {
    util.compile(
        fs.getPath("foo.js"),
        "goog.provide('foo.bar');",
        "goog.provide('math');",
        "goog.scope(function() {",
        "  var math = foo.bar;",
        "  math.Baz = class {};",
        "});");

    NominalType ref = typeRegistry.getType("foo.bar.Baz");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("math.Baz");
    checkLink(link, "foo.bar.Baz", "foo.bar.Baz.html");
  }

  @Test
  public void createAliasedNamedType_forTypeRequiredByClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("one.js"),
            "goog.module('one');",
            "exports.One = class {};"),
        createSourceFile(
            fs.getPath("two.js"),
            "goog.module('two');",
            "",
            "var a = goog.require('one');"));

    NominalType ref = typeRegistry.getType("module$exports$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("a"), "one", "one.html");
    checkLink(factory.createLink("a.One"), "One", "one.One", "one.One.html");
  }

  @Test
  public void createAliasedNamedType_forTypeRequiredByNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.One = class {};"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "var a = require('./one');",
            "var b = require('./one').One"));

    NominalType ref = typeRegistry.getType("module$exports$module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("a"), "one", "one.html");
    checkLink(factory.createLink("a.One"), "One", "one.One", "one_exports_One.html");
    checkLink(factory.createLink("b"), "One", "one.One", "one_exports_One.html");
  }

  @Test
  public void createAliasedNamedType_importedEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import * as a from './one';",
            "export class Two extends One {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("a"), "one", "one.html");
    checkLink(factory.createLink("a.One"), "One", "one.One", "one_exports_One.html");
  }

  @Test
  public void createAliasedNamedType_importedEs6ModuleType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {One} from './one';",
            "export class Two extends One {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("One"), "One", "one.One", "one_exports_One.html");
  }

  @Test
  public void createAliasedNamedType_multipleImportedEs6ModuleTypes() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}",
            "export class Two {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {One, Two} from './one';",
            "export class Three extends One {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("One"), "One", "one.One", "one_exports_One.html");
    checkLink(factory.createLink("Two"), "Two", "one.Two", "one_exports_Two.html");
  }

  @Test
  public void createAliasedNamedType_renamedImportedEs6ModuleType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {One as TheOne} from './one';",
            "export class Two extends TheOne {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("TheOne"), "One", "one.One", "one_exports_One.html");
    checkLink(factory.createLink("One"), "One", "");
  }

  @Test
  public void createAliasedNamedType_multipleRenamedImportedEs6ModuleTypes() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}",
            "export class Two {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {One as X, Two as Y} from './one';",
            "export class Three extends X {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("X"), "One", "one.One", "one_exports_One.html");
    checkLink(factory.createLink("Y"), "Two", "one.Two", "one_exports_Two.html");

    checkLink(factory.createLink("One"), "One", "");
    checkLink(factory.createLink("Two"), "Two", "");
  }

  @Test
  public void createAliasedNamedType_importRenamedEs6ModuleDefault() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export default class {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {default as X} from './one';",
            "export class Three extends X {}"));

    NominalType ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("X"), "default", "one.default", "one_exports_default.html");
  }

  @Test
  public void createLinkToStaticProperty() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('utils.array');",
        "utils.array.forEach = function(arr, fn) {};");

    NamedType link = createFactory().createLink("utils.array.forEach");
    checkLink(link, "utils.array.forEach", "utils.array.html#forEach");
  }

  @Test
  public void createLinkToStaticProperty_contextIsOwner() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('utils.array');",
        "utils.array.forEach = function(arr, fn) {};");

    NominalType ref = typeRegistry.getType("utils.array");

    NamedType link = createFactory(ref).withTypeContext(ref).createLink("#forEach");
    checkLink(link, "utils.array.forEach", "utils.array.html#forEach");
  }

  @Test
  public void createLinkToStaticProperty_unknownProperty() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo.bar');",
        "foo.bar.baz = function() {};");

    NamedType link = createFactory().createLink("foo.bar.unknown");
    checkLink(link, "foo.bar.unknown", "foo.bar.html");
  }

  @Test
  public void createLinkToInstanceProperty() {
    util.compile(
        fs.getPath("foo.js"),
        "goog.provide('foo.Bar');",
        "foo.Bar = class {",
        "  static baz() {}",
        "  bar() {}",
        "};");

    LinkFactory factory = createFactory();

    checkLink(factory.createLink("foo.Bar"), "foo.Bar", "foo.Bar.html");
    checkLink(factory.createLink("foo.Bar#"), "foo.Bar", "foo.Bar.html");
    checkLink(factory.createLink("foo.Bar#bar"), "foo.Bar#bar", "foo.Bar.html#bar");
    checkLink(factory.createLink("foo.Bar#bar()"), "foo.Bar#bar", "foo.Bar.html#bar");
    checkLink(factory.createLink("foo.Bar.prototype"), "foo.Bar", "foo.Bar.html");
    checkLink(factory.createLink("foo.Bar.prototype.bar"), "foo.Bar#bar", "foo.Bar.html#bar");
    checkLink(factory.createLink("foo.Bar.prototype.bar()"), "foo.Bar#bar", "foo.Bar.html#bar");
    checkLink(factory.createLink("foo.Bar.prototype.unknown"), "foo.Bar.unknown", "foo.Bar.html");
  }

  @Test
  public void createLinkToEnum() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo');",
        "/** @enum {string} */",
        "/** @enum {string} */",
        "foo.Bar = {yes: 'yes', no: 'no'};",
        "foo.Bar.valueOf = function () {};");

    LinkFactory factory = createFactory();
    checkLink(factory.createLink("foo.Bar"), "foo.Bar", "foo.Bar.html");
    checkLink(factory.createLink("foo.Bar#yes"), "foo.Bar.yes", "foo.Bar.html#yes");
    checkLink(factory.createLink("foo.Bar.valueOf"), "foo.Bar.valueOf", "foo.Bar.html#Bar.valueOf");
  }

  @Test
  public void createLink_qualifiedHashProperty() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @constructor */",
        "foo.Bar = function() {};",
        "foo.Bar.bot = function() {};",
        "foo.Bar.prototype.box = function() {}");

    LinkFactory factory = createFactory();
    checkLink(factory.createLink("foo.Bar#bot"), "foo.Bar.bot", "foo.Bar.html#Bar.bot");
    checkLink(factory.createLink("foo.Bar#box"), "foo.Bar#box", "foo.Bar.html#box");
  }

  @Test
  public void createLink_qualifiedHashProperty_favorsInstanceOverStatic() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @constructor */",
        "foo.Bar = function() {};",
        "foo.Bar.baz = function() {};",
        "foo.Bar.prototype.baz = function() {}");

    LinkFactory factory = createFactory();
    checkLink(factory.createLink("foo.Bar#baz"), "foo.Bar#baz", "foo.Bar.html#baz");
    checkLink(factory.createLink("foo.Bar.baz"), "foo.Bar.baz", "foo.Bar.html#Bar.baz");
  }

  @Test
  public void createLink_cannotReferToInstancePropertyWithDotNotation() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @constructor */",
        "foo.Bar = function() {};",
        "foo.Bar.prototype.baz = function() {}");

    LinkFactory factory = createFactory();
    checkLink(factory.createLink("foo.Bar.baz"), "foo.Bar.baz", "foo.Bar.html");
  }

  @Test
  public void createLink_contextHash_contextIsClass() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @constructor */",
        "foo.Bar = function() { this.x = 123; };",
        "foo.Bar.baz = function() {};",
        "foo.Bar.bar = function() {};",
        "foo.Bar.prototype.bar = function() {}");

    NominalType context = typeRegistry.getType("foo.Bar");
    LinkFactory factory = createFactory(context);

    checkLink(factory.createLink("#bar"), "foo.Bar#bar", "foo.Bar.html#bar");
    checkLink(factory.createLink("#x"), "foo.Bar#x", "foo.Bar.html#x");
    checkLink(factory.createLink("#baz"), "foo.Bar.baz", "foo.Bar.html#Bar.baz");
  }

  @Test
  public void createLink_contextHash_contextIsInterface() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @interface */",
        "foo.Bar = function() {};",
        "foo.Bar.baz = function() {};",
        "foo.Bar.prototype.bar = function() {}");

    NominalType context = typeRegistry.getType("foo.Bar");
    LinkFactory factory = createFactory(context);

    checkLink(factory.createLink("#bar"), "foo.Bar#bar", "foo.Bar.html#bar");
    checkLink(factory.createLink("#baz"), "foo.Bar.baz", "foo.Bar.html#Bar.baz");
  }

  @Test
  public void createLink_contextHash_contextIsEnum() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.provide('foo.Bar');",
        "/** @enum {number} */",
        "foo.Bar = {x: 1, y: 2};",
        "foo.Bar.baz = function() {};");

    NominalType context = typeRegistry.getType("foo.Bar");
    LinkFactory factory = createFactory(context);

    checkLink(factory.createLink("#x"), "foo.Bar.x", "foo.Bar.html#x");
    checkLink(factory.createLink("#baz"), "foo.Bar.baz", "foo.Bar.html#Bar.baz");
  }

  @Test
  public void createLink_contextHash_contextIsModule() {
    util.compile(
        fs.getPath("source/modules/one.js"),
        "exports = {bar: function() {}};");

    NominalType context = typeRegistry.getType("module$exports$module$source$modules$one");
    LinkFactory factory = createFactory(context).withTypeContext(context);

    checkLink(factory.createLink("#bar"), "one.bar", "one.html#bar");
  }

  @Test
  public void createLink_referenceToContextModuleExportedType() {
    util.compile(
        fs.getPath("source/modules/one.js"),
        "/** @constructor */",
        "var InternalClass = function() {};",
        "InternalClass.staticFunc = function() {};",
        "InternalClass.prototype.method = function() {};",
        "exports.ExternalClass = InternalClass");

    NominalType context = typeRegistry.getType("module$exports$module$source$modules$one");
    LinkFactory factory = createFactory(context).withTypeContext(context);

    util.getCompiler().getTypeRegistry();

    checkLink(factory.createLink("InternalClass"),
        "ExternalClass",
        "one.ExternalClass",
        "one_exports_ExternalClass.html");
    checkLink(factory.createLink("InternalClass.staticFunc"),
        "ExternalClass.staticFunc",
        "one.ExternalClass",
        "one_exports_ExternalClass.html#ExternalClass.staticFunc");
    checkLink(factory.createLink("InternalClass#method"),
        "ExternalClass#method",
        "one.ExternalClass",
        "one_exports_ExternalClass.html#method");
  }

  @Test
  public void createLinkToExterns() {
    util.compile(fs.getPath("foo.js"), "class NotUsed {}");
    LinkFactory factory = createFactory();

    String url =
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String";

    checkExternLink(factory.createLink("string"), "string", url);
    checkExternLink(factory.createLink("String"), "String", url);
    checkExternLink(factory.createLink("String.prototype.indexOf"),
        "String.prototype.indexOf", url);
    checkExternLink(factory.createLink("String#indexOf"), "String#indexOf", url);
    checkExternLink(factory.createLink("String.fromCharCode"), "String.fromCharCode", url);
  }

  @Test
  public void createLinkToForwardedExport() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "export {One as Two} from './one';"));

    NominalType ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("One"), "One", "one.One", "one_exports_One.html");
    checkLink(factory.createLink("Two"), "One", "one.One", "one_exports_One.html");
    checkLink(factory.createLink("module$source$modules$two.Two"),
        "Two", "two.Two", "two_exports_Two.html");
  }

  @Test
  public void createLinkWherePathAndTypeContextsDiffer() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/a/b/c.js"),
            "class A {}",
            "export { A as Y }",
            "export class Z {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "",
            "import {Y, Z} from './a/b/c';",
            "export class A {}"));
    NominalType one = typeRegistry.getType("module$source$modules$one");
    NominalType abc = typeRegistry.getType("module$source$modules$a$b$c");

    // Check global scope for a base line.
    LinkFactory factory = createFactory();
    checkLink(factory.createLink("A"), "A", "");
    checkLink(factory.createLink("Z"), "Z", "");

    // Check resolving types relative to one, put paths from global scope.
    factory = createFactory().withTypeContext(one);
    checkLink(factory.createLink("A"), "A", "one.A", "module/one_exports_A.html");
    checkLink(factory.createLink("Y"), "Y", "a/b/c.Y", "module/a/b/c_exports_Y.html");
    checkLink(factory.createLink("Z"), "Z", "a/b/c.Z", "module/a/b/c_exports_Z.html");

    // Check everything relative to abc.
    factory = createFactory().withTypeContext(abc);
    checkLink(factory.createLink("A"), "Y", "a/b/c.Y", "module/a/b/c_exports_Y.html");
    checkLink(factory.createLink("Y"), "Y", "a/b/c.Y", "module/a/b/c_exports_Y.html");
    checkLink(factory.createLink("Z"), "Z", "a/b/c.Z", "module/a/b/c_exports_Z.html");

    // Check type resolution with |one|, but paths generated relative to |abc|.
    factory = createFactory(abc).withTypeContext(one);
    checkLink(factory.createLink("A"), "A", "one.A", "../../one_exports_A.html");
    checkLink(factory.createLink("Y"), "Y", "a/b/c.Y", "c_exports_Y.html");
    checkLink(factory.createLink("Z"), "Z", "a/b/c.Z", "c_exports_Z.html");

    // Check type resolution with |abc|, but paths generated relative to |one|.
    factory = createFactory(one).withTypeContext(abc);
    checkLink(factory.createLink("A"), "Y", "a/b/c.Y", "a/b/c_exports_Y.html");
    checkLink(factory.createLink("Y"), "Y", "a/b/c.Y", "a/b/c_exports_Y.html");
    checkLink(factory.createLink("Z"), "Z", "a/b/c.Z", "a/b/c_exports_Z.html");
  }

  @Test
  public void usesLocalContextForUnqualifiedProperties() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/a/b/c.js"),
            "export class A {",
            "  constructor() {  this.x = 123; }",
            "}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "",
            "import {A} from './a/b/c';",
            "export class B extends A {}"));

    NominalType a = typeRegistry.getType("module$source$modules$a$b$c.A");
    NominalType b = typeRegistry.getType("module$source$modules$one.B");

    LinkFactory factory = createFactory(a).withTypeContext(a);
    checkLink(factory.createLink("#x"), "A#x", "a/b/c.A", "c_exports_A.html#x");

    factory = createFactory(b).withTypeContext(a);
    checkLink(factory.createLink("#x"), "B#x", "one.B", "one_exports_B.html#x");
  }

  @Test
  public void unqualifiedReferenceToPropertiesExportedFromEs6Module() {
    util.compile(
        fs.getPath("source/modules/one.js"),
        "export function foo() {}",
        "export class Foo {}",
        "",
        "function bar() {}",
        "export {bar as Bar}");

    NominalType module = typeRegistry.getType("module$source$modules$one");
    NominalType foo = typeRegistry.getType("module$source$modules$one.Foo");

    LinkFactory factory = createFactory();
    checkLink(factory.createLink("Foo"), "Foo", "");
    checkLink(factory.createLink("foo"), "foo", "");

    factory = factory.withTypeContext(foo);
    checkLink(factory.createLink("Foo"), "Foo", "one.Foo", "module/one_exports_Foo.html");
    checkLink(factory.createLink("foo"), "one.foo", "module/one.html#foo");
    checkLink(factory.createLink("Bar"), "one.Bar", "module/one.html#Bar");
    checkLink(factory.createLink("bar"), "one.Bar", "module/one.html#Bar");

    factory = factory.withTypeContext(module);
    checkLink(factory.createLink("Foo"), "Foo", "one.Foo", "module/one_exports_Foo.html");
    checkLink(factory.createLink("foo"), "one.foo", "module/one.html#foo");
    checkLink(factory.createLink("Bar"), "one.Bar", "module/one.html#Bar");
    checkLink(factory.createLink("bar"), "one.Bar", "module/one.html#Bar");
  }

  @Test
  public void createLinkToDefaultExport_isHoistedDeclaration() {
    util.compile(
        fs.getPath("source/modules/a/b/c.js"),
        "export default class Foo {",
        "  constructor() { this.x = 123; }",
        "}");

    NominalType type = typeRegistry.getType("module$source$modules$a$b$c.default");
    NominalType module = typeRegistry.getType("module$source$modules$a$b$c");

    LinkFactory factory = createFactory();

    checkLink(factory.createLink(type),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(factory.createLink(type, "#x"),
        "default#x", "a/b/c.default", "module/a/b/c_exports_default.html#x");
    checkLink(factory.createLink(type.getName()),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(
        factory.createLink(module, "default"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(
        factory.createLink("a/b/c.default"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");

    checkLink(factory.createLink("a/b/c.Foo"), "a/b/c.Foo", "module/a/b/c.html");
    checkLink(
        factory.withTypeContext(module).createLink("Foo"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
  }

  @Test
  public void createLinkToDefaultExport_isInternalClass1() {
    util.compile(
        fs.getPath("source/modules/a/b/c.js"),
        "class Foo {",
        "  constructor() { this.x = 123; }",
        "}",
        "export default Foo");

    NominalType type = typeRegistry.getType("module$source$modules$a$b$c.default");
    NominalType module = typeRegistry.getType("module$source$modules$a$b$c");

    LinkFactory factory = createFactory();

    checkLink(factory.createLink(type),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(factory.createLink(type, "#x"),
        "default#x", "a/b/c.default", "module/a/b/c_exports_default.html#x");
    checkLink(factory.createLink(type.getName()),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(
        factory.createLink(module, "default"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(
        factory.createLink("a/b/c.default"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");

    checkLink(factory.createLink("a/b/c.Foo"), "a/b/c.Foo", "module/a/b/c.html");
    checkLink(
        factory.withTypeContext(module).createLink("Foo"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
  }

  @Test
  public void createLinkToDefaultExport_isInternalClass2() {
    util.compile(
        fs.getPath("source/modules/a/b/c.js"),
        "class Foo {",
        "  constructor() { this.x = 123; }",
        "}",
        "export {Foo as default}");

    NominalType type = typeRegistry.getType("module$source$modules$a$b$c.default");
    NominalType module = typeRegistry.getType("module$source$modules$a$b$c");

    LinkFactory factory = createFactory();

    checkLink(factory.createLink(type),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(factory.createLink(type, "#x"),
        "default#x", "a/b/c.default", "module/a/b/c_exports_default.html#x");
    checkLink(factory.createLink(type.getName()),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(
        factory.createLink(module, "default"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(
        factory.createLink("a/b/c.default"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");

    checkLink(factory.createLink("a/b/c.Foo"), "a/b/c.Foo", "module/a/b/c.html");
    checkLink(
        factory.withTypeContext(module).createLink("Foo"),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
  }

  @Test
  public void createLinkToDefaultExport_isAnonymousClass() {
    util.compile(
        fs.getPath("source/modules/a/b/c.js"),
        "export default class {",
        "  constructor() { this.x = 123; }",
        "}");

    NominalType type = typeRegistry.getType("module$source$modules$a$b$c.default");
    NominalType module = typeRegistry.getType("module$source$modules$a$b$c");

    LinkFactory factory = createFactory();

    checkLink(factory.createLink(type),
        "default", "a/b/c.default", "module/a/b/c_exports_default.html");
    checkLink(factory.createLink(type, "#x"), "default#x", "a/b/c.default",
        "module/a/b/c_exports_default.html#x");
    checkLink(factory.createLink(type.getName()), "default", "a/b/c.default",
        "module/a/b/c_exports_default.html");
    checkLink(
        factory.createLink(module, "default"), "default", "a/b/c.default",
        "module/a/b/c_exports_default.html");
    checkLink(
        factory.createLink("a/b/c.default"), "default", "a/b/c.default",
        "module/a/b/c_exports_default.html");
  }

  @Test
  public void createLinkToDefaultExport_isAnonymousFunction() {
    util.compile(
        fs.getPath("source/modules/a/b/c.js"),
        "export default function() {}");

    NominalType module = typeRegistry.getType("module$source$modules$a$b$c");
    assertThat(typeRegistry.isType("module$source$modules$a$b$c.default")).isFalse();

    LinkFactory factory = createFactory();

    checkLink(factory.createLink(module, "default"), "a/b/c.default", "module/a/b/c.html#default");
    checkLink(factory.createLink("a/b/c.default"), "a/b/c.default", "module/a/b/c.html#default");

    factory = factory.withTypeContext(module);
    checkLink(factory.createLink("default"), "a/b/c.default", "module/a/b/c.html#default");
  }

  @Test
  public void createLinkToCompilerConstant_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo.bar');",
        "/** @define {number} */",
        "foo.bar.BAZ = 1234;");

    LinkFactory factory = createFactory();
    checkLink(factory.createLink("foo.bar.BAZ"), "foo.bar.BAZ", "foo.bar.html#foo.bar.BAZ");
  }

  @Test
  public void createLinkToCompilerConstant_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.provide('foo.bar');",
            "/** @define {number} */",
            "foo.bar.BAZ = 1234;"));

    NominalType ref = typeRegistry.getType("module$source$modules$one$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);
    checkLink(factory.createLink("foo.bar.BAZ"), "foo.bar.BAZ", "../../foo.bar.html#foo.bar.BAZ");
  }

  @Test
  public void createLinkToGoogDefinedCompilerConstant_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo.bar');",
        "/** @define {number} */",
        "goog.define('foo.bar.BAZ', 1234);");

    LinkFactory factory = createFactory();
    checkLink(factory.createLink("foo.bar.BAZ"), "foo.bar.BAZ", "foo.bar.html#foo.bar.BAZ");
  }

  @Test
  public void createLinkToGoogDefinedCompilerConstant_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"),
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.provide('foo.bar');",
            "/** @define {number} */",
            "goog.define('foo.bar.BAZ', 1234);"));

    NominalType ref = typeRegistry.getType("module$source$modules$one$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);
    checkLink(factory.createLink("foo.bar.BAZ"), "foo.bar.BAZ", "../../foo.bar.html#foo.bar.BAZ");
  }

  @Test
  public void createLinkForExternalModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "let fs = require('fs');",
            "exports.fs = fs;"));

    NominalType one = typeRegistry.getType("module$exports$module$source$modules$one");
    LinkFactory factory = createFactory(one).withTypeContext(one);

    NamedType link = factory.createLink("module$exports$fs");
    checkLink(link, "fs", "");

    link = factory.createLink("module$exports$fs.stat");
    checkLink(link, "fs.stat", "");
  }

  private LinkFactory createFactory() {
    return createFactory(null);
  }

  private LinkFactory createFactory(@Nullable NominalType context) {
    return linkFactoryBuilder.create(context);
  }

  private static void checkLink(SourceLink link, String text, int line) {
    SourceLink expected = SourceLink.newBuilder()
        .setPath(text)
        .setLine(line)
        .build();
    assertMessage(link).isEqualTo(expected);
  }

  private static void checkLink(SourceLink link, String url, String text, int line) {
    SourceLink expected = SourceLink.newBuilder()
        .setPath(text)
        .setLine(line)
        .setUri(url)
        .build();
    assertMessage(link).isEqualTo(expected);
  }

  private static void checkLink(NamedType link, String text, String href) {
    NamedType.Builder expected = NamedType.newBuilder().setName(text);
    if (!href.isEmpty()) {
      expected.setHref(href);
    }
    assertMessage(link).isEqualTo(expected.build());
  }

  private static void checkLink(NamedType link, String name, String qualifiedName, String href) {
    NamedType expected = NamedType.newBuilder()
        .setName(name)
        .setQualifiedName(qualifiedName)
        .setHref(href)
        .build();
    assertMessage(link).isEqualTo(expected);
  }

  private static void checkExternLink(NamedType link, String text, String href) {
    NamedType.Builder expected = NamedType.newBuilder().setName(text).setExtern(true);
    if (!href.isEmpty()) {
      expected.setHref(href);
    }
    assertThat(link).isEqualTo(expected.build());
  }
}
