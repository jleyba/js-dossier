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

import com.google.common.io.ByteSource;

/**
 * Represents a file that contributes to the generated documentation.
 */
final class TemplateFile {

  private final ByteSource source;
  private final String name;

  TemplateFile(ByteSource source, String name) {
    this.source = source;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public ByteSource getSource() {
    return source;
  }
}
