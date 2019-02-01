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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.proto.Index;
import com.github.jsdossier.soy.JsonRenderer;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

/** Task for rendering the main type index. */
@DocumentationScoped
final class RenderTypeIndexTask implements RenderTask {

  private final DossierFileSystem dfs;
  private final JsonRenderer jsonRenderer;
  private final IndexBuilder index;

  @Inject
  RenderTypeIndexTask(DossierFileSystem dfs, JsonRenderer jsonRenderer, IndexBuilder index) {
    this.dfs = dfs;
    this.jsonRenderer = jsonRenderer;
    this.index = index;
  }

  @Override
  public Path call() throws IOException {
    Index message = index.toNormalizedProto();

    StringWriter sw = new StringWriter();
    sw.append("var TYPES = ");
    jsonRenderer.render(sw, message);
    sw.append(";");

    Path path = dfs.getPath("types.js");
    Files.write(path, sw.toString().getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING);
    return path;
  }
}
