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

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.jscomp.CompilerOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;

import javax.inject.Inject;

/**
 * Tests for {@link TypeContext}.
 */
@RunWith(JUnit4.class)
public class TypeContextTest {

  @Rule
  public GuiceRule guice = GuiceRule.builder(this)
      .setOutputDir("out")
      .setSourcePrefix("src")
      .setModulePrefix("src/modules")
      .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
      .build();

  @Inject @Input private FileSystem fs;
  @Inject private CompilerUtil util;
  @Inject private TypeRegistry typeRegistry;
  @Inject private TypeContext context;

  @Test
  public void canResolveTypes() {
    util.compile(
        createSourceFile(
            fs.getPath("foo.js"),
            "goog.provide('foo');",
            "class A {}",
            "class B {}",
            "class C {}",
            "goog.scope(function() {",
            "  let ns = foo;",
            "  let A = B;",
            "  let D = C;",
            "  ns.X = class X {}",
            "});"),
        createSourceFile(
            fs.getPath("bar.js"),
            "goog.provide('bar');",
            "goog.scope(function() {",
            "  let ns = bar;",
            "  let A = C;",
            "  let B = foo.X;",
            "  ns.X = class Y {}",
            "});"));

    assertThat(context.resolveType("A")).isSameAs(typeRegistry.getType("A"));
    assertThat(context.resolveType("B")).isSameAs(typeRegistry.getType("B"));
    assertThat(context.resolveType("C")).isSameAs(typeRegistry.getType("C"));
    assertThat(context.resolveType("D")).isNull();
    assertThat(context.resolveType("ns")).isNull();
    assertThat(context.resolveType("ns.X")).isNull();

    context = context.changeContext(typeRegistry.getType("foo.X"));
    assertThat(context.resolveType("A")).isSameAs(typeRegistry.getType("B"));
    assertThat(context.resolveType("B")).isSameAs(typeRegistry.getType("B"));
    assertThat(context.resolveType("C")).isSameAs(typeRegistry.getType("C"));
    assertThat(context.resolveType("D")).isSameAs(typeRegistry.getType("C"));
    assertThat(context.resolveType("ns")).isSameAs(typeRegistry.getType("foo"));
    assertThat(context.resolveType("ns.X")).isSameAs(typeRegistry.getType("foo.X"));

    context = context.changeContext(typeRegistry.getType("bar.X"));
    assertThat(context.resolveType("A")).isSameAs(typeRegistry.getType("C"));
    assertThat(context.resolveType("B")).isSameAs(typeRegistry.getType("foo.X"));
    assertThat(context.resolveType("C")).isSameAs(typeRegistry.getType("C"));
    assertThat(context.resolveType("D")).isNull();
    assertThat(context.resolveType("ns")).isSameAs(typeRegistry.getType("bar"));
    assertThat(context.resolveType("ns.X")).isSameAs(typeRegistry.getType("bar.X"));
  }

  @Test
  public void canResolveTypesWithModuleContexts() {
    util.compile(
        createSourceFile(
            fs.getPath("globals.js"),
            "class A {}"),
        createSourceFile(
            fs.getPath("src/modules/foo.js"),
            "class A {}",
            "export {A as B}"),
        createSourceFile(
            fs.getPath("src/modules/dir/foo.js"),
            "class A {}",
            "export {A as C}"));

    NominalType typeA = typeRegistry.getType("A");
    NominalType typeB = typeRegistry.getType("module$src$modules$foo.B");
    NominalType typeC = typeRegistry.getType("module$src$modules$dir$foo.C");
    NominalType moduleFoo = typeRegistry.getType("module$src$modules$foo");
    NominalType moduleDirFoo = typeRegistry.getType("module$src$modules$dir$foo");
    NominalType moduleFooTypeB = typeRegistry.getType("module$src$modules$foo.B");
    NominalType moduleDirFooTypeC = typeRegistry.getType("module$src$modules$dir$foo.C");

    assertThat(context.resolveType("A")).isSameAs(typeA);
    assertThat(context.resolveType("foo")).isSameAs(moduleFoo);
    assertThat(context.resolveType("foo.B")).isSameAs(moduleFooTypeB);
    assertThat(context.resolveType("dir/foo")).isSameAs(moduleDirFoo);
    assertThat(context.resolveType("dir/foo.C")).isSameAs(moduleDirFooTypeC);
    assertThat(context.resolveType("./foo")).isSameAs(moduleFoo);
    assertThat(context.resolveType("./foo.B")).isSameAs(moduleFooTypeB);
    assertThat(context.resolveType("./dir/foo")).isSameAs(moduleDirFoo);
    assertThat(context.resolveType("./dir/foo.C")).isSameAs(moduleDirFooTypeC);
    assertThat(context.resolveType("../foo")).isNull();

    context = context.changeContext(moduleFoo);
    assertThat(context.resolveType("A")).isSameAs(typeB);
    assertThat(context.resolveType("foo")).isSameAs(moduleFoo);
    assertThat(context.resolveType("dir/foo")).isSameAs(moduleDirFoo);
    assertThat(context.resolveType("./foo")).isSameAs(moduleFoo);
    assertThat(context.resolveType("./dir/foo")).isSameAs(moduleDirFoo);
    assertThat(context.resolveType("../foo")).isNull();

    context = context.changeContext(moduleDirFoo);
    assertThat(context.resolveType("A")).isSameAs(typeC);
    assertThat(context.resolveType("foo")).isSameAs(moduleFoo);
    assertThat(context.resolveType("dir/foo")).isSameAs(moduleDirFoo);
    assertThat(context.resolveType("./foo")).isSameAs(moduleDirFoo);
    assertThat(context.resolveType("./dir/foo")).isNull();
    assertThat(context.resolveType("../foo")).isSameAs(moduleFoo);
  }

