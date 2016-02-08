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

import com.github.jsdossier.annotations.ModuleExterns;
import com.github.jsdossier.annotations.Modules;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class NodeLibrary {

  private static final Logger log = Logger.getLogger(NodeLibrary.class.getName());

  private static final String NODE_EXTERNS_RESOURCE_DIRECTORY = "/third_party/js/externs/node";
  private static final String FILE_NAME_PREFIX = "dossier//node-externs.zip//";

  private final boolean loadExterns;
  private ImmutableSet<Path> userExterns;
  private ImmutableSet<String> externIds;
  private ImmutableList<SourceFile> externFiles;
  private ImmutableMap<String, SourceFile> modulesByPath;
  private ImmutableMap<String, String> idsByPath;

  @Inject
  NodeLibrary(
      @ModuleExterns ImmutableSet<Path> userExterns,
      @Modules ImmutableSet<Path> modulePaths) {
    this.userExterns = userExterns;
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

  public ImmutableCollection<SourceFile> getExternModules() throws IOException {
    loadExterns();
    return modulesByPath.values();
  }

  public boolean isModuleId(String id) {
    return getExternModuleNames().contains(id);
  }

  public String getIdFromPath(String path) {
    checkArgument(idsByPath.containsKey(path), "not an extern module: %s", path);
    return idsByPath.get(path);
  }

  public boolean isModulePath(String path) {
    return modulesByPath.containsKey(path);
  }

  private void loadExterns() throws IOException {
    if (externFiles == null) {
      synchronized (this) {
        if (externFiles == null) {
          if (!loadExterns) {
            externFiles = ImmutableList.of();
            externIds = ImmutableSet.of();
            modulesByPath = ImmutableMap.of();
            idsByPath = ImmutableMap.of();
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

    final Set<String> externIds = new HashSet<>();
    final List<SourceFile> externFiles = new ArrayList<>();
    final Map<String, SourceFile> modulesByPath = new HashMap<>();
    final BiMap<String, String> modulePathsById = HashBiMap.create();

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
            externFiles.add(SourceFile.fromInputStream(
                FILE_NAME_PREFIX + file.getFileName(),
                Files.newInputStream(file),
                UTF_8));
          } else {
            externIds.add(id);

            SourceFile source = loadSource(file, true);
            modulesByPath.put(source.getName(), source);
            modulePathsById.put(id, source.getName());
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

    for (Path path : userExterns) {
      String id = getNameWithoutExtension(path.getFileName().toString());
      if (modulePathsById.containsKey(id)) {
        throw new IllegalArgumentException(
            "Duplicate extern module ID <" + id + "> (" + path + "); originally defined by "
                + modulePathsById.get(id));
      }
      modulePathsById.put(id, path.toString());
      externIds.add(id);
      SourceFile source = loadSource(path, false);
      modulesByPath.put(source.getName(), source);
      modulePathsById.put(id, source.getName());
    }

    this.externIds = ImmutableSet.copyOf(externIds);
    this.externFiles = ImmutableList.copyOf(externFiles);
    this.modulesByPath = ImmutableMap.copyOf(modulesByPath);
    this.idsByPath = ImmutableMap.copyOf(modulePathsById.inverse());
  }

  private static SourceFile loadSource(Path path, boolean isInternal)
      throws IOException {
    String sourceName = isInternal ? (FILE_NAME_PREFIX + path.getFileName()) : path.toString();
    String content = new String(readAllBytes(path), UTF_8);
    return SourceFile.fromCode(sourceName, content);
  }

  private static URI getExternZipUri() {
    URL url = NodeLibrary.class.getResource(NODE_EXTERNS_RESOURCE_DIRECTORY);
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
