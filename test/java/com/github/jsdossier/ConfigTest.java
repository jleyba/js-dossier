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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for {@link Config}.
 */
@RunWith(JUnit4.class)
public class ConfigTest {

  private final FileSystem fs = Jimfs.newFileSystem();

  @Test
  public void outputPathMustBeADirectoryOrZipFile_pathDoesNotExist() throws IOException {
    Path output = fs.getPath("/foo/bar/out");
    Path input = fs.getPath("/input.js");
    createFile(input);

    Config initial = Config.builder()
        .setFileSystem(fs)
        .setOutput(output)
        .setSources(ImmutableSet.of(input))
        .build();

    Config loaded = load(initial.toJson().toString());

    assertThat(loaded).isEqualTo(initial);
    assertThat(exists(output)).isFalse();
  }

  @Test
  public void outputPathMustBeADirectoryOrZipFile_pathIsADirectory() throws IOException {
    Path output = fs.getPath("/foo/bar/out");
    createDirectories(output);

    Path input = fs.getPath("/input.js");
    createFile(input);

    Config initial = Config.builder()
        .setFileSystem(fs)
        .setOutput(output)
        .setSources(ImmutableSet.of(input))
        .build();

    Config loaded = load(initial.toJson().toString());

    assertThat(loaded).isEqualTo(initial);
    assertThat(isDirectory(output)).isTrue();
  }

  @Test
  public void outputPathMustBeADirectoryOrZipFile_pathIsAZipFile() throws IOException {
    Path output = fs.getPath("/foo/bar/out.zip");
    createDirectories(output.getParent());
    createFile(output);

    Path input = fs.getPath("/input.js");
    createFile(input);

    Config initial = Config.builder()
        .setFileSystem(fs)
        .setOutput(output)
        .setSources(ImmutableSet.of(input))
        .build();

    Config loaded = load(initial.toJson().toString());

    assertThat(loaded).isEqualTo(initial);
    assertThat(isDirectory(output)).isFalse();
    assertThat(exists(output)).isTrue();
  }

  @Test
  public void outputPathMustBeADirectoryOrZipFile_pathIsARegularFile() throws IOException {
    Path output = fs.getPath("/foo/bar/out.html");
    createDirectories(output.getParent());
    createFile(output);

    Path input = fs.getPath("/input.js");
    createFile(input);

    try {
      load("{\"output\":\"" + output + "\", \"sources\":[\"" + input + "\"]}");
      fail();
    } catch (InvalidConfigurationException expected) {
      // Do nothing.
    }
  }

  @Test
  public void checksAtLeastOneSourceOrModuleIsSpecified() throws IOException {
    Config.Builder builder = Config.builder()
        .setOutput(fs.getPath("out"))
        .setFileSystem(fs);

    try {
      builder.build();
      fail();
    } catch (InvalidConfigurationException expected) {
      assertThat(expected).hasMessage("There must be at least one input module or source file");
    }

    createFile(fs.getPath("in.js"));

    ImmutableSet<Path> files = ImmutableSet.of(fs.getPath("in.js"));
    Config config = builder.setSources(files).build();
    assertThat(config.getSources()).containsExactlyElementsIn(files);
    assertThat(config.getModules()).isEmpty();

    config = builder
        .setSources(ImmutableSet.<Path>of())
        .setModules(files)
        .build();
    assertThat(config.getSources()).isEmpty();
    assertThat(config.getModules()).containsExactlyElementsIn(files);
  }

  @Test
  public void checksEveryInputSourceExists() throws IOException {
    Config.Builder builder = Config.builder()
        .setFileSystem(fs)
        .setOutput(fs.getPath("out"))
        .setSources(
            ImmutableSet.of(
                fs.getPath("a.js"),
                fs.getPath("b.js"),
                fs.getPath("c.js")));

    createFile(fs.getPath("a.js"));
    createFile(fs.getPath("b.js"));

    try {
      builder.build();
      fail();
    } catch (InvalidConfigurationException expected) {
      assertThat(expected).hasMessage("Input file does not exist: c.js");
    }
  }

