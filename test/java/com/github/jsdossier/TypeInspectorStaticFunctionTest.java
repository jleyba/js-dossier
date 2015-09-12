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
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Function.Detail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for extracting function information with a {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorStaticFunctionTest extends AbstractTypeInspectorTest {
  
  @Test
  public void returnsInfoOnStaticFunctions_constructor() {
    compile(
        "/** @constructor */",
        "function A() {}",
        "",
        "/**",
        " * Says hello.",
        " * @param {string} name The person to greet.",
        " * @return {string} A greeting.",
        " * @throws {Error} If the person does not exist.",
        " */",
        "A.sayHi = function(name) { return 'Hello, ' + name; };");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectType(a);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("A.sayHi")
                .setSource(sourceFile("source/foo.js.src.html", 10))
                .setDescription(htmlComment("<p>Says hello.</p>\n")))
            .addParameter(Detail.newBuilder()
                .setName("name")
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>The person to greet.</p>\n")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>A greeting.</p>\n")))
            .addThrown(Detail.newBuilder()
                .setType(errorTypeComment())
                .setDescription(htmlComment("<p>If the person does not exist.</p>\n")))
            .build());
  }
  
  @Test
  public void returnsInfoOnStaticFunctions_interface() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/**",
        " * Says hello.",
        " * @param {string} name The person to greet.",
        " * @return {string} A greeting.",
        " * @throws {Error} If the person does not exist.",
        " */",
        "A.sayHi = function(name) { return 'Hello, ' + name; };");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectType(a);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("A.sayHi")
                .setSource(sourceFile("source/foo.js.src.html", 10))
                .setDescription(htmlComment("<p>Says hello.</p>\n")))
            .addParameter(Detail.newBuilder()
                .setName("name")
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>The person to greet.</p>\n")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>A greeting.</p>\n")))
            .addThrown(Detail.newBuilder()
                .setType(errorTypeComment())
                .setDescription(htmlComment("<p>If the person does not exist.</p>\n")))
            .build());
  }
  
  @Test
  public void returnsInfoOnStaticFunctions_enum() {
    compile(
        "/** @enum {string} */",
        "var Color = {RED: 'red'};",
        "",
        "/**",
        " * Darkens a color.",
        " * @param {!Color} c The color to darken.",
        " * @return {!Color} The darkened color.",
        " * @throws {Error} If the color cannot be darkened.",
        " */",
        "Color.darken = function(c) { return c; };");

    NominalType type = typeRegistry.getNominalType("Color");
    TypeInspector.Report report = typeInspector.inspectType(type);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("Color.darken")
                .setSource(sourceFile("source/foo.js.src.html", 10))
                .setDescription(htmlComment("<p>Darkens a color.</p>\n")))
            .addParameter(Detail.newBuilder()
                .setName("c")
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>The color to darken.</p>\n")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>The darkened color.</p>\n")))
            .addThrown(Detail.newBuilder()
                .setType(errorTypeComment())
                .setDescription(htmlComment("<p>If the color cannot be darkened.</p>\n")))
            .build());
  }
  
  @Test
  public void returnsInfoOnStaticFunctions_namespace() {
    compile(
        "goog.provide('Color');",
        "",
        "/**",
        " * Darkens a color.",
        " * @param {string} c The color to darken.",
        " * @return {string} The darkened color.",
        " * @throws {Error} If the color cannot be darkened.",
        " */",
        "Color.darken = function(c) { return c; };");

    NominalType type = typeRegistry.getNominalType("Color");
    TypeInspector.Report report = typeInspector.inspectType(type);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("darken")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(htmlComment("<p>Darkens a color.</p>\n")))
            .addParameter(Detail.newBuilder()
                .setName("c")
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>The color to darken.</p>\n")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>The darkened color.</p>\n")))
            .addThrown(Detail.newBuilder()
                .setType(errorTypeComment())
                .setDescription(htmlComment("<p>If the color cannot be darkened.</p>\n")))
            .build());
  }

  @Test
  public void extractsFunctionTemplateTypeNames() {
    compile(
        "goog.provide('foo');",
        "",
        "/**",
        " * @param {TYPE} v A value.",
        " * @return {TYPE} The value.",
        " * @template TYPE",
        " */",
        "foo.bar = function(v) { return v;};");

    NominalType type = typeRegistry.getNominalType("foo");
    TypeInspector.Report report = typeInspector.inspectType(type);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("bar")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(Comment.getDefaultInstance()))
            .addParameter(Detail.newBuilder()
                .setName("v")
                .setType(textComment("TYPE"))
                .setDescription(htmlComment("<p>A value.</p>\n")))
            .setReturn(Detail.newBuilder()
                .setType(textComment("TYPE"))
                .setDescription(htmlComment("<p>The value.</p>\n")))
            .addTemplateName("TYPE")
            .build());
  }

  @Test
  public void staticFunctionsCanLinkToOtherStaticFunctions_onSameType() {
    compile(
        "/** @constructor */",
        "function Clazz() {}",
        "",
        "/**",
        " * Link to {@link Clazz.bar}.",
        " */",
        "Clazz.foo = function() {};",
        "",
        "/**",
        " * Link to {@link Clazz.foo}.",
        " */",
        "Clazz.bar = function() {};");

    NominalType type = typeRegistry.getNominalType("Clazz");
    TypeInspector.Report report = typeInspector.inspectType(type);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("Clazz.foo")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment(
                    "<p>Link to <a href=\"class_Clazz.html#Clazz.bar\">" +
                        "<code>Clazz.bar</code></a>.</p>\n")))
            .build(),
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("Clazz.bar")
                .setSource(sourceFile("source/foo.js.src.html", 12))
                .setDescription(htmlComment(
                    "<p>Link to <a href=\"class_Clazz.html#Clazz.foo\">" +
                        "<code>Clazz.foo</code></a>.</p>\n")))
            .build());
  }

  @Test
  public void staticFunctionsCanLinkToOtherStaticFunctions_onOtherType() {
    compile(
        "/** @constructor */",
        "function Clazz() {}",
        "",
        "/**",
        " * Link to {@link OtherClazz.bar}.",
        " */",
        "Clazz.foo = function() {};",
        "",
        "/** @constructor */",
        "function OtherClazz() {}",
        "OtherClazz.bar = function() {};");

    NominalType type = typeRegistry.getNominalType("Clazz");
    TypeInspector.Report report = typeInspector.inspectType(type);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("Clazz.foo")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment(
                    "<p>Link to <a href=\"class_OtherClazz.html#OtherClazz.bar\">" +
                        "<code>OtherClazz.bar</code></a>.</p>\n")))
            .build());
  }

  @Test
  public void staticFunctionsCanLinkToInstanceMethods() {
    compile(
        "/** @constructor */",
        "function Clazz() {}",
        "",
        "/**",
        " * Link to {@link #bar}.",
        " */",
        "Clazz.foo = function() {};",
        "",
        "Clazz.prototype.bar = function() {};");

    NominalType type = typeRegistry.getNominalType("Clazz");
    TypeInspector.Report report = typeInspector.inspectType(type);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("Clazz.foo")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment(
                    "<p>Link to <a href=\"class_Clazz.html#bar\">" +
                        "<code>#bar</code></a>.</p>\n")))
            .build());
  }
}
