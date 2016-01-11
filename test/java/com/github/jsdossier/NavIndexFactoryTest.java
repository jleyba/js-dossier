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
import static org.junit.Assert.fail;

import com.github.jsdossier.proto.Index;
import com.github.jsdossier.proto.TypeLink;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;
import java.nio.file.Path;

/**
 * Tests for {@link NavIndexFactory}.
 */
@RunWith(JUnit4.class)
public class NavIndexFactoryTest {

  private final FileSystem fs = Jimfs.newFileSystem();
  private final Path root = fs.getPath("/out");
  private final Path home = root.resolve("index.html");
  private final ImmutableList<MarkdownPage> pages = ImmutableList.of(
      new MarkdownPage("foo", fs.getPath("/pages/foo.md")),
      new MarkdownPage("bar", fs.getPath("/pages/bar.md")));
  private final NavIndexFactory factory = NavIndexFactory.create(home, true, true, pages);

  @Test
  public void markdownPagesMayNotHaveTheSameNameAsThePrimaryIndex() {
    try {
      NavIndexFactory.create(
          root.resolve("index.html"),
          true, true,
          ImmutableList.of(new MarkdownPage("index", root.resolve("index.md"))));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void generateIndexForMainIndex() {
    Index index = factory.create(home);
    assertThat(index).isEqualTo(
        Index.newBuilder()
            .setHome("index.html")
            .setIncludeModules(true)
            .setIncludeTypes(true)
            .addLinks(TypeLink.newBuilder()
                .setText("foo")
                .setHref("foo.html"))
            .addLinks(TypeLink.newBuilder()
                .setText("bar")
                .setHref("bar.html"))
            .build());
  }

  @Test
  public void generateIndexForIndexSibling() {
    assertThat(factory.create(home.resolveSibling("other.html")))
        .isEqualTo(factory.create(home));
  }

  @Test
  public void generateIndexForDescendantOfRootDir() {
    Index index = factory.create(root.resolve("a/b/c.html"));
    assertThat(index).isEqualTo(
        Index.newBuilder()
            .setHome("../../index.html")
            .setIncludeModules(true)
            .setIncludeTypes(true)
            .addLinks(TypeLink.newBuilder()
                .setText("foo")
                .setHref("../../foo.html"))
            .addLinks(TypeLink.newBuilder()
                .setText("bar")
                .setHref("../../bar.html"))
            .build());
  }

  @Test
  public void normalizesPathUrlsOnWindows() {
    FileSystem fs = Jimfs.newFileSystem(Configuration.windows());
    Path root = fs.getPath("out");
    NavIndexFactory factory = NavIndexFactory.create(
        root.resolve("index.html"),
        true, true,
        ImmutableList.of(
            new MarkdownPage("foo", fs.getPath("pages/foo.md")),
            new MarkdownPage("bar", fs.getPath("pages/bar.md"))));

    assertThat(factory.create(root.resolve("a/b/c.html"))).isEqualTo(
        Index.newBuilder()
            .setHome("../../index.html")
            .setIncludeModules(true)
            .setIncludeTypes(true)
            .addLinks(TypeLink.newBuilder()
                .setText("foo")
                .setHref("../../foo.html"))
            .addLinks(TypeLink.newBuilder()
                .setText("bar")
                .setHref("../../bar.html"))
            .build());
  }
}
