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

package com.github.jsdossier.markdown;

import static com.github.jsdossier.markdown.Alignment.CENTER;
import static com.github.jsdossier.markdown.Alignment.LEFT;
import static com.github.jsdossier.markdown.Alignment.NONE;
import static com.github.jsdossier.markdown.Alignment.RIGHT;
import static com.github.jsdossier.markdown.TableBlockParser.parseHeaderDivider;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.IterableSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TableBlockParser}.
 */
@RunWith(JUnit4.class)
public class TableBlockParserTest {

  @Test
  public void singleColumnHeaderDivider() {
    assertHeader("-|").containsExactly(NONE);
    assertHeader(" -|").containsExactly(NONE);
    assertHeader("  -|").containsExactly(NONE);
    assertHeader("   -|").containsExactly(NONE);
    assertHeader("   -|      ").containsExactly(NONE);
    assertHeader("    -|").isEmpty();

    assertHeader("|-").containsExactly(NONE);
    assertHeader(" |-").containsExactly(NONE);
    assertHeader("  |-").containsExactly(NONE);
    assertHeader("   |-").containsExactly(NONE);
    assertHeader("   |-      ").containsExactly(NONE);
    assertHeader("    |-").isEmpty();

    assertHeader(":-|").containsExactly(LEFT);
    assertHeader(":-:|").containsExactly(CENTER);
    assertHeader("-:|").containsExactly(RIGHT);

    assertHeader("|:-").containsExactly(LEFT);
    assertHeader("|:-:").containsExactly(CENTER);
    assertHeader("|-:").containsExactly(RIGHT);

    assertHeader("|-|").containsExactly(NONE);
    assertHeader("| - |").containsExactly(NONE);
    assertHeader("|   -  |").containsExactly(NONE);
    assertHeader("|   -  |    ").containsExactly(NONE);

    assertHeader("|-----|").containsExactly(NONE);
    assertHeader("|-----|   ").containsExactly(NONE);

    assertHeader("|:-|").containsExactly(LEFT);
    assertHeader("|:-----|").containsExactly(LEFT);
    assertHeader("|:-----  |").containsExactly(LEFT);
    assertHeader("|   :-----  |").containsExactly(LEFT);

    assertHeader("|:-:|").containsExactly(CENTER);
    assertHeader("|:------:|").containsExactly(CENTER);
    assertHeader("|   :------:|").containsExactly(CENTER);
    assertHeader("|   :------:    |").containsExactly(CENTER);

    assertHeader("|-:|").containsExactly(RIGHT);
    assertHeader("|    -:|").containsExactly(RIGHT);
    assertHeader("|-:     |").containsExactly(RIGHT);
    assertHeader("|    -:     |").containsExactly(RIGHT);
    assertHeader("|----:|").containsExactly(RIGHT);
    assertHeader("|----:   |").containsExactly(RIGHT);
    assertHeader("|   ----:   |").containsExactly(RIGHT);
    assertHeader("|   ----:|").containsExactly(RIGHT);

    assertHeader("|------:---|").isEmpty();
    assertHeader("|:------:---:|").isEmpty();

    assertHeader("|:-:|").containsExactly(CENTER);
    assertHeader(" |:-:|").containsExactly(CENTER);
    assertHeader("  |:-:|").containsExactly(CENTER);
    assertHeader("   |:-:|").containsExactly(CENTER);
    assertHeader("    |:-:|").isEmpty();
  }

  @Test
  public void twoColumnHeader() {
    assertHeader("-|-").containsExactly(NONE, NONE);
    assertHeader("-|-|").containsExactly(NONE, NONE);
    assertHeader("|-|-|").containsExactly(NONE, NONE);
    assertHeader("|-|-").containsExactly(NONE, NONE);

    assertHeader(":-|-:").containsExactly(LEFT, RIGHT).inOrder();
    assertHeader(":-  |  -:").containsExactly(LEFT, RIGHT).inOrder();
    assertHeader(":-  |  :-:").containsExactly(LEFT, CENTER).inOrder();

    assertHeader(" -|-").containsExactly(NONE, NONE);
    assertHeader("  -|-").containsExactly(NONE, NONE);
    assertHeader("   -|-").containsExactly(NONE, NONE);
    assertHeader("    -|-").isEmpty();

    assertHeader(" |-|-").containsExactly(NONE, NONE);
    assertHeader("  |-|-").containsExactly(NONE, NONE);
    assertHeader("   |-|-").containsExactly(NONE, NONE);
    assertHeader("   |    -|-").containsExactly(NONE, NONE);
  }

  @Test
  public void threeColumnHeader() {
    assertHeader(":-|:-:|-:").containsExactly(LEFT, CENTER, RIGHT).inOrder();
    assertHeader("  :-     |   :-:     |    -:    ").containsExactly(LEFT, CENTER, RIGHT).inOrder();
    assertHeader(":-----|:------:|------:").containsExactly(LEFT, CENTER, RIGHT).inOrder();
    assertHeader("|:-|:-:|-:|")
        .containsExactly(LEFT, CENTER, RIGHT)
        .inOrder();
    assertHeader(" |:-|:-:|-:|")
        .containsExactly(LEFT, CENTER, RIGHT)
        .inOrder();
    assertHeader("  |:-|:-:|-:|")
        .containsExactly(LEFT, CENTER, RIGHT)
        .inOrder();
    assertHeader("   |:-|:-:|-:|")
        .containsExactly(LEFT, CENTER, RIGHT)
        .inOrder();
    assertHeader("   |      :-|:-:|-:|")
        .containsExactly(LEFT, CENTER, RIGHT)
        .inOrder();
    assertHeader("    |:-|:-:|-:|").isEmpty();
  }

  private static IterableSubject assertHeader(String str) {
    return assertThat(parseHeaderDivider(str));
  }
}
