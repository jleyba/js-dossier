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

import static java.nio.file.Files.readAllLines;

import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.proto.SourceFile;
import com.github.jsdossier.proto.SourceFileRenderSpec;
import com.github.jsdossier.soy.Renderer;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Charsets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Renders a single source file in the output tree.
 */
@AutoFactory
final class RenderSourceFileTask implements Callable<Path> {
  
  private static final String MODULE_SLASH = "/";
  
  private final DossierFileSystem dfs;
  private final DocTemplate template;
  private final NavIndexFactory navIndexFactory;
  private final Renderer renderer;
  private final Path prefix;
  private final Path path;

  RenderSourceFileTask(
      @Provided DossierFileSystem dfs,
      @Provided DocTemplate template,
      @Provided NavIndexFactory navIndexFactory,
      @Provided Renderer renderer,
      @Provided @SourcePrefix Path prefix,
      Path path) {
    this.dfs = dfs;
    this.template = template;
    this.navIndexFactory = navIndexFactory;
    this.renderer = renderer;
    this.prefix = prefix;
    this.path = path;
  }

  @Override
  public Path call() throws IOException {
    String displayPath = prefix.relativize(path)
        .toString()
        .replace(path.getFileSystem().getSeparator(), MODULE_SLASH);
    Path output = dfs.getPath(path);

    SourceFile file = SourceFile.newBuilder()
        .setBaseName(path.getFileName().toString())
        .setPath(displayPath)
        .addAllLines(readAllLines(path, Charsets.UTF_8))
        .build();

    SourceFileRenderSpec.Builder spec = SourceFileRenderSpec.newBuilder()
        .setFile(file)
        .setResources(dfs.getResources(output, template))
        .setIndex(navIndexFactory.create(output));

    renderer.render(output, spec.build());
    return output;
  }
}
