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

import com.google.common.collect.FluentIterable;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.TypeLink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * An index of all types and properties in generated documentation.
 */
@DocumentationScoped
final class TypeIndex {

  private final DossierFileSystem dfs;
  private final LinkFactory linkFactory;
  private final TypeRegistry typeRegistry;

  private final JsonObject json = new JsonObject();
  private final Map<NominalType, IndexReference> seenTypes = new HashMap<>();

  @Inject
  TypeIndex(
      DossierFileSystem dfs,
      LinkFactoryBuilder linkFactoryBuilder,
      TypeRegistry typeRegistry) {
    this.dfs = dfs;
    this.typeRegistry = typeRegistry;
    this.linkFactory = linkFactoryBuilder.create(null);
  }

  /**
   * Returns this index as a JSON string.
   */
  @Override
  public String toString() {
    return json.toString();
  }

  public synchronized IndexReference addModule(NominalType module) {
    if (seenTypes.containsKey(module)) {
      return seenTypes.get(module);
    }
    checkArgument(module.isModuleExports(), "not a module exports object: %s", module.getName());
    String dest = dfs.getRelativePath(dfs.getPath(module)).toString();

    JsonObject obj = new JsonObject();
    obj.addProperty("name", dfs.getDisplayName(module));
    obj.addProperty("qualifiedName", dfs.getQualifiedDisplayName(module));
    obj.addProperty("href", dest);

    getJsonArray(json, "modules").add(obj);
    IndexReference ref = new IndexReference(module, obj);
    seenTypes.put(module, ref);
    return ref;
  }

  public synchronized IndexReference addType(NominalType type) {
    return addTypeInfo(getJsonArray(json, "types"), type);
  }

  private synchronized static JsonArray getJsonArray(JsonObject object, String name) {
    if (!object.has(name)) {
      object.add(name, new JsonArray());
    }
    return object.get(name).getAsJsonArray();
  }

  private synchronized IndexReference addTypeInfo(JsonArray array, NominalType type) {
    if (seenTypes.containsKey(type)) {
      return seenTypes.get(type);
    }
    String dest = dfs.getRelativePath(dfs.getPath(type)).toString();

    JsonObject details = new JsonObject();
    details.addProperty("name", dfs.getDisplayName(type));
    details.addProperty("qualifiedName", dfs.getQualifiedDisplayName(type));
    details.addProperty("href", dest);
    details.addProperty("namespace", type.isNamespace());
    details.addProperty("interface", type.getType().isInterface());
    array.add(details);

    List<NominalType> allTypes = typeRegistry.getTypes(type.getType());
    if (allTypes.get(0) != type) {
      List<NominalType> typedefs = FluentIterable.from(typeRegistry.getNestedTypes(type))
          .filter(isTypedef())
          .toSortedList(new QualifiedNameComparator());
      for (NominalType typedef : typedefs) {
        TypeLink link = linkFactory.createLink(typedef);
        checkArgument(
            !link.getHref().isEmpty(), "Failed to build link for %s", typedef.getName());
        JsonObject typedefDetails = new JsonObject();
        typedefDetails.addProperty("name", link.getText());
        typedefDetails.addProperty("href", link.getHref());
        array.add(typedefDetails);
      }
    }
    IndexReference ref = new IndexReference(type, details);
    seenTypes.put(type, ref);
    return ref;
  }

  final class IndexReference {
    private final NominalType type;
    protected final JsonObject index;

    private IndexReference(NominalType type, JsonObject index) {
      this.type = type;
      this.index = index;
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
      return addTypeInfo(getJsonArray(index, "types"), type);
    }

    public void addStaticProperty(String name) {
      getJsonArray(index, "statics").add(new JsonPrimitive(name));
    }

    public void addInstanceProperty(String name) {
      getJsonArray(index, "members").add(new JsonPrimitive(name));
    }
  }
}
