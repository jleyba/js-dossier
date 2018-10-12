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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.Index;
import com.github.jsdossier.proto.Index.Entry.Builder;
import com.github.jsdossier.proto.Link;
import com.github.jsdossier.proto.NamedType;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.html.types.SafeUrls;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** An index of all types and properties in generated documentation. */
@DocumentationScoped
final class IndexBuilder {

  private static final Comparator<Index.Entry> ENTRY_COMPARATOR =
      (o1, o2) -> {
        String name1 =
            o1.getType().getQualifiedName().isEmpty()
                ? o1.getType().getName()
                : o1.getType().getQualifiedName();

        String name2 =
            o2.getType().getQualifiedName().isEmpty()
                ? o2.getType().getName()
                : o2.getType().getQualifiedName();

        return name1.compareTo(name2);
      };

  private final DossierFileSystem dfs;
  private final LinkFactory linkFactory;
  private final TypeRegistry typeRegistry;
  private final ImmutableSet<MarkdownPage> userPages;

  private final Index.Builder index = Index.newBuilder();
  private final Map<NominalType, IndexReference> seenTypes = new HashMap<>();

  @Inject
  IndexBuilder(
      DossierFileSystem dfs,
      LinkFactoryBuilder linkFactoryBuilder,
      TypeRegistry typeRegistry,
      ImmutableSet<MarkdownPage> userPages) {
    this.dfs = dfs;
    this.typeRegistry = typeRegistry;
    this.userPages = userPages;
    this.linkFactory = linkFactoryBuilder.create(null).withJsonPaths();
  }

  Index toNormalizedProto() {
    return Index.newBuilder()
        .addAllModule(sortEntries(index.getModuleList()))
        .addAllType(sortEntries(index.getTypeList()))
        .addAllPage(sortPages(userPages))
        .addAllSourceFile(
            index.getSourceFileList()
            .stream()
            .sorted(comparing(Link::getHref))
            .collect(toList()))
        .build();
  }

  private Iterable<Link> sortPages(Collection<MarkdownPage> pages) {
    return pages
        .stream()
        .map(
            input -> {
              final Path htmlPath = dfs.getPath(input);
              final Path jsonPath = dfs.getJsonPath(input);
              return Link.newBuilder()
                  .setText(input.getName())
                  .setHref(toUriPath(dfs.getRelativePath(htmlPath)))
                  .setJson(toUriPath(dfs.getRelativePath(jsonPath)))
                  .build();
            })
        .sorted(comparing(Link::getText))
        .collect(toSet());
  }

  private static String toUriPath(Path path) {
    return path.toString().replace(path.getFileSystem().getSeparator(), "/");
  }

  private static List<Index.Entry> sortEntries(List<Index.Entry> types) {
    return types
        .stream()
        .map(
            input -> {
              if (input.getStaticPropertyCount() == 0
                  && input.getPropertyCount() == 0
                  && input.getChildCount() == 0) {
                return input;
              }

              return Index.Entry.newBuilder()
                  .setType(input.getType())
                  .setIsInterface(input.getIsInterface())
                  .setIsNamespace(input.getIsNamespace())
                  .addAllChild(sortEntries(input.getChildList()))
                  .addAllProperty(Ordering.usingToString().sortedCopy(input.getPropertyList()))
                  .addAllStaticProperty(
                      Ordering.usingToString().sortedCopy(input.getStaticPropertyList()))
                  .build();
            })
        .sorted(ENTRY_COMPARATOR)
        .collect(toList());
  }

  synchronized void addSourceFile(Path html, Path json) {
    index
        .addSourceFileBuilder()
        .setHref(toUri(dfs.getRelativePath(html)))
        .setJson(toUri(dfs.getRelativePath(json)));
  }

  private static String toUri(Path path) {
    return path.toString().replace(path.getFileSystem().getSeparator(), "/");
  }

  synchronized IndexReference addModule(NominalType module) {
    if (seenTypes.containsKey(module)) {
      return seenTypes.get(module);
    }
    checkArgument(module.isModuleExports(), "not a module exports object: %s", module.getName());

    Index.Entry.Builder indexedModule =
        index
            .addModuleBuilder()
            .setType(linkFactory.createTypeReference(module))
            .setIsNamespace(true);

    IndexReference ref = new IndexReference(module, indexedModule);
    seenTypes.put(module, ref);
    return ref;
  }

  synchronized IndexReference addType(NominalType type) {
    return addTypeInfo(type, Optional.empty());
  }

  private synchronized IndexReference addTypeInfo(NominalType type, Optional<Builder> module) {
    if (seenTypes.containsKey(type)) {
      return seenTypes.get(type);
    }

    Index.Entry.Builder indexedType =
        newEntryBuilder(module)
            .setType(linkFactory.createTypeReference(type))
            .setIsInterface(type.getType().isInterface())
            .setIsNamespace(type.isNamespace());

    List<NominalType> allTypes = typeRegistry.getTypes(type.getType());
    if (allTypes.get(0) != type) {
      List<NominalType> typedefs =
          typeRegistry
              .getNestedTypes(type)
              .stream()
              .filter(NominalType::isTypedef)
              .sorted(new QualifiedNameComparator())
              .collect(toList());
      for (NominalType typedef : typedefs) {
        NamedType ref = linkFactory.createTypeReference(typedef);
        checkArgument(
            !SafeUrls.fromProto(ref.getLink().getHref()).getSafeUrlString().isEmpty(),
            "Failed to build link for %s",
            typedef.getName());
        newEntryBuilder(module).setType(ref);
      }
    }

    IndexReference ref = new IndexReference(type, indexedType);
    seenTypes.put(type, ref);
    return ref;
  }

  private Index.Entry.Builder newEntryBuilder(Optional<Index.Entry.Builder> module) {
    if (module.isPresent()) {
      return module.get().addChildBuilder();
    }
    return index.addTypeBuilder();
  }

  final class IndexReference {
    private final NominalType type;
    private final Index.Entry.Builder entry;

    private IndexReference(NominalType type, Index.Entry.Builder entry) {
      this.type = type;
      this.entry = entry;
    }

    NominalType getNominalType() {
      return type;
    }

    IndexReference addNestedType(NominalType type) {
      checkArgument(
          getNominalType().isModuleExports(),
          "Nested types should only be recorded for modules: %s",
          getNominalType().getName());
      checkArgument(
          getNominalType().getModule().equals(type.getModule()),
          "Type does not belong to this module: (%s, %s)",
          getNominalType().getName(),
          type.getName());
      return addTypeInfo(type, Optional.of(entry));
    }

    void addStaticProperty(String name) {
      entry.addStaticProperty(name);
    }

    void addInstanceProperty(String name) {
      entry.addProperty(name);
    }
  }
}
