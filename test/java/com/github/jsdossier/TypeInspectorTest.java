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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.github.jsdossier.TypeInspector.InstanceProperty;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.annotations.Stderr;
import com.github.jsdossier.annotations.TypeFilter;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Comment.Token;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Function.Detail;
import com.github.jsdossier.proto.Property;
import com.github.jsdossier.proto.SourceLink;
import com.github.jsdossier.proto.Tags;
import com.github.jsdossier.proto.Visibility;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

import javax.inject.Inject;

/**
 * Tests for {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorTest {

  @Rule
  public GuiceRule guice = new GuiceRule(this, new AbstractModule() {
    @Override protected void configure() {
      install(new CompilerModule());
    }
    @Provides @Input FileSystem provideFs() { return fileSystem; }
    @Provides @Output Path provideOutputDir() { return fileSystem.getPath("/out"); }
    @Provides @Stderr PrintStream provideStderr() { return System.err; }
    @Provides @Modules ImmutableSet<Path> provideModules() { return ImmutableSet.of(); }
    @Provides @ModulePrefix Path provideModulePrefix() { return fileSystem.getPath("/modules"); }
    @Provides @SourcePrefix Path provideSourcePrefix() { return fileSystem.getPath("/src"); }
    @Provides @TypeFilter Predicate<NominalType> provideFilter() {
      return Predicates.alwaysFalse();
    }
  });

  private final FileSystem fileSystem = Jimfs.newFileSystem();

  @Inject CompilerUtil util;
  @Inject TypeInspector typeInspector;
  @Inject TypeRegistry typeRegistry;

  @Test
  public void getInstanceProperties_vanillaClass() {
    util.compile(path("/src/foo.js"),
        "/** @constructor */",
        "function Person() {",
        "  this.name = 'Bob';",
        "  this.sleep = function() {};",
        "}",
        "Person.shouldIgnoreStaticProperty = function() {}",
        "/** @type {number} */",
        "Person.prototype.age;",
        "Person.prototype.eat = function() {};");

    NominalType person = typeRegistry.getNominalType("Person");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getJsType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getJsType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(typeRegistry.getJsType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getJsType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getJsType());
    assertInstanceProperty(sleep).isDefinedOn(person.getJsType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getJsType());
    assertInstanceProperty(eat).isDefinedOn(person.getJsType());
  }

  @Test
  public void getInstanceProperties_googDefinedClass() {
    util.compile(path("/src/foo.js"),
        "var Person = goog.defineClass(null, {",
        "  constructor: function() {",
        "    this.name = 'Bob';",
        "    this.sleep = function() {};",
        "  },",
        "  statics: { shouldIgnoreStaticProperty: function() {} },",
        "  /** @type {number} */age: 123,",
        "  eat: function() {}",
        "});");

    NominalType person = typeRegistry.getNominalType("Person");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getJsType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getJsType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(typeRegistry.getJsType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getJsType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getJsType());
    assertInstanceProperty(sleep).isDefinedOn(person.getJsType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getJsType());
    assertInstanceProperty(eat).isDefinedOn(person.getJsType());
  }

  @Test
  public void getInstanceProperties_vanillaInterface() {
    util.compile(path("/src/foo.js"),
        "/** @interface */",
        "function Person() {}",
        "/** @type {number} */",
        "Person.prototype.age;",
        "/** @type {string} */",
        "Person.prototype.name;",
        "Person.prototype.sleep = function() {}",
        "Person.prototype.eat = function(food) {};");

    NominalType person = typeRegistry.getNominalType("Person");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getJsType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getJsType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(typeRegistry.getJsType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getJsType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getJsType());
    assertInstanceProperty(sleep).isDefinedOn(person.getJsType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getJsType());
    assertInstanceProperty(eat).isDefinedOn(person.getJsType());
  }

  @Test
  public void getInstanceProperties_googDefinedInterface() {
    util.compile(path("/src/foo.js"),
        "/** @interface */",
        "var Person = goog.defineClass(null, {",
        "  statics: { shouldIgnoreStaticProperty: function() {} },",
        "  /** @type {string} */name: 'Bob',",
        "  /** @type {number} */age: 123,",
        "  eat: function() {},",
        "  sleep: function() {}",
        "});");

    NominalType person = typeRegistry.getNominalType("Person");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getJsType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getJsType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(typeRegistry.getJsType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getJsType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getJsType());
    assertInstanceProperty(sleep).isDefinedOn(person.getJsType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getJsType());
    assertInstanceProperty(eat).isDefinedOn(person.getJsType());
  }

  @Test
  public void getInstanceProperties_doesNotIncludePropertiesFromSuperClass() {
    util.compile(path("/src/foo.js"),
        "/** @constructor */",
        "function Person() {}",
        "/** @type {number} */Person.prototype.age;",
        "/** @type {string} */Person.prototype.name;",
        "",
        "/** @constructor @extends {Person} */",
        "function SuperHero() {}",
        "goog.inherits(SuperHero, Person);",
        "/** @type {string} */SuperHero.prototype.power;");

    NominalType hero = typeRegistry.getNominalType("SuperHero");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(hero.getJsType());

    assertThat(properties.keySet()).containsExactly("power");

    InstanceProperty power = properties.get("power");
    assertInstanceProperty(power).isNamed("power");
    assertInstanceProperty(power).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(power).isDefinedOn(hero.getJsType());
  }

  @Test
  public void getInstanceProperties_doesNotIncludePropertiesFromParentInterface() {
    util.compile(path("/src/foo.js"),
        "/** @interface */",
        "function Person() {}",
        "Person.prototype.eat = function() {};",
        "Person.prototype.sleep = function() {};",
        "",
        "/** @interface @extends {Person} */",
        "function Athlete() {}",
        "Athlete.prototype.run = function() {}");

    NominalType athlete = typeRegistry.getNominalType("Athlete");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(athlete.getJsType());

    assertThat(properties.keySet()).containsExactly("run");

    InstanceProperty run = properties.get("run");
    assertInstanceProperty(run).isNamed("run");
    assertInstanceProperty(run).isInstanceMethod(athlete.getJsType());
    assertInstanceProperty(run).isDefinedOn(athlete.getJsType());
  }

  @Test
  public void inspectMembers_returnsAnEmptyReportForNonConstructorInterfaceTypes() {
    util.compile(path("/src/foo.js"),
        "/** @enum {string} */",
        "var Color = {RED: 'red', GREEN: 'green'};");
    NominalType color = typeRegistry.getNominalType("Color");
    TypeInspector.Report report = typeInspector.inspectMembers(color);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
  }

  @Test
  public void collectsPropertiesDefinedOnPrototype_classHasNoSuperType() {
    util.compile(path("/src/foo.js"),
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
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>This person\'s age.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC))
            .setType(Comment.newBuilder()
                .addToken(numberToken()))
            .build());
  }

  @Test
  public void collectsPropertiesDefinedInsideConstructor_classHasNoSuperType() {
    util.compile(path("/src/foo.js"),
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
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>This person\'s age.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC))
            .setType(Comment.newBuilder()
                .addToken(numberToken()))
            .build());
  }

  @Test
  public void collectsPropertiesDefinedOnParentTypePrototype_googInherits() {
    util.compile(path("/src/foo.js"),
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
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>This person\'s age.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC)
                .setDefinedBy(Comment.newBuilder()
                    .addToken(linkToken("Person", "Person.html#age"))))
            .setType(Comment.newBuilder()
                .addToken(numberToken()))
            .build());
  }

  @Test
  public void collectsPropertiesDefinedInParentTypeConstructor() {
    util.compile(path("/src/foo.js"),
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
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>This person\'s age.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC)
                .setDefinedBy(Comment.newBuilder()
                    .addToken(linkToken("Person", "Person.html#age"))))
            .setType(Comment.newBuilder()
                .addToken(numberToken()))
            .build());
  }

  /** TODO(jleyba): Figure this one out! */
  @Test
  public void prototypePropertyOverridesDoNotRegisterAsOverridden() {
    util.compile(path("/src/foo.js"),
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
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>Original comment.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC)
                .setDefinedBy(Comment.newBuilder()
                    .addToken(linkToken("A", "A.html#a"))))
            .setType(Comment.newBuilder()
                .addToken(numberToken()))
            .build());
  }

  @Test
  public void detectsPropertiesOverriddenInTheConstructor() {
    util.compile(path("/src/foo.js"),
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
        "};",
        "goog.inherits(B, A);");

    NominalType typeB = typeRegistry.getNominalType("B");
    TypeInspector.Report reportB = typeInspector.inspectMembers(typeB);
    assertThat(reportB.getFunctions()).isEmpty();
    assertThat(reportB.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("a")
                .setSource(sourceFile("source/foo.js.src.html", 15))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>Custom comment.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC)
                .setOverrides(Comment.newBuilder()
                    .addToken(linkToken("A", "A.html#a"))))
            .setType(Comment.newBuilder()
                .addToken(numberToken()))
            .build());
  }

  @Test
  public void extractsFunctionDataFromPrototype() {
    util.compile(path("/src/foo.js"),
        "/** @constructor */",
        "function A() {}",
        "",
        "/**",
        " * Says hello.",
        " * @return {string} A greeting.",
        " */",
        "A.prototype.sayHi = function() { return 'Hi'; };");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectMembers(a);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>Says hello.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC))
            .setIsConstructor(false)
            .setReturn(Detail.newBuilder()
                .setType(Comment.newBuilder()
                    .addToken(stringToken()))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>A greeting.</p>\n"))))
            .build());
  }

  @Test
  public void extractsFunctionDataFromSuperParentType() {
    util.compile(path("/src/foo.js"),
        "/** @constructor */",
        "function A() {}",
        "",
        "/**",
        " * Says hello.",
        " * @return {string} A greeting.",
        " */",
        "A.prototype.sayHi = function() { return 'Hi'; };",
        "",
        "/** @constructor @extends {A} */",
        "function B() {}",
        "goog.inherits(B, A);");

    NominalType b = typeRegistry.getNominalType("B");
    TypeInspector.Report report = typeInspector.inspectMembers(b);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
                .setSource(sourceFile("source/foo.js.src.html", 8))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>Says hello.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC)
                .setDefinedBy(Comment.newBuilder()
                    .addToken(linkToken("A", "A.html#sayHi"))))
            .setIsConstructor(false)
            .setReturn(Detail.newBuilder()
                .setType(Comment.newBuilder()
                    .addToken(stringToken()))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>A greeting.</p>\n"))))
            .build());
  }

  @Test
  public void extractsOverriddenFunctionData() {
    util.compile(path("/src/foo.js"),
        "/** @constructor */",
        "function A() {}",
        "",
        "/**",
        " * Says hello.",
        " * @return {string} A greeting.",
        " */",
        "A.prototype.sayHi = function() { return 'Hi'; };",
        "",
        "/** @constructor @extends {A} */",
        "function B() {}",
        "goog.inherits(B, A);",
        "",
        "/**",
        " * Enthusiastically says hello.",
        " * @return {string} A super greeting.",
        " */",
        "B.prototype.sayHi = function() { return 'HELLO!'; };");

    NominalType b = typeRegistry.getNominalType("B");
    TypeInspector.Report report = typeInspector.inspectMembers(b);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
                .setSource(sourceFile("source/foo.js.src.html", 18))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>Enthusiastically says hello.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC)
                .setOverrides(Comment.newBuilder()
                    .addToken(linkToken("A", "A.html#sayHi"))))
            .setIsConstructor(false)
            .setReturn(Detail.newBuilder()
                .setType(Comment.newBuilder()
                    .addToken(stringToken()))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>A super greeting.</p>\n"))))
            .build());
  }

  @Test
  public void extractsFunctionDataFromPrototype_functionSpecifiedByAnInterface() {
    util.compile(path("/src/foo.js"),
        "/** @interface */",
        "function A() {}",
        "A.prototype.sayHi = function() {};",
        "",
        "/** @interface */",
        "function B() {}",
        "B.prototype.sayHi = function() {};",
        "",
        "",
        "/**",
        " * @constructor",
        " * @implements {A}",
        " * @implements {B}",
        " */",
        "function Person() {}",
        "",
        "/**",
        " * Says hello.",
        " * @return {string} A greeting.",
        " * @override",
        " */",
        "Person.prototype.sayHi = function() { return 'Hi'; };");

    NominalType person = typeRegistry.getNominalType("Person");
    TypeInspector.Report report = typeInspector.inspectMembers(person);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
                .setSource(sourceFile("source/foo.js.src.html", 22))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>Says hello.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC)
                .addSpecifiedBy(Comment.newBuilder()
                    .addToken(linkToken("A", "A.html#sayHi")))
                .addSpecifiedBy(Comment.newBuilder()
                    .addToken(linkToken("B", "B.html#sayHi"))))
            .setIsConstructor(false)
            .setReturn(Detail.newBuilder()
                .setType(Comment.newBuilder()
                    .addToken(stringToken()))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>A greeting.</p>\n"))))
            .build());
  }

  @Test
  public void extractsOverriddenFunctionData_functionAlsoSpecifiedByInterface() {
    util.compile(path("/src/foo.js"),
        "/** @interface */",
        "function Greeter() {}",
        "",
        "/** @return {string} A generic greeting. */",
        "Greeter.prototype.sayHi = function() {};",
        "",
        "/** @constructor @implements {Greeter} */",
        "function A() {}",
        "",
        "/**",
        " * Says hello.",
        " * @return {string} A greeting.",
        " */",
        "A.prototype.sayHi = function() { return 'Hi'; };",
        "",
        "/** @constructor @extends {A} */",
        "function B() {}",
        "goog.inherits(B, A);",
        "",
        "/**",
        " * Enthusiastically says hello.",
        " * @return {string} A super greeting.",
        " */",
        "B.prototype.sayHi = function() { return 'HELLO!'; };");

    NominalType b = typeRegistry.getNominalType("B");
    TypeInspector.Report report = typeInspector.inspectMembers(b);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
                .setSource(sourceFile("source/foo.js.src.html", 24))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>Enthusiastically says hello.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC)
                .addSpecifiedBy(Comment.newBuilder()
                    .addToken(linkToken("Greeter", "Greeter.html#sayHi")))
                .setOverrides(Comment.newBuilder()
                    .addToken(linkToken("A", "A.html#sayHi"))))
            .setIsConstructor(false)
            .setReturn(Detail.newBuilder()
                .setType(Comment.newBuilder()
                    .addToken(stringToken()))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>A super greeting.</p>\n"))))
            .build());
  }

  @Test
  public void extractsFunctionDataFromConstructor() {
    util.compile(path("/src/foo.js"),
        "/** @constructor */",
        "function A() {",
        "  /**",
        "   * Says hello.",
        "   * @return {string} A greeting.",
        "   */",
        "  this.sayHi = function() { return 'Hi'; };",
        "}");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectMembers(a);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("sayHi")
                .setSource(sourceFile("source/foo.js.src.html", 7))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>Says hello.</p>\n")))
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC))
            .setIsConstructor(false)
            .setReturn(Detail.newBuilder()
                .setType(Comment.newBuilder()
                    .addToken(stringToken()))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>A greeting.</p>\n"))))
            .build());
  }

  @Test
  public void extractsTemplatizedFunctionData() {
    util.compile(path("/src/foo.js"),
        "/** @constructor */",
        "function A() {}",
        "",
        "/**",
        " * @param {T} value .",
        " * @return {T} the value.",
        " * @template T",
        " */",
        "A.prototype.echo = function(value) { return value; };");

    NominalType a = typeRegistry.getNominalType("A");
    TypeInspector.Report report = typeInspector.inspectMembers(a);
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getFunctions()).containsExactly(
        Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("echo")
                .setSource(sourceFile("source/foo.js.src.html", 9))
                .setDescription(Comment.getDefaultInstance())
                .setTags(Tags.newBuilder()
                    .setIsConst(false)
                    .setIsDeprecated(false))
                .setVisibility(Visibility.PUBLIC))
            .setIsConstructor(false)
            .addTemplateName("T")
            .addParameter(Detail.newBuilder()
                .setName("value")
                .setType(Comment.newBuilder()
                    .addToken(textToken("T")))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>.</p>\n"))))
            .setReturn(Detail.newBuilder()
                .setType(Comment.newBuilder()
                    .addToken(textToken("T")))
                .setDescription(Comment.newBuilder()
                    .addToken(htmlToken("<p>the value.</p>\n"))))
                .build());
  }

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }

  private static SourceLink sourceFile(String path, int line) {
    return SourceLink.newBuilder().setPath(path).setLine(line).build();
  }

  private static Token linkToken(String text, String href) {
    return Token.newBuilder().setText(text).setHref(href).build();
  }

  private static Token htmlToken(String html) {
    return Token.newBuilder().setHtml(html).build();
  }

  private static Token textToken(String text) {
    return Token.newBuilder().setText(text).build();
  }

  private static Token numberToken() {
    return Token.newBuilder()
        .setText("number")
        .setHref("https://developer.mozilla.org/en-US/docs/Web/JavaScript/" +
            "Reference/Global_Objects/Number")
        .build();
  }

  private static Token stringToken() {
    return Token.newBuilder()
        .setText("string")
        .setHref("https://developer.mozilla.org/en-US/docs/Web/JavaScript/" +
            "Reference/Global_Objects/String")
        .build();
  }

  private static InstancePropertySubject assertInstanceProperty(final InstanceProperty property) {
    return assertAbout(new SubjectFactory<InstancePropertySubject, InstanceProperty>() {
      @Override
      public InstancePropertySubject getSubject(
          FailureStrategy failureStrategy, InstanceProperty property) {
        return new InstancePropertySubject(failureStrategy, property);
      }
    }).that(property);
  }

  private static final class InstancePropertySubject
      extends Subject<InstancePropertySubject, InstanceProperty> {
    public InstancePropertySubject(FailureStrategy failureStrategy, InstanceProperty subject) {
      super(failureStrategy, subject);
    }

    public void isNamed(String name) {
      assertWithMessage("wrong name").that(getSubject().getName()).isEqualTo(name);
    }

    public void hasType(JSType type) {
      assertWithMessage("wrong type").that(getSubject().getType()).isEqualTo(type);
    }

    public void isInstanceMethod(JSType typeOfThis) {
      JSType type = getSubject().getType();
      assertWithMessage("not a function").that(type.isFunctionType()).isTrue();

      if (typeOfThis.isConstructor() || typeOfThis.isInterface()) {
        typeOfThis = ((FunctionType) typeOfThis).getInstanceType();
      }
      FunctionType function = (FunctionType) type;
      assertWithMessage("wrong type of this").that(function.getTypeOfThis()).isEqualTo(typeOfThis);
    }

    public void isDefinedOn(JSType type) {
      assertWithMessage("wrong defining type").that(getSubject().getDefinedOn()).isEqualTo(type);
    }
  }
}
