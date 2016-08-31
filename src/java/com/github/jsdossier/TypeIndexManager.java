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

import static com.github.jsdossier.jscomp.Types.isTypedef;
import static com.google.common.base.Preconditions.checkArgument;

import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.TypeIndex;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * An index of all types and properties in generated documentation.
 */
@DocumentationScoped
final class TypeIndexManager {

  private static final Comparator<TypeIndex.Entry> ENTRY_COMPARATOR =
      new Comparator<TypeIndex.Entry>() {
        @Override
        public int compare(TypeIndex.Entry o1, TypeIndex.Entry o2) {
          String name1 = o1.getType().getQualifiedName().isEmpty()
              ? o1.getType().getName()
              : o1.getType().getQualifiedName();

          String name2 = o2.getType().getQualifiedName().isEmpty()
              ? o2.getType().getName()
              : o2.getType().getQualifiedName();

          return name1.compareTo(name2);
        }
      };

  private final LinkFactory linkFactory;
  private final TypeRegistry typeRegistry;

  private final TypeIndex.Builder index = TypeIndex.newBuilder();
  private final Map<NominalType, IndexReference> seenTypes = new HashMap<>();

  @Inject
  TypeIndexManager(
      LinkFactoryBuilder linkFactoryBuilder,
      TypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    this.linkFactory = linkFactoryBuilder.create(null);
  }

  public TypeIndex toNormalizedProto() {
    return TypeIndex.newBuilder()
        .addAllModule(sortEntries(index.getModuleList()))
        .addAllType(sortEntries(index.getTypeList()))
        .build();
  }

  private static List<TypeIndex.Entry> sortEntries(Iterable<TypeIndex.Entry> types) {
    return FluentIterable.from(types)
        .transform(new Function<TypeIndex.Entry, TypeIndex.Entry>() {
          @Override
          public TypeIndex.Entry apply(TypeIndex.Entry input) {
            if (input.getStaticPropertyCount() == 0
                && input.getPropertyCount() == 0
                && input.getChildCount() == 0) {
              return input;
            }

            return TypeIndex.Entry.newBuilder()
                .setType(input.getType())
                .setIsInterface(input.getIsInterface())
                .setIsNamespace(input.getIsNamespace())
                .addAllChild(sortEntries(input.getChildList()))
                .addAllProperty(Ordering.usingToString().sortedCopy(input.getPropertyList()))
                .addAllStaticProperty(
                    Ordering.usingToString().sortedCopy(input.getStaticPropertyList()))
                .build();
          }
        })
        .toSortedList(ENTRY_COMPARATOR);
  }

  public synchronized IndexReference addModule(NominalType module) {
    if (seenTypes.containsKey(module)) {
      return seenTypes.get(module);
    }
    checkArgument(module.isModuleExports(), "not a module exports object: %s", module.getName());

    TypeIndex.Entry.Builder indexedModule = index.addModuleBuilder()
        .setType(linkFactory.createTypeReference(module))
        .setIsNamespace(true);

    IndexReference ref = new IndexReference(module, indexedModule);
    seenTypes.put(module, ref);
    return ref;
  }

  public synchronized IndexReference addType(NominalType type) {
    return addTypeInfo(type, Optional.<TypeIndex.Entry.Builder>absent());
  }

  private synchronized IndexReference addTypeInfo(
      NominalType type, Optional<TypeIndex.Entry.Builder> module) {
    if (seenTypes.containsKey(type)) {
      return seenTypes.get(type);
    }

    TypeIndex.Entry.Builder indexedType = newEntryBuilder(module)
        .setType(linkFactory.createTypeReference(type))
        .setIsInterface(type.getType().isInterface())
        .setIsNamespace(type.isNamespace());

    List<NominalType> allTypes = typeRegistry.getTypes(type.getType());
    if (allTypes.get(0) != type) {
      List<NominalType> typedefs = FluentIterable.from(typeRegistry.getNestedTypes(type))
          .filter(isTypedef())
          .toSortedList(new QualifiedNameComparator());
      for (NominalType typedef : typedefs) {
        NamedType ref = linkFactory.createTypeReference(typedef);
        checkArgument(!ref.getLink().getHref().isEmpty(),
            "Failed to build link for %s", typedef.getName());
        newEntryBuilder(module).setType(ref);
      }
    }

    IndexReference ref = new IndexReference(type, indexedType);
    seenTypes.put(type, ref);
    return ref;
  }

  private TypeIndex.Entry.Builder newEntryBuilder(Optional<TypeIndex.Entry.Builder> module) {
    if (module.isPresent()) {
      return module.get().addChildBuilder();
    }
    return index.addTypeBuilder();
  }

  final class IndexReference {
    private final NominalType type;
    private final TypeIndex.Entry.Builder entry;

    private IndexReference(NominalType type, TypeIndex.Entry.Builder entry) {
      this.type = type;
      this.entry = entry;
    }

    public NominalType getNominalType() {
      return type;
    }

    public IndexReference addNestedType(NominalType type) {
      checkArgument(getNominalType().isModuleExports(),
          "Nested types should only be recorded for modules: %s", getNominalType().getName());
      checkArgument(getNominalType().getModule().equals(type.getModule()),
          "Type does not belong to this module: (%s, %s)",
          getNominalType().getName(), type.getName());
      return addTypeInfo(type, Optional.of(entry));
    }

    public void addStaticProperty(String name) {
      entry.addStaticProperty(name);
    }

    public void addInstanceProperty(String name) {
      entry.addProperty(name);
    }
  }
}
