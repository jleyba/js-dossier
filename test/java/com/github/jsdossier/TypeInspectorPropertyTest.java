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
import com.github.jsdossier.proto.Property;
import com.github.jsdossier.proto.Tags;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for extracting property information with a {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorPropertyTest extends AbstractTypeInspectorTest {

  @Test
  public void collectsPropertiesDefinedOnPrototype_classHasNoSuperType() {
    compile(
        "/** @constructor */",
        "function Person() {}",
        "/**",
        " * This person's age.",
        " * @type {number}",
        " */",
        "Person.prototype.age = 123;");

    NominalType person = typeRegistry.getNominalType("Person");

    TypeInspector.Report report = typeInspector.inspectMembers(person);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("age")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>This person\'s age.</p>\n")))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void collectsPropertiesDefinedInsideConstructor_classHasNoSuperType() {
    compile(
        "/** @constructor */",
        "function Person() {",
        "  /**",
        "   * This person's age.",
        "   * @type {number}",
        "   */",
        "  this.age = 123;",
        "}");

    NominalType person = typeRegistry.getNominalType("Person");

    TypeInspector.Report report = typeInspector.inspectMembers(person);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("age")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>This person\'s age.</p>\n")))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void collectsPropertiesDefinedOnParentTypePrototype_googInherits() {
    compile(
        "/** @constructor */",
        "function Person() {",
        "  /**",
        "   * This person's age.",
        "   * @type {number}",
        "   */",
        "  this.age = 123;",
        "}",
        "",
        "/** @constructor @extends {Person} */",
        "function Character() {}",
        "goog.inherits(Character, Person);");

    NominalType character = typeRegistry.getNominalType("Character");

    TypeInspector.Report report = typeInspector.inspectMembers(character);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("age")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>This person\'s age.</p>\n"))
                .setDefinedBy(linkComment("Person", "class_Person.html#age")))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void collectsPropertiesDefinedInParentTypeConstructor() {
    compile(
        "/** @constructor */",
        "function Person() {",
        "  /**",
        "   * This person's age.",
        "   * @type {number}",
        "   */",
        "  this.age = 123;",
        "}",
        "",
        "/** @constructor @extends {Person} */",
        "function Character() {}",
        "goog.inherits(Character, Person);");

    NominalType character = typeRegistry.getNominalType("Character");

    TypeInspector.Report report = typeInspector.inspectMembers(character);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("age")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>This person\'s age.</p>\n"))
                .setDefinedBy(linkComment("Person", "class_Person.html#age")))
            .setType(numberTypeComment())
            .build());
  }

  /** TODO(jleyba): Figure this one out! */
  @Test
  public void prototypePropertyOverridesDoNotRegisterAsOverridden() {
    compile(
        "/** @constructor */",
        "var A = function() {};",
        "/**",
        " * Original comment.",
        " * @type {number}",
        " */",
        "A.prototype.a = 123;",
        "",
        "/** @constructor @extends {A} */",
        "var B = function() {};",
        "goog.inherits(B, A);",
        "",
        "/**",
        " * Custom comment.",
        " * @override",
        " */",
        "B.prototype.a = 456;");

    NominalType typeB = typeRegistry.getNominalType("B");
    TypeInspector.Report reportB = typeInspector.inspectMembers(typeB);
    assertThat(reportB.getFunctions()).isEmpty();
    assertThat(reportB.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(htmlComment("<p>Original comment.</p>\n"))
                .setDefinedBy(linkComment("A", "class_A.html#a")))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void overriddenPropertyUsesLocallyDefinedCommentIfPresent() {
    compile(
        "/** @constructor */",
        "var A = function() {};",
        "/**",
        " * Original comment.",
        " * @type {number}",
        " */",
        "A.prototype.a = 123;",
        "",
        "/** @constructor @extends {A} */",
        "var B = function() {",
        "  /**",
        "   * Custom comment.",
        "   * @override",
        "   */",
        "  this.a = 456",
        "};");

    NominalType typeB = typeRegistry.getNominalType("B");
    TypeInspector.Report reportB = typeInspector.inspectMembers(typeB);
    assertThat(reportB.getFunctions()).isEmpty();
    assertThat(reportB.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(htmlComment("<p>Custom comment.</p>\n"))
                .setOverrides(linkComment("A", "class_A.html#a")))
            .setType(numberTypeComment())
            .build());
  }
  
  @Test
  public void overriddenPropertyUsesCommentFromDeclaredInterfaceBeforeSuperClass() {
    compile(
        "/** @constructor */",
        "var A = function() {};",
        "/**",
        " * Comment on class A.",
        " * @type {number}",
        " */",
        "A.prototype.a = 123;",
        "",
        "/** @interface */",
        "var B = function() {};",
        "/**",
        " * Comment on interface B.",
        " * @type {number}",
        " */",
        "B.prototype.a = 123;",
        "",
        "/** @constructor @extends {A} @implements {B} */",
        "var C = function() {",
        "  /**",
        "   * @override",
        "   */",
        "  this.a = 456",
        "};");

    NominalType type = typeRegistry.getNominalType("C");
    TypeInspector.Report report = typeInspector.inspectMembers(type);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 22))
                .setDescription(htmlComment("<p>Comment on interface B.</p>\n"))
                .setOverrides(linkComment("A", "class_A.html#a"))
                .addSpecifiedBy(linkComment("B", "interface_B.html#a")))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void recordsWhenPropertyIsDeprecated() {
    compile(
        "/** @constructor */",
        "var A = function() {};",
        "/**",
        " * Some value.",
        " * @deprecated Do not use this.",
        " * @type {number}",
        " */",
        "A.prototype.a = 123;");

    NominalType type = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectMembers(type);
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(htmlComment("<p>Some value.</p>\n"))
                .setTags(Tags.newBuilder()
                    .setIsDeprecated(true))
                .setDeprecation(htmlComment("<p>Do not use this.</p>\n")))
            .setType(numberTypeComment())
            .build());
  }
}
