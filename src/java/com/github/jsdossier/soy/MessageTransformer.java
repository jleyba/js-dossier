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

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Transforms {@link Message} objects into another representation.
 */
abstract class MessageTransformer<T> {

  private static final ImmutableSet<FieldDescriptor.JavaType> CONVERTIBLE_TYPES =
      ImmutableSet.<FieldDescriptor.JavaType>builder()
          .add(FieldDescriptor.JavaType.BOOLEAN)
          .add(FieldDescriptor.JavaType.INT)
          .add(FieldDescriptor.JavaType.LONG)
          .add(FieldDescriptor.JavaType.FLOAT)
          .add(FieldDescriptor.JavaType.DOUBLE)
          .add(FieldDescriptor.JavaType.STRING)
          .add(FieldDescriptor.JavaType.ENUM)
          .add(FieldDescriptor.JavaType.MESSAGE)
          .build();

  public T transform(Message message) {
    MapBuilder<T> map = newMapBuilder(message);
    message.getDescriptorForType().getFields()
        .stream()
        .filter(field -> CONVERTIBLE_TYPES.contains(field.getJavaType()))
        .filter(field -> field.isRepeated() || message.hasField(field))
        .forEach(field -> map.put(computeFieldName(field), transform(message, field)));
    return map.build();
  }

  protected abstract String computeFieldName(FieldDescriptor field);
  protected abstract MapBuilder<T> newMapBuilder(Message message);
  protected abstract ListBuilder<T> newListBuilder();
  protected abstract T transform(EnumValueDescriptor e);
  protected abstract T transform(@Nullable Number n);
  protected abstract T transform(@Nullable Boolean b);
  protected abstract T transform(@Nullable String s);
  protected abstract T nullValue();

  @SuppressWarnings("unchecked")
  private T transform(Message message, FieldDescriptor field) {
    Object fieldValue = message.getField(field);
    switch (field.getJavaType()) {
      case ENUM:
        if (field.isRepeated()) {
          List<EnumValueDescriptor> values = (List<EnumValueDescriptor>) fieldValue;
          return transform(values, this::transform);
        }
        EnumValueDescriptor value = (EnumValueDescriptor) fieldValue;
        return transform(value);

      case MESSAGE:
        if (field.isRepeated()) {
          List<Message> messages = (List<Message>) fieldValue;
          return transform(messages, input -> input == null ? nullValue() : transform(input));
        }
        if (message.hasField(field)) {
          return transform((Message) fieldValue);
        }
        return nullValue();

      case LONG:
      case INT:
        if (field.isRepeated()) {
          List<Number> values = (List<Number>) fieldValue;
          return transform(values, this::transform);
        }
        return transform((Number) fieldValue);

      case STRING:
        if (field.isRepeated()) {
          List<String> values = (List<String>) fieldValue;
          return transform(values, this::transform);
        }
        return transform((String) fieldValue);

      case BOOLEAN:
        if (field.isRepeated()) {
          List<Boolean> values = (List<Boolean>) fieldValue;
          return transform(values, this::transform);
        }
        return transform((Boolean) fieldValue);

      default:
        throw new UnsupportedOperationException(
            "Cannot convert type for field " + field.getFullName());
    }
  }

  private <E> T transform(Iterable<E> items, Function<E, T> fn) {
    ListBuilder<T> list = newListBuilder();
    for (E item : items) {
      list.add(fn.apply(item));
    }
    return list.build();
  }

  protected interface MapBuilder<T> {
    void put(String key, T value);
    T build();
  }

  protected interface ListBuilder<T> {
    void add(T value);
    T build();
  }
}
