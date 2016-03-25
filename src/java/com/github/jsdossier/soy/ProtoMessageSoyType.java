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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Iterables.transform;
import static com.google.template.soy.data.SanitizedContent.ContentKind.HTML;
import static com.google.template.soy.data.SanitizedContent.ContentKind.URI;

import com.github.jsdossier.proto.Dossier;
import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessage;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.shared.restricted.Sanitizers;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.StringType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class ProtoMessageSoyType implements SoyObjectType {

  private static final Pattern PERMISSIBLE_URI_PREFIX_PATTERN =
      Pattern.compile("^((?:\\.{1,2}/)+)");

  private static final
  ImmutableMap<FieldDescriptor.JavaType, ? extends SoyType> JAVA_TO_PRIMITIVE_TYPES =
      ImmutableMap.of(
          FieldDescriptor.JavaType.BOOLEAN, BoolType.getInstance(),
          FieldDescriptor.JavaType.INT, IntType.getInstance(),
          FieldDescriptor.JavaType.STRING, StringType.getInstance());

  private static final LoadingCache<Descriptor, ProtoMessageSoyType> CACHE =
      CacheBuilder.newBuilder()
          .build(new CacheLoader<Descriptor, ProtoMessageSoyType>() {
            @Override
            public ProtoMessageSoyType load(@Nonnull Descriptor key) throws Exception {
              return new ProtoMessageSoyType(key);
            }
          });

  private final Descriptor descriptor;

  private final ImmutableMap<String, SoyType> fieldTypes;
  private final ImmutableMap<String, FieldDescriptor> fieldDescriptors;

  private ProtoMessageSoyType(Descriptor descriptor) {
    this.descriptor = descriptor;

    ImmutableMap.Builder<String, SoyType> typeBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<String, FieldDescriptor> descBuilder = ImmutableMap.builder();
    for (FieldDescriptor field : descriptor.getFields()) {
      SoyType fieldType;

      if (field.getJavaType() == FieldDescriptor.JavaType.ENUM) {
        fieldType = ProtoEnumSoyType.get(field.getEnumType());
      } else if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
        fieldType = ProtoMessageSoyType.get(field.getMessageType());
      } else {
        fieldType = JAVA_TO_PRIMITIVE_TYPES.get(field.getJavaType());
      }

      if (fieldType != null) {
        if (field.isRepeated()) {
          fieldType = ListType.of(fieldType);
        }
        String name = LOWER_UNDERSCORE.to(LOWER_CAMEL, field.getName());
        typeBuilder.put(name, fieldType);
        descBuilder.put(name, field);
      }
    }
    fieldTypes = typeBuilder.build();
    fieldDescriptors = descBuilder.build();
  }

  static ProtoMessageSoyType get(Descriptor descriptor) {
    try {
      return CACHE.get(descriptor);
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
  }

  static SoyValue toSoyValue(GeneratedMessage message) {
    ProtoMessageSoyType type = ProtoMessageSoyType.get(message.getDescriptorForType());

    Map<String, Object> data = Maps.newHashMapWithExpectedSize(
        type.fieldDescriptors.size());
    for (Map.Entry<String, FieldDescriptor> entry : type.fieldDescriptors.entrySet()) {
      data.put(entry.getKey(), toSoyValue(entry.getValue(), message));
    }
    return new SoyMapData(data);
  }

  private static SoyValue toSoyValue(FieldDescriptor field, GeneratedMessage message) {
    Object fieldValue = message.getField(field);
    switch (field.getJavaType()) {
      case ENUM: {
        if (field.isRepeated()) {
          @SuppressWarnings("unchecked")
          List<EnumValueDescriptor> values = (List<EnumValueDescriptor>) fieldValue;
          return toSoyValue(values, new Function<EnumValueDescriptor, SoyValue>() {
              @Nullable
              @Override
              public SoyValue apply(@Nullable EnumValueDescriptor input) {
                return input == null ? NullData.INSTANCE : ProtoEnumSoyValue.get(input);
              }
            });
        }

        @SuppressWarnings("unchecked")
        EnumValueDescriptor value = (EnumValueDescriptor) fieldValue;
        return ProtoEnumSoyValue.get(value);
      }

      case MESSAGE: {
        if (field.isRepeated()) {
          @SuppressWarnings("unchecked")
          List<GeneratedMessage> messages = (List<GeneratedMessage>) fieldValue;
          return toSoyValue(messages);
        }

        if (!message.hasField(field)) {
          return NullData.INSTANCE;
        }

        @SuppressWarnings("unchecked")
        GeneratedMessage value = (GeneratedMessage) fieldValue;
        return toSoyValue(value);
      }

      case INT: {
        if (field.isRepeated()) {
          @SuppressWarnings("unchecked")
          List<Number> values = (List<Number>) fieldValue;
          return toSoyValue(values, new Function<Number, SoyValue>() {
            @Nullable
            @Override
            public SoyValue apply(@Nullable Number input) {
              return toSoyValue(input);
            }
          });
        }
        @SuppressWarnings("unchecked")
        Number value = (Number) fieldValue;
        return toSoyValue(value);
      }

      case STRING: {
        if (field.getOptions().hasExtension(Dossier.sanitized)) {
          return toSanitizedContent(field, fieldValue);
        }
        if (field.isRepeated()) {
          @SuppressWarnings("unchecked")
          List<String> values = (List<String>) fieldValue;
          return toSoyValue(values, new Function<String, SoyValue>() {
            @Nullable
            @Override
            public SoyValue apply(@Nullable String input) {
              return toSoyValue(input);
            }
          });
        }
        @SuppressWarnings("unchecked")
        String value = (String) fieldValue;
        return toSoyValue(value);
      }

      case BOOLEAN: {
        if (field.isRepeated()) {
          @SuppressWarnings("unchecked")
          List<Boolean> values = (List<Boolean>) fieldValue;
          return toSoyValue(values, new Function<Boolean, SoyValue>() {
            @Nullable
            @Override
            public SoyValue apply(@Nullable Boolean input) {
              return toSoyValue(input);
            }
          });
        }
        @SuppressWarnings("unchecked")
        Boolean value = (Boolean) fieldValue;
        return toSoyValue(value);
      }

      default:
        throw new UnsupportedOperationException(
            "Cannot convert type for field " + field.getFullName());
    }
  }

  private static SoyValue toSoyValue(@Nullable Number value) {
    if (value == null) {
      return IntegerData.forValue(0);
    } else if (value instanceof Float || value instanceof Double) {
      return FloatData.forValue(value.doubleValue());
    } else {
      return IntegerData.forValue(value.longValue());
    }
  }

  private static SoyValue toSoyValue(@Nullable String value) {
    return StringData.forValue(nullToEmpty(value));
  }

  private static SoyValue toSoyValue(@Nullable Boolean value) {
    return value == null ? BooleanData.forValue(false) : BooleanData.forValue(value);
  }

  private static SoyValue toSanitizedContent(FieldDescriptor field, Object fieldValue) {
    checkArgument(field.getOptions().hasExtension(Dossier.sanitized));

    com.github.jsdossier.proto.SanitizedContent sc =
        field.getOptions().getExtension(Dossier.sanitized);
    if (sc.getHtml()) {
      return toSanitizedContent(field, fieldValue, HTML);
    } else if (sc.getUri()) {
      if (fieldValue instanceof String) {
        return toSanitizedUri((String) fieldValue);
      } else if (field.isRepeated()) {
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) fieldValue;
        return toSanitizedUri(values);
      }

      throw new IllegalArgumentException(
          "sanitized URI fields must be strings: " + field.getName());
    } else {
      throw new IllegalArgumentException();
    }
  }

  private static SoyListData toSanitizedUri(List<String> uris) {
    return new SoyListData(transform(uris, new Function<String, SoyValue>() {
      @Override
      public SoyValue apply(String input) {
        return toSanitizedUri(input);
      }
    }));
  }

  private static SoyValue toSanitizedUri(String uri) {
    Matcher matcher = PERMISSIBLE_URI_PREFIX_PATTERN.matcher(uri);
    if (matcher.find()) {
      String prefix = matcher.group(1);
      String rest = uri.substring(matcher.end());
      uri = prefix + Sanitizers.filterNormalizeUri(rest);
    } else {
      uri = Sanitizers.filterNormalizeUri(uri);
    }
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(uri, URI);
  }

  private static SoyValue toSanitizedContent(
      FieldDescriptor field, Object fieldValue, final SanitizedContent.ContentKind kind) {
    if (field.isRepeated()) {
      @SuppressWarnings("unchecked")
      List<String> values = (List<String>) fieldValue;
      return toSoyValue(values, new Function<String, SoyValue>() {
        @Nullable
        @Override
        public SoyValue apply(@Nullable String input) {
          if (input == null) {
            return NullData.INSTANCE;
          }
          if (kind == HTML) {
            input = HtmlSanitizer.sanitize(input);
          }
          return UnsafeSanitizedContentOrdainer.ordainAsSafe(input, kind);
        }
      });
    }
    @SuppressWarnings("unchecked")
    String value = (String) fieldValue;
    if (kind == HTML) {
      value = HtmlSanitizer.sanitize(value);
    }
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(value, kind);
  }

  private static <T> SoyValue toSoyValue(Iterable<T> values, Function<T, SoyValue> fn) {
    return new SoyListData(transform(values, fn));
  }

  static <T extends GeneratedMessage> SoyValue toSoyValue(Iterable<T> messages) {
    return toSoyValue(messages, new Function<T, SoyValue>() {
      @Nullable
      @Override
      public SoyValue apply(@Nullable T input) {
        return input == null ? NullData.INSTANCE : toSoyValue(input);
      }
    });
  }

  @Override
  public int hashCode() {
    return descriptor.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ProtoMessageSoyType) {
      ProtoMessageSoyType that = (ProtoMessageSoyType) obj;
      return this.descriptor.equals(that.descriptor);
    }
    return false;
  }

  @Override
  public String toString() {
    return this.getName();
  }

  @Override
  public Kind getKind() {
    return Kind.RECORD;
  }

  @Override
  public boolean isAssignableFrom(SoyType soyType) {
    return soyType instanceof NullType || this.equals(soyType);
  }

  @Override
  public boolean isInstance(SoyValue soyValue) {
    if (soyValue instanceof SoyRecord) {
      SoyRecord record = (SoyRecord) soyValue;
      for (String key : fieldTypes.keySet()) {
        if (record.hasField(key)) {
          SoyValue item = record.getField(key);
          if (NullType.getInstance().isInstance(item)
              && fieldDescriptors.get(key).isOptional()) {
            continue;
          }

          if (!fieldTypes .get(key).isInstance(item)) {
            return false;
          }
        } else if (!fieldDescriptors.get(key).isOptional()) {
          return false;
        }
      }
      return true;
    }

    return soyValue instanceof NullData;
  }

  @Override
  public String getName() {
    return descriptor.getFullName();
  }

  @Override
  public String getNameForBackend(SoyBackendKind soyBackendKind) {
    return descriptor.getFullName();
  }

  @Override
  public SoyType getFieldType(String fieldName) {
    return fieldTypes.get(fieldName);
  }

  @Override
  public ImmutableSet<String> getFieldNames() {
    return fieldTypes.keySet();
  }

  @Override
  public String getFieldAccessExpr(
      String fieldContainerExpr, String fieldName, SoyBackendKind backend) {
    if (backend == SoyBackendKind.JS_SRC) {
      return fieldContainerExpr + "." + fieldName;
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public ImmutableSet<String> getFieldAccessImports(String fieldName, SoyBackendKind backend) {
    return ImmutableSet.of();
  }

  @Override
  public Class<? extends SoyValue> javaType() {
    return SoyRecord.class;
  }
}
