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

package com.github.jsdossier;

import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Function.Detail;
import com.github.jsdossier.proto.Property;
import com.github.jsdossier.proto.Tags;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for extracting property information with a {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorStaticPropertyTest extends AbstractTypeInspectorTest {

  @Test
  public void returnsInfoOnStaticProperties_constructor() {
    compile(
        "/** @constructor */",
        "function A() {}",
        "",
        "/**",
        " * A property.",
        " * @type {number}",
        " */",
        "A.b = 1234;");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectType(a);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("A.b")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(htmlComment("<p>A property.</p>\n")))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void returnsInfoOnStaticProperties_interface() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/**",
        " * A property.",
        " * @type {number}",
        " */",
        "A.b = 1234;");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectType(a);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("A.b")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(htmlComment("<p>A property.</p>\n")))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void returnsInfoOnStaticProperties_namespace() {
    compile(
        "goog.provide('A');",
        "",
        "/**",
        " * A property.",
        " * @type {number}",
        " */",
        "A.b = 1234;");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectType(a);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>A property.</p>\n")))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void returnsInfoOnCompilerConstants() {
    compile(
        "goog.provide('A');",
        "",
        "/**",
        " * This is a constant defined on A.",
        " * @define {number}",
        " */",
        "A.b = 1234;");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectType(a);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>This is a constant defined on A.</p>\n"))
                .setTags(Tags.newBuilder()
                    .setIsConst(true)))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void returnsInfoOnCompilerConstants_usingGoogDefine() {
    compile(
        "goog.provide('A');",
        "",
        "/**",
        " * This is a constant defined on A.",
        " * @define {number}",
        " */",
        "goog.define('A.b', 1234);");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectType(a);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>This is a constant defined on A.</p>\n"))
                .setTags(Tags.newBuilder()
                    .setIsConst(true)))
            .setType(numberTypeComment())
            .build());
  }
}
