package com.github.jleyba.dossier.soy;

import com.google.protobuf.Descriptors;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.SoyEnumType;
import com.google.template.soy.types.SoyType;

import javax.annotation.Nullable;

class ProtoEnumSoyType implements SoyEnumType {

  private final Descriptors.EnumDescriptor descriptor;

  private ProtoEnumSoyType(Descriptors.EnumDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  static ProtoEnumSoyType get(Descriptors.EnumDescriptor descriptor) {
    return new ProtoEnumSoyType(descriptor);
  }

  @Override
  public int hashCode() {
    return this.descriptor.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ProtoEnumSoyType) {
      ProtoEnumSoyType that = (ProtoEnumSoyType) o;
      return this.descriptor.equals(that.descriptor);
    }
    return false;
  }

  @Override
  public String toString() {
    return this.getName();
  }

  @Override
  public String getName() {
    return descriptor.getFullName();
  }

  @Override
  public String getNameForBackend(SoyBackendKind soyBackendKind) {
    return descriptor.getFullName();
  }

  @Nullable
  @Override
  public Integer getValue(String value) {
    Descriptors.EnumValueDescriptor ev = descriptor.findValueByName(value);
    if (ev == null) {
      return null;
    }
    return ev.getNumber();
  }

  @Override
  public Kind getKind() {
    return Kind.ENUM;
  }

  @Override
  public boolean isAssignableFrom(SoyType soyType) {
    return this.equals(soyType);
  }

  @Override
  public boolean isInstance(SoyValue soyValue) {
    if (soyValue instanceof ProtoEnumSoyValue) {
      ProtoEnumSoyValue v = (ProtoEnumSoyValue) soyValue;
      return this.descriptor.equals(v.getValue().getType());
    }
    return false;
  }
}