  @Test
  public void checksEveryInputModuleExists() throws IOException {
    Config.Builder builder = Config.builder()
        .setFileSystem(fs)
        .setOutput(fs.getPath("out"))
        .setModules(
            ImmutableSet.of(
                fs.getPath("a.js"),
                fs.getPath("b.js"),
                fs.getPath("c.js")));

    createFile(fs.getPath("a.js"));
    createFile(fs.getPath("b.js"));

    try {
      builder.build();
      fail();
    } catch (InvalidConfigurationException expected) {
      assertThat(expected).hasMessage("Input file does not exist: c.js");
    }
  }

  @Test
  public void checksEveryInputExternExists() throws IOException {
    Config.Builder builder = Config.builder()
        .setFileSystem(fs)
        .setOutput(fs.getPath("out"))
        .setSources(ImmutableSet.of(fs.getPath("a.js")))
        .setExterns(
            ImmutableSet.of(
                fs.getPath("b.js"),
                fs.getPath("c.js")));

    createFile(fs.getPath("a.js"));
    createFile(fs.getPath("b.js"));

    try {
      builder.build();
      fail();
    } catch (InvalidConfigurationException expected) {
      assertThat(expected).hasMessage("Input file does not exist: c.js");
    }
  }

  @Test
  public void checksEveryInputExternModuleExists() throws IOException {
    Config.Builder builder = Config.builder()
        .setFileSystem(fs)
        .setOutput(fs.getPath("out"))
        .setSources(ImmutableSet.of(fs.getPath("a.js")))
        .setExternModules(
            ImmutableSet.of(
                fs.getPath("b.js"),
                fs.getPath("c.js")));

    createFile(fs.getPath("a.js"));
    createFile(fs.getPath("b.js"));

    try {
      builder.build();
      fail();
    } catch (InvalidConfigurationException expected) {
      assertThat(expected).hasMessage("Input file does not exist: c.js");
    }
  }

  @Test
  public void checksReadmeFileExists() throws IOException {
    Path input = fs.getPath("/input.js");
    createFile(input);

    Config.Builder builder = Config.builder()
        .setFileSystem(fs)
        .setSources(ImmutableSet.of(input))
        .setOutput(fs.getPath("/output"))
        .setReadme(fs.getPath("not-there.md"));

    try {
      builder.build();
      fail();
    } catch (InvalidConfigurationException expected) {
      assertThat(expected).hasMessage("Input file does not exist: not-there.md");
    }

    createFile(fs.getPath("not-there.md"));

    Config config = builder.build();
    assertThat(config.getReadme()).isPresent();
    assertThat(config.getReadme()).hasValue(fs.getPath("not-there.md"));
  }

  @Test
  public void loadIgnoresNullFilePaths() throws IOException {
    Path rootDir = fs.getPath("/root");
    createDirectories(rootDir);
    createFile(rootDir.resolve("foo.js"));

    String content = String.format(
        "{\"output\":\"%s\", \"sources\":[\"%s\",]}",  // Want a trailing comma inside sources.
        rootDir.resolveSibling("out"),
        rootDir.resolve("**.js"));

    Config config = load(content);
    Config expected = Config.builder()
        .setFileSystem(fs)
        .setOutput(rootDir.resolveSibling("out"))
        .setSources(ImmutableSet.of(rootDir.resolve("foo.js")))
        .build();
    assertThat(config).isEqualTo(expected);
  }

  @Test
  public void pathSpecResolvesToExpectedSetOfFiles() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    Path baseDir = fs.getPath("/root");
    Path foo = baseDir.resolve("foo.js");
    Path bar = baseDir.resolve("a/bar.js");
    Path baz = baseDir.resolve("a/b/baz.js");
    Path quux = baseDir.resolve("a/b/c/quux.js");
    Path quot = baseDir.resolve("a/b/c/quot.js");

