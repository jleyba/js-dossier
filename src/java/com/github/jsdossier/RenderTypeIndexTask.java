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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.github.jsdossier.annotations.DocumentationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javax.inject.Inject;

/**
 * Task for rendering the main type index.
 */
@DocumentationScoped
final class RenderTypeIndexTask implements Callable<Path> {
  
  private final DossierFileSystem dfs;
  private final TypeIndex index;

  @Inject
  RenderTypeIndexTask(DossierFileSystem dfs, TypeIndex index) {
    this.dfs = dfs;
    this.index = index;
  }

  @Override
  public Path call() throws IOException {
    // NOTE: JSON is not actually a subset of JavaScript, but in our case we know we only
    // have valid JavaScript input, so we can use JSONObject#toString() as a quick-and-dirty
    // formatting mechanism.
    String content = "var TYPES = " + index + ";";
    Path path = dfs.getOutputRoot().resolve("types.js");
    Files.write(path, content.getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING);
    return path;
  }
}
