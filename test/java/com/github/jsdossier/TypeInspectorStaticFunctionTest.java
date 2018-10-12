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
import static org.junit.Assert.fail;

import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Function.Detail;
import com.github.jsdossier.proto.Tags;
import com.github.jsdossier.proto.TypeExpression;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for extracting function information with a {@link TypeInspector}. */
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

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("A.sayHi")
                        .setSource(sourceFile("source/foo.js.src.html", 10))
                        .setDescription(htmlComment("<p>Says hello.</p>\n")))
                .addParameter(
                    Detail.newBuilder()
                        .setName("name")
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>The person to greet.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>A greeting.</p>\n")))
                .addThrown(
                    Detail.newBuilder()
                        .setType(nullableErrorTypeExpression())
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

    NominalType a = typeRegistry.getType("A");
    TypeInspector typeInspector = typeInspectorFactory.create(a);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("A.sayHi")
                        .setSource(sourceFile("source/foo.js.src.html", 10))
                        .setDescription(htmlComment("<p>Says hello.</p>\n")))
                .addParameter(
                    Detail.newBuilder()
                        .setName("name")
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>The person to greet.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>A greeting.</p>\n")))
                .addThrown(
                    Detail.newBuilder()
                        .setType(nullableErrorTypeExpression())
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

    NominalType type = typeRegistry.getType("Color");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Color.darken")
                        .setSource(sourceFile("source/foo.js.src.html", 10))
                        .setDescription(htmlComment("<p>Darkens a color.</p>\n")))
                .addParameter(
                    Detail.newBuilder()
                        .setName("c")
                        .setType(namedTypeExpression("Color", "Color.html"))
                        .setDescription(htmlComment("<p>The color to darken.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(namedTypeExpression("Color", "Color.html"))
                        .setDescription(htmlComment("<p>The darkened color.</p>\n")))
                .addThrown(
                    Detail.newBuilder()
                        .setType(nullableErrorTypeExpression())
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

    NominalType type = typeRegistry.getType("Color");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("darken")
                        .setSource(sourceFile("source/foo.js.src.html", 9))
                        .setDescription(htmlComment("<p>Darkens a color.</p>\n")))
                .addParameter(
                    Detail.newBuilder()
                        .setName("c")
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>The color to darken.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>The darkened color.</p>\n")))
                .addThrown(
                    Detail.newBuilder()
                        .setType(nullableErrorTypeExpression())
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

    NominalType type = typeRegistry.getType("foo");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("bar")
                        .setSource(sourceFile("source/foo.js.src.html", 8))
                        .setDescription(Comment.getDefaultInstance()))
                .addParameter(
                    Detail.newBuilder()
                        .setName("v")
                        .setType(nullableNamedTypeExpression("TYPE"))
                        .setDescription(htmlComment("<p>A value.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(namedTypeExpression("TYPE"))
                        .setDescription(htmlComment("<p>The value.</p>\n")))
                .addTemplateName("TYPE")
                .build());
  }

  @Test
  public void functionThrowsGenericType() {
    compile(
        "goog.provide('foo');",
        "",
        "/** @template TYPE */",
        "class GenericError extends Error {",
        "  /** @param {!TYPE} value . */",
        "  constructor(value) {",
        "    super();",
        "    this.value = value;",
        "  }",
        "}",
        "",
        "/**",
        " * @param {THROWN_TYPE} input .",
        " * @throws {GenericError<THROWN_TYPE>}",
        " * @template THROWN_TYPE",
        " */",
        "foo.throw = function(input) {};");

    NominalType type = typeRegistry.getType("foo");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("throw")
                        .setSource(sourceFile("source/foo.js.src.html", 17))
                        .setDescription(Comment.getDefaultInstance()))
                .addParameter(
                    Detail.newBuilder()
                        .setName("input")
                        .setType(nullableNamedTypeExpression("THROWN_TYPE"))
                        .setDescription(htmlComment("<p>.</p>\n")))
                .addThrown(
                    Detail.newBuilder()
                        .setType(
                            unionType(
                                TypeExpression.newBuilder()
                                    .setNamedType(
                                        addTemplateTypes(
                                            namedType("GenericError", "GenericError.html"),
                                            nullableNamedTypeExpression("THROWN_TYPE")))
                                    .build(),
                                TypeExpressions.NULL_TYPE)))
                .addTemplateName("THROWN_TYPE")
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

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Clazz.bar")
                        .setSource(sourceFile("source/foo.js.src.html", 12))
                        .setDescription(
                            htmlComment(
                                "<p>Link to <a href=\"Clazz.html#Clazz.foo\">"
                                    + "<code>Clazz.foo</code></a>.</p>\n")))
                .build(),
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Clazz.foo")
                        .setSource(sourceFile("source/foo.js.src.html", 7))
                        .setDescription(
                            htmlComment(
                                "<p>Link to <a href=\"Clazz.html#Clazz.bar\">"
                                    + "<code>Clazz.bar</code></a>.</p>\n")))
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

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Clazz.foo")
                        .setSource(sourceFile("source/foo.js.src.html", 7))
                        .setDescription(
                            htmlComment(
                                "<p>Link to <a href=\"OtherClazz.html#OtherClazz.bar\">"
                                    + "<code>OtherClazz.bar</code></a>.</p>\n")))
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

    NominalType type = typeRegistry.getType("Clazz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Clazz.foo")
                        .setSource(sourceFile("source/foo.js.src.html", 7))
                        .setDescription(
                            htmlComment(
                                "<p>Link to <a href=\"Clazz.html#bar\">"
                                    + "<code>#bar</code></a>.</p>\n")))
                .build());
  }

  @Test
  public void doesNotIdentifyConstructorPropertyAsStaticFunction1() {
    compile(
        "/** @constructor */",
        "var One = function () {};",
        "",
        "/** @constructor */",
        "One.Two = function() {};");

    NominalType type = typeRegistry.getType("One");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
  }

  @Test
  public void doesNotIdentifyConstructorPropertyAsStaticFunction2() {
    compile("goog.provide('foo');", "", "/** @constructor */", "foo.One = function() {};");

    NominalType type = typeRegistry.getType("foo");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
  }

  @Test
  public void doesNotIdentifyConstructorPropertyAsStaticFunction3() {
    guice
        .toBuilder()
        .setTypeNameFilter("foo.One"::equals)
        .build()
        .createInjector()
        .injectMembers(this);

    compile(
        "goog.provide('foo');",
        "",
        "/** @constructor */",
        "foo.One = function() {};",
        "",
        "/** @return {!foo.One} A new object. */",
        "foo.newOne = function() { return new foo.One; };");

    NominalType type = typeRegistry.getType("foo");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(getOnlyElement(report.getFunctions()))
        .isEqualTo(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("newOne")
                        .setSource(sourceFile("source/foo.js.src.html", 7))
                        .setDescription(Comment.getDefaultInstance()))
                .setReturn(
                    Detail.newBuilder()
                        .setDescription(htmlComment("<p>A new object.</p>\n"))
                        // This should not have a link b/c foo.One is filtered out.
                        .setType(namedTypeExpression("foo.One", "foo.One.html")))
                .build());
  }

  @Test
  public void doesNotRecordConstructorCallAsStaticFunction() {
    compile(
        DEFINE_INHERITS,
        "/** @constructor */",
        "var One = function () {};",
        "",
        "/** @constructor @extends {One} */",
        "var Two = function() { One.call(this); };",
        "inherits(Two, One);");

    NominalType type = typeRegistry.getType("One");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
  }

  @Test
  public void doesNotRecordConstructorCallAsStaticFunction_es6() {
    compile(
        "class One {}",
        "",
        "class Two extends One {",
        "  constructor() {",
        "    super();",
        "    One.call(this);",
        "  }",
        "}");

    NominalType type = typeRegistry.getType("One");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions()).isEmpty();
  }

  @Test
  public void usesDocsFromModuleVarIfExportedInstanceHasNoDocs_nodeModule() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** Hello, world! */",
        "function greet() {}",
        "exports.greet = greet");
    NominalType type = typeRegistry.getType("module$exports$module$src$modules$foo$bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("greet")
                        .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 2))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n")))
                .build());
  }

  @Test
  public void usesDocsFromModuleVarIfExportedInstanceHasNoDocs_es6Module() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** Hello, world! */",
        "function greet() {}",
        "export {greet}");
    NominalType type = typeRegistry.getType("module$src$modules$foo$bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("greet")
                        .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 3))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n")))
                .build());
  }

  @Test
  public void usesDocsFromModuleVarIfExportedInstanceHasNoDocs_nodeModuleForwardsExport() {
    util.compile(
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "/** Hello, world! */",
            "function greet() {}",
            "exports.greet = greet"),
        createSourceFile(
            fs.getPath("/src/modules/foo/baz.js"),
            "var bar = require('./bar');",
            "exports.greeting1 = bar.greet;",
            "",
            "const greet = require('./bar').greet;",
            "exports.greeting2 = greet;"));

    NominalType type = typeRegistry.getType("module$exports$module$src$modules$foo$baz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();

    List<Function> functions =
        report
            .getFunctions()
            .stream()
            .sorted(Comparator.comparing(fn -> fn.getBase().getName()))
            .collect(Collectors.toList());
    assertThat(functions).hasSize(2);
    assertThat(functions.get(0))
        .isEqualTo(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("greeting1")
                        .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 2))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n")))
                .build());
    assertThat(functions.get(1))
        .isEqualTo(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("greeting2")
                        .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 4))
                        .setDescription(Comment.getDefaultInstance()))
                .build());
  }

  @Test
  public void linkReferencesAreParsedRelativeToOwningType() {
    util.compile(
        createSourceFile(fs.getPath("/src/globals.js"), "/** Global person. */", "class Person {}"),
        createSourceFile(
            fs.getPath("/src/modules/foo/bar.js"),
            "",
            "/** Hides global person. */",
            "class Person {}",
            "exports.Person = Person;",
            "",
            "/** Greet a {@link Person}. */",
            "exports.greet = function() {};"));

    NominalType type = typeRegistry.getType("module$exports$module$src$modules$foo$bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("greet")
                        .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 7))
                        .setDescription(
                            htmlComment(
                                "<p>Greet a <a href=\"bar_exports_Person.html\">"
                                    + "<code>Person</code></a>.</p>\n")))
                .build());
  }

  @Test
  public void inspectGoogDefinedClass() {
    util.compile(
        fs.getPath("/src/foo.js"),
        "goog.provide('foo.bar');",
        "foo.bar.Baz = goog.defineClass(null, {",
        "  constructor: function() {},",
        "  statics: {",
        "    /** Does stuff. */",
        "    go: function() {}",
        "  }",
        "});");

    NominalType type = typeRegistry.getType("foo.bar.Baz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Baz.go")
                        .setSource(sourceFile("source/foo.js.src.html", 6))
                        .setDescription(htmlComment("<p>Does stuff.</p>\n")))
                .build());
  }

  @Test
  public void inspectGoogDefinedInterface() {
    util.compile(
        fs.getPath("/src/foo.js"),
        "goog.provide('foo.bar');",
        "/** @interface */",
        "foo.bar.Baz = goog.defineClass(null, {",
        "  statics: {",
        "    /** Does stuff. */",
        "    go: function() {}",
        "  }",
        "});");

    NominalType type = typeRegistry.getType("foo.bar.Baz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Baz.go")
                        .setSource(sourceFile("source/foo.js.src.html", 6))
                        .setDescription(htmlComment("<p>Does stuff.</p>\n")))
                .build());
  }

  @Test
  public void exportedEs6Class_nodeModule() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** A person. */",
        "class Person {",
        "  constructor(name) {}",
        "",
        "  /**",
        "   * Creates a person.",
        "   * @param {string} name The person's name.",
        "   * @return {!Person} The new person.",
        "   */",
        "  static create(name) {",
        "    return new Person(name);",
        "  }",
        "}",
        "exports.Person = Person;");

    NominalType type = typeRegistry.getType("module$exports$module$src$modules$foo$bar.Person");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Person.create")
                        .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 10))
                        .setDescription(htmlComment("<p>Creates a person.</p>\n")))
                .addParameter(
                    Detail.newBuilder()
                        .setName("name")
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>The person&#39;s name.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(
                            namedTypeExpression(
                                "Person", "foo/bar.Person", "bar_exports_Person.html"))
                        .setDescription(htmlComment("<p>The new person.</p>\n")))
                .build());
  }

  @Test
  public void exportedEs6Class_es6Module() {
    util.compile(
        fs.getPath("/src/modules/foo/bar.js"),
        "/** A person. */",
        "class Person {",
        "  constructor(name) {}",
        "",
        "  /**",
        "   * Creates a person.",
        "   * @param {string} name The person's name.",
        "   * @return {!Person} The new person.",
        "   */",
        "  static create(name) {",
        "    return new Person(name);",
        "  }",
        "}",
        "export {Person};");

    NominalType type = typeRegistry.getType("module$src$modules$foo$bar.Person");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Person.create")
                        .setSource(sourceFile("../../source/modules/foo/bar.js.src.html", 10))
                        .setDescription(htmlComment("<p>Creates a person.</p>\n")))
                .addParameter(
                    Detail.newBuilder()
                        .setName("name")
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>The person&#39;s name.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(
                            namedTypeExpression(
                                "Person", "foo/bar.Person", "bar_exports_Person.html"))
                        .setDescription(htmlComment("<p>The new person.</p>\n")))
                .build());
  }

  @Test
  public void typeExpressionsCanReferToAnotherModuleByRelativePath_es6Modules() {
    util.compile(
        createSourceFile(fs.getPath("/src/modules/foo/bar.js"), "export class X {}"),
        createSourceFile(
            fs.getPath("/src/modules/foo/baz.js"),
            "/** @param {./bar.X} x an object. */",
            "export function go(x) {}"));

    NominalType type = typeRegistry.getType("module$src$modules$foo$baz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("go")
                        .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 2))
                        .setDescription(Comment.getDefaultInstance()))
                .addParameter(
                    Detail.newBuilder()
                        .setName("x")
                        .setType(
                            nullableNamedTypeExpression("X", "foo/bar.X", "bar_exports_X.html"))
                        .setDescription(htmlComment("<p>an object.</p>\n")))
                .build());
  }

  @Test
  public void typeExpressionsCanReferToAnotherModuleByRelativePath_nodeModules() {
    util.compile(
        createSourceFile(fs.getPath("/src/modules/foo/bar.js"), "exports.X = class {}"),
        createSourceFile(
            fs.getPath("/src/modules/foo/baz.js"),
            "/** @param {!./bar.X} x an object. */",
            "exports.go = function(x) {};"));

    NominalType type = typeRegistry.getType("module$exports$module$src$modules$foo$baz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getProperties()).isEmpty();
    assertThat(report.getCompilerConstants()).isEmpty();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("go")
                        .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 2))
                        .setDescription(Comment.getDefaultInstance()))
                .addParameter(
                    Detail.newBuilder()
                        .setName("x")
                        .setType(namedTypeExpression("X", "foo/bar.X", "bar_exports_X.html"))
                        .setDescription(htmlComment("<p>an object.</p>\n")))
                .build());
  }

  @Test
  public void subclassesInheritEs6StaticProperties() {
    util.compile(
        fs.getPath("/src/modules/foo/baz.js"),
        "export class X { static go() {}}",
        "export class Y extends X {}");

    NominalType type = typeRegistry.getType("module$src$modules$foo$baz.Y");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Y.go")
                        .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 1))
                        .setDescription(Comment.getDefaultInstance()))
                .build());
  }

  @Test
  public void subclassesInheritEs6StaticProperties_docsReferenceInheritedProperty() {
    util.compile(
        fs.getPath("/src/modules/foo/baz.js"),
        "export class X {",
        "  constructor() { this.x = 123; }",
        "",
        "  /** Reference to {@link #x} */",
        "  static go() {}",
        "}",
        "export class Y extends X {}");

    NominalType type = typeRegistry.getType("module$src$modules$foo$baz.Y");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Y.go")
                        .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 5))
                        .setDescription(
                            htmlComment(
                                "<p>Reference to <a href=\"baz_exports_Y.html#x\"><code>#x</code></a></p>\n")))
                .build());
  }

  @Test
  public void identifiesDefaultModuleExport() {
    util.compile(
        fs.getPath("/src/modules/foo/baz.js"),
        "/** Hello, world! */",
        "export default function go() {}");

    NominalType type = typeRegistry.getType("module$src$modules$foo$baz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("default")
                        .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 2))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n"))
                        .setTags(Tags.newBuilder().setIsDefault(true)))
                .build());
  }

  @Test
  public void doesNotFlagNamespacePropertiesWithNameDefaultAsDefaultModuleExports() {
    util.compile(
        fs.getPath("/src/ns.js"),
        "goog.provide('ns');",
        "/** Hello, world! */",
        "ns.default = function go() {}");

    NominalType type = typeRegistry.getType("ns");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("default")
                        .setSource(sourceFile("source/ns.js.src.html", 3))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n")))
                .build());
  }

  @Test
  public void doesNotFlagNodeExportedDefaultPropertyAsDefaultModuleExports() {
    util.compile(
        fs.getPath("/src/modules/foo/baz.js"),
        "/** Hello, world! */",
        "exports.default = function go() {}");

    NominalType type = typeRegistry.getType("module$exports$module$src$modules$foo$baz");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("default")
                        .setSource(sourceFile("../../source/modules/foo/baz.js.src.html", 2))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n")))
                .build());
  }

  @Test
  public void doesNotFlagClosureExportedDefaultPropertyAsDefaultModuleExports() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.module('foo.bar');",
        "/** Hello, world! */",
        "exports.default = function go() {}");

    NominalType type = typeRegistry.getType("module$exports$foo$bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("default")
                        .setSource(sourceFile("source/foo/bar.js.src.html", 3))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n")))
                .build());
  }

  @Test
  public void recordsFunctionDataExportedByClosureModule_1() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.module('foo.bar');",
        "/**",
        " * Hello, world!",
        " * @param {number} x The input.",
        " * @return {string} The output.",
        " */",
        "exports.go = function(x) { return x + ''; };");

    NominalType type = typeRegistry.getType("module$exports$foo$bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("go")
                        .setSource(sourceFile("source/foo/bar.js.src.html", 7))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n")))
                .addParameter(
                    Detail.newBuilder()
                        .setName("x")
                        .setType(numberTypeExpression())
                        .setDescription(htmlComment("<p>The input.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>The output.</p>\n")))
                .build());
  }

  @Test
  public void recordsFunctionDataExportedByClosureModule_2() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.module('foo.bar');",
        "/**",
        " * Hello, world!",
        " * @param {number} x The input.",
        " * @return {string} The output.",
        " */",
        "function go(x) { return x + ''; }",
        "exports = {go: go};");

    NominalType type = typeRegistry.getType("module$exports$foo$bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("go")
                        .setSource(sourceFile("source/foo/bar.js.src.html", 7))
                        .setDescription(htmlComment("<p>Hello, world!</p>\n")))
                .addParameter(
                    Detail.newBuilder()
                        .setName("x")
                        .setType(numberTypeExpression())
                        .setDescription(htmlComment("<p>The input.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(stringTypeExpression())
                        .setDescription(htmlComment("<p>The output.</p>\n")))
                .build());
  }

  @Test
  public void parameterWithExplicitlyUnknownType() {
    compile(
        "class Worker {",
        "  /** @param {?} input Any type will do. */",
        "  static process(input) {",
        "  }",
        "}");

    NominalType type = typeRegistry.getType("Worker");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Worker.process")
                        .setSource(sourceFile("source/foo.js.src.html", 3))
                        .setDescription(Comment.getDefaultInstance()))
                .addParameter(
                    Detail.newBuilder()
                        .setName("input")
                        .setType(TypeExpression.newBuilder().setUnknownType(true))
                        .setDescription(htmlComment("<p>Any type will do.</p>\n")))
                .build());
  }

  @Test
  public void varargsParameter_moduleExportedFunction() {
    util.compile(
        fs.getPath("/src/foo/bar.js"),
        "goog.module('foo.bar');",
        "/** @param {...number} numbers The numbers to add. */",
        "exports.add = function(numbers) {};");

    NominalType type = typeRegistry.getType("module$exports$foo$bar");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("add")
                        .setSource(sourceFile("source/foo/bar.js.src.html", 3))
                        .setDescription(Comment.getDefaultInstance()))
                .addParameter(
                    Detail.newBuilder()
                        .setName("numbers")
                        .setType(numberTypeExpression().toBuilder().setIsVarargs(true))
                        .setDescription(htmlComment("<p>The numbers to add.</p>\n")))
                .build());
  }

  @Test
  public void varargsParameter_classStaticFunction() {
    compile(
        "class Worker {",
        "  /** @param {...number} numbers The numbers to add. */",
        "  static add(...numbers) {",
        "  }",
        "}");

    NominalType type = typeRegistry.getType("Worker");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(report.getFunctions())
        .containsExactly(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Worker.add")
                        .setSource(sourceFile("source/foo.js.src.html", 3))
                        .setDescription(Comment.getDefaultInstance()))
                .addParameter(
                    Detail.newBuilder()
                        .setName("numbers")
                        .setType(numberTypeExpression().toBuilder().setIsVarargs(true))
                        .setDescription(htmlComment("<p>The numbers to add.</p>\n")))
                .build());
  }

  @Test
  public void asyncStaticFunction_withReturnJsDoc() {
    compile(
        "class Worker {",
        "  /**",
        "   * @param {...number} numbers The numbers to add. ",
        "   * @return {!Promise} the sum.",
        "   */",
        "  static async add(...numbers) {",
        "  }",
        "}");

    NominalType type = typeRegistry.getType("Worker");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    assertThat(getOnlyElement(report.getFunctions()))
        .isEqualTo(
            Function.newBuilder()
                .setBase(
                    BaseProperty.newBuilder()
                        .setName("Worker.add")
                        .setSource(sourceFile("source/foo.js.src.html", 6))
                        .setDescription(Comment.getDefaultInstance()))
                .addParameter(
                    Detail.newBuilder()
                        .setName("numbers")
                        .setType(numberTypeExpression().toBuilder().setIsVarargs(true))
                        .setDescription(htmlComment("<p>The numbers to add.</p>\n")))
                .setReturn(
                    Detail.newBuilder()
                        .setType(
                            TypeExpression.newBuilder()
                                .setNamedType(namedType("Promise").toBuilder().setExtern(true)))
                        .setDescription(htmlComment("<p>the sum.</p>\n")))
                .build());
  }

  @Test
  public void asyncStaticFunction_noReturnJsDoc() {
    compile(
        "class Worker {",
        "  /**",
        "   * @param {...number} numbers The numbers to add. ",
        "   */",
        "  static async add(...numbers) {",
        "  }",
        "}");

    NominalType type = typeRegistry.getType("Worker");
    TypeInspector typeInspector = typeInspectorFactory.create(type);
    TypeInspector.Report report = typeInspector.inspectType();
    try {
      assertThat(getOnlyElement(report.getFunctions()))
          .isEqualTo(
              Function.newBuilder()
                  .setBase(
                      BaseProperty.newBuilder()
                          .setName("Worker.add")
                          .setSource(sourceFile("source/foo.js.src.html", 5))
                          .setDescription(Comment.getDefaultInstance()))
                  .addParameter(
                      Detail.newBuilder()
                          .setName("numbers")
                          .setType(numberTypeExpression().toBuilder().setIsVarargs(true))
                          .setDescription(htmlComment("<p>The numbers to add.</p>\n")))
                  .setReturn(
                      Detail.newBuilder()
                          .setType(
                              TypeExpression.newBuilder()
                                  .setNamedType(namedType("Promise").toBuilder().setExtern(true))))
                  .build());
      fail("test needs to be updated to remove try-catch!");
    } catch (AssertionError expected) {
      // TODO(jleyba): should know that async functions return a promise.
      assertThat(getOnlyElement(report.getFunctions()))
          .isEqualTo(
              Function.newBuilder()
                  .setBase(
                      BaseProperty.newBuilder()
                          .setName("Worker.add")
                          .setSource(sourceFile("source/foo.js.src.html", 5))
                          .setDescription(Comment.getDefaultInstance()))
                  .addParameter(
                      Detail.newBuilder()
                          .setName("numbers")
                          .setType(numberTypeExpression().toBuilder().setIsVarargs(true))
                          .setDescription(htmlComment("<p>The numbers to add.</p>\n")))
                  .build());
    }
  }
}