    Path otherDir = fs.getPath("/other");
    Path otherFooTest = otherDir.resolve("a/b/foo_test.js");
    Path otherBarTest = otherDir.resolve("a/bar_test.js");

    Files.createDirectories(quux.getParent());
    Files.createDirectories(otherFooTest.getParent());
    Files.createFile(foo);
    Files.createFile(bar);
    Files.createFile(baz);
    Files.createFile(quux);
    Files.createFile(quot);
    Files.createFile(otherFooTest);
    Files.createFile(otherBarTest);

    assertContentsAnyOrder(getPaths(baseDir, ""), foo, bar, baz, quux, quot);
    assertContentsAnyOrder(getPaths(baseDir, "foo.js"), foo);
    assertContentsAnyOrder(getPaths(baseDir, "a"), bar, baz, quux, quot);
    assertContentsAnyOrder(getPaths(baseDir, "a/b"), baz, quux, quot);
    assertContentsAnyOrder(getPaths(baseDir, "a/b/c"), quux, quot);

    assertContentsAnyOrder(getPaths(baseDir, "*.js"), foo);
    assertContentsAnyOrder(getPaths(baseDir, "a/b/*.js"), baz);
    assertContentsAnyOrder(getPaths(baseDir, "a/b/c/*.js"), quux, quot);
    assertContentsAnyOrder(getPaths(baseDir, "**.js"), foo, bar, baz, quux, quot);
    assertContentsAnyOrder(getPaths(baseDir, "a/**.js"), bar, baz, quux, quot);

