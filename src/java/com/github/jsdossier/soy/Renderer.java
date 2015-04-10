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

package com.github.jsdossier.soy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.github.jsdossier.proto.HtmlRenderSpec;
import com.github.jsdossier.proto.JsTypeRenderSpec;
import com.github.jsdossier.proto.SourceFileRenderSpec;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.GeneratedMessage;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Renders soy templates.
 */
public class Renderer {

  private final SoyTofu tofu = SoyFileSet.builder()
      .add(Renderer.class.getResource("resources/dossier.soy"))
      .setLocalTypeRegistry(new SoyTypeRegistry(ImmutableSet.of(
          (SoyTypeProvider) new DossierSoyTypeProvider())))
      .build()
      .compileToTofu();

  public void render(Path output, HtmlRenderSpec spec) throws IOException {
    render(output, "dossier.soy.htmlFile", spec);
  }

  public void render(Path output, SourceFileRenderSpec spec) throws IOException {
    render(output, "dossier.soy.srcfile", spec);
  }

  public void render(Path output, JsTypeRenderSpec spec) throws IOException {
    render(output, "dossier.soy.typefile", spec);
  }

  private void render(Path output, String templateName, GeneratedMessage message)
      throws IOException {
    Files.createDirectories(output.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(output, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
      tofu.newRenderer(templateName)
          .setData(new SoyMapData("spec", ProtoMessageSoyType.toSoyValue(message)))
          .render(writer);
    }
  }

  @VisibleForTesting SoyTofu getTofu() {
    return tofu;
  }
}
