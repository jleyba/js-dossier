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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.rhino.JSDocInfo;

import java.nio.file.Path;

import javax.annotation.Nullable;

class FileDescriptor {

  private final Path path;
  @Nullable private final JSDocInfo info;

  FileDescriptor(Path path, @Nullable JSDocInfo info) {
    this.path = checkNotNull(path);
    this.info = info;
  }

  Path getPath() {
    return path;
  }

  @Nullable
  JSDocInfo getInfo() {
    return info;
  }
}
