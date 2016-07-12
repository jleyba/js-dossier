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

package com.github.jsdossier;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * The default documentation template.
 */
final class DefaultDocTemplate implements DocTemplate {

  @Override
  public ImmutableList<TemplateFile> getHeadJs() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<TemplateFile> getTailJs() {
    return ImmutableList.of(loadResourceFile("dossier.js", "/src/js/dossier.js"));
  }

  @Override
  public ImmutableList<TemplateFile> getCss() {
    return ImmutableList.of(loadResourceFile("dossier.css", "resources/dossier.css"));
  }

  private static TemplateFile loadResourceFile(String name, String path) {
    URL url = DefaultDocTemplate.class.getResource(path);
    checkNotNull(url, "Resource not found: %s", path);
    return new TemplateFile(Resources.asByteSource(url), name);
  }
}
