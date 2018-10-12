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

package com.github.jsdossier;

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Property;
import com.github.jsdossier.proto.Tags;
import com.github.jsdossier.proto.Visibility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for extracting property information with a {@link TypeInspector}. */
@RunWith(JUnit4.class)
public class TypeInspectorInstancePropertyTest extends AbstractTypeInspectorTest {

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

    NominalType person = typeRegistry.getType("Person");
    TypeInspector typeInspector = typeInspectorFactory.create(person);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("age")
                        .setSource(sourceFile("source/foo.js.src.html", 7))
                        .setDescription(htmlComment("<p>This person&#39;s age.</p>\n")))
                .setType(numberTypeExpression())
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

    NominalType person = typeRegistry.getType("Person");
    TypeInspector typeInspector = typeInspectorFactory.create(person);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("age")
                        .setSource(sourceFile("source/foo.js.src.html", 7))
                        .setDescription(htmlComment("<p>This person&#39;s age.</p>\n")))
                .setType(numberTypeExpression())
                .build());
  }

  @Test
  public void collectsPropertiesDefinedOnParentTypePrototype_googInherits() {
    compile(
        DEFINE_INHERITS,
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
        "inherits(Character, Person);");

    NominalType character = typeRegistry.getType("Character");
    TypeInspector typeInspector = typeInspectorFactory.create(character);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("age")
                        .setSource(sourceFile("source/foo.js.src.html", 13))
                        .setDescription(htmlComment("<p>This person&#39;s age.</p>\n"))
                        .setDefinedBy(namedType("Person", "Person.html#age")))
                .setType(numberTypeExpression())
                .build());
  }

  @Test
  public void collectsPropertiesDefinedInParentTypeConstructor() {
    compile(
        DEFINE_INHERITS,
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
        "inherits(Character, Person);");

    NominalType character = typeRegistry.getType("Character");
    TypeInspector typeInspector = typeInspectorFactory.create(character);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("age")
                        .setSource(sourceFile("source/foo.js.src.html", 13))
                        .setDescription(htmlComment("<p>This person&#39;s age.</p>\n"))
                        .setDefinedBy(namedType("Person", "Person.html#age")))
                .setType(numberTypeExpression())
                .build());
  }

  /** TODO(jleyba): Figure this one out! */
  @Test
  public void prototypePropertyOverridesDoNotRegisterAsOverridden() {
    compile(
        DEFINE_INHERITS,
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
        "inherits(B, A);",
        "",
        "/**",
        " * Custom comment.",
        " * @override",
        " */",
        "B.prototype.a = 456;");

    NominalType typeB = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(typeB);
    TypeInspector.Report reportB = typeInspector.inspectInstanceType();
    assertThat(reportB.getFunctions()).isEmpty();
    assertThat(reportB.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("a")
                        .setSource(sourceFile("source/foo.js.src.html", 13))
                        .setDescription(htmlComment("<p>Original comment.</p>\n"))
                        .setDefinedBy(namedType("A", "A.html#a")))
                .setType(numberTypeExpression())
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

    NominalType typeB = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(typeB);
    TypeInspector.Report reportB = typeInspector.inspectInstanceType();
    assertThat(reportB.getFunctions()).isEmpty();
    assertThat(reportB.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("a")
                        .setSource(sourceFile("source/foo.js.src.html", 15))
                        .setDescription(htmlComment("<p>Custom comment.</p>\n"))
                        .setOverrides(namedType("A", "A.html#a")))
                .setType(numberTypeExpression())
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
        "B.prototype.a;",
        "",
        "/** @constructor @extends {A} @implements {B} */",
        "var C = function() {",
        "  /**",
        "   * @override",
        "   */",
        "  this.a = 456",
        "};");

    NominalType type = typeRegistry.getType("C");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("a")
                        .setSource(sourceFile("source/foo.js.src.html", 22))
                        .setDescription(htmlComment("<p>Comment on interface B.</p>\n"))
                        .setOverrides(namedType("A", "A.html#a"))
                        .addSpecifiedBy(namedType("B", "B.html#a")))
                .setType(numberTypeExpression())
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

    NominalType type = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("a")
                        .setSource(sourceFile("source/foo.js.src.html", 8))
                        .setDescription(htmlComment("<p>Some value.</p>\n"))
                        .setTags(Tags.newBuilder().setIsDeprecated(true))
                        .setDeprecation(htmlComment("<p>Do not use this.</p>\n")))
                .setType(numberTypeExpression())
                .build());
  }

  @Test
  public void linkReferencesAreParsedRelativeToOwningType_contextIsQueriedType() {
    util.compile(
        createSourceFile(fs.getPath("/src/globals.js"), "/** Global person. */", "class Person {}"),
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "",
            "/** Hides global person. */",
            "class Person {",
            "  constructor() {",
            "    /** Link to a {@link Person}. */",
            "    this.limit = 123;",
            "  }",
            "}",
            "export {Person}"));

    NominalType type = typeRegistry.getType("module$src$modules$foo$bar.Person");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(getOnlyElement(report.getProperties()))
        .isEqualTo(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("limit")
                        .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 6))
                        .setDescription(
                            htmlComment(
                                "<p>Link to a <a href=\"bar_exports_Person.html\">"
                                    + "<code>Person</code></a>.</p>\n")))
                .setType(numberTypeExpression())
                .build());
  }

  @Test
  public void linkReferencesAreParsedRelativeToOwningType_contextIsQueriedBaseType() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/globals.js"),
            "/** Global person. */",
            "class Person {}",
            "",
            "class Greeter {",
            "  constructor() {",
            "    /**",
            "     * The {@link Person} to greet.",
            "     * @type {Person}",
            "     */",
            "    this.p = null;",
            "  }",
            "}"),
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "",
            "export class CustomGreeter extends Greeter {",
            "}"));

    NominalType type = typeRegistry.getType("Greeter");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("p")
                        .setSource(sourceFile("source/globals.js.src.html", 10))
                        .setDescription(
                            htmlComment(
                                "<p>The <a href=\"Person.html\"><code>Person</code></a> to greet.</p>\n")))
                .setType(nullableNamedTypeExpression("Person", "Person.html"))
                .build());

    type = typeRegistry.getType("module$src$modules$foo$bar.CustomGreeter");
    typeInspector = typeInspectorFactory.create(type);
    report = typeInspector.inspectInstanceType();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("p")
                        .setSource(sourceFile("../../source/globals.js.src.html", 10))
                        .setDescription(
                            htmlComment(
                                "<p>The <a href=\"../../Person.html\"><code>Person</code></a> to greet.</p>\n"))
                        .setDefinedBy(namedType("Greeter", "../../Greeter.html#p")))
                .setType(nullableNamedTypeExpression("Person", "../../Person.html"))
                .build());
  }

  @Test
  public void linkReferencesAreParsedRelativeToOwningType_contextIsInterfaceType() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/globals.js"),
            "/** Global person. */",
            "class Person {}",
            "",
            "/** @interface */",
            "function Greeter() {}",
            "/**",
            " * The {@link Person} to greet.",
            " * @type {Person}",
            " */",
            "Greeter.prototype.p;"),
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "",
            "/** @implements {Greeter} */",
            "export class CustomGreeter {",
            "  constructor() { this.p = new Person; }",
            "}"));

    NominalType type = typeRegistry.getType("module$src$modules$foo$bar.CustomGreeter");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("p")
                        .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 4))
                        .setDescription(
                            htmlComment(
                                "<p>The <a href=\"../../Person.html\"><code>Person</code></a> to greet.</p>\n"))
                        .addSpecifiedBy(namedType("Greeter", "../../Greeter.html#p")))
                .setType(namedTypeExpression("Person", "../../Person.html"))
                .build());
  }

  @Test
  public void overriddenPropertiesInheritVisibilityFromParentType() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/globals.js"),
            "class Greeter {",
            "  constructor() {",
            "    /**",
            "     * A greeting.",
            "     * @protected",
            "     */",
            "    this.greeting = '';",
            "  }",
            "}",
            "",
            "class CustomGreeter extends Greeter {",
            "  constructor() {",
            "    super();",
            "    /** @override */",
            "    this.greeting = '';",
            "  }",
            "}",
            "",
            "class FinalGreeter extends CustomGreeter {",
            "  constructor() {",
            "    super();",
            "    /** @override */",
            "    this.greeting = 'bye';",
            "  }",
            "}"));

    NominalType type = typeRegistry.getType("Greeter");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("greeting")
                        .setSource(sourceFile("source/globals.js.src.html", 7))
                        .setDescription(htmlComment("<p>A greeting.</p>\n"))
                        .setVisibility(Visibility.newBuilder().setProtected(true)))
                .setType(stringTypeExpression())
                .build());

    type = typeRegistry.getType("CustomGreeter");
    typeInspector = typeInspectorFactory.create(type);
    report = typeInspector.inspectInstanceType();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("greeting")
                        .setSource(sourceFile("source/globals.js.src.html", 15))
                        .setDescription(htmlComment("<p>A greeting.</p>\n"))
                        .setOverrides(namedType("Greeter", "Greeter.html#greeting"))
                        .setVisibility(Visibility.newBuilder().setProtected(true)))
                .setType(stringTypeExpression())
                .build());

    type = typeRegistry.getType("FinalGreeter");
    typeInspector = typeInspectorFactory.create(type);
    report = typeInspector.inspectInstanceType();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("greeting")
                        .setSource(sourceFile("source/globals.js.src.html", 23))
                        .setDescription(htmlComment("<p>A greeting.</p>\n"))
                        .setOverrides(namedType("CustomGreeter", "CustomGreeter.html#greeting"))
                        .setVisibility(Visibility.newBuilder().setProtected(true)))
                .setType(stringTypeExpression())
                .build());
  }
}
