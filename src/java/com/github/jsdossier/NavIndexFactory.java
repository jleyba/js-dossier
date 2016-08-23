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

import static com.github.jsdossier.Paths.toUrlPath;
import static com.google.common.base.Preconditions.checkArgument;

import com.github.jsdossier.proto.Index;
import com.github.jsdossier.proto.Link;
import com.google.common.collect.ImmutableSet;
import org.joda.time.Instant;

import java.nio.file.Path;

/**
 * Generates a descriptor for the data that should be included in the navigation index for
 * generated documentation.
 */
final class NavIndexFactory {

  private final Path root;
  private final Index mainIndex;

  /**
   * @param root path to the main output directory.
   * @param mainIndex the main index, used as a template for all generated indices.
   */
  private NavIndexFactory(Path root, Index mainIndex) {
    this.root = root;
    this.mainIndex = mainIndex;
  }

  /**
   * Creates a new navigation index.
   *
   * @param homePage path to the main home page.
   * @param includeModules whether to include a "modules" sub-menu.
   * @param includeTypes whether to include a "types" sub-menu.
   * @param markdownPages list of custom markdown pages to include in the index.
   * @return A new {@link NavIndexFactory} object.
   */
  public static NavIndexFactory create(
      Path homePage,
      boolean includeModules,
      boolean includeTypes,
      ImmutableSet<MarkdownPage> markdownPages) {
    Index.Builder builder = Index.newBuilder()
        .setHome(homePage.getFileName().toString())
        .setIncludeModules(includeModules)
        .setIncludeTypes(includeTypes)
        .setTimestamp(Instant.now().getMillis());

    for (MarkdownPage page : markdownPages) {
      String fileName = page.getName().replace(' ', '_') + ".html";
      checkArgument(!fileName.equals(homePage.getFileName().toString()),
          "Custom markdown page (%s) may not have the same name as the home page: %s",
          page.getPath(), homePage.getFileName());

      builder.addLinkBuilder()
          .setHref(fileName)
          .setText(page.getName());
    }

    return new NavIndexFactory(homePage.getParent(), builder.build());
  }

  /**
   * Generates a nav index for a file with the given {@code path}.
   */
  public Index create(Path path) {
    if ((path.getParent() == null && root.equals(path))
        || (root.equals(path.getParent()))) {
      return mainIndex;
    }
    Index.Builder builder = Index.newBuilder().mergeFrom(mainIndex);

    Path toRoot = path.getParent().relativize(root);
    if (toRoot.getNameCount() == 0) {
      toRoot = root;
    }

    builder.setHome(toUrlPath(toRoot.resolve(mainIndex.getHome())));

    for (Link.Builder link : builder.getLinkBuilderList()) {
      link.setHref(toUrlPath(toRoot.resolve(link.getHref())));
    }

    return builder.build();
  }
}
