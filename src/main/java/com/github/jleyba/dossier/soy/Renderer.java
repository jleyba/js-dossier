package com.github.jleyba.dossier.soy;

import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
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

  public void render(Path output, Dossier.IndexFileRenderSpec spec) throws IOException {
    render(output, "dossier.soy.indexFile", spec);
  }

  public void render(Path output, Dossier.SourceFileRenderSpec spec) throws IOException {
    render(output, "dossier.soy.srcfile", spec);
  }

  public void render(Path output, Dossier.JsTypeRenderSpec spec) throws IOException {
    render(output, "dossier.soy.typefile", spec);
  }

  private void render(Path output, String templateName, GeneratedMessage message)
      throws IOException {
    Files.createDirectories(output.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(output, Charsets.UTF_8)) {
      tofu.newRenderer(templateName)
          .setData(new SoyMapData("spec", ProtoMessageSoyType.toSoyValue(message)))
          .render(writer);
    }
  }

  @VisibleForTesting SoyTofu getTofu() {
    return tofu;
  }
}
