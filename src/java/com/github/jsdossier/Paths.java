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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Iterables.transform;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Utilities for working with {@link Path paths}.
 */
class Paths {
  private Paths() {}

  /**
   * Computes the relative path {@code from} one path {@code to} another. The
   * origin path is assumed to be a file.
   */
  static Path getRelativePath(Path from, Path to) {
    from = from.toAbsolutePath().getParent();
    to = to.toAbsolutePath();

    Path root = getCommonPrefix(from.getRoot(), ImmutableSet.of(from, to));
    Path pathToRoot = from.relativize(root);
    Path pathFromRoot = root.relativize(to);

    return pathToRoot.resolve(pathFromRoot).normalize();
  }

  /**
   * Returns the {@link Path} that represents the longest common prefix for the provided
   * {@code paths}. All paths will be resolved and normalized relative to the given {@code root}
   * directory before computing a common prefix.
   *
   * <p>If all of the provided {@code paths} do not designate
   */
  static Path getCommonPrefix(Path root, Iterable<Path> paths) {
    if (isEmpty(paths)) {
      return root;
    }
    root = root.toAbsolutePath();
    paths = transform(paths, normalizeRelativeTo(root));

    Path prefix = root.getRoot();
    Path shortest = Ordering.from(length()).min(paths);
    for (Path part : shortest) {
      Path possiblePrefix = prefix.resolve(part);
      if (all(paths, startsWith(possiblePrefix))) {
        prefix = possiblePrefix;
      } else {
        break;
      }
    }
    return prefix;
  }

  /**
   * Expands the given directory path, collecting all of its descendant files that are accepted
   * by the filter.
   *
   * @param dir The directory to expand.
   * @param filter The filter to apply to the directory entries.
   * @return All of the files located under the directory accepted by the filter.
   * @throws IOException If an I/O error occurs.
   */
  static List<Path> expandDir(Path dir, DirectoryStream.Filter<Path> filter) throws IOException {
    checkArgument(Files.isDirectory(dir), "%s is not a directory", dir);
    List<Path> paths = new LinkedList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
      for (Path path : stream) {
        if (Files.isDirectory(path)) {
          paths.addAll(expandDir(path, filter));
        } else {
          paths.add(path);
        }
      }
    }
    return paths;
  }

  private static Predicate<Path> startsWith(final Path root) {
    return new Predicate<Path>() {
      @Override
      public boolean apply(Path input) {
        return input.startsWith(root);
      }
    };
  }

  private static Function<Path, Path> normalizeRelativeTo(final Path root) {
    return new Function<Path, Path>() {
      @Override
      public Path apply(Path input) {
        return root.resolve(input).normalize();
      }
    };
  }

  private static Comparator<Path> length() {
    return new Comparator<Path>() {
      @Override
      public int compare(Path o1, Path o2) {
        return Integer.compare(o1.getNameCount(), o2.getNameCount());
      }
    };
  }
}
