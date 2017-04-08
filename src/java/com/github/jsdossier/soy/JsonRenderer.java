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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.google.protobuf.Message;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import javax.inject.Inject;

/** Renders protobuf messages to JSON. */
public final class JsonRenderer {

  private final JsonEncoder encoder;

  @Inject
  JsonRenderer(JsonEncoder encoder) {
    this.encoder = encoder;
  }

  public void render(Path output, Message message) throws IOException {
    render(output, encoder.encode(message));
  }

  public void render(Writer writer, Message message) throws IOException {
    render(writer, encoder.encode(message));
  }

  public void render(Path output, Iterable<? extends Message> messages) throws IOException {
    render(output, toArray(messages));
  }

  public void render(Writer writer, Iterable<? extends Message> messages) throws IOException {
    render(writer, toArray(messages));
  }

  private JsonArray toArray(Iterable<? extends Message> messages) {
    JsonArray array = new JsonArray();
    for (Message message : messages) {
      array.add(encoder.encode(message));
    }
    return array;
  }

  private void render(Path output, JsonElement json) throws IOException {
    createDirectories(output.getParent());
    try (Writer writer = newBufferedWriter(output, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
      render(writer, json);
    }
  }

  private void render(Writer writer, JsonElement json) throws IOException {
    JsonWriter jsonWriter = new JsonWriter(writer);
    jsonWriter.setLenient(false);
    jsonWriter.setIndent("");
    jsonWriter.setSerializeNulls(false);
    Streams.write(json, jsonWriter);
  }
}