  @Test
  public void canResolveImportedTypeNames() {
    util.compile(
        createSourceFile(
            fs.getPath("globals.js"),
            "class A {}"),
        createSourceFile(
            fs.getPath("src/modules/foo.js"),
            "class A {}",
            "class B {}",
            "export {A as B, B as default}"),
        createSourceFile(
            fs.getPath("src/modules/bar.js"),
            "import Foo from './foo';",
            "import {default as Bar} from './foo';",
            "import Baz, {B, B as C} from './foo';",
            "",
            "export default class {}"));

    NominalType fooB = typeRegistry.getType("module$src$modules$foo.B");
    NominalType fooDefault = typeRegistry.getType("module$src$modules$foo.default");

    NominalType moduleBar = typeRegistry.getType("module$src$modules$bar");
    context = context.changeContext(moduleBar);

    assertThat(context.resolveType("Foo")).isSameAs(fooDefault);
    assertThat(context.resolveType("Bar")).isSameAs(fooDefault);
    assertThat(context.resolveType("Baz")).isSameAs(fooDefault);
    assertThat(context.resolveType("B")).isSameAs(fooB);
    assertThat(context.resolveType("C")).isSameAs(fooB);
  }

  @Test
  public void doesNotResolveAsTypeWhenDefaultExportIsNotANominalType() {
    util.compile(
        createSourceFile(
            fs.getPath("src/modules/foo.js"),
            "export default 123;"),
        createSourceFile(
            fs.getPath("src/modules/bar.js"),
            "export default function() {}"),
        createSourceFile(
            fs.getPath("src/modules/baz.js"),
            "import foo from './foo';",
            "import bar from './bar';",
            "export default class {}"));

    NominalType module = typeRegistry.getType("module$src$modules$baz");
    context = context.changeContext(module);

    assertThat(context.resolveType("foo")).isNull();
    assertThat(context.resolveType("bar")).isNull();
  }

  @Test
  public void canResolveReexportedTypes() {
    util.compile(
        createSourceFile(
            fs.getPath("src/modules/foo.js"),
            "export default class {}",
            "export class A {}"),
        createSourceFile(
            fs.getPath("src/modules/bar.js"),
            "export {default as B, A as C} from './foo';"),
        createSourceFile(
            fs.getPath("src/modules/baz.js"),
            "import {B, C} from './bar';",
            "export default class {}"));

    NominalType fooA = typeRegistry.getType("module$src$modules$foo.A");
    NominalType fooDefault = typeRegistry.getType("module$src$modules$foo.default");
    NominalType barB = typeRegistry.getType("module$src$modules$bar.B");
    NominalType barC = typeRegistry.getType("module$src$modules$bar.C");

    NominalType moduleBar = typeRegistry.getType("module$src$modules$bar");
    NominalType moduleBaz = typeRegistry.getType("module$src$modules$baz");

    context = context.changeContext(moduleBar);
    assertWithMessage("default is not a valid identifier")
        .that(context.resolveType("default"))
        .isNull();

    assertThat(context.resolveType("A")).isSameAs(fooA);

    context = context.changeContext(moduleBaz);
    assertThat(context.resolveType("B")).isSameAs(barB);
    assertThat(context.resolveType("C")).isSameAs(barC);

    assertThat(typeRegistry.getTypes(barB.getType()))
        .containsExactly(fooDefault, barB)
        .inOrder();
    assertThat(typeRegistry.getTypes(barC.getType()))
        .containsExactly(fooA, barC)
        .inOrder();
  }
}
