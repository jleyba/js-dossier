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

package com.github.jsdossier.soy;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import javax.annotation.Nullable;

/**
 * Transforms protobuf messages into JSON representation.
 */
final class JsonMessageTransformer extends MessageTransformer<JsonElement> {

  @Override
  protected String computeFieldName(Descriptors.FieldDescriptor field) {
    return String.valueOf(field.getNumber());
  }

  @Override
  protected MapBuilder<JsonElement> newMapBuilder(Message message) {
    return new MessageTransformer.MapBuilder<JsonElement>() {
      JsonObject object = new JsonObject();

      @Override
      public void put(String key, JsonElement value) {
        if (isFalseLike(value)) {
          return;
        }
        object.add(key, value);
      }

      private boolean isFalseLike(JsonElement value) {
        if (value.isJsonNull()
            || (value.isJsonArray() && value.getAsJsonArray().size() == 0)) {
          return true;
        }

        if (value.isJsonPrimitive()) {
          JsonPrimitive p = value.getAsJsonPrimitive();
          return (p.isBoolean() && !p.getAsBoolean())
              || (p.isString() && p.getAsString().isEmpty())
              || (p.isNumber() && p.getAsNumber().equals(0));
        }

        return false;
      }

      @Override
      public JsonElement build() {
        return object;
      }
    };
  }

  @Override
  protected ListBuilder<JsonElement> newListBuilder() {
    return new MessageTransformer.ListBuilder<JsonElement>() {
      JsonArray array = new JsonArray();

      @Override
      public void add(JsonElement value) {
        array.add(value);
      }

      @Override
      public JsonElement build() {
        return array;
      }
    };
  }

  @Override
  protected JsonElement transform(Descriptors.EnumValueDescriptor e) {
    return new JsonPrimitive(e.getNumber());
  }

  @Override
  protected JsonElement transform(@Nullable Number n) {
    if (n == null) {
      return JsonNull.INSTANCE;
    }

    if (n instanceof Double) {
      Double d = (Double) n;
      if (d.isInfinite() || d.isNaN()) {
        return JsonNull.INSTANCE;
      }
    }

    return new JsonPrimitive(n);
  }

  @Override
  protected JsonElement transform(@Nullable Boolean b) {
    return b == null ? JsonNull.INSTANCE : new JsonPrimitive(b);
  }

  @Override
  protected JsonElement transform(@Nullable String s) {
    if (isNullOrEmpty(s)) {
      return JsonNull.INSTANCE;
    }
    return new JsonPrimitive(s);
  }

  @Override
  protected JsonElement nullValue() {
    return JsonNull.INSTANCE;
  }
}
