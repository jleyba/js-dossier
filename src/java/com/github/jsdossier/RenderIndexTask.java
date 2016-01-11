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

import com.github.jsdossier.annotations.Readme;
import com.github.jsdossier.proto.Comment;
import com.google.common.base.Optional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javax.inject.Inject;

/**
 * Renders the main index file.
 */
final class RenderIndexTask implements Callable<Path> {

  private final DossierFileSystem dfs;
  private final CommentParser parser;
  private final LinkFactory linkFactory;
  private final HtmlRenderer htmlRenderer;
  private final Optional<Path> readmeFile;

  @Inject
  RenderIndexTask(
      DossierFileSystem dfs,
      CommentParser parser,
      LinkFactoryBuilder linkFactoryBuilder,
      HtmlRenderer htmlRenderer,
      @Readme Optional<Path> readmeFile) {
    this.dfs = dfs;
    this.parser = parser;
    this.linkFactory = linkFactoryBuilder.create(null);
    this.htmlRenderer = htmlRenderer;
    this.readmeFile = readmeFile;
  }

  @Override
  public Path call() throws IOException {
    // TODO: render an index of all the types.
    Comment content = Comment.getDefaultInstance();

    if (readmeFile.isPresent()) {
      String readme = new String(
          Files.readAllBytes(readmeFile.get()),
          StandardCharsets.UTF_8);
      content = parser.parseComment(readme, linkFactory);
    }

    Path output = dfs.getPath("index.html");
    htmlRenderer.renderHtml(output, "Index", content);
    return output;
  }
}
