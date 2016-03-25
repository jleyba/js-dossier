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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;

import com.github.jsdossier.soy.TestProto.Fruit;
import com.github.jsdossier.soy.TestProto.Order;
import com.github.jsdossier.soy.TestProto.Top;
import com.github.jsdossier.soy.TestProto.Top.NestedMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class ProtoMessageSoyTypeRenderTest {

  private final SoyTofu tofu = SoyFileSet.builder()
      .add(ProtoMessageSoyTypeRenderTest.class.getResource("sample.soy"))
      .setLocalTypeRegistry(
          new SoyTypeRegistry(ImmutableSet.<SoyTypeProvider>of(new TestSoyTypeProvider())))
      .build()
      .compileToTofu();

  @Test
  public void renderOptionalBool_notSet() {
    String got = render("sample.optionalBool", new SoyMapData(
        "order", toSoy(Order.newBuilder().build())));
    assertThat(got).isEqualTo("false");
  }

  @Test
  public void renderOptionalBool_set() {
    String got = render("sample.optionalBool", new SoyMapData(
        "order", toSoy(Order.newBuilder().setBoolField(true).build())));
    assertThat(got).isEqualTo("true");
  }

  @Test
  public void renderOptionalInt_notSet() {
    String got = render("sample.optionalInt", new SoyMapData(
        "order", toSoy(Order.newBuilder().build())));
    assertThat(got).isEqualTo("");
  }

  @Test
  public void renderOptionalInt_set() {
    String got = render("sample.optionalInt", new SoyMapData(
        "order", toSoy(Order.newBuilder().setIntField(1234).build())));
    assertThat(got).isEqualTo("1234");
  }

  @Test
  public void renderOptionalString_notSet() {
    String got = render("sample.optionalString", new SoyMapData(
        "order", toSoy(Order.newBuilder().build())));
    assertThat(got).isEqualTo("");
  }

  @Test
  public void renderOptionalString_set() {
    String got = render("sample.optionalString", new SoyMapData(
        "order", toSoy(Order.newBuilder().setStringField("hello!").build())));
    assertThat(got).isEqualTo("hello!");
  }

  @Test
  public void renderOptionalEnum_notSet() {
    String got = render("sample.optionalEnum", new SoyMapData(
        "order", toSoy(Order.newBuilder().build())));
    assertWithMessage("should default to enum entry 0").that(got).isEqualTo("UNKNOWN");
  }

  @Test
  public void renderOptionalEnum_set() {
    String got = render("sample.optionalEnum", new SoyMapData(
        "order", toSoy(Order.newBuilder().setFruit(Fruit.APPLE).build())));
    assertThat(got).isEqualTo("APPLE");
  }

  @Test
  public void sanitizedHtmlField() {
    String got = render("sample.sanitizedHtmlField", new SoyMapData(
        "order", toSoy(Order.newBuilder().setHtmlField("<div>Hi!</div>").build())));
    assertThat(got).isEqualTo("<div>Hi!</div>");
  }

  @Test
  public void renderMessageWithRepeatedSubMessage_noMessagesSet() {
    String got = render("sample.repeatedMessageField", new SoyMapData(
        "top", toSoy(Top.newBuilder().build())));
    assertThat(got).isEmpty();
  }

  @Test
  public void renderMessageWithRepeatedSubMessage_hasMessages() {
    String got = render("sample.repeatedMessageField", new SoyMapData(
        "top",
        toSoy(Top.newBuilder()
            .addOrder(Order.newBuilder().setBoolField(true))
            .addOrder(Order.newBuilder().setBoolField(false))
            .build())));
    assertThat(got).isEqualTo("truefalse");
  }

  @Test
  public void renderMessageWithOptionalSubMessage_notSet() {
    String got = render("sample.optionalMessageField", new SoyMapData(
        "top", toSoy(Top.newBuilder().build())));
    assertThat(got).isEmpty();
  }

  @Test
  public void renderMessageWithOptionalSubMessage_set() {
    String got = render("sample.optionalMessageField", new SoyMapData(
        "top",
        toSoy(Top.newBuilder()
            .setNestedMessage(NestedMessage.newBuilder())
            .build())));
    assertThat(got).isEqualTo("false");
  }

  private SoyValue toSoy(GeneratedMessage message) {
    return ProtoMessageSoyType.toSoyValue(message);
  }

  private String render(String templateName, SoyMapData data) {
    return tofu.newRenderer(templateName).setData(data).render();
  }

  private static final class TestSoyTypeProvider implements SoyTypeProvider {

    private final ImmutableMap<String, SoyType> types;

    TestSoyTypeProvider() {
      Map<String, SoyType> map = new HashMap<>();

      Descriptors.FileDescriptor fileDescriptor = TestProto.getDescriptor();

      for (Descriptors.EnumDescriptor enumDescriptor : fileDescriptor.getEnumTypes()) {
        ProtoEnumSoyType type = ProtoEnumSoyType.get(enumDescriptor);
        map.put(type.getName(), type);
      }

      for (Descriptors.Descriptor descriptor : fileDescriptor.getMessageTypes()) {
        registerTypes(map, descriptor);
      }

      types = ImmutableMap.copyOf(map);
    }

    private static void registerTypes(Map<String, SoyType> map, Descriptors.Descriptor descriptor) {
      ProtoMessageSoyType type = ProtoMessageSoyType.get(descriptor);
      map.put(type.getName(), type);

      for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
        if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
          registerTypes(map, field.getMessageType());
        }
      }
    }

    @Override
    public SoyType getType(String name, SoyTypeRegistry soyTypeRegistry) {
      return types.get(name);
    }
  }

}
