package com.github.jleyba.dossier;

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
