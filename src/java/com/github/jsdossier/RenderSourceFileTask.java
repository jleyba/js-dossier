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

import static java.nio.file.Files.readAllLines;

import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.proto.PageData;
import com.github.jsdossier.proto.SourceFile;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders a single source file in the output tree.
 */
@AutoFactory
final class RenderSourceFileTask implements RenderTask {

  private static final String MODULE_SLASH = "/";

  private final DossierFileSystem dfs;
  private final IndexBuilder index;
  private final PageRenderer renderer;
  private final Path prefix;
  private final Path path;

  RenderSourceFileTask(
      @Provided DossierFileSystem dfs,
      @Provided IndexBuilder index,
      @Provided PageRenderer renderer,
      @Provided @SourcePrefix Path prefix,
      Path path) {
    this.dfs = dfs;
    this.index = index;
    this.renderer = renderer;
    this.prefix = prefix;
    this.path = path;
  }

  @Override
  public List<Path> call() throws IOException {
    String displayPath = prefix.relativize(path)
        .toString()
        .replace(path.getFileSystem().getSeparator(), MODULE_SLASH);

    SourceFile file = SourceFile.newBuilder()
        .setBaseName(path.getFileName().toString())
        .setPath(displayPath)
        .addAllLines(readAllLines(path, Charsets.UTF_8))
        .build();

    PageData page = PageData.newBuilder()
        .setFile(file)
        .build();

    Path htmlPath = dfs.getPath(path);
    Path jsonPath = dfs.getJsonPath(htmlPath);
    renderer.render(htmlPath, jsonPath, page);

    index.addSourceFile(htmlPath, jsonPath);

    return ImmutableList.of(htmlPath, jsonPath);
  }
}
