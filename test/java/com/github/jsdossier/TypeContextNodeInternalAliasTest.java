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
import com.github.jsdossier.jscomp.Module;
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

import javax.inject.Inject;

/**
 * Tests for resolving aliases for types defined within a Node module.
 */
@RunWith(JUnit4.class)
public final class TypeContextNodeInternalAliasTest {

  private static final String ALIAS = "module$contents$module$$src$modules$foo_Internal";

  private static TypeRegistry typeRegistry;
  private static TypeContext context;
  private static NominalType target;

  @BeforeClass
  public static void classSetup() {
    TestData data = GuiceRule.builder(new Object())
        .setOutputDir("/out")
        .setSourcePrefix("/src")
        .setModulePrefix("/src/modules")
        .setModules("foo.js", "bar.js", "baz.js")
        .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
        .build()
        .createInjector()
        .getInstance(TestData.class);

    data.util.compile(
        createSourceFile(
            data.fs.getPath("/src/modules/foo.js"),
            "class Internal {}",
            "class Other {}",
            "module.exports = {Other, External: Internal}"),
        createSourceFile(
            data.fs.getPath("/src/modules/bar.js"),
            "const Ext = require('./foo').External;",
            "exports.ForwardedExternal = Ext;",
            "exports.B = class extends Ext {}"),
        createSourceFile(
            data.fs.getPath("/src/modules/baz.js"),
            "exports.C = class {};"));

    typeRegistry = data.typeRegistry;
    context = data.context;
    target = typeRegistry.getType("module$exports$module$$src$modules$foo.External");
  }

  @Test
  public void canResolveAliasFromGlobalScope() {
    assertThat(context.clearContext().resolveType(ALIAS)).isEqualTo(target);
  }

  @Test
  public void canResolveAliasFromDefiningModule() {
    Module module = target.getModule().get();
    NominalType exports = typeRegistry.getType(module.getId());
    assertThat(context.changeContext(exports).resolveType(ALIAS)).isEqualTo(target);
  }

  @Test
  public void canResolveAliasFromExportedType() {
    assertThat(context.changeContext(target).resolveType(ALIAS)).isEqualTo(target);
  }

  @Test
  public void canResolveAliasFromAnotherTypeExportedByTheSameModule() {
    Module module = target.getModule().get();
    NominalType other = typeRegistry.getType(module.getId() + ".Other");
    assertThat(context.changeContext(other).resolveType(ALIAS)).isEqualTo(target);
  }

  @Test
  public void canResolveAliasFromAnotherModule() {
    assertThat(
        context
            .changeContext(typeRegistry.getType("module$exports$module$$src$modules$baz"))
            .resolveType(ALIAS))
        .isEqualTo(target);
  }

  @Test
  public void resolvesAliasesFromAnotherModule_globalScope() {
    String alias = "module$contents$module$$src$modules$bar_Ext";
    assertThat(context.clearContext().resolveType(alias)).isEqualTo(target);
  }

  @Test
  public void resolvessAliasesFromAnotherModule_fromAnotherModule() {
    String alias = "module$contents$module$$src$modules$bar_Ext";
    assertThat(
        context
            .changeContext(typeRegistry.getType("module$exports$module$$src$modules$baz"))
            .resolveType(alias))
        .isEqualTo(target);
  }

  private static final class TestData {

    @Inject @Input private FileSystem fs;
    @Inject private CompilerUtil util;
    @Inject private TypeRegistry typeRegistry;
    @Inject private TypeContext context;
  }
}
