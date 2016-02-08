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

package com.github.jsdossier.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static com.google.common.io.Files.getNameWithoutExtension;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;

import com.github.jsdossier.annotations.Modules;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.SourceFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class NodeCoreLibrary {

  private static final Logger log = Logger.getLogger(NodeCoreLibrary.class.getName());

  private static final String NODE_EXTERNS_RESOURCE_DIRECTORY = "/third_party/js/externs/node";
  private static final String FILE_NAME_PREFIX = "node/externs.zip//";

  private final boolean loadExterns;
  private ImmutableSet<String> externIds;
  private ImmutableList<SourceFile> externFiles;
  private ImmutableMap<String, SourceFile> coreModulesByPath;

  @Inject
  NodeCoreLibrary(@Modules ImmutableSet<Path> modulePaths) {
    this.loadExterns = !modulePaths.isEmpty();
  }

  public ImmutableSet<String> getExternModuleNames() {
    try {
      loadExterns();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return externIds;
  }

  public ImmutableList<SourceFile> getExternFiles() throws IOException {
    loadExterns();
    return externFiles;
  }

  public ImmutableCollection<SourceFile> getCoreModules() throws IOException {
    loadExterns();
    return coreModulesByPath.values();
  }

  public boolean isModuleId(String id) {
    return getExternModuleNames().contains(id);
  }

  public String getIdFromPath(String path) {
    checkArgument(isModulePath(path), "not a node core module: %s", path);
    String name = path.substring(FILE_NAME_PREFIX.length());
    return getNameWithoutExtension(name);
  }

  public boolean isModulePath(String path) {
    return coreModulesByPath.containsKey(path);
  }

  private void loadExterns() throws IOException {
    if (externFiles == null) {
      synchronized (this) {
        if (externFiles == null) {
          if (!loadExterns) {
            externFiles = ImmutableList.of();
            externIds = ImmutableSet.of();
            coreModulesByPath = ImmutableMap.of();
            return;
          }

          URI uri = getExternZipUri();
          if ("file".equals(uri.getScheme())) {
            loadExterns(Paths.get(uri));
          } else {
            log.fine("Loading externs from jar: " + uri);
            ImmutableMap<String, String> env = ImmutableMap.of();
            try (FileSystem jarFs = FileSystems.newFileSystem(uri, env)) {
              Path directory = jarFs.getPath(NODE_EXTERNS_RESOURCE_DIRECTORY);
              verify(Files.isDirectory(directory), "Node externs not found: %s", directory);
              loadExterns(directory);
            }
          }
        }
      }
    }
  }

  private void loadExterns(Path directory) throws IOException {
    log.fine("Loading node core library externs from " + directory);

    final ImmutableSet.Builder<String> idsBuilder = ImmutableSet.builder();
    final ImmutableList.Builder<SourceFile> externsBuilder = ImmutableList.builder();
    final ImmutableMap.Builder<String, SourceFile> modulesBuilder = ImmutableMap.builder();

    Files.walkFileTree(directory, new FileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
          throws IOException {
        if (file.getFileName().toString().endsWith(".js")) {
          String id = getNameWithoutExtension(file.getFileName().toString());
          if ("globals".equals(id)) {
            externsBuilder.add(SourceFile.fromInputStream(
                FILE_NAME_PREFIX + file.getFileName(),
                Files.newInputStream(file),
                UTF_8));
          } else {
            idsBuilder.add(id);

            String content = new String(readAllBytes(file), UTF_8);
            checkArgument(content.contains("module.exports = "), id);

            SourceFile source = SourceFile.fromCode(FILE_NAME_PREFIX + file.getFileName(), content);
            modulesBuilder.put(source.getName(), source);
          }
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        return FileVisitResult.CONTINUE;
      }
    });

    externIds = idsBuilder.build();
    externFiles = externsBuilder.build();
    coreModulesByPath = modulesBuilder.build();
  }

  private static URI getExternZipUri() {
    URL url = NodeCoreLibrary.class.getResource(NODE_EXTERNS_RESOURCE_DIRECTORY);
    checkNotNull(url, "Unable to find resource %s", NODE_EXTERNS_RESOURCE_DIRECTORY);
    try {
      URI uri = url.toURI();
      if ("file".equals(uri.getScheme())) {
        return uri;
      }
      verify("jar".equals(uri.getScheme()), "Unexpected resource URI: %s", uri);
      verify(uri.toString().endsWith("!" + NODE_EXTERNS_RESOURCE_DIRECTORY),
          "Unexpected resource URI: %s", uri);

      String jar = uri.toString();
      return URI.create(
          jar.substring(0, jar.length() - NODE_EXTERNS_RESOURCE_DIRECTORY.length() - 1));

    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
