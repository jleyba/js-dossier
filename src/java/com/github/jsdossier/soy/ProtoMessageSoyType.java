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
import static com.google.template.soy.data.SanitizedContent.ContentKind.HTML;
import static com.google.template.soy.data.SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI;

import com.github.jsdossier.proto.Options;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.template.soy.base.SoyBackendKind;
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
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.StringType;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

class ProtoMessageSoyType implements SoyObjectType {

  private static final
  ImmutableMap<FieldDescriptor.JavaType, SoyType> JAVA_TO_PRIMITIVE_TYPES =
      ImmutableMap.<FieldDescriptor.JavaType, SoyType>builder()
          .put(FieldDescriptor.JavaType.BOOLEAN, BoolType.getInstance())
          .put(FieldDescriptor.JavaType.INT, IntType.getInstance())
          .put(FieldDescriptor.JavaType.LONG, IntType.getInstance())
          .put(FieldDescriptor.JavaType.FLOAT, FloatType.getInstance())
          .put(FieldDescriptor.JavaType.DOUBLE, FloatType.getInstance())
          .put(FieldDescriptor.JavaType.STRING, StringType.getInstance())
          .build();

  private static final ImmutableSet<FieldDescriptor.JavaType> CONVERTIBLE_TYPES =
      ImmutableSet.<FieldDescriptor.JavaType>builder()
          .addAll(JAVA_TO_PRIMITIVE_TYPES.keySet())
          .add(FieldDescriptor.JavaType.ENUM)
          .add(FieldDescriptor.JavaType.MESSAGE)
          .build();

  private static final Map<Descriptor, ProtoMessageSoyType> CACHE = new HashMap<>();

  private static final MessageTransformer<SoyValue> SOY_MESSAGE_TRANSFORMER =
      new MessageTransformer<SoyValue>() {
        @Override
        protected String computeFieldName(FieldDescriptor field) {
          return LOWER_UNDERSCORE.to(LOWER_CAMEL, field.getName());
        }

        @Override
        protected MessageTransformer.MapBuilder<SoyValue> newMapBuilder() {
          return new MessageTransformer.MapBuilder<SoyValue>() {
            SoyMapData map = new SoyMapData();

            @Override
            public void put(String key, SoyValue value) {
              map.put(key, value);
            }

            @Override
            public SoyValue build() {
              return map;
            }
          };
        }

        @Override
        protected MessageTransformer.ListBuilder<SoyValue> newListBuilder() {
          return new MessageTransformer.ListBuilder<SoyValue>() {
            SoyListData list = new SoyListData();

            @Override
            public void add(SoyValue value) {
              list.add(value);
            }

            @Override
            public SoyValue build() {
              return list;
            }
          };
        }

        @Override
        protected SoyValue transform(EnumValueDescriptor value) {
          return ProtoEnumSoyValue.get(value);
        }

        @Override
        protected SoyValue transform(@Nullable Number value) {
          if (value == null) {
            return IntegerData.forValue(0);
          } else if (value instanceof Float || value instanceof Double) {
            return FloatData.forValue(value.doubleValue());
          } else {
            return IntegerData.forValue(value.longValue());
          }
        }

        @Override
        protected SoyValue transform(@Nullable Boolean value) {
          return value == null ? BooleanData.forValue(false) : BooleanData.forValue(value);
        }

        @Override
        protected SoyValue transform(@Nullable String value) {
          return StringData.forValue(nullToEmpty(value));
        }

        @Override
        protected SoyValue htmlValue(String s) {
          return UnsafeSanitizedContentOrdainer.ordainAsSafe(s, HTML);
        }

        @Override
        protected SoyValue uriValue(String s) {
          return UnsafeSanitizedContentOrdainer.ordainAsSafe(s, TRUSTED_RESOURCE_URI);
        }

        @Override
        protected SoyValue nullValue() {
          return NullData.INSTANCE;
        }
      };

  private final Descriptor descriptor;
  private final ImmutableMap<String, FieldDescriptor> fieldDescriptors;
  private final ImmutableSet<String> sanitizedHtmlFields;
  private final ImmutableSet<String> sanitizedUriFields;

  private ProtoMessageSoyType(Descriptor descriptor) {
    this.descriptor = descriptor;

    ImmutableMap.Builder<String, FieldDescriptor> descBuilder = ImmutableMap.builder();
    ImmutableSet.Builder<String> htmlFieldsBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<String> uriFieldsBuilder = ImmutableSet.builder();

    for (FieldDescriptor field : descriptor.getFields()) {
      if (CONVERTIBLE_TYPES.contains(field.getJavaType())) {
        String name = LOWER_UNDERSCORE.to(LOWER_CAMEL, field.getName());
        descBuilder.put(name, field);
      }

      if (field.getOptions().hasExtension(Options.sanitized)) {
        if (field.getOptions().getExtension(Options.sanitized).getHtml()) {
          htmlFieldsBuilder.add(field.getName());
        }

        if (field.getOptions().getExtension(Options.sanitized).getUri()) {
          uriFieldsBuilder.add(field.getName());
        }
      }
    }

    fieldDescriptors = descBuilder.build();
    sanitizedHtmlFields = htmlFieldsBuilder.build();
    sanitizedUriFields = uriFieldsBuilder.build();
  }

  private static SoyType toSoyType(FieldDescriptor field) {
    checkArgument(CONVERTIBLE_TYPES.contains(field.getJavaType()),
        "unexpected type: %s", field.getJavaType());

    SoyType fieldType;
    if (field.getJavaType() == FieldDescriptor.JavaType.ENUM) {
      fieldType = ProtoEnumSoyType.get(field.getEnumType());
    } else if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
      fieldType = ProtoMessageSoyType.get(field.getMessageType());
    } else {
      fieldType = JAVA_TO_PRIMITIVE_TYPES.get(field.getJavaType());
    }

    if (fieldType == null) {
      throw new AssertionError("failed to convert " + field);
    }

    if (field.isRepeated()) {
      fieldType = ListType.of(fieldType);
    }
    return fieldType;
  }

  static ProtoMessageSoyType get(Descriptor descriptor) {
    if (!CACHE.containsKey(descriptor)) {
      CACHE.put(descriptor, new ProtoMessageSoyType(descriptor));
    }
    return CACHE.get(descriptor);
  }

  static SoyValue toSoyValue(Message message) {
    return SOY_MESSAGE_TRANSFORMER.transform(message);
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
    return Kind.OBJECT;
  }

  @Override
  public boolean isAssignableFrom(SoyType soyType) {
    return soyType instanceof NullType || this.equals(soyType);
  }

  @Override
  public boolean isInstance(SoyValue soyValue) {
    if (soyValue instanceof SoyRecord) {
      SoyRecord record = (SoyRecord) soyValue;
      for (String key : fieldDescriptors.keySet()) {
        if (record.hasField(key)) {
          SoyValue item = record.getField(key);
          if (NullType.getInstance().isInstance(item)
              && fieldDescriptors.get(key).isOptional()) {
            continue;
          }

          SoyType fieldType = getFieldType(key);
          if (fieldType == null || !fieldType.isInstance(item)) {
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
    FieldDescriptor desc = fieldDescriptors.get(fieldName);
    return desc == null ? null : toSoyType(desc);
  }

  @Override
  public ImmutableSet<String> getFieldNames() {
    return fieldDescriptors.keySet();
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

  public boolean isSanitizedHtml(String fieldName) {
    return sanitizedHtmlFields.contains(fieldName);
  }

  public boolean isSanitizedUri(String uri) {
    return sanitizedUriFields.contains(uri);
  }
}