    assertContentsAnyOrder(getPaths(baseDir, otherDir + "/a/*.js"), otherBarTest);
    assertContentsAnyOrder(getPaths(baseDir, otherDir + "/a/**_test.js"),
        otherBarTest, otherFooTest);
  }

  @Test
  public void expandsPathForSources() throws IOException {
    Path rootDir = fs.getPath("/root");
    createDirectories(rootDir);
    createFile(rootDir.resolve("foo.js"));
    createFile(rootDir.resolve("bar.js"));
    createFile(rootDir.resolve("baz.js"));
    createDirectories(rootDir.resolve("nested"));
    createFile(rootDir.resolve("nested/bunnies.js"));

    String content = String.format(
        "{\"output\":\"%s\", \"sources\":[\"%s\"]}",
        rootDir.resolveSibling("out"),
        rootDir.resolve("**.js"));

    Config config = load(content);
    Config expected = Config.builder()
        .setFileSystem(fs)
        .setOutput(rootDir.resolveSibling("out"))
        .setSources(ImmutableSet.of(
            rootDir.resolve("foo.js"),
            rootDir.resolve("bar.js"),
            rootDir.resolve("baz.js"),
            rootDir.resolve("nested/bunnies.js")))
        .build();
    assertThat(config).isEqualTo(expected);
    assertThat(config.getSourcePrefix()).hasValue(rootDir);
  }

  @Test
  public void expandsPathForModules() throws IOException {
    Path rootDir = fs.getPath("/root");
    createDirectories(rootDir);
    createFile(rootDir.resolve("foo.js"));
    createFile(rootDir.resolve("bar.js"));
    createFile(rootDir.resolve("baz.js"));
    createDirectories(rootDir.resolve("nested"));
    createFile(rootDir.resolve("nested/bunnies.js"));

    String content = String.format(
        "{\"output\":\"%s\", \"modules\":[\"%s\"]}",
        rootDir.resolveSibling("out"),
        rootDir.resolve("**.js"));

    Config config = load(content);
    Config expected = Config.builder()
        .setFileSystem(fs)
        .setOutput(rootDir.resolveSibling("out"))
        .setModules(ImmutableSet.of(
            rootDir.resolve("foo.js"),
            rootDir.resolve("bar.js"),
            rootDir.resolve("baz.js"),
            rootDir.resolve("nested/bunnies.js")))
        .build();
    assertThat(config).isEqualTo(expected);
  }

  @Test
  public void expandsPathForExterns() throws IOException {
    Path rootDir = fs.getPath("/root");
    createDirectories(rootDir.resolve("sources"));
    createFile(rootDir.resolve("sources/one.js"));
    createDirectories(rootDir.resolve("externs/nested"));
    createFile(rootDir.resolve("externs/foo.js"));
    createFile(rootDir.resolve("externs/bar.js"));
    createFile(rootDir.resolve("externs/baz.js"));
    createFile(rootDir.resolve("externs/nested/bunnies.js"));

    String content = String.format(
        "{\"output\":\"%s\", \"sources\":[\"%s\"], \"externs\":[\"%s\"]}",
        rootDir.resolveSibling("out"),
        rootDir.resolve("sources/one.js"),
        rootDir.resolve("externs/**.js"));

    Config config = load(content);
    Config expected = Config.builder()
        .setFileSystem(fs)
        .setOutput(rootDir.resolveSibling("out"))
        .setSources(ImmutableSet.of(rootDir.resolve("sources/one.js")))
        .setExterns(ImmutableSet.of(
            rootDir.resolve("externs/foo.js"),
            rootDir.resolve("externs/bar.js"),
            rootDir.resolve("externs/baz.js"),
            rootDir.resolve("externs/nested/bunnies.js")))
        .build();
    assertThat(config).isEqualTo(expected);
  }

  @Test
  public void updatesSourcesWithClosureLibraryDeps() throws IOException {
    Path closure = fs.getPath("/tp/closure/goog");
    createDirectories(closure.resolve("array"));
    createDirectories(closure.resolve("dom"));
    createFile(closure.resolve("base.js"));
    createFile(closure.resolve("deps.js"));
    createFile(closure.resolve("array/array.js"));
    createFile(closure.resolve("dom/dom.js"));

    Path source = fs.getPath("/src/one.js");
    createDirectories(source.getParent());
    write(source, "goog.provide('one'); goog.require('goog.dom');".getBytes(UTF_8));

    Path closureDeps = closure.resolve("deps.js");
    write(closureDeps,
        ("goog.addDependency('array/array.js', ['goog.array'], [], false);\n" +
            "goog.addDependency('dom/dom.js', ['goog.dom'], ['goog.array'], false);\n")
            .getBytes(UTF_8));

    JsonObject json = new JsonObject();
    json.addProperty("output", fs.getPath("/out").toString());
    json.add("sources", jsonArray(source));
    json.addProperty("closureLibraryDir", closure.toString());

    Config config = load(json.toString());
    Config expected = Config.builder()
        .setFileSystem(fs)
        .setOutput(fs.getPath("/out"))
        .setSources(ImmutableSet.of(
            closure.resolve("base.js"),
            closure.resolve("array/array.js"),
            closure.resolve("dom/dom.js"),
            source))
        .setClosureLibraryDir(Optional.of(closure))
        .build();
    assertThat(config).isEqualTo(expected);
  }

  private JsonArray jsonArray(Object... items) {
    JsonArray array = new JsonArray();
    for (Object item : items) {
      array.add(new JsonPrimitive(item.toString()));
    }
    return array;
  }

  private Config load(String string) {
    ByteArrayInputStream input = new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    return Config.fromJson(input, fs);
  }

  @SafeVarargs
  private static <T> void assertContentsAnyOrder(Iterable<T> iterable, T... expected) {
    assertEquals(ImmutableSet.copyOf(expected), ImmutableSet.copyOf(iterable));
  }

  private static List<Path> getPaths(Path baseDir, String spec) throws IOException {
    return new Config.PathSpec(baseDir, spec).resolve();
  }
}
