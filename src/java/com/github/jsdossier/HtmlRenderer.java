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
import com.github.jsdossier.proto.PageData;
import com.github.jsdossier.soy.Renderer;

import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;

/**
 * Responsible for rendering a single HTML file.
 */
final class HtmlRenderer {

  private final DocTemplate template;
  private final DossierFileSystem dfs;
  private final NavIndexFactory navIndexFactory;
  private final Renderer renderer;

  @Inject
  HtmlRenderer(
      DocTemplate template,
      DossierFileSystem dfs,
      NavIndexFactory navIndexFactory,
      Renderer renderer) {
    this.template = template;
    this.dfs = dfs;
    this.navIndexFactory = navIndexFactory;
    this.renderer = renderer;
  }

  /**
   * @param output path to the file to render.
   * @param title the page title.
   * @param content the page content.
   * @throws IOException if an I/O error occurs.
   */
  public void renderHtml(Path output, String title, Comment content) throws IOException {
    PageData page = PageData.newBuilder()
        .setResources(dfs.getResources(output, template))
        .setIndex(navIndexFactory.create(output))
        .setMarkdown(
            PageData.Markdown.newBuilder()
                .setTitle(title)
                .setContent(content))
        .build();
    renderer.render(output, page);
  }
}
