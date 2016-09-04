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
import static com.google.common.base.Strings.nullToEmpty;

import com.github.jsdossier.proto.Options;
import com.github.jsdossier.proto.SanitizedContent;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.template.soy.shared.restricted.Sanitizers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Transforms {@link Message} objects into another representation.
 */
abstract class MessageTransformer<T> {

  private static final Pattern PERMISSIBLE_URI_PREFIX_PATTERN =
      Pattern.compile("^((?:\\.{1,2}/)+)");

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
    for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
      if (CONVERTIBLE_TYPES.contains(field.getJavaType())) {
        if (field.isRepeated() || message.hasField(field)) {
          map.put(computeFieldName(field), transform(message, field));
        }
      }
    }
    return map.build();
  }

  protected abstract String computeFieldName(FieldDescriptor field);
  protected abstract MapBuilder<T> newMapBuilder(Message message);
  protected abstract ListBuilder<T> newListBuilder();
  protected abstract T transform(EnumValueDescriptor e);
  protected abstract T transform(@Nullable Number n);
  protected abstract T transform(@Nullable Boolean b);
  protected abstract T transform(@Nullable String s);
  protected abstract T htmlValue(String s);
  protected abstract T uriValue(String s);
  protected abstract T nullValue();

  @SuppressWarnings("unchecked")
  private T transform(Message message, FieldDescriptor field) {
    Object fieldValue = message.getField(field);
    switch (field.getJavaType()) {
      case ENUM:
        if (field.isRepeated()) {
          List<EnumValueDescriptor> values = (List<EnumValueDescriptor>) fieldValue;
          return transform(values, new Function<EnumValueDescriptor, T>() {
            @Override
            public T apply(EnumValueDescriptor input) {
              return transform(input);
            }
          });
        }
        EnumValueDescriptor value = (EnumValueDescriptor) fieldValue;
        return transform(value);

      case MESSAGE:
        if (field.isRepeated()) {
          List<Message> messages = (List<Message>) fieldValue;
          return transform(messages, new Function<Message, T>() {
            @Override
            public T apply(@Nullable Message input) {
              return input == null ? nullValue() : transform(input);
            }
          });
        }
        if (message.hasField(field)) {
          return transform((Message) fieldValue);
        }
        return nullValue();

      case LONG:
      case INT:
        if (field.isRepeated()) {
          List<Number> values = (List<Number>) fieldValue;
          return transform(values, new Function<Number, T>() {
            @Override
            public T apply(Number input) {
              return transform(input);
            }
          });
        }
        return transform((Number) fieldValue);

      case STRING:
        ContentType contentType = ContentType.STRING;
        if (field.getOptions().hasExtension(Options.sanitized)) {
          SanitizedContent sc = field.getOptions().getExtension(Options.sanitized);
          if (sc.getHtml()) {
            contentType = ContentType.HTML;
          } else if (sc.getUri()) {
            contentType = ContentType.URI;
          }
        }

        Function<String, T> xform = stringTransformer(contentType);
        if (field.isRepeated()) {
          List<String> values = (List<String>) fieldValue;
          return transform(values, xform);
        }
        return xform.apply((String) fieldValue);

      case BOOLEAN:
        if (field.isRepeated()) {
          List<Boolean> values = (List<Boolean>) fieldValue;
          return transform(values, new Function<Boolean, T>() {
            @Override
            public T apply(Boolean input) {
              return transform(input);
            }
          });
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

  private Function<String, T> stringTransformer(final ContentType type) {
    return new Function<String, T>() {
      @Override
      public T apply(@Nullable String input) {
        if (isNullOrEmpty(input)) {
          return transform(input);
        }
        input = type.sanitize(input);
        switch (type) {
          case HTML:
            return htmlValue(input);
          case URI:
            return uriValue(input);
          case STRING:
            return transform(input);
          default:
            throw new AssertionError();
        }
      }
    };
  }

  private enum ContentType {
    HTML() {
      @Override
      String sanitize(String value) {
        return HtmlSanitizer.sanitize(value);
      }
    },

    URI() {
      @Override
      String sanitize(String value) {
        if (value.isEmpty()) {
          return value;
        }
        Matcher matcher = PERMISSIBLE_URI_PREFIX_PATTERN.matcher(value);
        if (matcher.find()) {
          String prefix = matcher.group(1);
          String rest = value.substring(matcher.end());
          value = prefix + Sanitizers.filterNormalizeUri(rest);
        } else {
          value = Sanitizers.filterNormalizeUri(value);
        }
        return value;
      }
    },

    STRING() {
      @Override
      String sanitize(String value) {
        return value;
      }
    };

    /**
     * Sanitizes the given string value for this content type.
     */
    abstract String sanitize(String value);
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
