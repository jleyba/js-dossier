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

package com.github.jsdossier.jscomp;

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.base.Preconditions.checkNotNull;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.truth.Truth;
import java.nio.file.FileSystem;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for tracking types defined within a node module. */
@RunWith(JUnit4.class)
public class NodeAliasDetectionTest {
  @Rule
  public GuiceRule guice =
      GuiceRule.builder(this)
          .setModulePrefix("/modules")
          .setModules("/modules/one.js", "/modules/two.js", "/modules.three.js")
          .setUseNodeLibrary(false)
          .build();

  @Inject @Input private FileSystem inputFs;
  @Inject private TypeRegistry typeRegistry;
  @Inject private CompilerUtil util;

  private NominalType context;

  @Test
  public void recordsAliasedImports() {
    util.compile(
        createSourceFile(
            inputFs.getPath("/modules/one.js"),
            "exports.One = class {};",
            "exports.Two = class {};",
            "exports.Three = class {};",
            "class X {}",
            "class Y {}",
            "exports.Four = {X, Y};"),
        createSourceFile(
            inputFs.getPath("/modules/two.js"),
            "var a = require('./one');",
            "var b = require('./one').One;",
            "var {One, Two, Three} = require('./one');",
            "var {X, Y} = require('./one').Four;"));

    NominalType type = typeRegistry.getType("module$exports$module$modules$two");
    setContext(type);
    assertThat("a").isAliasFor("module$exports$module$modules$one");
    assertThat("b").isAliasFor("module$exports$module$modules$one.One");
    assertThat("One").isAliasFor("module$exports$module$modules$one.One");
    assertThat("Two").isAliasFor("module$exports$module$modules$one.Two");
    assertThat("Three").isAliasFor("module$exports$module$modules$one.Three");
    assertThat("X").isAliasFor("module$exports$module$modules$one.Four.X");
    assertThat("Y").isAliasFor("module$exports$module$modules$one.Four.Y");
  }

  @Test
  public void canResolveInternalTypesThatAreNotExported() {
    util.compile(
        createSourceFile(
            inputFs.getPath("/modules/one.js"), "class X {}", "exports.A = class {};"));

    setContext(typeRegistry.getType("module$exports$module$modules$one.A"));
    assertThat("X").isAliasFor("module$contents$module$modules$one_X");
  }

  @Test
  public void recordsContentAliases() {
    util.compile(
        createSourceFile(
            inputFs.getPath("/modules/one.js"),
            "exports.One = class {};",
            "exports.Two = class {};",
            "exports.Three = class {};",
            "class X {}",
            "class Y {}",
            "/** @constructor */",
            "var Z = function() {}",
            "exports.Four = Z;"),
        createSourceFile(
            inputFs.getPath("/modules/two.js"),
            "var a = require('./one');",
            "var b = require('./one').One;",
            "var {One, Two, Three} = require('./one');"));

    setContext(typeRegistry.getType("module$exports$module$modules$one"));
    assertThat("X").isAliasFor("module$contents$module$modules$one_X");
    assertThat("Y").isAliasFor("module$contents$module$modules$one_Y");
    assertThat("Z").isAliasFor("module$contents$module$modules$one_Z");

    setContext(typeRegistry.getType("module$exports$module$modules$one.One"));
    assertThat("X").isAliasFor("module$contents$module$modules$one_X");
    assertThat("Y").isAliasFor("module$contents$module$modules$one_Y");
    assertThat("Z").isAliasFor("module$contents$module$modules$one_Z");

    setContext(typeRegistry.getType("module$exports$module$modules$one.Four"));
    assertThat("X").isAliasFor("module$contents$module$modules$one_X");
    assertThat("Y").isAliasFor("module$contents$module$modules$one_Y");
    assertThat("Z").isAliasFor("module$contents$module$modules$one_Z");

    setContext(typeRegistry.getType("module$exports$module$modules$two"));
    assertThat("module$contents$module$modules$two_a")
        .isAliasFor("module$exports$module$modules$one");
    assertThat("module$contents$module$modules$two_b")
        .isAliasFor("module$exports$module$modules$one.One");
    assertThat("module$contents$module$modules$two_One")
        .isAliasFor("module$exports$module$modules$one.One");
    assertThat("module$contents$module$modules$two_Two")
        .isAliasFor("module$exports$module$modules$one.Two");
  }

  @Test
  public void recordsContentAliases_multipleLevels() {
    util.compile(
        createSourceFile(inputFs.getPath("/modules/one.js"), "exports.One = class {};"),
        createSourceFile(
            inputFs.getPath("/modules/two.js"),
            "const a = require('./one');",
            "const b = a;",
            "const c = b;",
            "const One = a.One;",
            "const Two = b.One;",
            "const Three = c.One;",
            "exports.Two = One;"));

    NominalType type = typeRegistry.getType("module$exports$module$modules$two");
    setContext(type);
    assertThat("module$contents$module$modules$two_a")
        .isAliasFor("module$exports$module$modules$one");
    assertThat("module$contents$module$modules$two_a")
        .isAliasFor("module$exports$module$modules$one");
    assertThat("module$contents$module$modules$two_b")
        .isAliasFor("module$exports$module$modules$one");
    assertThat("module$contents$module$modules$two_c")
        .isAliasFor("module$exports$module$modules$one");
    assertThat("module$contents$module$modules$two_One")
        .isAliasFor("module$exports$module$modules$one.One");
    assertThat("module$contents$module$modules$two_Two")
        .isAliasFor("module$exports$module$modules$one.One");
    assertThat("module$contents$module$modules$two_Three")
        .isAliasFor("module$exports$module$modules$one.One");
  }

  @Test
  public void canResolveHiddenVarAliases() {
    util.compile(
        createSourceFile(
            inputFs.getPath("/modules/one.js"), "class One {}", "module.exports = {One};"),
        createSourceFile(inputFs.getPath("/modules/two.js"), "var One = require('./one').One;"));

    NominalType one = typeRegistry.getType("module$exports$module$modules$one");
    setContext(one);
    assertThat("One").isAliasFor("module$contents$module$modules$one_One");

    NominalType two = typeRegistry.getType("module$exports$module$modules$two");
    setContext(two);
    assertThat("One").isAliasFor("module$exports$module$modules$one.One");
    assertThat("module$contents$module$modules$two_One")
        .isAliasFor("module$exports$module$modules$one.One");
  }

  private void setContext(NominalType type) {
    context = checkNotNull(type);
  }

  private AliasTester assertThat(String alias) {
    return new AliasTester(alias);
  }

  private class AliasTester {
    private final String alias;

    private AliasTester(String alias) {
      this.alias = alias;
    }

    public void isAliasFor(String type) {
      String resolvedType = typeRegistry.resolveAlias(context, alias);
      Truth.assertThat(resolvedType)
          .named("(context=%s, alias=%s)", context.getName(), alias)
          .isEqualTo(type);
    }

    public void cannotBeResolved() {
      String resolvedType = typeRegistry.resolveAlias(context, alias);
      Truth.assertThat(resolvedType)
          .named("(context=%s, alias=%s)", context.getName(), alias)
          .isNull();
    }
  }
}
