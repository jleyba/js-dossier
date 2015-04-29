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

package com.github.jsdossier;

import static com.github.jsdossier.CompilerUtil.createSourceFile;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Jimfs;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;

import com.github.jsdossier.jscomp.DossierCompiler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link DocWriter}.
 */
@RunWith(JUnit4.class)
public class DocWriterTest {

  private FileSystem fs;
  private Path outputDir;
  private ConfigBuilder config;

  @Before
  public void setUp() {
    fs = Jimfs.newFileSystem();
    outputDir = fs.getPath("out");
    config = new ConfigBuilder();
  }

  @Test
  public void copiesResourcesToOutputDirectory() throws IOException {
    config.addSource(path("simple.js"), "function x() {}")
        .generateDocs();

    assertExists(fs.getPath("out/dossier.css"));
    assertExists(fs.getPath("out/dossier.js"));
  }

  @Test
  public void writesTypes_simple() throws IOException {
    config.addSource(path("simple.js"), "function x() {}").generateDocs();
    JsonObject json = readTypesJs();
    assertThat((Iterable) json.entrySet()).isEmpty();
  }

  @Test
  public void writesTypes_modulesNotIncludedInTypeList() throws IOException {
    config.addModule(path("module.js"),
        "/**",
        " * @param {string} name a name.",
        " * @return {string} a greeting.",
        " */",
        "exports.greet = function(name) { return 'hello, ' + name; };")
        .generateDocs();
    JsonObject json = readTypesJs();

    assertFalse(json.has("types"));

    JsonArray modules = json.getAsJsonArray("modules");
    assertEquals(1, modules.size());

    JsonObject module = modules.get(0).getAsJsonObject();
    assertEquals("work/module", module.get("name").getAsString());
    assertEquals("module_work_module.html", module.get("href").getAsString());
    assertFalse(module.has("types"));
    assertFalse(module.has("members"));
    assertEquals(
        new JsonArrayBuilder()
            .add(new JsonPrimitive("greet"))
            .build(),
        module.getAsJsonArray("statics"));
  }

  private JsonObject readTypesJs() throws IOException {
    String contents = new String(readAllBytes(fs.getPath("out/types.js")), UTF_8);
    // Trim trailing semicolon and var assignment prefix.
    contents = contents.substring(0, contents.length() - 1)
        .substring("var TYPES = ".length());

    return new Gson().fromJson(contents, JsonObject.class);
  }

  private Path path(String first, String... remaining) {
    return fs.getPath("").resolve(fs.getPath(first, remaining));
  }

  private class ConfigBuilder {

    private final JsonObject jsonConfig = new JsonObject();
    private final ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();
    private final ImmutableList.Builder<SourceFile> modules = ImmutableList.builder();
    private final ImmutableList.Builder<Path> modulePaths = ImmutableList.builder();
    private final ImmutableList.Builder<SourceFile> externs = ImmutableList.builder();

    ConfigBuilder() {
      jsonConfig.addProperty("output", outputDir.toString());
      jsonConfig.add("sources", new JsonArray());
      jsonConfig.add("modules", new JsonArray());
      jsonConfig.add("externs", new JsonArray());
    }

    ConfigBuilder addSource(Path path, String... lines) throws IOException {
      return addFile(jsonConfig.getAsJsonArray("sources"), sources, path, lines);
    }

    ConfigBuilder addModule(Path path, String... lines) throws IOException {
      modulePaths.add(path.toAbsolutePath());
      return addFile(jsonConfig.getAsJsonArray("modules"), modules, path, lines);
    }

    private ConfigBuilder addFile(
        JsonArray configList, ImmutableList.Builder<SourceFile> builder,
        Path path, String... lines)
        throws IOException {
      path = path.toAbsolutePath();
      configList.add(new JsonPrimitive(path.toString()));
      String content = Joiner.on("\n").join(lines);
      Files.write(path, content.getBytes(UTF_8));
      builder.add(createSourceFile(path, content));
      return this;
    }

    void generateDocs() throws IOException {
      DossierCompiler compiler = new DossierCompiler(System.err, modulePaths.build());
      TypeRegistry typeRegistry = new TypeRegistry(compiler.getTypeRegistry());
      CompilerOptions options = Main.createOptions(fs, typeRegistry, compiler);
      CompilerUtil util = new CompilerUtil(compiler, options);

      Config config = Config.load(
          new ByteArrayInputStream(jsonConfig.toString().getBytes(UTF_8)),
          fs);

      util.compile(
          externs.build(),
          ImmutableList.copyOf(
              Iterables.concat(sources.build(), modules.build())));

      DocWriter writer = new DocWriter(
          config.getOutput(),
          Iterables.concat(config.getSources(), config.getModules()),
          config.getSrcPrefix(),
          config.getReadme(),
          config.getCustomPages(),
          typeRegistry,
          config.getTypeFilter(),
          new Linker(
              config.getOutput(),
              config.getSrcPrefix(),
              config.getModulePrefix(),
              config.getTypeFilter(),
              typeRegistry),
          new CommentParser(config.useMarkdown()));
      writer.generateDocs();
    }
  }

  private static interface JsonBuilder<T extends JsonElement> {
    T build();
  }

  private static class JsonArrayBuilder implements JsonBuilder<JsonArray> {
    private final JsonArray array = new JsonArray();

    public JsonArrayBuilder add(JsonBuilder<?> builder) {
      array.add(builder.build());
      return this;
    }

    public JsonArrayBuilder add(JsonElement element) {
      array.add(element);
      return this;
    }

    @Override
    public JsonArray build() {
      return array;
    }
  }

  private static void assertExists(Path path) {
    assertTrue("expected to exist: " + path, Files.exists(path));
  }
}
