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
import static com.google.common.base.Verify.verify;
import static com.google.common.io.Files.getNameWithoutExtension;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;

import com.github.jsdossier.annotations.ModuleExterns;
import com.github.jsdossier.annotations.Modules;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
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
public final class NodeLibrary {

  private static final Logger log = Logger.getLogger(NodeLibrary.class.getName());

  private static final String NODE_EXTERNS_RESOURCE_DIRECTORY =
      "/" + NodeLibrary.class.getPackage().getName().replace('.', '/') + "/resources/externs/node";
  private static final String FILE_NAME_PREFIX = "dossier//node-externs.zip//";

  private static final Supplier<ExternCollection> NODE_EXTERNS = Suppliers.memoize(() -> {
    URI uri = getExternZipUri();
    try {
      if ("file".equals(uri.getScheme())) {
        return loadDirectory(Paths.get(uri));
      } else {
        log.fine("Loading externs from jar: " + uri);
        ImmutableMap<String, String> env = ImmutableMap.of();
        try (FileSystem jarFs = FileSystems.newFileSystem(uri, env)) {
          Path directory = jarFs.getPath(NODE_EXTERNS_RESOURCE_DIRECTORY);
          verify(Files.isDirectory(directory), "Node externs not found: %s", directory);
          return loadDirectory(directory);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  });

  private final Supplier<ExternCollection> externs;

  @Inject
  NodeLibrary(
      @ModuleExterns ImmutableSet<Path> userExterns,
      @Modules ImmutableSet<Path> modulePaths) {
    if (modulePaths.isEmpty()) {
      externs = Suppliers.ofInstance(ExternCollection.empty());
    } else {
      externs = Suppliers.memoize(new ExternSupplier(userExterns));
    }
  }

  public ImmutableList<SourceFile> getExternFiles() throws IOException {
    return externs.get().files;
  }

  public ImmutableCollection<SourceFile> getExternModules() throws IOException {
    return externs.get().modulesByPath.values();
  }

  public boolean isModuleId(String id) {
    return externs.get().ids.contains(toSafeName(id));
  }

  public String normalizeModuleId(String id) {
    return toSafeName(id);
  }

  public String getIdFromPath(String path) {
    checkArgument(
        externs.get().idsByPath.containsKey(path),
        "not an extern module: %s", path);
    return externs.get().idsByPath.get(path);
  }

  public boolean isModulePath(String path) {
    return externs.get().modulesByPath.containsKey(path);
  }

  private static String toSafeName(String name) {
    return name.replace('-', '_');
  }

  private static SourceFile loadSource(Path path, boolean isInternal)
      throws IOException {
    String sourceName = isInternal ? (FILE_NAME_PREFIX + path.getFileName()) : path.toString();
    String content = new String(readAllBytes(path), UTF_8);
    return SourceFile.fromCode(sourceName, content);
  }

  private static URI getExternZipUri() {
    URL url = Resources.getResource(NodeLibrary.class, NODE_EXTERNS_RESOURCE_DIRECTORY);
    try {
      URI uri = url.toURI();
      if ("file".equals(uri.getScheme())) {
        return uri;
      }
      verify("jar".equals(uri.getScheme()), "Unexpected resource URI: %s", uri);
      verify(uri.toString().endsWith("!" + NODE_EXTERNS_RESOURCE_DIRECTORY),
          "Unexpected resource URI: %s\nExpected final path: %s", uri,
          NODE_EXTERNS_RESOURCE_DIRECTORY);

      String jar = uri.toString();
      return URI.create(
          jar.substring(0, jar.length() - NODE_EXTERNS_RESOURCE_DIRECTORY.length() - 1));

    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private static ExternCollection loadCollection(ImmutableSet<Path> paths) throws IOException {
    Set<String> externIds = new HashSet<>();
    Map<String, SourceFile> modulesByPath = new HashMap<>();
    BiMap<String, String> modulePathsById = HashBiMap.create();

    for (Path path : paths) {
      String id = toSafeName(getNameWithoutExtension(path.getFileName().toString()));
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

    return new ExternCollection(
        ImmutableSet.copyOf(externIds),
        ImmutableList.of(),
        ImmutableMap.copyOf(modulesByPath),
        ImmutableMap.copyOf(modulePathsById.inverse()));
  }

  private static ExternCollection loadDirectory(Path directory) throws IOException {
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
          String id = toSafeName(getNameWithoutExtension(file.getFileName().toString()));
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

    return new ExternCollection(
        ImmutableSet.copyOf(externIds),
        ImmutableList.copyOf(externFiles),
        ImmutableMap.copyOf(modulesByPath),
        ImmutableMap.copyOf(modulePathsById.inverse()));
  }

  private static final class ExternSupplier implements Supplier<ExternCollection> {
    private final ImmutableSet<Path> userExternPaths;

    private ExternSupplier(ImmutableSet<Path> userExternPaths) {
      this.userExternPaths = userExternPaths;
    }

    @Override
    public ExternCollection get() {
      try {
        ExternCollection nodeExterns = NODE_EXTERNS.get();
        ExternCollection userExterns = loadCollection(userExternPaths);
        return nodeExterns.merge(userExterns);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final class ExternCollection {
    private final ImmutableSet<String> ids;
    private final ImmutableList<SourceFile> files;
    private final ImmutableMap<String, SourceFile> modulesByPath;
    private final ImmutableMap<String, String> idsByPath;

    private ExternCollection(
        ImmutableSet<String> ids,
        ImmutableList<SourceFile> externFiles,
        ImmutableMap<String, SourceFile> modulesByPath,
        ImmutableMap<String, String> idsByPath) {
      this.ids = ids;
      this.files = externFiles;
      this.modulesByPath = modulesByPath;
      this.idsByPath = idsByPath;
    }

    static ExternCollection empty() {
      return new ExternCollection(
          ImmutableSet.of(),
          ImmutableList.of(),
          ImmutableMap.of(),
          ImmutableMap.of());
    }

    ExternCollection merge(ExternCollection other) {
      return new ExternCollection(
          ImmutableSet.<String>builder()
              .addAll(ids)
              .addAll(other.ids)
              .build(),
          ImmutableList.<SourceFile>builder()
              .addAll(files)
              .addAll(other.files)
              .build(),
          ImmutableMap.<String, SourceFile>builder()
              .putAll(modulesByPath)
              .putAll(other.modulesByPath)
              .build(),
          ImmutableMap.<String, String>builder()
              .putAll(idsByPath)
              .putAll(other.idsByPath)
              .build());
    }
  }
}
