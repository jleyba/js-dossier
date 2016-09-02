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

import com.github.jsdossier.proto.PageData;
import com.github.jsdossier.soy.JsonRenderer;
import com.github.jsdossier.soy.Renderer;

import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;

final class PageRenderer {

  private final DossierFileSystem dfs;
  private final DocTemplate template;
  private final Renderer renderer;
  private final JsonRenderer jsonRenderer;

  @Inject
  PageRenderer(
      DossierFileSystem dfs,
      DocTemplate template,
      Renderer renderer,
      JsonRenderer jsonRenderer) {
    this.dfs = dfs;
    this.template = template;
    this.renderer = renderer;
    this.jsonRenderer = jsonRenderer;
  }

  void renderHtml(Path output, PageData data) throws IOException {
    data = data.toBuilder().setResources(dfs.getResources(output, template)).build();
    renderer.render(output, data);
  }

  void renderJson(Path output, PageData data) throws IOException {
    jsonRenderer.render(output, data);
  }
}
