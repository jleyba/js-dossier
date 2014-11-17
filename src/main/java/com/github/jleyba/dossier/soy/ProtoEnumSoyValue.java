package com.github.jleyba.dossier.soy;

import com.google.protobuf.Descriptors;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;

class ProtoEnumSoyValue extends SoyData implements SoyValue {

  private final Descriptors.EnumValueDescriptor value;

  private ProtoEnumSoyValue(Descriptors.EnumValueDescriptor value) {
    this.value = value;
  }

  static ProtoEnumSoyValue get(Descriptors.EnumValueDescriptor value) {
    return new ProtoEnumSoyValue(value);
  }

  static ProtoEnumSoyValue get(ProtocolMessageEnum message) {
    return new ProtoEnumSoyValue(message.getValueDescriptor());
  }

  Descriptors.EnumValueDescriptor getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ProtoEnumSoyValue) {
      ProtoEnumSoyValue that = (ProtoEnumSoyValue) o;
      return this.value.equals(that.value);
    }
    return false;
  }

  @Override
  public String toString() {
    return value.getName();
  }

  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public boolean toBoolean() {
    return false;
  }

  @Override
  public String coerceToString() {
    return value.getName();
  }

  @Override
  public boolean booleanValue() {
    return coerceToBoolean();
  }

  @Override
  public int integerValue() {
    return value.getNumber();
  }

  @Override
  public long longValue() {
    return value.getNumber();
  }

  @Override
  public double floatValue() {
    return value.getNumber();
  }

  @Override
  public double numberValue() {
    return value.getNumber();
  }

  @Override
  public String stringValue() {
    return coerceToString();
  }

  @Override
  public SoyValue resolve() {
    return this;
  }

  @Override
  public boolean equals(SoyValueProvider soyValueProvider) {
    return false;
  }
}
