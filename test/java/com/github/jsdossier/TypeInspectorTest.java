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

import com.github.jsdossier.TypeInspector.InstanceProperty;
import com.github.jsdossier.jscomp.NominalType2;
import com.github.jsdossier.proto.Comment;
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

    NominalType2 person = typeRegistry.getType("Person");
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

    NominalType2 person = typeRegistry.getType("Person");
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

    NominalType2 person = typeRegistry.getType("Person");
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

    NominalType2 person = typeRegistry.getType("Person");
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

    NominalType2 hero = typeRegistry.getType("SuperHero");
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

    NominalType2 athlete = typeRegistry.getType("Athlete");
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
    
    NominalType2 type = typeRegistry.getType("Foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }
  
  @Test
  public void getTypeDescription_noCommentFound() {
    compile(
        "class Foo {}");
    
    NominalType2 type = typeRegistry.getType("Foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription()).isEqualTo(Comment.getDefaultInstance());
  }

  @Test
  public void getTypeDescription_namespacedType() {
    compile(
        "goog.provide('foo.bar');",
        "/**",
        " * This is a comment on a type.",
        " */",
        "foo.bar.Baz = class {}");
    
    NominalType2 type = typeRegistry.getType("foo.bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
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
    
    NominalType2 type = typeRegistry.getType("foo.bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_nodeModuleExportedType() {
    util.compile(fs.getPath("/src/modules/foo/bar.js"),
        "/**",
        " * This is a comment on a type.",
        " */",
        "exports.Baz = class {}");
    
    NominalType2 type = typeRegistry.getType("module$$src$modules$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }

  @Test
  public void getTypeDescription_es6ModuleExportedType() {
    util.compile(fs.getPath("/src/modules/foo/bar.js"),
        "/**",
        " * This is a comment on a type.",
        " */",
        "export class Baz {}");
    
    NominalType2 type = typeRegistry.getType("module$src$modules$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
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

    NominalType2 type = typeRegistry.getType("foo.bar.AliasedBaz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
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

    NominalType2 type = typeRegistry.getType("module$$src$modules$foo$baz.AliasedBaz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
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

    NominalType2 type = typeRegistry.getType("foo.bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
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

    NominalType2 type = typeRegistry.getType("module$$src$modules$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
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

    NominalType2 type = typeRegistry.getType("module$src$modules$foo$bar.Baz");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment(
            "<p>This is a comment on a type.\n" +
                "<a href=\"bar_exports_Baz.html\"><code>InternalClazz</code></a></p>\n"));
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

    NominalType2 type = typeRegistry.getType("module$src$modules$foo$bar$baz.Foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>This is a comment on a type.</p>\n"));
  }
  
  @Test
  public void getTypeDescription_emptyDescriptionForGoogProvideNamespaces() {
    compile(
        "/** @fileoverview Hello, world! */",
        "goog.provide('foo.bar');");

    NominalType2 type = typeRegistry.getType("foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription()).isEqualTo(Comment.getDefaultInstance());

    type = typeRegistry.getType("foo.bar");
    inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription()).isEqualTo(Comment.getDefaultInstance());
  }
  
  @Test
  public void getTypeDescription_emptyDescriptionForImplicitNamespacesFromGoogModule() {
    compile(
        "/** @fileoverview Hello, world! */",
        "goog.module('foo.bar');");

    NominalType2 type = typeRegistry.getType("foo");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(Comment.getDefaultInstance());

    type = typeRegistry.getType("foo.bar");
    inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription()).isEqualTo(htmlComment("<p>Hello, world!</p>\n"));
  }
  
  @Test
  public void getTypeDescription_nodeModule() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** @fileoverview Exports {@link A}. */",
        "class A {}",
        "exports.A = A;");

    NominalType2 type = typeRegistry.getType("module$$src$modules$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment(
            "<p>Exports <a href=\"bar_exports_A.html\"><code>A</code></a>.</p>\n"));
  }
  
  @Test
  public void getTypeDescription_es6Module() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** @fileoverview Exports {@link A}. */",
        "class A {}",
        "export {A}");

    NominalType2 type = typeRegistry.getType("module$src$modules$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment(
              "<p>Exports <a href=\"bar_exports_A.html\"><code>A</code></a>.</p>\n"));
  }
  @Test
  public void getTypeDescription_closureModuleWithMainFunction() {
    compile(
        "/** @fileoverview This is the fileoverview. */",
        "goog.module('foo.bar');",
        "/** The main function. */",
        "exports = function() {}");

    NominalType2 type = typeRegistry.getType("foo.bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>The main function.</p>\n"));
  }

  @Test
  public void getTypeDescription_nodeModuleWithMainFunction() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** @fileoverview This is the fileoverview. */",
        "/** The main function. */",
        "exports = function() {}");

    NominalType2 type = typeRegistry.getType("module$$src$modules$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>The main function.</p>\n"));
  }

  @Test
  public void getTypeDescription_es6ModuleWithMainFunction() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** @fileoverview This is the fileoverview. */",
        "/** The main function. */",
        "export default function() {}");

    NominalType2 type = typeRegistry.getType("module$src$modules$foo$bar");
    TypeInspector inspector = typeInspectorFactory.create(type);
    assertThat(inspector.getTypeDescription())
        .isEqualTo(htmlComment("<p>The main function.</p>\n"));
  }
}
