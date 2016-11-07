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

import static com.github.jsdossier.ProtoTruth.assertMessage;
import static com.github.jsdossier.ProtoTruth.assertMessages;
import static com.github.jsdossier.TypeInspector.fakeNodeForType;
import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.TypeInspector.InstanceProperty;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Property;
import com.github.jsdossier.proto.Visibility;
import com.github.jsdossier.testing.Bug;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/**
 * Tests for {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorTest extends AbstractTypeInspectorTest {

  @Test
  public void getInstanceProperties_vanillaClass() {
    compile(
        "/** @constructor */",
        "function Person() {",
        "  this.name = 'Bob';",
        "  this.sleep = function() {};",
        "}",
        "Person.shouldIgnoreStaticProperty = function() {}",
        "/** @type {number} */",
        "Person.prototype.age;",
        "Person.prototype.eat = function() {};");

    NominalType person = typeRegistry.getType("Person");
    TypeInspector typeInspector = typeInspectorFactory.create(person);

    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(jsTypeRegistry.getType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(jsTypeRegistry.getType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getType());
    assertInstanceProperty(sleep).isDefinedOn(person.getType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getType());
    assertInstanceProperty(eat).isDefinedOn(person.getType());
  }

  @Test
  public void getInstanceProperties_googDefinedClass() {
    compile(
        "var Person = goog.defineClass(null, {",
        "  constructor: function() {",
        "    this.name = 'Bob';",
        "    this.sleep = function() {};",
        "  },",
        "  statics: { shouldIgnoreStaticProperty: function() {} },",
        "  /** @type {number} */age: 123,",
        "  eat: function() {}",
        "});");

    NominalType person = typeRegistry.getType("Person");
    TypeInspector typeInspector = typeInspectorFactory.create(person);

    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(jsTypeRegistry.getType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(jsTypeRegistry.getType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getType());
    assertInstanceProperty(sleep).isDefinedOn(person.getType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getType());
    assertInstanceProperty(eat).isDefinedOn(person.getType());
  }

  @Test
  public void getInstanceProperties_vanillaInterface() {
    compile(
        "/** @interface */",
        "function Person() {}",
        "/** @type {number} */",
        "Person.prototype.age;",
        "/** @type {string} */",
        "Person.prototype.name;",
        "Person.prototype.sleep = function() {}",
        "Person.prototype.eat = function(food) {};");

    NominalType person = typeRegistry.getType("Person");
    TypeInspector typeInspector = typeInspectorFactory.create(person);

    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(jsTypeRegistry.getType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(jsTypeRegistry.getType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getType());
    assertInstanceProperty(sleep).isDefinedOn(person.getType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getType());
    assertInstanceProperty(eat).isDefinedOn(person.getType());
  }

  @Test
  public void getInstanceProperties_googDefinedInterface() {
    compile(
        "/** @interface */",
        "var Person = goog.defineClass(null, {",
        "  statics: { shouldIgnoreStaticProperty: function() {} },",
        "  /** @type {string} */name: 'Bob',",
        "  /** @type {number} */age: 123,",
        "  eat: function() {},",
        "  sleep: function() {}",
        "});");

    NominalType person = typeRegistry.getType("Person");
    TypeInspector typeInspector = typeInspectorFactory.create(person);

    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(jsTypeRegistry.getType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(jsTypeRegistry.getType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getType());
    assertInstanceProperty(sleep).isDefinedOn(person.getType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getType());
    assertInstanceProperty(eat).isDefinedOn(person.getType());
  }

  @Test
  public void getInstanceProperties_doesNotIncludePropertiesFromSuperClass() {
    compile(
        "/** @constructor */",
        "function Person() {}",
        "/** @type {number} */Person.prototype.age;",
        "/** @type {string} */Person.prototype.name;",
        "",
        "/** @constructor @extends {Person} */",
        "function SuperHero() {}",
        "goog.inherits(SuperHero, Person);",
        "/** @type {string} */SuperHero.prototype.power;");

    NominalType hero = typeRegistry.getType("SuperHero");
    TypeInspector typeInspector = typeInspectorFactory.create(hero);

    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(hero.getType());

    assertThat(properties.keySet()).containsExactly("power");

    InstanceProperty power = properties.get("power");
    assertInstanceProperty(power).isNamed("power");
    assertInstanceProperty(power).hasType(jsTypeRegistry.getType("string"));
    assertInstanceProperty(power).isDefinedOn(hero.getType());
  }

  @Test
  public void getInstanceProperties_doesNotIncludePropertiesFromParentInterface() {
    compile(
        "/** @interface */",
        "function Person() {}",
        "Person.prototype.eat = function() {};",
        "Person.prototype.sleep = function() {};",
        "",
        "/** @interface @extends {Person} */",
        "function Athlete() {}",
        "Athlete.prototype.run = function() {}");

    NominalType athlete = typeRegistry.getType("Athlete");
    TypeInspector typeInspector = typeInspectorFactory.create(athlete);

    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(athlete.getType());

    assertThat(properties.keySet()).containsExactly("run");

    InstanceProperty run = properties.get("run");
    assertInstanceProperty(run).isNamed("run");
    assertInstanceProperty(run).isInstanceMethod(athlete.getType());
    assertInstanceProperty(run).isDefinedOn(athlete.getType());
  }

  @Test
  public void getTypeDescription_globalType() {
    compile(
        "/**",
        " * This is a comment on a type.",
        " */",
        "class Foo {}");

    NominalType type = typeRegistry.getType("Foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_noCommentFound() {
    compile(
        "class Foo {}");

    NominalType type = typeRegistry.getType("Foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription()).isEqualTo(Comment.getDefaultInstance());
  }

  @Test
  public void getTypeDescription_namespacedType() {
    compile(
        "goog.provide('foo.bar');",
        "/**",
        " * This is a comment on a type.",
        " */",
        "foo.bar.Baz = class {}");

    NominalType type = typeRegistry.getType("foo.bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_closureModuleExportedType() {
    compile(
        "goog.module('foo.bar');",
        "/**",
        " * This is a comment on a type.",
        " */",
        "exports.Baz = class {}");

    NominalType type = typeRegistry.getType("module$exports$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_nodeModuleExportedType() {
    util.compile(fs.getPath("/src/modules/foo/bar.js"),
        "/**",
        " * This is a comment on a type.",
        " */",
        "exports.Baz = class {}");

    NominalType type = typeRegistry.getType("module$exports$module$$src$modules$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_es6ModuleExportedType() {
    util.compile(fs.getPath("/src/modules/foo/bar.js"),
        "/**",
        " * This is a comment on a type.",
        " */",
        "export class Baz {}");

    NominalType type = typeRegistry.getType("module$$src$modules$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_aliasedType() {
    compile(
        "goog.provide('foo.bar');",
        "/**",
        " * This is a comment on a type.",
        " */",
        "foo.bar.Baz = class {}",
        "foo.bar.AliasedBaz = foo.bar.Baz");

    NominalType type = typeRegistry.getType("foo.bar.AliasedBaz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_aliasedModuleExport() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "/**",
            " * This is a comment on a type.",
            " */",
            "exports.Baz = class {}"),
        createSourceFile(
            fs.getPath("/src/modules/foo/baz.js"),
            "exports.AliasedBaz = require('./bar').Baz;"));

    NominalType type =
        typeRegistry.getType("module$exports$module$$src$modules$foo$baz.AliasedBaz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_closureModuleExportsInternalType() {
    compile(
        "goog.module('foo.bar');",
        "/**",
        " * This is a comment on a type.",
        " */",
        "class InternalClazz {}",
        "exports.Baz = InternalClazz");

    NominalType type = typeRegistry.getType("module$exports$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_nodeModuleExportsInternalType() {
    util.compile(fs.getPath("/src/modules/foo/bar.js"),
        "/**",
        " * This is a comment on a type.",
        " */",
        "class InternalClazz {}",
        "exports.Baz = InternalClazz");

    NominalType type = typeRegistry.getType("module$exports$module$$src$modules$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_es6ModuleExportsInternalType() {
    util.compile(fs.getPath("/src/modules/foo/bar.js"),
        "/**",
        " * This is a comment on a type.",
        " * {@link InternalClazz}",
        " */",
        "class InternalClazz {}",
        "export {InternalClazz as Baz}");

    NominalType type = typeRegistry.getType("module$$src$modules$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment(
            "<p>This is a comment on a type.\n" +
                "<a href=\"bar_exports_Baz.html\">" +
                "<code>InternalClazz</code></a></p>\n"));
  }

  @Test
  public void getTypeDescription_aliasOfExportedModuleType() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "/**",
            " * This is a comment on a type.",
            " */",
            "class InternalClazz {}",
            "export {InternalClazz as Baz}"),
        createSourceFile(
            fs.getPath("/src/modules/foo/bar/baz.js"),
            "export {Baz as Foo} from '../bar';"));

    NominalType type = typeRegistry.getType("module$$src$modules$foo$bar$baz.Foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_emptyDescriptionForGoogProvideNamespaces() {
    compile(
        "/** @fileoverview Hello, world! */",
        "goog.provide('foo.bar');");

    NominalType type = typeRegistry.getType("foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription()).isEqualTo(Comment.getDefaultInstance());

    type = typeRegistry.getType("foo.bar");
    inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription()).isEqualTo(Comment.getDefaultInstance());
  }

  @Test
  public void getTypeDescription_nodeModule() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** @fileoverview Exports {@link A}. */",
        "class A {}",
        "exports.A = A;");

    NominalType type = typeRegistry.getType("module$exports$module$$src$modules$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment(
            "<p>Exports <a href=\"bar_exports_A.html\">" +
                "<code>A</code></a>.</p>\n"));
  }

  @Test
  public void getTypeDescription_es6Module() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** @fileoverview Exports {@link A}. */",
        "class A {}",
        "export {A}");

    NominalType type = typeRegistry.getType("module$$src$modules$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment(
            "<p>Exports <a href=\"bar_exports_A.html\">" +
                "<code>A</code></a>.</p>\n"));
  }

  @Test
  public void getTypeDescription_closureModuleWithMainFunction() {
    compile(
        "/** @fileoverview This is the fileoverview. */",
        "goog.module('foo.bar');",
        "/** The main function. */",
        "exports = function() {}");

    NominalType type = typeRegistry.getType("module$exports$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>The main function.</p>\n"));
  }

  @Test
  public void getTypeDescription_nodeModuleWithMainFunction() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** @fileoverview This is the fileoverview. */",
        "/** The main function. */",
        "exports = function() {}");

    NominalType type = typeRegistry.getType("module$exports$module$$src$modules$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>The main function.</p>\n"));
  }

  @Test
  public void getTypeDescription_es6ModuleWithMainFunction() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** @fileoverview This is the fileoverview. */",
        "/** The main function. */",
        "export default function() {}");

    NominalType type = typeRegistry.getType("module$$src$modules$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertMessage(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is the fileoverview.</p>\n"));
  }

  @Test
  @Bug(49)
  public void canGetInfoForEs6Constructor() {
    compile(
        "class Person {",
        "   /**",
        "    * @param {string} name The person's name.",
        "    * @throws {Error} Randomly.",
        "    */",
        "   constructor(name) { this.name = name; }",
        "}");

    NominalType type = typeRegistry.getType("Person");
    TypeInspector typeInspector = typeInspectorFactory.create(type);

    Node fakeNode = fakeNodeForType(type);
    Function data = typeInspector.getFunctionData(
        type.getName(), type.getType().toMaybeFunctionType(), fakeNode, type, type.getJsDoc());

    assertMessage(data).isEqualTo(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("Person")
                .setSource(sourceFile("source/foo.js.src.html", 1))
                .setDescription(Comment.getDefaultInstance()))
            .setIsConstructor(true)
            .addParameter(Function.Detail.newBuilder()
                .setName("name")
                .setType(stringTypeExpression())
                .setDescription(htmlComment("<p>The person&#39;s name.</p>\n")))
            .addThrown(Function.Detail.newBuilder()
                .setType(nullableErrorTypeExpression())
                .setDescription(htmlComment("<p>Randomly.</p>\n")))
            .build());
  }

  @Test
  public void seeOtherMethodOnType() {
    compile(
        "class Bar {",
        "  a() {}",
        "  /** @see #a */",
        "  b() {}",
        "}");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertMessages(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 2))
                .setDescription(Comment.getDefaultInstance()))
            .build(),
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 4))
                .setDescription(Comment.getDefaultInstance())
                .addSeeAlso(linkComment("#a", "Bar.html#a")))
            .build());
  }

  @Test
  public void seeMethodOnOtherType() {
    compile(
        "class Foo { a() {}}",
        "class Bar {",
        "  /** @see Foo#a */",
        "  b() {}",
        "}");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertMessages(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 4))
                .setDescription(Comment.getDefaultInstance())
                .addSeeAlso(linkComment("Foo#a", "Foo.html#a")))
            .build());
  }

  @Test
  public void seeOtherType() {
    compile(
        "class Foo { a() {}}",
        "class Bar {",
        "  /** @see Foo */",
        "  b() {}",
        "}");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertMessages(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 4))
                .setDescription(Comment.getDefaultInstance())
                .addSeeAlso(linkComment("Foo", "Foo.html")))
            .build());
  }

  @Test
  public void seeUrl() {
    compile(
        "class Bar {",
        "  /** @see http://www.example.com */",
        "  b() {}",
        "}");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertMessages(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 3))
                .setDescription(Comment.getDefaultInstance())
                .addSeeAlso(htmlComment(
                    "<p><a href=\"http://www.example.com\">http://www.example.com</a></p>\n"))
                .build())
            .build());
  }

  @Test
  public void seeMarkdown() {
    compile(
        "class Bar {",
        "  /** @see **foo *bar*** */",
        "  b() {}",
        "}");

    NominalType type = typeRegistry.getType("Bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertThat(report.getProperties()).isEmpty();
    assertMessages(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("b")
                .setSource(sourceFile("source/foo.js.src.html", 3))
                .setDescription(Comment.getDefaultInstance())
                .addSeeAlso(htmlComment("<p><strong>foo <em>bar</em></strong></p>\n"))
                .build())
            .build());
  }

  @Test
  public void globalFunctionsInheritVisibilityFromFileDefaults() {
    compile(
        "/** @fileoverview A test.",
        " *  @package",
        " */",
        "goog.provide('vis');",
        "",
        "/** @public */ vis.publicFn = function() {};",
        "/** @private */ vis.privateFn = function() {};",
        "",
        "vis.inheritsVisFn = function() {};");

    NominalType type = typeRegistry.getType("vis");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertMessages(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("inheritsVisFn")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(Comment.getDefaultInstance())
                .setVisibility(Visibility.newBuilder().setPackage(true))
                .build())
            .build(),
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("publicFn")
                .setSource(sourceFile("source/foo.js.src.html", 6))
                .setDescription(Comment.getDefaultInstance())
                .build())
            .build());
  }

  @Test
  public void handlesModuleExternTypes() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "var stream = require('stream');",
            "/** @constructor */",
            "function Writer() {}",
            "/** @type {stream.Stream} */",
            "Writer.prototype.stream = null;",
            "exports.Writer = Writer"));

    NominalType type =
        typeRegistry.getType("module$exports$module$$src$modules$foo$bar.Writer");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectInstanceType();
    assertMessages(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("stream")
                        .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 5))
                        .setDescription(Comment.getDefaultInstance()))
                .setType(nullableNamedTypeExpression("stream.Stream")));
  }

  @Test
  public void registersSingleInstanceAsStaticPropertyNotAnotherType_1() {
    compile(
        "goog.provide('foo.Bar');",
        "",
        "/** @constructor */",
        "foo.Bar = function() {};",
        "",
        "foo.Bar.getInstance = function() {",
        "  if (foo.Bar.instance) {",
        "    return foo.Bar.instance;",
        "  }",
        "  return foo.Bar.instance = new foo.Bar;",
        "};");

    NominalType type = typeRegistry.getType("foo.Bar");
    assertThat(typeRegistry.isType("foo.Bar.instance")).isFalse();

    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertMessages(report.getProperties())
        .containsExactly(
            Property.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Bar.instance")
                        .setSource(sourceFile("source/foo.js.src.html", 10))
                        .setDescription(Comment.getDefaultInstance()))
                .setType(namedTypeExpression("foo.Bar", "foo.Bar.html")));
  }
}
