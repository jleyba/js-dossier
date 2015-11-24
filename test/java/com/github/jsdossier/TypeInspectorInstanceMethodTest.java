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

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Function.Detail;
import com.github.jsdossier.proto.Tags;
import com.github.jsdossier.testing.Bug;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for extracting function information with a {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorInstanceMethodTest extends AbstractTypeInspectorTest {

  /**
   * Simulates the definition of goog.abstractMethod, which is not a built-in automatically
   *  handled by the closure compiler.
   */
  private static final String DEFINE_ABSTRACT_METHOD =
      "/** @type {!Function} */ var abstractMethod = function() {};";

  @Test
  public void extractsFunctionDataFromPrototype() {
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
        "A.prototype.sayHi = function(name) { return 'Hello, ' + name; };");

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
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
  @Bug(43)
  public void extractsFunctionDataFromPrototype_forInterface() {
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
        "A.prototype.sayHi = function(name) {};");

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
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
  @Bug(43)
  public void extractsFunctionDataFromPrototype_interfaceFunctionDeclaredButNotAssigned() {
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
        "A.prototype.sayHi;");

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
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
  public void extractsFunctionDataFromConstructor() {
    compile(
        "/** @constructor */",
        "function A() {",
        "",
        "  /**",
        "   * Says hello.",
        "   * @param {string} name The person to greet.",
        "   * @return {string} A greeting.",
        "   * @throws {Error} If the person does not exist.",
        "   */",
        "  this.sayHi = function(name) { return 'Hello, ' + name; };",
        "}");

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
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
  public void usesFunctionParameterDataFromJsDoc_noParametersAvailableInSource() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var Clazz = function() {};",
        "",
        "/**",
        " * @param {number} x the first number.",
        " * @param {number} y the second number.",
        " * @return {number} x + y.",
        " */",
        "Clazz.prototype.add = abstractMethod");

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("add")
                .setSource(sourceFile("source/foo.js.src.html", 10))
                .setDescription(Comment.getDefaultInstance()))
            .addParameter(Detail.newBuilder()
                .setName("x")
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>the first number.</p>\n")))
            .addParameter(Detail.newBuilder()
                .setName("y")
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>the second number.</p>\n")))
            .setReturn(Detail.newBuilder()
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>x + y.</p>\n")))
            .build());
  }

  @Test
  @Bug(43)
  public void usesFunctionParameterDataFromJsDoc_parameterNamesOnlyNoDescription() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var Clazz = function() {};",
        "",
        "/**",
        " * @param {number} x",
        " * @param {number} y",
        " */",
        "Clazz.prototype.add = abstractMethod");

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("add")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(Comment.getDefaultInstance()))
            .addParameter(Detail.newBuilder()
                .setName("x")
                .setType(numberTypeComment()))
            .addParameter(Detail.newBuilder()
                .setName("y")
                .setType(numberTypeComment()))
            .build());
  }

  @Test
  public void pullsFunctionParameterNamesFromSourceIfNoJsDoc() {
    compile(
        "/** @constructor */",
        "var Clazz = function() {};",
        "Clazz.prototype.add = function(x, y) { return x + y; };");

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("add")
                .setSource(sourceFile("source/foo.js.src.html", 3))
                .setDescription(Comment.getDefaultInstance()))
            .addParameter(Detail.newBuilder()
                .setName("x")
                .setType(textComment("?")))
            .addParameter(Detail.newBuilder()
                .setName("y")
                .setType(textComment("?")))
            .build());
  }

  @Test
  public void jsDocOnlySpecifiesParameterTypesIsSameAsNoDocsAtAll() {
    compile(
        "/** @constructor */",
        "var Clazz = function() {};",
        "",
        "/**",
        " * @param {number}",
        " * @param {number}",
        " */",
        "Clazz.prototype.add = function(x, y) { return x + y; };");

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("add")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(Comment.getDefaultInstance()))
            .addParameter(Detail.newBuilder()
                .setName("x")
                .setType(textComment("?")))
            .addParameter(Detail.newBuilder()
                .setName("y")
                .setType(textComment("?")))
            .build());
  }

  @Test
  public void jsDocDoesNotSpecifyParameterTypes() {
    compile(
        "/** @constructor */",
        "var Clazz = function() {};",
        "",
        "/**",
        " * @param x the first number to add.",
        " * @param y the second number to add.",
        " */",
        "Clazz.prototype.add = function(x, y) { return x + y; };");

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("add")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(Comment.getDefaultInstance()))
            .addParameter(Detail.newBuilder()
                .setName("x")
                .setDescription(htmlComment("<p>the first number to add.</p>\n")))
            .addParameter(Detail.newBuilder()
                .setName("y")
                .setDescription(htmlComment("<p>the second number to add.</p>\n")))
            .build());
  }

  @Test
  public void usesPositionalArgsIfNoJsDocAndNoneInSource() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var Clazz = function() {};",
        "",
        "/** @type {function(number, number)} */",
        "Clazz.prototype.add = abstractMethod;");

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("add")
                .setSource(sourceFile("source/foo.js.src.html", 6))
                .setDescription(Comment.getDefaultInstance()))
            .addParameter(Detail.newBuilder()
                .setName("arg0")
                .setType(numberTypeComment()))
            .addParameter(Detail.newBuilder()
                .setName("arg1")
                .setType(numberTypeComment()))
            .build());
  }
  
  @Test
  public void usesOverriddenComment_superClass() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var A = function() {};",
        "",
        "/** Comment on A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @extends {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * Comment on B.",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(htmlComment("<p>Comment on B.</p>\n"))
                .setOverrides(linkComment("A", "A.html#record")))
            .build());
  }
  
  @Test
  public void usesOverriddenComment_superInterface() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/** Comment on A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @interface @extends {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * Comment on B.",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(htmlComment("<p>Comment on B.</p>\n"))
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .build());
  }
  
  @Test
  public void usesOverriddenComment_declaredInterface() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/** Comment on A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * Comment on B.",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(htmlComment("<p>Comment on B.</p>\n"))
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .build());
  }
  
  @Test
  public void usesCommentFromOverriddenType_superClass() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var A = function() {};",
        "",
        "/** Comment on A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @extends {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(htmlComment("<p>Comment on A.</p>\n"))
                .setOverrides(linkComment("A", "A.html#record")))
            .build());
  }
  
  @Test
  public void usesCommentFromOverriddenType_superInterface() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/** Comment on A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @interface @extends {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(htmlComment("<p>Comment on A.</p>\n"))
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .build());
  }
  
  @Test
  public void usesParameterInfoFromOverriddenType_superClass() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var A = function() {};",
        "",
        "/**",
        " * @param {number} v The value to record.",
        " */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @extends {A} */",
        "var B = function() {};",
        "",
        "/** @override */",
        "B.prototype.record = function(x) {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance())
                .setOverrides(linkComment("A", "A.html#record")))
            .addParameter(Detail.newBuilder()
                .setName("v")
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>The value to record.</p>\n")))
            .build());
  }
  
  @Test
  public void usesParameterInfoFromOverriddenType_superInterface() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/**",
        " * @param {number} v The value to record.",
        " */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @interface @extends {A} */",
        "var B = function() {};",
        "",
        "/** @override */",
        "B.prototype.record = function(x) {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .addParameter(Detail.newBuilder()
                .setName("v")
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>The value to record.</p>\n")))
            .build());
  }
  
  @Test
  public void usesParameterInfoFromOverriddenType_declaredInterface() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/**",
        " * @param {number} v The value to record.",
        " */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/** @override */",
        "B.prototype.record = function(x) {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .addParameter(Detail.newBuilder()
                .setName("v")
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>The value to record.</p>\n")))
            .build());
  }
  
  @Test
  public void usesParameterInfoFromOverriddenType_interfaceDeclaredOnSuperClass() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/**",
        " * @param {number} v The value to record.",
        " */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/** @override */",
        "B.prototype.record = function(x) {};",
        "",
        "/** @constructor @extends {B} */",
        "var C = function() {};",
        "",
        "/** @override */",
        "C.prototype.record = function(y) {};");

    NominalType type = typeRegistry.getType("C");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 20))
                .setDescription(Comment.getDefaultInstance())
                .setOverrides(linkComment("B", "B.html#record"))
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .addParameter(Detail.newBuilder()
                .setName("v")
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>The value to record.</p>\n")))
            .build());
  }

  @Test
  public void usesParameterInfoFromOverriddenType_abstractMethodWithSuperClass() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var A = function() {};",
        "",
        "/**",
        " * @param {number} v The value to record.",
        " */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @extends {A} */",
        "var B = function() {};",
        "",
        "/** @override */",
        "B.prototype.record = abstractMethod;");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(Comment.getDefaultInstance())
                .setDefinedBy(linkComment("A", "A.html#record")))
            .addParameter(Detail.newBuilder()
                .setName("v")
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>The value to record.</p>\n")))
            .build());
  }

  @Test
  public void usesParameterInfoFromOverriddenType_abstractMethodWithDeclaredInterface() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/**",
        " * @param {number} v The value to record.",
        " */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/** @override */",
        "B.prototype.record = abstractMethod;");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .addParameter(Detail.newBuilder()
                .setName("v")
                .setType(numberTypeComment())
                .setDescription(htmlComment("<p>The value to record.</p>\n")))
            .build());
  }
  
  @Test
  public void usesReturnInfoFromOverriddenType_superClass() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var A = function() {};",
        "",
        "/** @return {string} Return from A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @extends {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance())
                .setOverrides(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }  

  @Test
  public void usesReturnInfoFromOverriddenType_superClassEs6() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "class A {",
        "  /** @return {string} Return from A. */",
        "  record() {}",
        "}",
        "",
        "class B extends A {",
        "  /** @override */",
        "  record() {}",
        "}");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(Comment.getDefaultInstance())
                .setOverrides(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }
  
  @Test
  public void usesReturnInfoFromOverriddenType_superInterface() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/** @return {string} Return from A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @interface @extends {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }
  
  @Test
  public void usesReturnInfoFromOverriddenType_declaredInterface() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/** @return {string} Return from A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }
  
  @Test
  public void usesReturnInfoFromOverriddenType_declaredInterfaceWithNoFunctionBody() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "",
        "/** @interface */",
        "var A = function() {};",
        "",
        "/** @return {string} Return from A. */",
        "A.prototype.record;",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = noOpFunc;");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }
  
  @Test
  public void usesReturnInfoFromOverriddenType_interfaceDeclaredOnSuperClass() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/** @return {string} Return from A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = function() {};",
        "",
        "/** @constructor @extends {B} */",
        "var C = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "C.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("C");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 22))
                .setDescription(Comment.getDefaultInstance())
                .setOverrides(linkComment("B", "B.html#record"))
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }

  @Test
  @Bug(35)
  public void usesReturnInfoFromOverriddenType_abstractMethod_interfaceSpecification() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/**",
        " * Returns some value.",
        " * @return {string} Return from A.",
        " */",
        "A.prototype.record = function() {};",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = abstractMethod;");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 17))
                .setDescription(htmlComment("<p>Returns some value.</p>\n"))
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }

  @Test
  @Bug(35)
  public void usesReturnInfoFromOverriddenType_abstractMethod_superClass() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var A = function() {};",
        "",
        "/**",
        " * Returns some value.",
        " * @return {string} Return from A.",
        " */",
        "A.prototype.record = function() {};",
        "",
        "/** @constructor @extends {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.record = abstractMethod;");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(htmlComment("<p>Returns some value.</p>\n"))
                .setDefinedBy(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }

  @Test
  @Bug(35)
  public void usesReturnInfoFromOverriddenType_abstractMethod_insideAGoogScope() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "goog.provide('foo.bar');",
        "goog.scope(function() {",
        "  /**",
        "   * This is an interface.",
        "   * @interface",
        "   */",
        "  foo.bar.AnInterface = function() {};",
        "  var A = foo.bar.AnInterface;",
        "",
        "  /**",
        "   * Returns some value.",
        "   * @return {string} Return from A.",
        "   */",
        "  A.prototype.record = function() {};",
        "",
        "  /**",
        "   * This is a class.",
        "   * @constructor",
        "   * @implements {A}",
        "   */",
        "  foo.bar.AClass = function() {};",
        "  var B = foo.bar.AClass;",
        "",
        "  /**",
        "   * @override",
        "   */",
        "  B.prototype.record = abstractMethod;",
        "});");

    NominalType type = typeRegistry.getType("foo.bar.AClass");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 28))
                .setDescription(htmlComment("<p>Returns some value.</p>\n"))
                .addSpecifiedBy(linkComment(
                    "foo.bar.AnInterface", "foo.bar.AnInterface.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from A.</p>\n")))
            .build());
  }
  
  @Test
  public void canOverrideReturnDescription() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @interface */",
        "var A = function() {};",
        "",
        "/** @return {string} Return from A. */",
        "A.prototype.record = abstractMethod;",
        "",
        "/** @constructor @implements {A} */",
        "var B = function() {};",
        "",
        "/**",
        " * @return Return from B.",
        " * @override",
        " */",
        "B.prototype.record = function() {};");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("record")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#record")))
            .setReturn(Detail.newBuilder()
                .setType(stringTypeComment())
                .setDescription(htmlComment("<p>Return from B.</p>\n")))
            .build());
  }
  
  @Test
  public void canNarrowParameterDefinedOnSuperType() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var Person = function() {};",
        "",
        "/** @constructor @extends {Person} */",
        "var Student = function() {};",
        "",
        "/** @interface */",
        "var Greeter = function() {};",
        "",
        "/**",
        " * @param {!Person} person The person to greet.",
        " */",
        "Greeter.prototype.greet = abstractMethod;",
        "",
        "/**",
        " * @constructor",
        " * @implements {Greeter}",
        " */",
        "var StudentGreeter = function() {};",
        "",
        "/**",
        " * @param {!Student} student The student to greet.",
        " * @override",
        " */",
        "StudentGreeter.prototype.greet = function(student) {};");

    NominalType type = typeRegistry.getType("StudentGreeter");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("greet")
                .setSource(sourceFile("source/foo.js.src.html", 26))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("Greeter", "Greeter.html#greet")))
            .addParameter(Detail.newBuilder()
                .setName("student")
                .setType(
                    linkComment("Student", "Student.html").toBuilder().addToken(0, textToken("!")))
                .setDescription(htmlComment("<p>The student to greet.</p>\n")))
            .build());

    // Sanity check the interface specification.
    type = typeRegistry.getType("Greeter");
    typeInspector = typeInspectorFactory.create(type);
    report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("greet")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance()))
            .addParameter(Detail.newBuilder()
                .setName("person")
                .setType(linkComment("Person", "Person.html")
                    .toBuilder()
                    .addToken(0, textToken("!")))
                .setDescription(htmlComment("<p>The person to greet.</p>\n")))
            .build());
  }
  
  @Test
  public void canNarrowReturnTypeOfSuperType() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/** @constructor */",
        "var Greeting = function() {};",
        "",
        "/** @constructor @extends {Greeting} */",
        "var HappyGreeting = function() {};",
        "",
        "/** @interface */",
        "var Greeter = function() {};",
        "",
        "/**",
        " * @return {!Greeting} Returns a greeting.",
        " */",
        "Greeter.prototype.greet = abstractMethod;",
        "",
        "/**",
        " * @constructor",
        " * @implements {Greeter}",
        " */",
        "var HappyGreeter = function() {};",
        "",
        "/**",
        " * @return {!HappyGreeting} A happy greeting.",
        " * @override",
        " */",
        "HappyGreeter.prototype.greet = function() {};");

    NominalType type = typeRegistry.getType("HappyGreeter");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("greet")
                .setSource(sourceFile("source/foo.js.src.html", 26))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("Greeter", "Greeter.html#greet")))
            .setReturn(Detail.newBuilder()
                .setType(linkComment("HappyGreeting", "HappyGreeting.html"))
                .setDescription(htmlComment("<p>A happy greeting.</p>\n")))
            .build());

    // Sanity check the interface specification.
    type = typeRegistry.getType("Greeter");
    typeInspector = typeInspectorFactory.create(type);
    report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("greet")
                .setSource(sourceFile("source/foo.js.src.html", 14))
                .setDescription(Comment.getDefaultInstance()))
            .setReturn(Detail.newBuilder()
                .setType(linkComment("Greeting", "Greeting.html"))
                .setDescription(htmlComment("<p>Returns a greeting.</p>\n")))
            .build());
  }
  
  @Test
  public void doesNotInheritDeprecationNoticeFromSuperType() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/**",
        " * @deprecated Do not use this.",
        " */",
        "A.prototype.go = function() {};",
        "",
        "/** @constructor @implements {A} */",
        "function B() {}",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.go = function() {};");
    
    NominalType type = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("go")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(Comment.getDefaultInstance())
                .setTags(Tags.newBuilder()
                    .setIsDeprecated(true))
                .setDeprecation(htmlComment("<p>Do not use this.</p>\n")))
            .build());

    type = typeRegistry.getType("B");
    typeInspector = typeInspectorFactory.create(type);
    report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("go")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#go")))
            .build());
  }
  
  @Test
  public void doesNotInheritThrownClausesFromSuperType() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/**",
        " * @throws {Error} if something goes wrong.",
        " */",
        "A.prototype.go = function() {};",
        "",
        "/** @constructor @implements {A} */",
        "function B() {}",
        "",
        "/**",
        " * @override",
        " */",
        "B.prototype.go = function() {};");

    NominalType type = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("go")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(Comment.getDefaultInstance()))
            .addThrown(Detail.newBuilder()
                .setType(errorTypeComment())
                .setDescription(htmlComment("<p>if something goes wrong.</p>\n")))
            .build());

    type = typeRegistry.getType("B");
    typeInspector = typeInspectorFactory.create(type);
    report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("go")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(Comment.getDefaultInstance())
                .addSpecifiedBy(linkComment("A", "A.html#go")))
            .build());
  }
  
  @Test
  public void includesFunctionsDefinedBySuperTypeButNotOverriddenByInspectedType() {
    compile(
        "/** @constructor */",
        "function A() {}",
        "",
        "/**",
        " * Runs this instance.",
        " */",
        "A.prototype.run = function() {};",
        "",
        "/** @constructor @extends {A} */",
        "function B() {}");

    NominalType type = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("run")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>Runs this instance.</p>\n"))
                .setDefinedBy(linkComment("A", "A.html#run")))
            .build());
  }

  @Test
  @Bug(30)
  public void abstractMethodInheritsDocsFromInterfaceSpecification() {
    compile(
        DEFINE_ABSTRACT_METHOD,
        "/**",
        " * @interface",
        " */",
        "var Edge = function() {};",
        "",
        "/**",
        " * Render this edge to the given context",
        " * @param {!CanvasRenderingContext2D} context The canvas to draw this object into",
        " */",
        "Edge.prototype.addPath = abstractMethod;",
        "",
        "/**",
        " * Abstract edge implementation.",
        " * @constructor",
        " * @struct",
        " * @implements {Edge}",
        " */",
        "var AbstractEdge = function() {};",
        "",
        "/** @inheritDoc */",
        "AbstractEdge.prototype.addPath = function() {};");

    NominalType type = typeRegistry.getType("AbstractEdge");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("addPath")
                .setSource(sourceFile("source/foo.js.src.html", 22))
                .setDescription(htmlComment("<p>Render this edge to the given context</p>\n"))
                .addSpecifiedBy(linkComment("Edge", "Edge.html#addPath")))
            .addParameter(Detail.newBuilder()
                .setName("context")
                .setType(textComment("!CanvasRenderingContext2D"))
                .setDescription(htmlComment("<p>The canvas to draw this object into</p>\n")))
            .build());
  }

  @Test
  @Bug(38)
  public void methodsCanLinkToOtherMethodsOnTheSameClass() {
    compile(
        "/** @constructor */",
        "var Foo = function() {};",
        "",
        "/** Go to {@link #b}. */",
        "Foo.prototype.a = function() {};",
        "",
        "/** Go to {@link #a}. */",
        "Foo.prototype.b = function() {};");

    NominalType type = typeRegistry.getType("Foo");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 5))
                .setDescription(htmlComment(
                    "<p>Go to <a href=\"Foo.html#b\"><code>#b</code></a>.</p>\n")))
            .build(),
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(htmlComment(
                    "<p>Go to <a href=\"Foo.html#a\"><code>#a</code></a>.</p>\n")))
            .build());
  }

  @Test
  @Bug(38)
  public void methodsCanLinkToMethodDefinedBySuperClass() {
    compile(
        "/** @constructor */",
        "var Foo = function() {};",
        "Foo.prototype.a = function() {};",
        "",
        "/** @constructor @extends {Foo} */",
        "var Bar = function() {};",
        "",
        "/** Go to {@link #a}. */",
        "Bar.prototype.b = function() {};");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 3))
                .setDescription(Comment.getDefaultInstance())
                .setDefinedBy(linkComment("Foo", "Foo.html#a")))
            .build(),
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(htmlComment(
                    "<p>Go to <a href=\"Bar.html#a\"><code>#a</code></a>.</p>\n")))
            .build());
  }

  @Test
  @Bug(38)
  public void methodLinksToOverriddenMethodIfNoTypeQualifierSpecified() {
    compile(
        "/** @constructor */",
        "var Foo = function() {};",
        "Foo.prototype.a = function() {};",
        "",
        "/** @constructor @extends {Foo} */",
        "var Bar = function() {};",
        "",
        "/** @override */",
        "Bar.prototype.a = function() {};",
        "",
        "/** Go to {@link #a}. */",
        "Bar.prototype.b = function() {};");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(Comment.getDefaultInstance())
                .setOverrides(linkComment("Foo", "Foo.html#a")))
            .build(),
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 12))
                .setDescription(htmlComment(
                    "<p>Go to <a href=\"Bar.html#a\"><code>#a</code></a>.</p>\n")))
            .build());
  }

  @Test
  @Bug(38)
  public void methodCanLinkToOriginalDefinitionIfQualifierProvided() {
    compile(
        "/** @constructor */",
        "var Foo = function() {};",
        "Foo.prototype.a = function() {};",
        "",
        "/** @constructor @extends {Foo} */",
        "var Bar = function() {};",
        "",
        "/** @override */",
        "Bar.prototype.a = function() {};",
        "",
        "/** Go to {@link Foo#a}. */",
        "Bar.prototype.b = function() {};");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(Comment.getDefaultInstance())
                .setOverrides(linkComment("Foo", "Foo.html#a")))
            .build(),
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 12))
                .setDescription(htmlComment(
                    "<p>Go to <a href=\"Foo.html#a\"><code>Foo#a</code></a>.</p>\n")))
            .build());
  }

  @Test
  @Bug(38)
  public void methodCanLinkToMethodOnAnotherClass() {
    compile(
        "/** @constructor */",
        "var Foo = function() {};",
        "Foo.prototype.a = function() {};",
        "",
        "/** @constructor */",
        "var Bar = function() {};",
        "/** Go to {@link Foo#a}. */",
        "Bar.prototype.b = function() {};");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(htmlComment(
                    "<p>Go to <a href=\"Foo.html#a\"><code>Foo#a</code></a>.</p>\n")))
            .build());
  }
  
  @Test
  public void methodSpecifiedByInterfaceInAnotherModule() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/modules/one.js"),
            "",
            "/** @interface */",
            "export class Greeter {",
            "  /** Returns a greeting. */",
            "  greet() {}",
            "}"),
        createSourceFile(
            fs.getPath("/src/modules/two.js"),
            "",
            "/** @implements {./one.Greeter} */",
            "export class CustomGreeter {",
            "  /** @override */",
            "  greet() {}",
            "}"));

    NominalType type = typeRegistry.getType("module$src$modules$two.CustomGreeter");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("greet")
                .setSource(sourceFile("../source/modules/two.js.src.html", 5))
                .setDescription(htmlComment("<p>Returns a greeting.</p>\n"))
                .addSpecifiedBy(linkComment("Greeter", "one_exports_Greeter.html#greet")))
            .build());
  }

  @Test
  public void moduleClassInheritsMethodsFromAnotherModuleClass() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/modules/a/b/c.js"),
            "class X {}",
            "export { X as Y }",
            "",
            "export class Z {",
            "  /**",
            "   * @param {!X} x A reference to {@link X}",
            "   */",
            "  method(x) {}",
            "}"),
        createSourceFile(
            fs.getPath("/src/modules/one.js"),
            "",
            "import {Z} from './a/b/c';",
            "export class B extends Z {}"));

    NominalType type = typeRegistry.getType("module$src$modules$one.B");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("method")
                .setSource(sourceFile("../source/modules/a/b/c.js.src.html", 8))
                .setDescription(Comment.getDefaultInstance())
                .setDefinedBy(linkComment("Z", "a/b/c_exports_Z.html#method")))
            .addParameter(Detail.newBuilder()
                .setName("x")
                .setType(linkComment("Y", "a/b/c_exports_Y.html")
                    .toBuilder()
                    .addToken(0, textToken("!")))
                .setDescription(htmlComment(
                    "<p>A reference to <a href=\"a/b/c_exports_Y.html\">" +
                        "<code>X</code></a></p>\n")))
            .build());
  }
}
