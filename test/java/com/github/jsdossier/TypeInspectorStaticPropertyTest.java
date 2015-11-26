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
import com.github.jsdossier.proto.Property;
import com.github.jsdossier.proto.Tags;
import com.google.common.base.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for extracting property information with a {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorStaticPropertyTest extends AbstractTypeInspectorTest {

  @Test
  public void doesNotReturnNestedTypeAsProperty() {
    compile(
        "/** @constructor */",
        "function A() {}",
        "",
        "A.B = class {};",
        "",
        "/** @constructor */",
        "A.C = function() {};",
        "",
        "/** @interface */",
        "A.D = function() {};",
        "",
        "/** @enum {string} */",
        "A.E = {};");

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);

    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getProperties()).isEmpty();
  }

  @Test
  public void doesNotReturnNestedNamespacesAsProperty() {
    compile(
        "goog.provide('foo');",
        "goog.provide('foo.bar');",
        "",
        "foo.x = 123;",
        "foo.bar.y = 456;");

    NominalType type = typeRegistry.getType("foo");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("x")
                .setSource(sourceFile("source/foo.js.src.html", 4))
                .setDescription(Comment.getDefaultInstance()))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void doesNotReturnNestedNamespacesAsProperty_subNamespaceHasBeenFiltered() {
    guice.toBuilder()
        .setTypeNameFilter(new Predicate<String>() {
          @Override
          public boolean apply(String input) {
            return "foo.bar".equals(input);
          }
        })
        .build()
        .createInjector()
        .injectMembers(this);

    compile(
        "goog.provide('foo');",
        "goog.provide('foo.bar');",
        "",
        "foo.x = 123;",
        "foo.bar.y = 456;");

    assertThat(typeRegistry.isType("foo.bar")).isFalse();

    NominalType type = typeRegistry.getType("foo");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("x")
                .setSource(sourceFile("source/foo.js.src.html", 4))
                .setDescription(Comment.getDefaultInstance()))
            .setType(numberTypeComment())
            .build());
  }

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

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectType();
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

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectType();
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

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectType();
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

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectType();
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

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectType();
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
  public void linkReferencesAreParsedRelativeToOwningType() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/globals.js"),
            "/** Global person. */",
            "class Person {}"),
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "",
            "/** Hides global person. */",
            "class Person {}",
            "exports.Person = Person;",
            "",
            "/** Link to a {@link Person}. */",
            "exports.limit = 123;"));

    NominalType type = typeRegistry.getType("module$$src$modules$foo$bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("limit")
                .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 7))
                .setDescription(htmlComment(
                    "<p>Link to a <a href=\"bar_exports_Person.html\">"
                        + "<code>Person</code></a>.</p>\n"))
                .setTags(Tags.newBuilder()
                    .setIsConst(true)))
            .setType(numberTypeComment())
            .build());
  }

  @Test
  public void identifiesDefaultModuleExport() {
    util.compile(
        fs.getPath("/src/modules/foo/baz.js"),
        "/** Hello, world! */",
        "const x = 1234;",
        "export default x;");

    NominalType type = typeRegistry.getType("module$src$modules$foo$baz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).containsExactly(
        Property.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("default")
                .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 3))
                .setDescription(htmlComment("<p>Hello, world!</p>\n"))
                .setTags(Tags.newBuilder().setIsDefault(true))
                .build())
            .setType(numberTypeComment())
            .build());
  }
}
