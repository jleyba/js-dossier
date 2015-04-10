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

import static com.github.jsdossier.Paths.expandDir;
import static com.github.jsdossier.Paths.getCommonPrefix;
import static com.github.jsdossier.Paths.getRelativePath;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link Paths}.
 */
@RunWith(JUnit4.class)
public class PathsTest {

  private static final FileSystem FILE_SYSTEM = Jimfs.newFileSystem();

  @Test
  public void returnsRootIfNoPathsGiven() {
    Path root = FILE_SYSTEM.getPath("foo");
    assertSame(root, getCommonPrefix(root, ImmutableList.<Path>of()));
  }

  @Test
  public void returnsTheRootPathIfAllPathsAreUnderDifferentSubtrees() {
    Path a = FILE_SYSTEM.getPath("/a/path");
    Path b = FILE_SYSTEM.getPath("/b/happy");
    Path c = FILE_SYSTEM.getPath("/c/is/for/cookie");
    assertEquals(FILE_SYSTEM.getPath("/"), getCommonPrefix(a.getRoot(), newArrayList(a, b, c)));
  }

  @Test
  public void returnsTheLongestCommonPrefix() {
    Path root = FILE_SYSTEM.getPath("/one/two/three");
    Path a = FILE_SYSTEM.getPath("/one/two/three/here/we/go");
    Path b = FILE_SYSTEM.getPath("/one/two/three/here/we/go/again");
    Path c = FILE_SYSTEM.getPath("/one/two/three/here/we/go/again/down/the/rabbit/hole");
    Path d = FILE_SYSTEM.getPath("/one/two/three/a/b/c");

    assertEquals(a, getCommonPrefix(a.getRoot(), newArrayList(a, b, c)));
    assertEquals(root, getCommonPrefix(a.getRoot(), newArrayList(a, b, c, d)));
  }

  @Test
  public void returnsSingletonInput() {
    Path root = FILE_SYSTEM.getPath("/one/two/three");
    Path file = root.resolve("foo");

    List<Path> paths = newArrayList();
    paths.add(file);
    assertEquals(file, getCommonPrefix(root, paths));
  }

  @Test
  public void normalizesPathsBeforeComputingLongest() {
    Path root = FILE_SYSTEM.getPath("/root/and/then/some");

    assertEquals(
        root.resolve("..").normalize(),
        getCommonPrefix(root,
            newArrayList(
                FILE_SYSTEM.getPath("../up/a/level/foo/bar"),
                FILE_SYSTEM.getPath("../up/a/level"),
                FILE_SYSTEM.getPath("../up/a/"),
                FILE_SYSTEM.getPath("../../up/two/levels/../../../then/and/."),
                FILE_SYSTEM.getPath("../up/a/level/foo/crazy/town"))));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void recursivelyWalksTreeWhenExpandingADirectory() throws IOException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path testData = fileSystem.getPath("/src/testdata");

    createDirectories(testData);
    write(testData.resolve("externs.js"), new byte[0]);
    write(testData.resolve("one.js"), new byte[0]);
    write(testData.resolve("two.js"), new byte[0]);

    Path subDir = testData.resolve("subdir");
    createDirectories(subDir);
    write(subDir.resolve("apples.txt"), new byte[0]);
    write(subDir.resolve("three.js"), new byte[0]);

    Path deepDir = subDir.resolve("deep");
    createDirectories(deepDir);
    write(deepDir.resolve("hidden.js"), new byte[0]);

    List<Path> found = expandDir(testData, new JsFileFilter(newHashSet(
        "externs.js", "three.js")));

    assertEquals(
        ImmutableSet.of(
            testData.resolve("one.js"),
            testData.resolve("two.js"),
            testData.resolve("subdir/deep/hidden.js")),
        ImmutableSet.copyOf(found));
  }

  @Test
  public void computingRelativePaths() {
    Path a = FILE_SYSTEM.getPath("/foo/bar/bim/baz");
    Path b = FILE_SYSTEM.getPath("/foo/bar/one/two/three");
    Path c = FILE_SYSTEM.getPath("/foo/bar/one/apple");
    Path d = FILE_SYSTEM.getPath("/foo/bar/one/orange");
    Path e = FILE_SYSTEM.getPath("/foo/bar/one/color/red");

    assertEquals(
        FILE_SYSTEM.getPath("../one/two/three"),
        getRelativePath(a, b));
    assertEquals(
        FILE_SYSTEM.getPath("../one/apple"),
        getRelativePath(a, c));
    assertEquals(
        FILE_SYSTEM.getPath("../one/orange"),
        getRelativePath(a, d));
    assertEquals(
        FILE_SYSTEM.getPath("../one/color/red"),
        getRelativePath(a, e));

    assertEquals(
        FILE_SYSTEM.getPath("../../bim/baz"),
        getRelativePath(b, a));
    assertEquals(
        FILE_SYSTEM.getPath("../apple"),
        getRelativePath(b, c));
    assertEquals(
        FILE_SYSTEM.getPath("../orange"),
        getRelativePath(b, d));
    assertEquals(
        FILE_SYSTEM.getPath("../color/red"),
        getRelativePath(b, e));

    assertEquals(
        FILE_SYSTEM.getPath("../bim/baz"),
        getRelativePath(c, a));
    assertEquals(
        FILE_SYSTEM.getPath("two/three"),
        getRelativePath(c, b));
    assertEquals(
        FILE_SYSTEM.getPath("orange"),
        getRelativePath(c, d));
    assertEquals(
        FILE_SYSTEM.getPath("color/red"),
        getRelativePath(c, e));

    assertEquals(
        FILE_SYSTEM.getPath("../bim/baz"),
        getRelativePath(d, a));
    assertEquals(
        FILE_SYSTEM.getPath("two/three"),
        getRelativePath(d, b));
    assertEquals(
        FILE_SYSTEM.getPath("apple"),
        getRelativePath(d, c));
    assertEquals(
        FILE_SYSTEM.getPath("color/red"),
        getRelativePath(d, e));

    assertEquals(
        FILE_SYSTEM.getPath("../bim/baz"),
        getRelativePath(d, a));
    assertEquals(
        FILE_SYSTEM.getPath("two/three"),
        getRelativePath(d, b));
    assertEquals(
        FILE_SYSTEM.getPath("apple"),
        getRelativePath(d, c));
    assertEquals(
        FILE_SYSTEM.getPath("color/red"),
        getRelativePath(d, e));

    assertEquals(
        FILE_SYSTEM.getPath("../../bim/baz"),
        getRelativePath(e, a));
    assertEquals(
        FILE_SYSTEM.getPath("../two/three"),
        getRelativePath(e, b));
    assertEquals(
        FILE_SYSTEM.getPath("../apple"),
        getRelativePath(e, c));
    assertEquals(
        FILE_SYSTEM.getPath("../orange"),
        getRelativePath(e, d));
  }

  private static class JsFileFilter implements DirectoryStream.Filter<Path> {

    private final Set<String> excluded;

    private JsFileFilter(Set<String> excluded) {
      this.excluded = excluded;
    }

    @Override
    public boolean accept(Path entry) {
      return Files.isDirectory(entry)
          || (entry.toString().endsWith(".js")
          && !excluded.contains(entry.getFileName().toString()));
    }
  }
}
