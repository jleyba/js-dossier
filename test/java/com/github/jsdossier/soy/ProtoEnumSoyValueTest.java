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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.template.soy.data.restricted.IntegerData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtoEnumSoyValueTest {

  private static final ProtoEnumSoyValue RED = ProtoEnumSoyValue.get(TestProto.Color.RED);
  private static final ProtoEnumSoyValue BLUE = ProtoEnumSoyValue.get(TestProto.Color.BLUE);

  @Test
  public void testCoerceToBoolean() {
    assertEquals(0, RED.integerValue());
    assertFalse(RED.booleanValue());
    assertFalse(RED.coerceToBoolean());

    assertTrue(BLUE.booleanValue());
    assertTrue(BLUE.coerceToBoolean());
  }

  @Test
  public void testCoerceToString() {
    assertEquals("RED", RED.coerceToString());
    assertEquals("RED", RED.stringValue());

    assertEquals("BLUE", BLUE.coerceToString());
    assertEquals("BLUE", BLUE.stringValue());
  }

  @Test
  public void testNumberConversion() {
    assertEquals(TestProto.Color.BLUE_VALUE, BLUE.integerValue());

    assertEquals(TestProto.Color.RED_VALUE, RED.integerValue());
    assertEquals(TestProto.Color.RED_VALUE, RED.longValue());
    assertEquals(TestProto.Color.RED_VALUE, RED.floatValue(), 0);
    assertEquals(TestProto.Color.RED_VALUE, RED.numberValue(), 0);
  }

  @Test
  public void testEquality() {
    assertEquals(RED, RED);
    assertEquals(RED.hashCode(), RED.hashCode());
    assertEquals(RED, IntegerData.forValue(RED.integerValue()));

    assertNotEquals(RED, BLUE);
  }
}
