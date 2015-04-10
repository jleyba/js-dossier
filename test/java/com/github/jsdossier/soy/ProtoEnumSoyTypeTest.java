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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.template.soy.data.restricted.BooleanData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtoEnumSoyTypeTest {

  @Test
  public void testGetName() {
    assertEquals("test.Color",
        ProtoEnumSoyType.get(TestProto.Color.getDescriptor()).getName());
    assertEquals("test.Color",
        ProtoEnumSoyType.get(TestProto.Color.getDescriptor())
            .getNameForBackend(null));

    assertEquals("test.Color",
        ProtoEnumSoyType.get(TestProto.Order.getDescriptor()
            .findFieldByName("color")
            .getEnumType())
            .getName());
    assertEquals("test.Color",
        ProtoEnumSoyType.get(TestProto.Order.getDescriptor()
            .findFieldByName("color")
            .getEnumType())
            .getNameForBackend(null));
  }

  @Test
  public void testGetName_nestedEnum() {
    assertEquals("test.Top.NestedEnum",
        ProtoEnumSoyType.get(TestProto.Top.NestedEnum.getDescriptor()).getName());
    assertEquals("test.Top.NestedEnum",
        ProtoEnumSoyType.get(TestProto.Top.NestedEnum.getDescriptor()).getNameForBackend(null));
  }

  @Test
  public void testEquality() {
    ProtoEnumSoyType a = ProtoEnumSoyType.get(TestProto.Color.getDescriptor());
    ProtoEnumSoyType b = ProtoEnumSoyType.get(TestProto.Color.getDescriptor());

    assertEquals(a, a);
    assertEquals(a.hashCode(), a.hashCode());

    assertEquals(a, b);
    assertEquals(b, a);
    assertEquals(a.hashCode(), b.hashCode());

    ProtoEnumSoyType c = ProtoEnumSoyType.get(TestProto.Fruit.getDescriptor());
    assertNotEquals(a, c);
    assertNotEquals(c, a);

    ProtoEnumSoyType d = ProtoEnumSoyType.get(
        TestProto.Order.getDescriptor()
            .findFieldByName("color")
            .getEnumType());
    assertEquals(a, d);
    assertEquals(d, a);
    assertEquals(a.hashCode(), d.hashCode());

    ProtoEnumSoyType e = ProtoEnumSoyType.get(
        TestProto.Order.getDescriptor()
            .findFieldByName("fruit")
            .getEnumType());
    assertEquals(c, e);
    assertEquals(e, c);
    assertEquals(c.hashCode(), e.hashCode());
    assertNotEquals(a, e);
  }

  @Test
  public void testGetValue() {
    ProtoEnumSoyType type = ProtoEnumSoyType.get(TestProto.Color.getDescriptor());
    assertNull(type.getValue("unknown value"));
    assertEquals(Integer.valueOf(TestProto.Color.BLUE_VALUE), type.getValue("BLUE"));
  }

  @Test
  public void testIsInstance() {
    ProtoEnumSoyValue red = ProtoEnumSoyValue.get(TestProto.Color.RED);
    ProtoEnumSoyValue apple = ProtoEnumSoyValue.get(TestProto.Fruit.APPLE);

    ProtoEnumSoyType color = ProtoEnumSoyType.get(TestProto.Color.getDescriptor());
    assertTrue(color.isInstance(red));
    assertFalse(color.isInstance(apple));
    assertFalse(color.isInstance(BooleanData.TRUE));
  }
}
