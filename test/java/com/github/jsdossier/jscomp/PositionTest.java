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

package com.github.jsdossier.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
//import com.google.common.testing.EqualsTester;
import com.google.javascript.rhino.SourcePosition;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link Position}.
 */
@RunWith(JUnit4.class)
public class PositionTest {

  private static final SourcePosition<Void> sourcePosition1 = new SourcePosition<Void>() {};
  private static final SourcePosition<Void> sourcePosition2 = new SourcePosition<Void>() {};
  private static final SourcePosition<Void> sourcePosition3 = new SourcePosition<Void>() {};
  private static final SourcePosition<Void> sourcePosition4 = new SourcePosition<Void>() {};
  private static final SourcePosition<Void> sourcePosition5 = new SourcePosition<Void>() {};

  private static final Position a, b, c, d, e, f, g, h;

  static {
    sourcePosition1.setPositionInformation(1, 2, 1, 10);
    sourcePosition2.setPositionInformation(1, 10, 2, 10);
    sourcePosition3.setPositionInformation(1, 2, 2, 10);
    sourcePosition4.setPositionInformation(2, 2, 2, 10);
    sourcePosition5.setPositionInformation(1, 0, 5, 10);

    a = Position.fromStart(sourcePosition1);
    b = Position.fromEnd(sourcePosition1);
    c = Position.fromStart(sourcePosition2);
    d = Position.fromEnd(sourcePosition2);
    e = Position.fromStart(sourcePosition3);
    f = Position.fromEnd(sourcePosition3);
    g = Position.fromStart(sourcePosition4);
    h = Position.fromEnd(sourcePosition4);
  }

  @Test
  public void ordering() {
    assertThat(Ordering.natural().sortedCopy(ImmutableList.of(a, b, c, d, e, f, g, h)))
        .containsExactly(a, e, b, c, g, d, f, h)
        .inOrder();
  }
}
