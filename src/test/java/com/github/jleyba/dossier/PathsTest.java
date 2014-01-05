package com.github.jleyba.dossier;

import static com.github.jleyba.dossier.Paths.expandDir;
import static com.github.jleyba.dossier.Paths.getCommonPrefix;
import static com.github.jleyba.dossier.Paths.getRelativePath;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link Paths}.
 */
@RunWith(JUnit4.class)
public class PathsTest {

  private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();

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
    assertEquals(FILE_SYSTEM.getPath("/"), getCommonPrefix(newArrayList(a, b, c)));
  }

  @Test
  public void returnsTheLongestCommonPrefix() {
    Path root = FILE_SYSTEM.getPath("/one/two/three");
    Path a = FILE_SYSTEM.getPath("/one/two/three/here/we/go");
    Path b = FILE_SYSTEM.getPath("/one/two/three/here/we/go/again");
    Path c = FILE_SYSTEM.getPath("/one/two/three/here/we/go/again/down/the/rabbit/hole");
    Path d = FILE_SYSTEM.getPath("/one/two/three/a/b/c");

    assertEquals(a, getCommonPrefix(newArrayList(a, b, c)));
    assertEquals(root, getCommonPrefix(newArrayList(a, b, c, d)));
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
    Path testData = new File("src/test/java/com/github/jleyba/dossier/testdata").toPath();
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
