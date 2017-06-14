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
import com.google.auto.value.AutoValue;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class NodeLibraryImpl implements NodeLibrary {

  private static final Logger log = Logger.getLogger(NodeLibraryImpl.class.getName());

  private static final String NODE_EXTERNS_RESOURCE_DIRECTORY =
      "/" + NodeLibraryImpl.class.getPackage().getName().replace('.', '/') + "/resources/externs/node";
  private static final String FILE_NAME_PREFIX = "dossier//node-externs.zip//";

  private static final Supplier<ExternCollection> NODE_EXTERNS =
      Suppliers.memoize(
          () -> {
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
  NodeLibraryImpl(
      @ModuleExterns ImmutableSet<Path> userExterns, @Modules ImmutableSet<Path> modulePaths) {
    if (modulePaths.isEmpty()) {
      externs = Suppliers.ofInstance(ExternCollection.empty());
    } else {
      externs = Suppliers.memoize(new ExternSupplier(userExterns));
    }
  }

  @Override
  public ImmutableList<SourceFile> getExternFiles() throws IOException {
    return externs.get().getFiles();
  }

  @Override
  public ImmutableCollection<SourceFile> getExternModules() throws IOException {
    return externs.get().getModulesByPath().values();
  }

  @Override
  public boolean canRequireId(String id) {
    return externs.get().getRequireIds().contains(id);
  }

  @Override
  public String normalizeRequireId(String id) {
    checkArgument(canRequireId(id), "not an extern module: %s", id);
    return toSafeName(id);
  }

  @Override
  public String getIdFromPath(String path) {
    checkArgument(externs.get().getIdsByPath().containsKey(path), "not an extern module: %s", path);
    return externs.get().getIdsByPath().get(path);
  }

  @Override
  public boolean isModulePath(String path) {
    return externs.get().getModulesByPath().containsKey(path);
  }

  private static String toSafeName(String name) {
    return name.replace('-', '_');
  }

  private static SourceFile loadSource(Path path, boolean isInternal) throws IOException {
    String sourceName = isInternal ? (FILE_NAME_PREFIX + path.getFileName()) : path.toString();
    String content = new String(readAllBytes(path), UTF_8);
    return SourceFile.fromCode(sourceName, content);
  }

  private static URI getExternZipUri() {
    URL url = Resources.getResource(NodeLibraryImpl.class, NODE_EXTERNS_RESOURCE_DIRECTORY);
    try {
      URI uri = url.toURI();
      if ("file".equals(uri.getScheme())) {
        return uri;
      }
      verify("jar".equals(uri.getScheme()), "Unexpected resource URI: %s", uri);
      verify(
          uri.toString().endsWith("!" + NODE_EXTERNS_RESOURCE_DIRECTORY),
          "Unexpected resource URI: %s\nExpected final path: %s",
          uri,
          NODE_EXTERNS_RESOURCE_DIRECTORY);

      String jar = uri.toString();
      return URI.create(
          jar.substring(0, jar.length() - NODE_EXTERNS_RESOURCE_DIRECTORY.length() - 1));

    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private static ExternCollection loadCollection(ImmutableSet<Path> paths) throws IOException {
    ExternCollection.Builder builder = ExternCollection.builder();
    Map<String, String> pathsById = new HashMap<>();

    for (Path path : paths) {
      String id = getNameWithoutExtension(path.getFileName().toString());
      if (pathsById.containsKey(id)) {
        throw new IllegalArgumentException(
            "Duplicate extern module ID <"
                + id
                + "> ("
                + path
                + "); originally defined by "
                + pathsById.get(id));
      }
      pathsById.put(id, path.toString());
      builder.add(id, loadSource(path, false));
    }
    return builder.build();
  }

  private static ExternCollection loadDirectory(Path directory) throws IOException {
    log.fine("Loading node core library externs from " + directory);

    final ExternCollection.Builder builder = ExternCollection.builder();

    Files.walkFileTree(
        directory,
        new FileVisitor<Path>() {
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
                builder.filesBuilder().add(
                    SourceFile.fromInputStream(
                        FILE_NAME_PREFIX + file.getFileName(), Files.newInputStream(file), UTF_8));
              } else {
                builder.add(id, loadSource(file, true));
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
    return builder.build();
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

  @AutoValue
  abstract static class ExternCollection {
    ExternCollection() {
    }

    static Builder builder() {
      return new AutoValue_NodeLibraryImpl_ExternCollection.Builder();
    }

    static ExternCollection empty() {
      return builder().build();
    }

    abstract ImmutableSet<String> getRequireIds();

    abstract ImmutableList<SourceFile> getFiles();

    abstract ImmutableMap<String, SourceFile> getModulesByPath();

    abstract ImmutableMap<String, String> getIdsByPath();

    abstract Builder toBuilder();

    ExternCollection merge(ExternCollection other) {
      Builder builder = toBuilder();
      builder.requireIdsBuilder().addAll(other.getRequireIds());
      builder.filesBuilder().addAll(other.getFiles());
      builder.modulesByPathBuilder().putAll(other.getModulesByPath());
      builder.idsByPathBuilder().putAll(other.getIdsByPath());
      return builder.build();
    }

    @AutoValue.Builder
    abstract static class Builder {
      Builder() {
      }

      abstract ImmutableSet.Builder<String> requireIdsBuilder();

      abstract ImmutableList.Builder<SourceFile> filesBuilder();

      abstract ImmutableMap.Builder<String, SourceFile> modulesByPathBuilder();

      abstract ImmutableMap.Builder<String, String> idsByPathBuilder();

      void add(String id, SourceFile file) {
        requireIdsBuilder().add(id);
        modulesByPathBuilder().put(file.getName(), file);
        idsByPathBuilder().put(file.getName(), toSafeName(id));
      }

      abstract ExternCollection build();
    }
  }
}
