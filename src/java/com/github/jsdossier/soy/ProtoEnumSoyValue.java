/*
 Copyright 2013-2015 Jason Leyba

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
