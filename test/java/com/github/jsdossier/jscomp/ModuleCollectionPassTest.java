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
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import java.nio.file.FileSystem;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModuleCollectionPassTest {

  @Rule public GuiceRule guice = GuiceRule.builder(this).setUseNodeLibrary(false).build();

  @Inject @Input private FileSystem inputFs;
  @Inject private TypeRegistry typeRegistry;
  @Inject private CompilerUtil util;

  @Test
  public void identifiesClosureModules() {
    util.compile(
        createSourceFile(
            inputFs.getPath("module/foo.js"), "goog.module('foo');", "exports.X = class {};"));
    Module module = getOnlyElement(typeRegistry.getAllModules());
    assertThat(module.getOriginalName()).isEqualTo("foo");
    assertThat(module.isClosure()).isTrue();
  }

  @Test
  public void identifiesEs6Modules() {
    util.compile(createSourceFile(inputFs.getPath("module/foo.js"), "export class X {}"));
    Module module = getOnlyElement(typeRegistry.getAllModules());
    assertThat(module.getOriginalName()).isEqualTo("module/foo.js");
    assertThat(module.isEs6()).isTrue();
  }

  @Test
  public void identifiesNodeModules() {
    defineInputModules("module", "foo.js");
    util.compile(createSourceFile(inputFs.getPath("module/foo.js"), "exports.X = class {};"));
    Module module = getOnlyElement(typeRegistry.getAllModules());
    assertThat(module.getOriginalName()).isEqualTo("module$module$foo");
    assertThat(module.isNode()).isTrue();
  }

  private void defineInputModules(String prefix, String... modules) {
    guice
        .toBuilder()
        .setModulePrefix(prefix)
        .setModules(modules)
        .build()
        .createInjector()
        .injectMembers(this);
  }
}
