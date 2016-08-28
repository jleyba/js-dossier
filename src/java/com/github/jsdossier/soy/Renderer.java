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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.github.jsdossier.proto.PageData;
import com.github.jsdossier.proto.TypeLink;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.inject.Inject;

/**
 * Renders soy templates.
 */
public class Renderer {

  private final SoyTofu tofu;

  @Inject
  Renderer(
      SoyFileSet.Builder filesetBuilder,
      DossierSoyTypeProvider typeProvider) {
    tofu = filesetBuilder
        .add(Renderer.class.getResource("resources/types.soy"))
        .add(Renderer.class.getResource("resources/dossier.soy"))
        .setLocalTypeRegistry(new SoyTypeRegistry(ImmutableSet.of((SoyTypeProvider) typeProvider)))
        .build()
        .compileToTofu();
  }

  public void render(Path output, PageData page) throws IOException {
    Files.createDirectories(output.getParent());
    try (BufferedWriter writer =
             newBufferedWriter(output, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
      tofu.newRenderer("dossier.soy.page")
          .setData(new SoyMapData("data", ProtoMessageSoyType.toSoyValue(page)))
          .render(writer);
    }
  }

  public void render(Appendable appendable, String text, TypeLink link, boolean codeLink)
      throws IOException {
    tofu.newRenderer("dossier.soy.type.typeLink")
        .setData(new SoyMapData(
            "content", text,
            "codeLink", codeLink,
            "link", ProtoMessageSoyType.toSoyValue(link)))
        .render(appendable);
  }

  public static void main(String args[]) throws IOException {
    checkArgument(args.length > 0, "no output directory specified");

    Path outputDir = FileSystems.getDefault().getPath(args[0]);
    checkArgument(Files.isDirectory(outputDir), "not a directory: %s", outputDir);

    Injector injector = Guice.createInjector(new DossierSoyModule());

    DossierSoyTypeProvider typeProvider = injector.getInstance(DossierSoyTypeProvider.class);
    SoyFileSet fileSet = injector.getInstance(SoyFileSet.Builder.class)
        .add(Renderer.class.getResource("resources/dossier.soy"))
        .add(Renderer.class.getResource("resources/nav.soy"))
        .add(Renderer.class.getResource("resources/types.soy"))
        .setLocalTypeRegistry(
            new SoyTypeRegistry(ImmutableSet.of((SoyTypeProvider) typeProvider)))
        .build();

    SoyJsSrcOptions options = new SoyJsSrcOptions();

    // These options must be disabled before enabling goog modules below.
    options.setShouldDeclareTopLevelNamespaces(false);
    options.setShouldProvideRequireSoyNamespaces(false);
    options.setShouldProvideRequireJsFunctions(false);
    options.setShouldProvideBothSoyNamespacesAndJsFunctions(false);
    options.setShouldGenerateJsdoc(true);

    options.setShouldGenerateGoogModules(true);

    Iterator<Path> files = ImmutableList.of(
        outputDir.resolve("dossier.soy.js"),
        outputDir.resolve("nav.soy.js"),
        outputDir.resolve("types.soy.js")).iterator();
    for (String string : fileSet.compileToJsSrc(options, null)) {
      Path file = files.next();
      Files.write(file, string.getBytes(StandardCharsets.UTF_8));
    }
  }
}
