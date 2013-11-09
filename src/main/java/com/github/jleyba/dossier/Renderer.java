package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkArgument;

import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.tofu.SoyTofu;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Renders soy templates.
 */
class Renderer {

  void render(Path output, Dossier.IndexFileRenderSpec spec) throws IOException {
    render(output, "dossier.soy.indexFile", spec);
  }

  void render(Path output, Dossier.LicenseRenderSpec spec) throws IOException {
    render(output, "dossier.soy.licenseFile", spec);
  }

  void render(Path output, Dossier.SourceFileRenderSpec spec) throws IOException {
    render(output, "dossier.soy.srcfile", spec);
  }

  void render(Path output, Dossier.JsTypeRenderSpec spec) throws IOException {
    render(output, "dossier.soy.typefile", spec);
  }

  private void render(Path output, String templateName, GeneratedMessage message)
      throws IOException {
    Files.createDirectories(output.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(output, Charsets.UTF_8)) {
      Tofu.INSTANCE.newRenderer(templateName)
          .setData(new SoyMapData("spec", toMap(message)))
          .render(writer);
    }
  }

  @VisibleForTesting
  static Map<String, Object> toMap(GeneratedMessage message) {
    Map<String, Object> data = Maps.newHashMapWithExpectedSize(
        message.getDescriptorForType().getFields().size());
    Descriptors.Descriptor descriptor =  message.getDescriptorForType();
    for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
      data.put(
          CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, field.getName()),
          toSoyData(field, message));
    }
    return data;
  }

  private static Object toSoyData(Descriptors.FieldDescriptor field, GeneratedMessage message) {
    checkArgument(field.getJavaType() != Descriptors.FieldDescriptor.JavaType.BYTE_STRING,
        "byte strings not supported");

    switch (field.getJavaType()) {
      case ENUM: {
        if (field.isRepeated()) {
          @SuppressWarnings("unchecked")
          List<Descriptors.EnumValueDescriptor> values =
              (List<Descriptors.EnumValueDescriptor>) message.getField(field);
          return Lists.transform(values, toSimpleName());
        }
        @SuppressWarnings("unchecked")
        Descriptors.EnumValueDescriptor value =
            (Descriptors.EnumValueDescriptor) message.getField(field);
        return toSimpleName().apply(value);
      }

      case MESSAGE: {
        if (field.isRepeated()) {
          @SuppressWarnings("unchecked")
          List<GeneratedMessage> messages = (List<GeneratedMessage>) message.getField(field);
          return Lists.transform(messages, new Function<GeneratedMessage, Object>() {
            @Override
            public Object apply(GeneratedMessage input) {
              return toMap(input);
            }
          });
        }

        if (field.isOptional() && !message.hasField(field)) {
          return null;
        }

        @SuppressWarnings("unchecked")
        GeneratedMessage messageField = (GeneratedMessage) message.getField(field);
        return toMap(messageField);
      }

      default:
        return message.getField(field);
    }
  }

  private static Function<Descriptors.EnumValueDescriptor, String> toSimpleName() {
    return new Function<Descriptors.EnumValueDescriptor, String>() {
      @Override
      public String apply(Descriptors.EnumValueDescriptor input) {
        String name = input.getFullName();

        int index = name.lastIndexOf('.');
        if (index >= 0) {
          name = name.substring(index + 1);
        }

        return name;
      }
    };
  }

  @VisibleForTesting static class Tofu {
    static final SoyTofu INSTANCE = new SoyFileSet.Builder()
        .add(Renderer.class.getResource("/dossier.soy"))
        .build()
        .compileToTofu();
  }
}
