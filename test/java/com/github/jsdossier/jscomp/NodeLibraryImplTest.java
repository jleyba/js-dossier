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

import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.annotations.ModuleExterns;
import com.github.jsdossier.annotations.Modules;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NodeLibraryImplTest {

  private final FileSystem fs = Jimfs.newFileSystem();

  @Test
  public void libraryIsEmptyIfThereAreNoModules() throws IOException {
    NodeLibrary library = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
      }

      @Provides
      @ModuleExterns
      ImmutableSet<Path> provideExterns() {
        return ImmutableSet.of();
      }

      @Provides
      @Modules
      ImmutableSet<Path> provideModules() {
        return ImmutableSet.of();
      }
    }).getInstance(NodeLibraryImpl.class);

    assertThat(library.getExternFiles()).isEmpty();
    assertThat(library.getExternModules()).isEmpty();
  }

  @Test
  public void libraryLoadsStandardExterns() throws IOException {
    NodeLibrary library = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
      }

      @Provides
      @ModuleExterns
      ImmutableSet<Path> provideExterns() {
        return ImmutableSet.of();
      }

      @Provides
      @Modules
      ImmutableSet<Path> provideModules() {
        return ImmutableSet.of(fs.getPath("some-module"));
      }
    }).getInstance(NodeLibraryImpl.class);

    assertThat(library.getExternFiles()).hasSize(1);
    assertThat(library.getExternFiles().get(0).getName())
        .isEqualTo("dossier//node-externs.zip//globals.js");
    
    // Just a sampling.
    assertThat(library.canRequireId("child_process")).isTrue();
    assertThat(library.canRequireId("events")).isTrue();
    assertThat(library.canRequireId("http")).isTrue();
    assertThat(library.canRequireId("path")).isTrue();
    assertThat(library.canRequireId("stream")).isTrue();
  }
  
  @Test
  public void libraryWithCustomExterns() throws IOException {
    final ImmutableSet<Path> externs = ImmutableSet.of(fs.getPath("foo-bar"), fs.getPath("baz"));
    for (Path path : externs) {
      Files.createFile(path);
    }
    
    NodeLibrary library = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
      }

      @Provides
      @ModuleExterns
      ImmutableSet<Path> provideExterns() {
        return externs;
      }

      @Provides
      @Modules
      ImmutableSet<Path> provideModules() {
        return ImmutableSet.of(fs.getPath("some-module"));
      }
    }).getInstance(NodeLibraryImpl.class);

    assertThat(library.canRequireId("baz")).isTrue();
    assertThat(library.canRequireId("foo-bar")).isTrue();
    assertThat(library.normalizeRequireId("foo-bar")).isEqualTo("foo_bar");
    assertThat(library.canRequireId("foo_bar")).isFalse();
  }
}
