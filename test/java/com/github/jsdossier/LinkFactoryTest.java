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

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.NominalType2;
import com.github.jsdossier.jscomp.TypeRegistry2;
import com.github.jsdossier.proto.TypeLink;
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
  @Inject private TypeRegistry2 typeRegistry;
  @Inject private CompilerUtil util;
  @Inject private LinkFactoryBuilder linkFactoryBuilder;
  
  @Test
  public void generateLinkToGlobalType_fromGlobalScope() {
    util.compile(fs.getPath("source/foo.js"),
        "class Foo {}");

    NominalType2 type = typeRegistry.getType("Foo");
    TypeLink link = createFactory().createLink(type);
    checkLink(link, "Foo", "Foo.html");
  }
  
  @Test
  public void generateLinkToGlobalType_fromGlobalType() {
    util.compile(fs.getPath("source/foo.js"),
        "class Foo {}",
        "class Bar {}");

    NominalType2 foo = typeRegistry.getType("Foo");
    NominalType2 bar = typeRegistry.getType("Bar");
    
    TypeLink link = createFactory().withTypeContext(bar).createLink(foo);
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

    NominalType2 foo = typeRegistry.getType("Foo");
    NominalType2 ref = typeRegistry.getType("a.b.X");
    
    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(foo);
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

    NominalType2 foo = typeRegistry.getType("Foo");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");
    
    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(foo);
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

    NominalType2 foo = typeRegistry.getType("Foo");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one.X");
    
    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "Foo", "../Foo.html");
  }

  @Test 
  public void generateLinkToNamespacedType_fromGlobalScope() {
    util.compile(fs.getPath("source/foo.js"),
        "goog.provide('foo.bar');",
        "foo.bar.Baz = class {};");

    NominalType2 type = typeRegistry.getType("foo.bar.Baz");
    TypeLink link = createFactory().createLink(type);
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

    NominalType2 foo = typeRegistry.getType("foo.Bar");
    NominalType2 ref = typeRegistry.getType("Foo");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(foo);
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

    NominalType2 foo = typeRegistry.getType("foo.Bar");
    NominalType2 ref = typeRegistry.getType("a.b.X");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(foo);
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

    NominalType2 foo = typeRegistry.getType("foo.Bar");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(foo);
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

    NominalType2 foo = typeRegistry.getType("foo.Bar");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one.X");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(foo);
    checkLink(link, "foo.Bar", "../foo.Bar.html");
  }
  
  @Test
  public void generateLinkToClosureModule_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"));
    
    NominalType2 type = typeRegistry.getType("a.b");

    TypeLink link = createFactory().createLink(type);
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

    NominalType2 type = typeRegistry.getType("a.b");
    NominalType2 ref = typeRegistry.getType("Bar");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("a.b");
    NominalType2 ref = typeRegistry.getType("x");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("a.b");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("a.b");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "a.b", "../a.b.html");
  }
  
  @Test
  public void generateLinkToClosureModuleExportedType_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/module.js"),
            "goog.module('a.b');",
            "exports.X = class {}"));

    NominalType2 type = typeRegistry.getType("a.b.X");

    TypeLink link = createFactory().createLink(type);
    checkLink(link, "X", "a.b.X.html");
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

    NominalType2 type = typeRegistry.getType("a.b.X");
    NominalType2 ref = typeRegistry.getType("Bar");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "a.b.X.html");
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

    NominalType2 type = typeRegistry.getType("a.b.X");
    NominalType2 ref = typeRegistry.getType("x");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "a.b.X.html");
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

    NominalType2 type = typeRegistry.getType("a.b.X");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "../a.b.X.html");
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

    NominalType2 type = typeRegistry.getType("a.b.X");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "../a.b.X.html");
  }

  @Test
  public void generateLinkToNodeModule_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"));

    NominalType2 type = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory().createLink(type);
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one");
    NominalType2 ref = typeRegistry.getType("Bar");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one");
    NominalType2 ref = typeRegistry.getType("x");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one");
    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one");
    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "one.html");
  }

  @Test
  public void generateLinkToNodeModuleExportedType_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {}"));

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");

    TypeLink link = createFactory().createLink(type);
    checkLink(link, "X", "module/one_exports_X.html");
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");
    NominalType2 ref = typeRegistry.getType("Bar");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "module/one_exports_X.html");
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");
    NominalType2 ref = typeRegistry.getType("x");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "module/one_exports_X.html");
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");
    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    checkLink(createFactory(ref).withTypeContext(ref).createLink(type), "X", "one_exports_X.html");
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");
    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one_exports_X.html");
  }
  
  @Test
  public void generateLinkToEs6Module_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"));

    NominalType2 type = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory().createLink(type);
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one");
    NominalType2 ref = typeRegistry.getType("Bar");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one");
    NominalType2 ref = typeRegistry.getType("x");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one");
    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one");
    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "one", "one.html");
  }

  @Test
  public void generateLinkToEs6ModuleExportedType_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class X {}"));

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");

    TypeLink link = createFactory().createLink(type);
    checkLink(link, "X", "module/one_exports_X.html");
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");
    NominalType2 ref = typeRegistry.getType("Bar");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "module/one_exports_X.html");
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");
    NominalType2 ref = typeRegistry.getType("x");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "module/one_exports_X.html");
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");
    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one_exports_X.html");
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

    NominalType2 type = typeRegistry.getType("module$source$modules$one.X");
    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "X", "one_exports_X.html");
  }
  
  @Test
  public void generateLinkToGlobalTypedef_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "/** @typedef {string} */",
        "var AString;");
    
    NominalType2 type = typeRegistry.getType("AString");

    TypeLink link = createFactory().createLink(type);
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
    
    NominalType2 type = typeRegistry.getType("AString");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "AString", "../.globals.html#AString");
  }
  
  @Test
  public void generateLinkToGlobalCompilerConstant_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "/** @define {boolean} */",
        "var DEBUG=true;");
    
    NominalType2 type = typeRegistry.getType("DEBUG");

    TypeLink link = createFactory().createLink(type);
    checkLink(link, "DEBUG", ".globals.html#DEBUG");
  }
  
  @Test
  public void generateLinkToGlobalCompilerConstant_fromEs6Modules() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "/** @define {boolean} */",
            "var DEBUG=true;"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));
    
    NominalType2 type = typeRegistry.getType("DEBUG");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "DEBUG", "../.globals.html#DEBUG");
  }

  @Test
  public void generateLinkToNamespacedTypedef_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo');",
        "/** @typedef {string} */",
        "foo.AString;");

    NominalType2 type = typeRegistry.getType("foo.AString");

    TypeLink link = createFactory().createLink(type);
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

    NominalType2 type = typeRegistry.getType("foo.AString");
    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink(type);
    checkLink(link, "foo.AString", "../foo.html#foo.AString");
  }
  
  @Test
  public void createLink_unresolvedSymbol() {
    util.compile(fs.getPath("foo.js"),
        "class Foo {}");

    TypeLink link = createFactory().createLink("Bar");
    checkLink(link, "Bar", "");
  }
  
  @Test
  public void createGlobalTypeLink_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "class Foo {}");
    
    TypeLink link = createFactory().createLink("Foo");
    checkLink(link, "Foo", "Foo.html");
  }
  
  @Test
  public void createGlobalTypeLink_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "class Foo {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$one");
    
    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("Foo");
    checkLink(link, "Foo", "../Foo.html");
  }
  
  @Test
  public void createNamespacedTypeLink_fromGlobalScope() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo');",
        "foo.Bar = class {}");
    
    TypeLink link = createFactory().createLink("foo.Bar");
    checkLink(link, "foo.Bar", "foo.Bar.html");
  }
  
  @Test
  public void createNamespacedTypeLink_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.provide('foo');",
            "foo.Bar = class {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$one");
    
    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("foo.Bar");
    checkLink(link, "foo.Bar", "../foo.Bar.html");
  }
  
  @Test
  public void createModuleTypeLink_fromGlobalScope() {
    util.compile(fs.getPath("source/modules/foo/bar/baz.js"),
        "export default class {}");

    TypeLink link = createFactory().createLink("foo/bar/baz");
    checkLink(link, "foo/bar/baz", "module/foo/bar/baz.html");
  }
  
  @Test
  public void createModuleTypeLink_fromModule_withFullModulePath() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/foo/bar/baz.js"),
            "export default class {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("foo/bar/baz");
    checkLink(link, "foo/bar/baz", "foo/bar/baz.html");
  }
  
  @Test
  public void createModuleTypeLink_fromModule_withRelativeModulePath1() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/foo/bar/baz.js"),
            "export class A {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class B {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$one");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("./foo/bar/baz");
    checkLink(link, "foo/bar/baz", "foo/bar/baz.html");

    link = createFactory(ref).withTypeContext(ref).createLink("./foo/bar/baz.A");
    checkLink(link, "A", "foo/bar/baz_exports_A.html");
  }
  
  @Test
  public void createModuleTypeLink_fromModule_withRelativeModulePath2() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/foo/bar/baz.js"),
            "export class A {}"),
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$one$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/baz");
    checkLink(link, "foo/bar/baz", "../foo/bar/baz.html");

    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/baz.A");
    checkLink(link, "A", "../foo/bar/baz_exports_A.html");
  }
  
  @Test
  public void createModuleTypeLink_fromModule_withRelativeModulePath3() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.X = class {};"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "exports.Y = class {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("./one");
    checkLink(link, "one", "one.html");
    
    link = createFactory(ref).withTypeContext(ref).createLink("./one.X");
    checkLink(link, "X", "one_exports_X.html");
  }
  
  @Test
  public void createIndexModuleTypeLink_withRelativeModulePath_es6Conventions() {
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

    NominalType2 ref = typeRegistry.getType("module$source$modules$one$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/index");
    checkLink(link, "foo/bar/index", "../foo/bar/index.html");

    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/");
    checkLink(link, "../foo/bar/", "");

    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar");
    checkLink(link, "../foo/bar", "");
  }
  
  @Test
  public void createIndexModuleTypeLink_withRelativeModulePath_nodeConventions() {
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

    NominalType2 ref = typeRegistry.getType("module$source$modules$one$two");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/index");
    checkLink(link, "foo/bar", "../foo/bar/index.html");
    
    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar/");
    checkLink(link, "foo/bar", "../foo/bar/index.html");
    
    link = createFactory(ref).withTypeContext(ref).createLink("../foo/bar");
    checkLink(link, "foo/bar", "../foo/bar/index.html");
  }
  
  @Test
  public void createModuleExportedTypeLink_fromGlobalScope() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    TypeLink link = createFactory().createLink("module$source$modules$one$two.B");
    checkLink(link, "B", "module/one/two_exports_B.html");
  }
  
  @Test
  public void createModuleExportedTypeLink_fromModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one/two.js"),
            "export class B {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$one$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    TypeLink link = factory.createLink("module$source$modules$one$two.B");
    checkLink(link, "B", "two_exports_B.html");

    link = factory.createLink("B");
    checkLink(link, "B", "two_exports_B.html");
  }

  @Test
  public void createAliasedTypeLink_forTypeInsideGoogScopeBlock() {
    util.compile(
        fs.getPath("foo.js"),
        "goog.provide('foo.bar');",
        "goog.scope(function() {",
        "  var fb = foo.bar;",
        "  fb.Baz = class {};",
        "});");

    NominalType2 ref = typeRegistry.getType("foo.bar.Baz");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("fb.Baz");
    checkLink(link, "foo.bar.Baz", "foo.bar.Baz.html");
  }
  
  @Test
  public void createAliasedTypeLink_forTypeInsideGoogScopeBlock_aliasHidesAnotherType() {
    util.compile(
        fs.getPath("foo.js"),
        "goog.provide('foo.bar');",
        "goog.provide('math');",
        "goog.scope(function() {",
        "  var math = foo.bar;",
        "  math.Baz = class {};",
        "});");

    NominalType2 ref = typeRegistry.getType("foo.bar.Baz");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("math.Baz");
    checkLink(link, "foo.bar.Baz", "foo.bar.Baz.html");
  }
  
  @Test
  public void createAliasedTypeLink_forTypeRequiredByClosureModule() {
    util.compile(
        createSourceFile(
            fs.getPath("one.js"),
            "goog.module('one');",
            "exports.One = class {};"),
        createSourceFile(
            fs.getPath("two.js"),
            "goog.module('two');",
            "",
            "var a = goog.require('one');",
            "var b = goog.require('one').One"));

    NominalType2 ref = typeRegistry.getType("two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("a"), "one", "one.html");
    checkLink(factory.createLink("a.One"), "One", "one.One.html");
    checkLink(factory.createLink("b"), "One", "one.One.html");
  }
  
  @Test
  public void createAliasedTypeLink_forTypeRequiredByNodeModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "exports.One = class {};"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "var a = require('./one');",
            "var b = require('./one').One"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("a"), "one", "one.html");
    checkLink(factory.createLink("a.One"), "One", "one_exports_One.html");
    checkLink(factory.createLink("b"), "One", "one_exports_One.html");
  }

  @Test
  public void createAliasedTypeLink_importedEs6Module() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import * as a from './one';",
            "export class Two extends One {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("a"), "one", "one.html");
    checkLink(factory.createLink("a.One"), "One", "one_exports_One.html");
  }

  @Test
  public void createAliasedTypeLink_importedEs6ModuleType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import One from './one';",
            "export class Two extends One {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("One"), "One", "one_exports_One.html");
  }

  @Test
  public void createAliasedTypeLink_multipleImportedEs6ModuleTypes() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}",
            "export class Two {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {One, Two} from './one';",
            "export class Three extends One {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("One"), "One", "one_exports_One.html");
    checkLink(factory.createLink("Two"), "Two", "one_exports_Two.html");
  }

  @Test
  public void createAliasedTypeLink_renamedImportedEs6ModuleType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {One as TheOne} from './one';",
            "export class Two extends TheOne {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("TheOne"), "One", "one_exports_One.html");
    checkLink(factory.createLink("One"), "One", "");
  }

  @Test
  public void createAliasedTypeLink_multipleRenamedImportedEs6ModuleTypes() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class One {}",
            "export class Two {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {One as X, Two as Y} from './one';",
            "export class Three extends X {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("X"), "One", "one_exports_One.html");
    checkLink(factory.createLink("Y"), "Two", "one_exports_Two.html");

    checkLink(factory.createLink("One"), "One", "");
    checkLink(factory.createLink("Two"), "Two", "");
  }

  @Test
  public void createAliasedTypeLink_importRenamedEs6ModuleDefault() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export default class {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {default as X} from './one';",
            "export class Three extends X {}"));

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("X"), "one", "one.html");
  }
  
  @Test
  public void createLinkToStaticProperty() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('util.array');",
        "util.array.forEach = function(arr, fn) {};");

    TypeLink link = createFactory().createLink("util.array.forEach");
    checkLink(link, "util.array.forEach", "util.array.html#forEach");
  }
  
  @Test
  public void createLinkToStaticProperty_contextIsOwner() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('util.array');",
        "util.array.forEach = function(arr, fn) {};");

    NominalType2 ref = typeRegistry.getType("util.array");

    TypeLink link = createFactory(ref).withTypeContext(ref).createLink("#forEach");
    checkLink(link, "util.array.forEach", "util.array.html#forEach");
  }
  
  @Test
  public void createLinkToStaticProperty_unknownProperty() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('foo.bar');",
        "foo.bar.baz = function() {};");

    TypeLink link = createFactory().createLink("foo.bar.unknown");
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

    NominalType2 context = typeRegistry.getType("foo.Bar");
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

    NominalType2 context = typeRegistry.getType("foo.Bar");
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

    NominalType2 context = typeRegistry.getType("foo.Bar");
    LinkFactory factory = createFactory(context);

    checkLink(factory.createLink("#x"), "foo.Bar.x", "foo.Bar.html#x");
    checkLink(factory.createLink("#baz"), "foo.Bar.baz", "foo.Bar.html#Bar.baz");
  }
  
  @Test
  public void createLink_contextHash_contextIsModule() {
    util.compile(
        fs.getPath("source/modules/one.js"),
        "exports = {bar: function() {}};");

    NominalType2 context = typeRegistry.getType("module$source$modules$one");
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

    NominalType2 context = typeRegistry.getType("module$source$modules$one");
    LinkFactory factory = createFactory(context).withTypeContext(context);

    util.getCompiler().getTypeRegistry();

    checkLink(factory.createLink("InternalClass"),
        "ExternalClass",
        "one_exports_ExternalClass.html");
    checkLink(factory.createLink("InternalClass.staticFunc"),
        "ExternalClass.staticFunc",
        "one_exports_ExternalClass.html#ExternalClass.staticFunc");
    checkLink(factory.createLink("InternalClass#method"),
        "ExternalClass#method",
        "one_exports_ExternalClass.html#method");
  }
  
  @Test
  public void createLinkToExterns() {
    util.compile(fs.getPath("foo.js"), "class NotUsed {}");
    LinkFactory factory = createFactory();

    String url = 
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String";

    checkLink(factory.createLink("string"), "string", url);
    checkLink(factory.createLink("String"), "String", url);
    checkLink(factory.createLink("String.prototype.indexOf"),
        "String.prototype.indexOf", url);
    checkLink(factory.createLink("String#indexOf"), "String#indexOf", url);
    checkLink(factory.createLink("String.fromCharCode"), "String.fromCharCode", url);
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

    NominalType2 ref = typeRegistry.getType("module$source$modules$two");
    LinkFactory factory = createFactory(ref).withTypeContext(ref);

    checkLink(factory.createLink("One"), "One", "");
    checkLink(factory.createLink("Two"), "One", "one_exports_One.html");
    checkLink(factory.createLink("module$source$modules$two.Two"), "Two", "two_exports_Two.html");
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
    NominalType2 one = typeRegistry.getType("module$source$modules$one");
    NominalType2 abc = typeRegistry.getType("module$source$modules$a$b$c");
    
    // Check global scope for a base line.
    LinkFactory factory = createFactory();
    checkLink(factory.createLink("A"), "A", "");
    checkLink(factory.createLink("Z"), "Z", "");
    
    // Check resolving types relative to one, put paths from global scope.
    factory = createFactory().withTypeContext(one);
    checkLink(factory.createLink("A"), "A", "module/one_exports_A.html");
    checkLink(factory.createLink("Y"), "Y", "module/a/b/c_exports_Y.html");
    checkLink(factory.createLink("Z"), "Z", "module/a/b/c_exports_Z.html");
    
    // Check everything relative to abc.
    factory = createFactory().withTypeContext(abc);
    checkLink(factory.createLink("A"), "Y", "module/a/b/c_exports_Y.html");
    checkLink(factory.createLink("Y"), "Y", "");  // Because Y is an export alias.
    checkLink(factory.createLink("Z"), "Z", "module/a/b/c_exports_Z.html");
    
    // Check type resolution with |one|, but paths generated relative to |abc|.
    factory = createFactory(abc).withTypeContext(one);
    checkLink(factory.createLink("A"), "A", "../../one_exports_A.html");
    checkLink(factory.createLink("Y"), "Y", "c_exports_Y.html");
    checkLink(factory.createLink("Z"), "Z", "c_exports_Z.html");
    
    // Check type resolution with |abc|, but paths generated relative to |one|.
    factory = createFactory(one).withTypeContext(abc);
    checkLink(factory.createLink("A"), "Y", "a/b/c_exports_Y.html");
    checkLink(factory.createLink("Y"), "Y", "");
    checkLink(factory.createLink("Z"), "Z", "a/b/c_exports_Z.html");
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

    NominalType2 a = typeRegistry.getType("module$source$modules$a$b$c.A");
    NominalType2 b = typeRegistry.getType("module$source$modules$one.B");

    LinkFactory factory = createFactory(a).withTypeContext(a);
    checkLink(factory.createLink("#x"), "A#x", "c_exports_A.html#x");
    
    factory = createFactory(b).withTypeContext(a);
    checkLink(factory.createLink("#x"), "B#x", "one_exports_B.html#x");
  }

  private LinkFactory createFactory() {
    return createFactory(null);
  }

  private LinkFactory createFactory(@Nullable NominalType2 context) {
    return linkFactoryBuilder.create(context);
  }

  private static void checkLink(TypeLink link, String text, String href) {
    TypeLink.Builder expected = TypeLink.newBuilder()
        .setText(text);
    if (!href.isEmpty()) {
      expected.setHref(href);
    }
    assertThat(link).isEqualTo(expected.build());
  }
}
