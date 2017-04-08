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
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.List;

/**
 * Encodes {@link Message} objects as JSON arrays, storing each field in the array at the position
 * {@code field_number - 1}.
 */
final class JsonEncoder {

  @Inject
  JsonEncoder() {}

  /** Encode the given message as a JSON array. */
  public JsonArray encode(Message message) {
    JsonArray array = new JsonArray();
    for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
      if (field.isRepeated() || message.hasField(field)) {
        JsonElement element = encodeField(field, message.getField(field));
        if (!element.isJsonNull()) {
          while (array.size() < field.getNumber()) {
            array.add(JsonNull.INSTANCE);
          }
          array.set(field.getNumber() - 1, element);
        }
      }
    }
    return array;
  }

  @SuppressWarnings("unchecked")
  private JsonElement encodeField(FieldDescriptor field, Object value) {
    if (field.isRepeated()) {
      return encodeList(field, (List<Object>) value);
    }
    return encodeValue(field, value);
  }

  private JsonArray encodeList(FieldDescriptor field, List<Object> values) {
    JsonArray array = new JsonArray();
    for (Object value : values) {
      array.add(encodeValue(field, value));
    }
    return array;
  }

  @SuppressWarnings("unchecked")
  private JsonElement encodeValue(FieldDescriptor field, Object value) {
    switch (field.getJavaType()) {
      case ENUM:
        return new JsonPrimitive(((EnumValueDescriptor) value).getNumber());

      case MESSAGE:
        return encode((Message) value);

      case BOOLEAN:
        return !((boolean) value) ? new JsonPrimitive(0) : new JsonPrimitive(1);

      case INT:
      case LONG:
        return new JsonPrimitive((Number) value);

      case STRING:
        return isNullOrEmpty((String) value)
            ? JsonNull.INSTANCE
            : new JsonPrimitive((String) value);

      default:
        return JsonNull.INSTANCE;
    }
  }
}
