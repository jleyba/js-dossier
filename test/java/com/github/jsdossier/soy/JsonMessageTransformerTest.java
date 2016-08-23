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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link JsonMessageTransformer}.
 */
@RunWith(JUnit4.class)
public final class JsonMessageTransformerTest {

  private static final JsonMessageTransformer TRANSFORMER = new JsonMessageTransformer();

  @Test
  public void defaultMessageTransformsToEmptyObj  () {
    assertThat(transform(TestProto.Order.getDefaultInstance()))
        .isEqualTo(new JsonObject());
  }

  @Test
  public void enumsTransformedToNumbers() {
    TestProto.Order order = TestProto.Order.newBuilder()
        .setFruit(TestProto.Fruit.APPLE)
        .build();

    JsonObject obj = json(
        String.valueOf(TestProto.Order.FRUIT_FIELD_NUMBER),
        new JsonPrimitive(TestProto.Fruit.APPLE.getNumber()));

    assertThat(transform(order)).isEqualTo(obj);
  }

  @Test
  public void omitsZeroEnums() {
    TestProto.Order order = TestProto.Order.newBuilder()
        .setStringField("hi")
        .setFruit(TestProto.Fruit.UNKNOWN)
        .build();

    JsonObject obj = json(
        String.valueOf(TestProto.Order.STRING_FIELD_FIELD_NUMBER),
        new JsonPrimitive("hi"));

    assertThat(transform(order)).isEqualTo(obj);
  }

  @Test
  public void omitsZero() {
    TestProto.Order order = TestProto.Order.newBuilder()
        .setStringField("hi")
        .setIntField(0)
        .build();

    JsonObject obj = json(
        String.valueOf(TestProto.Order.STRING_FIELD_FIELD_NUMBER),
        new JsonPrimitive("hi"));
    assertThat(transform(order)).isEqualTo(obj);

    order = order.toBuilder().setIntField(123).build();
    obj.addProperty(String.valueOf(TestProto.Order.INT_FIELD_FIELD_NUMBER), 123);
    assertThat(transform(order)).isEqualTo(obj);
  }

  @Test
  public void omitsEmptyStrings() {
    TestProto.Order order = TestProto.Order.newBuilder()
        .setStringField("")
        .setIntField(123)
        .build();

    JsonObject obj = json(
        String.valueOf(TestProto.Order.INT_FIELD_FIELD_NUMBER),
        new JsonPrimitive(123));
    assertThat(transform(order)).isEqualTo(obj);
  }

  @Test
  public void omitsFalse() {
    TestProto.Order order = TestProto.Order.newBuilder()
        .setBoolField(false)
        .setIntField(123)
        .build();

    JsonObject obj = json(
        String.valueOf(TestProto.Order.INT_FIELD_FIELD_NUMBER),
        new JsonPrimitive(123));
    assertThat(transform(order)).isEqualTo(obj);

    order = order.toBuilder().setBoolField(true).build();
    obj.addProperty(String.valueOf(TestProto.Order.BOOL_FIELD_FIELD_NUMBER), true);
    assertThat(transform(order)).isEqualTo(obj);
  }

  @Test
  public void repeatedPrimitive() {
    TestProto.Order order = TestProto.Order.newBuilder()
        .setIntField(123)
        .addRepeatedInt(45)
        .addRepeatedInt(23)
        .build();

    JsonArray arr = new JsonArray();
    arr.add(45);
    arr.add(23);

    JsonObject obj = json(
        String.valueOf(TestProto.Order.INT_FIELD_FIELD_NUMBER),
        new JsonPrimitive(123),
        String.valueOf(TestProto.Order.REPEATED_INT_FIELD_NUMBER),
        arr);
    assertThat(transform(order)).isEqualTo(obj);
  }

  @Test
  public void doesNotOmitZeroInList() {
    TestProto.Order order = TestProto.Order.newBuilder()
        .setIntField(123)
        .addRepeatedInt(45)
        .addRepeatedInt(0)
        .addRepeatedInt(23)
        .build();

    JsonArray arr = new JsonArray();
    arr.add(45);
    arr.add(0);
    arr.add(23);

    JsonObject obj = json(
        String.valueOf(TestProto.Order.INT_FIELD_FIELD_NUMBER),
        new JsonPrimitive(123),
        String.valueOf(TestProto.Order.REPEATED_INT_FIELD_NUMBER),
        arr);
    assertThat(transform(order)).isEqualTo(obj);
  }

  @Test
  public void omitsEmptyNestedMessage() {
    TestProto.Top top = TestProto.Top.newBuilder()
        .setNestedMessage(TestProto.Top.NestedMessage.getDefaultInstance())
        .build();
    assertThat(transform(top)).isEqualTo(new JsonObject());
  }

  @Test
  public void nestedMessage() {
    TestProto.Top top = TestProto.Top.newBuilder()
        .setNestedMessage(TestProto.Top.NestedMessage.newBuilder().setItem(true))
        .build();

    JsonObject nested = new JsonObject();
    nested.addProperty(String.valueOf(TestProto.Top.NestedMessage.ITEM_FIELD_NUMBER), true);

    JsonObject obj = new JsonObject();
    obj.add(String.valueOf(TestProto.Top.NESTED_MESSAGE_FIELD_NUMBER), nested);

    assertThat(transform(top)).isEqualTo(obj);
  }

  @Test
  public void doesNotOmitEmptyMessageInList() {
    TestProto.Top top = TestProto.Top.newBuilder()
        .addOrder(TestProto.Order.getDefaultInstance())
        .addOrder(TestProto.Order.getDefaultInstance())
        .build();

    JsonArray arr = new JsonArray();
    arr.add(new JsonObject());
    arr.add(new JsonObject());

    JsonObject obj = new JsonObject();
    obj.add(String.valueOf(TestProto.Top.ORDER_FIELD_NUMBER), arr);

    assertThat(transform(top)).isEqualTo(obj);
  }

  private static JsonObject json(Object... data) {
    checkArgument(data.length % 2 == 0);
    JsonObject obj = new JsonObject();
    for (int i = 0; i < data.length; i += 2) {
      String key = (String) data[i];
      JsonElement value = (JsonElement) data[i + 1];
      obj.add(key, value);
    }
    return obj;
  }

  private static JsonElement transform(Message m) {
    return TRANSFORMER.transform(m);
  }
}
