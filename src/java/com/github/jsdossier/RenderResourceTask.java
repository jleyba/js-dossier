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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Task copies a resource file to the output directory.
 */
@AutoFactory
final class RenderResourceTask implements Callable<Path> {
  
  private final DossierFileSystem dfs;
  private final TemplateFile file;

  RenderResourceTask(
      @Provided DossierFileSystem dfs,
      TemplateFile file) {
    this.dfs = dfs;
    this.file = file;
  }

  @Override
  public Path call() throws IOException {
    Path output = dfs.getPath(file);
    try (InputStream input = file.getSource().openStream()) {
      Files.copy(input, output, REPLACE_EXISTING);
    }
    return output;
  }
}
