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

package com.github.jsdossier.soy;

import static com.google.common.base.Preconditions.checkArgument;

import com.github.jsdossier.proto.Dossier;
import com.github.jsdossier.proto.Expression;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.protobuf.Descriptors;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Qualifier;

/**
 * Generates JavaScript classes for JSON-encoded protocol buffers.
 */
final class ProtoDescriptorsToJs {

  private final Path output;
  private final SoyTofu tofu;

  @Inject
  ProtoDescriptorsToJs(@Internal Path output, @Internal SoyTofu tofu) {
    this.output = output;
    this.tofu = tofu;
  }

  private void processFiles(Descriptors.FileDescriptor... files) throws IOException {
    List<SoyRecord> data = new ArrayList<>();

    for (Descriptors.FileDescriptor file : files) {
      collectEnumData(file.getEnumTypes(), data);
      collectMessageData(file.getMessageTypes(), data);
    }

    try (PrintWriter writer = new PrintWriter(Files.newOutputStream(output))) {
      tofu.newRenderer("dossier.soy.proto.render")
          .setData(new SoyMapData("data", data))
          .render(writer);
    }
  }

  private static void collectEnumData(
      Iterable<Descriptors.EnumDescriptor> items, List<SoyRecord> data) {
    for (Descriptors.EnumDescriptor item : items) {
      data.add(asRecord(item));
    }
  }

  private static void collectMessageData(
      Iterable<Descriptors.Descriptor> items, List<SoyRecord> data) {
    for (Descriptors.Descriptor item : items) {
      data.add(asRecord(item));
      collectEnumData(item.getEnumTypes(), data);
      collectMessageData(item.getNestedTypes(), data);
    }
  }

  private static SoyRecord asRecord(Descriptors.EnumDescriptor descriptor) {
    return new SoyMapData(
        "enum", ProtoMessageSoyType.toSoyValue(descriptor.toProto()),
        "name", descriptor.getFullName());
  }

  private static SoyRecord asRecord(Descriptors.Descriptor descriptor) {
    return new SoyMapData(
        "message", ProtoMessageSoyType.toSoyValue(descriptor.toProto()),
        "name", descriptor.getFullName());
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  private @interface Internal {}

  public static void main(String[] args) throws IOException {
    checkArgument(args.length > 0, "no output directory specified");

    final Path output = FileSystems.getDefault().getPath(args[0]);
    Injector injector = Guice.createInjector(
        new DossierSoyModule(),
        new AbstractModule() {
          @Override
          protected void configure() {
          }

          @Provides
          @Internal
          Path provideOutput() {
            return output;
          }

          @Provides
          @Internal
          SoyTofu provideTofu(
              SoyFileSet.Builder builder, DossierSoyTypeProvider typeProvider) {
            return builder.add(ProtoDescriptorsToJs.class.getResource("resources/proto.soy"))
                .setLocalTypeRegistry(
                    new SoyTypeRegistry(ImmutableSet.of((SoyTypeProvider) typeProvider)))
                .build()
                .compileToTofu();
          }
        });

    injector.getInstance(ProtoDescriptorsToJs.class)
        .processFiles(
            Dossier.getDescriptor(),
            Expression.getDescriptor());
  }
}
