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

import com.github.jsdossier.proto.Comment;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Renders a single markdown file.
 */
@AutoFactory
final class RenderMarkdownTask implements Callable<Path> {

  private final DossierFileSystem dfs;
  private final CommentParser parser;
  private final LinkFactory linkFactory;
  private final HtmlRenderer htmlRenderer;
  private final MarkdownPage page;

  RenderMarkdownTask(
      @Provided DossierFileSystem dfs,
      @Provided CommentParser parser,
      @Provided LinkFactoryBuilder linkFactoryBuilder,
      @Provided HtmlRenderer htmlRenderer,
      MarkdownPage page) {
    this.dfs = dfs;
    this.htmlRenderer = htmlRenderer;
    this.linkFactory = linkFactoryBuilder.create(null);
    this.page = page;
    this.parser = parser;
  }

  @Override
  public Path call() throws IOException {
    String name = page.getName();
    Path output = dfs.getPath(name.replace(' ', '_') + ".html");
    String text = new String(Files.readAllBytes(page.getPath()), StandardCharsets.UTF_8);
    Comment content = parser.parseComment(text, linkFactory);
    htmlRenderer.renderHtml(output, name, content);
    return output;
  }
}
