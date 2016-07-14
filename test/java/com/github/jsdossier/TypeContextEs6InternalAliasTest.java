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

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.jscomp.CompilerOptions;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Tests for resolving aliases for types defined within an ES6 module.
 */
@RunWith(JUnit4.class)
public final class TypeContextEs6InternalAliasTest {

  private static TypeRegistry typeRegistry;
  private static TypeContext context;
  private static NominalType target;

  @BeforeClass
  public static void classSetup() {
    TestData data = GuiceRule.builder(new Object())
        .setOutputDir("out")
        .setSourcePrefix("src")
        .setModulePrefix("src/modules")
        .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
        .build()
        .createInjector()
        .getInstance(TestData.class);

    data.util.compile(
        createSourceFile(
            data.fs.getPath("src/modules/foo.js"),
            "class Internal {}",
            "export class Other {}",
            "export {Internal as External}"),
        createSourceFile(
            data.fs.getPath("src/modules/bar.js"),
            "import {External as BB} from './foo';",
            "export {BB as ForwardExternal}",
            "export class B extends BB {}"),
        createSourceFile(
            data.fs.getPath("src/modules/baz.js"),
            "export class C {}"));

    typeRegistry = data.typeRegistry;
    context = data.context;
    target = typeRegistry.getType("module$src$modules$foo.External");
  }

  private static NominalType resolveAlias(@Nullable NominalType contextType) {
    String alias = "Internal$$module$src$modules$foo";
    if (contextType == null) {
      return context.clearContext().resolveType(alias);
    } else {
      return context.changeContext(contextType).resolveType(alias);
    }
  }

  @Test
  public void canResolveAliasFromGlobalScope() {
    assertThat(resolveAlias(null)).isEqualTo(target);
  }

  @Test
  public void canResolveAliasFromDefiningModule() {
    assertThat(resolveAlias(typeRegistry.getType("module$src$modules$foo"))).isEqualTo(target);
  }

  @Test
  public void canResolveAliasFromExportedType() {
    assertThat(resolveAlias(target)).isEqualTo(target);
  }

  @Test
  public void canResolveAliasFromAnotherTypeExportedByTheSameModule() {
    assertThat(resolveAlias(typeRegistry.getType("module$src$modules$foo.Other")))
        .isEqualTo(target);
  }

  @Test
  public void canResolveAliasFromAnotherModule() {
    assertThat(resolveAlias(typeRegistry.getType("module$src$modules$baz"))).isEqualTo(target);
  }

  private static final class TestData {

    @Inject @Input private FileSystem fs;
    @Inject private CompilerUtil util;
    @Inject private TypeRegistry typeRegistry;
    @Inject private TypeContext context;
  }
}
