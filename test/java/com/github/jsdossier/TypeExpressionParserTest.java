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
import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.FunctionType;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.RecordType;
import com.github.jsdossier.proto.TypeExpression;
import com.github.jsdossier.proto.UnionType;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.jstype.JSType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;

import javax.inject.Inject;

/**
 * Tests for {@link TypeExpressionParser}.
 */
@RunWith(JUnit4.class)
public class TypeExpressionParserTest {

  @Rule
  public GuiceRule guice = GuiceRule.builder(this)
      .setOutputDir("out")
      .setSourcePrefix("source")
      .setModulePrefix("source/modules")
      .setModules("one.js", "two.js", "three.js")
      .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
      .build();

  @Inject
  @Input
  private FileSystem fs;
  @Inject
  private CompilerUtil util;
  @Inject
  private TypeRegistry typeRegistry;
  @Inject
  private LinkFactoryBuilder linkFactoryBuilder;
  @Inject
  private TypeExpressionParserFactory parserFactory;

  @Test
  public void parseTypeDefinition() {
    util.compile(fs.getPath("foo.js"), "/** @typedef {{name: string, age: number}} */var Person;");

    NominalType type = typeRegistry.getType("Person");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSType jsType = util.evaluate(type.getJsDoc().getInfo().getTypedefType());

    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setRecordType(
                    RecordType.newBuilder()
                        .addEntry(
                            RecordType.Entry.newBuilder()
                                .setKey("age")
                                .setValue(numberType()))
                        .addEntry(
                            RecordType.Entry.newBuilder()
                                .setKey("name")
                                .setValue(stringType()))));
  }

  @Test
  public void parseConstructorFunctionReference() {
    util.compile(fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(new: Person)} a A person constructor.",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression jsExpression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(
        TypeExpression.newBuilder()
            .setFunctionType(
                FunctionType.newBuilder()
                    .setIsConstructor(true)
                    .setInstanceType(namedTypeExpression("Person", "Person.html"))));
  }

  @Test
  public void parseFunctionTypeExpressionWithNoReturnType() {
    util.compile(fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(this: Person)} a .",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression jsExpression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(
        TypeExpression.newBuilder()
            .setFunctionType(
                FunctionType.newBuilder()
                    .setInstanceType(namedTypeExpression("Person", "Person.html"))
                    .setReturnType(TypeExpression.newBuilder().setUnknownType(true))));
  }

  @Test
  public void parseFunctionTypeExpressionWithReturnType() {
    util.compile(fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(): Person} a .",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression jsExpression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(
        TypeExpression.newBuilder()
            .setFunctionType(
                FunctionType.newBuilder()
                    .setReturnType(
                        namedTypeExpression("Person", "Person.html")
                            .toBuilder()
                            .setAllowNull(true))));
  }

  @Test
  public void parseFunctionTypeExpressionWithVarArgs() {
    util.compile(fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(...!Person)} a .",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(expression);

    TypeExpression typeExpression = parser.parse(jsType);
    assertMessage(typeExpression).isEqualTo(
        TypeExpression.newBuilder()
            .setFunctionType(
                FunctionType.newBuilder()
                    .addParameter(TypeExpression.newBuilder()
                        .setIsVarargs(true)
                        .setNamedType(namedType("Person", "Person.html")))
                    .setReturnType(TypeExpression.newBuilder().setUnknownType(true))));
  }

  @Test
  public void parseFunctionTypeExpressionWithVarArgs_withContext() {
    util.compile(fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(this: Person, ...!Person)} a .",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression jsExpression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(
        TypeExpression.newBuilder()
            .setFunctionType(
                FunctionType.newBuilder()
                    .setInstanceType(namedTypeExpression("Person", "Person.html"))
                    .addParameter(
                        namedTypeExpression("Person", "Person.html")
                            .toBuilder()
                            .setIsVarargs(true))
                    .setReturnType(TypeExpression.newBuilder().setUnknownType(true))));
  }

  @Test
  public void moduleContextWillHideGlobalTypeNames() {
    util.compile(
        createSourceFile(fs.getPath("source/global.js"),
            "class Person {}"),
        createSourceFile(fs.getPath("source/modules/one.js"),
            "export class Foo {}"),
        createSourceFile(fs.getPath("source/modules/two.js"),
            "import {Foo as Person} from './one';",
            "/**",
            " * @param {!Person} a A person.",
            " * @constructor",
            " */",
            "export function Greeter(a) {}"));

    NominalType type = typeRegistry.getType("module$source$modules$two.Greeter");
    TypeExpressionParser parser = parserFactory.create(
        linkFactoryBuilder.create(type).withTypeContext(type));

    JSType jsType = util.evaluate(type.getJsDoc().getParameter("a").getType());
    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(
        TypeExpression.newBuilder()
            .setNamedType(namedType("Foo", "one.Foo", "one_exports_Foo.html"))
            .build());

    parser = parserFactory.create(
        linkFactoryBuilder.create(typeRegistry.getType("Person")));
    expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(
        TypeExpression.newBuilder()
            .setNamedType(namedType("Person", "Person.html"))
            .build());
  }

  @Test
  public void redundantNonNullQualifierOnPrimitiveIsIncluded() {
    util.compile(fs.getPath("foo.js"), "/** @typedef {!string} */var Name;");

    NominalType type = typeRegistry.getType("Name");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));

    JSTypeExpression jsExpression = type.getJsDoc().getInfo().getTypedefType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(stringType());
  }

  @Test
  public void parseExpressionWithTemplatizedType() {
    util.compile(
        createSourceFile(fs.getPath("source/global.js"),
            "/** @template T */",
            "class Container {}",
            "class Person {",
            "  /** @return {!Container<string>} . */",
            "  name() { return new Container; }",
            "}"));

    NominalType type = typeRegistry.getType("Person");
    JSDocInfo info = type.getType()
        .toMaybeFunctionType()
        .getPrototype()
        .getOwnPropertyJSDocInfo("name");
    JSTypeExpression jsExpression = info.getReturnType();
    JSType jsType = util.evaluate(jsExpression);

    TypeExpressionParser parser = parserFactory.create(
        linkFactoryBuilder.create(type).withTypeContext(type));
    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(
        TypeExpression.newBuilder()
            .setNamedType(
                namedType("Container", "Container.html")
                    .toBuilder()
                    .addTemplateType(stringType())));
  }

  @Test
  public void parseExpressionWithTemplatizedTypeFromAnotherModule() {
    util.compile(
        createSourceFile(fs.getPath("source/modules/one.js"),
            "/** @template T */",
            "export class Container {}"),
        createSourceFile(fs.getPath("source/modules/two.js"),
            "import {Container} from './one';",
            "export class Person {",
            "  /** @return {!Container<string>} . */",
            "  name() { return new Container; }",
            "}"));

    NominalType type = typeRegistry.getType("module$source$modules$two.Person");
    JSDocInfo info = type.getType()
        .toMaybeFunctionType()
        .getPrototype()
        .getOwnPropertyJSDocInfo("name");
    JSTypeExpression jsExpression = info.getReturnType();
    JSType jsType = util.evaluate(jsExpression);

    TypeExpressionParser parser = parserFactory.create(
        linkFactoryBuilder.create(type).withTypeContext(type));
    TypeExpression expression = parser.parse(jsType);
    assertMessage(expression).isEqualTo(
        TypeExpression.newBuilder()
            .setNamedType(
                namedType("Container", "one.Container", "one_exports_Container.html")
                    .toBuilder()
                    .addTemplateType(stringType())));
  }

  @Test
  public void parseExpression_unknownType() {
    TypeExpression expression = compileExpression("?");
    assertMessage(expression)
        .isEqualTo(TypeExpression.newBuilder().setUnknownType(true));
  }

  @Test
  public void parseExpression_anyType() {
    TypeExpression expression = compileExpression("*");
    assertMessage(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setAllowNull(true)
                .setAllowUndefined(true)
                .setAnyType(true));
  }

  @Test
  public void parsesExpression_primitiveType() {
    TypeExpression expression = compileExpression("string");
    assertMessage(expression).isEqualTo(stringType());
  }

  @Test
  public void parsesExpression_nullablePrimitiveType1() {
    TypeExpression expression = compileExpression("?string");
    assertMessage(expression).isEqualTo(stringType().toBuilder().setAllowNull(true));
  }

  @Test
  public void parsesExpression_nullablePrimitiveType2() {
    TypeExpression expression = compileExpression("string?");
    assertMessage(expression).isEqualTo(stringType().toBuilder().setAllowNull(true));
  }

  @Test
  public void parseExpression_primitiveUnionType() {
    TypeExpression expression = compileExpression("string|number");
    assertMessage(expression)
        .isEqualTo(TypeExpression.newBuilder()
            .setUnionType(UnionType.newBuilder()
                .addType(stringType())
                .addType(numberType())));
  }

  @Test
  public void parseExpression_nullablePrimitiveUnionType() {
    TypeExpression expression = compileExpression("?(string|number)");
    assertMessage(expression)
        .isEqualTo(TypeExpression.newBuilder()
            .setAllowNull(true)
            .setUnionType(UnionType.newBuilder()
                .addType(stringType())
                .addType(numberType())));
  }

  @Test
  public void parseExpression_unionWithNullableComponent() {
    TypeExpression expression = compileExpression("(?string|number)");
    assertMessage(expression)
        .isEqualTo(TypeExpression.newBuilder()
            .setAllowNull(true)
            .setUnionType(UnionType.newBuilder()
                .addType(stringType())
                .addType(numberType())));
  }

  @Test
  public void parseExpression_recordTypeWithNullablePrimitive() {
    TypeExpression expression = compileExpression("{age: ?number}");
    assertMessage(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setRecordType(
                    RecordType.newBuilder()
                        .addEntry(
                            RecordType.Entry.newBuilder()
                                .setKey("age")
                                .setValue(
                                    numberType().toBuilder().setAllowNull(true)))));
  }

  @Test
  public void parseExpression_externalEnumReference() {
    ImmutableList<SourceFile> externs = ImmutableList.of(
        createSourceFile(fs.getPath("externs.js"),
            "/** @enum {string} */",
            "var Data = {ONE: 'one'};"));

    ImmutableList<SourceFile> sources = ImmutableList.of(
        createSourceFile(fs.getPath("one.js"),
            "/**",
            " * @param {Data} x .",
            " * @constructor",
            " */",
            "function Widget(x) {}"));
    util.compile(externs, sources);

    NominalType type = typeRegistry.getType("Widget");
    JSTypeExpression jsTypeExpression = type.getJsDoc().getParameter("x").getType();
    JSType jsType = util.evaluate(jsTypeExpression);
    TypeExpression expression = parserFactory.create(linkFactoryBuilder.create(type)).parse(jsType);
    assertMessage(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setNamedType(NamedType.newBuilder().setName("Data")));
  }

  private TypeExpression compileExpression(String expressionText) {
    util.compile(
        createSourceFile(fs.getPath("one.js"),
            "/**",
            " * @param {" + expressionText + "} x .",
            " * @constructor",
            " */",
            "function Widget(x) {}"));
    NominalType type = typeRegistry.getType("Widget");
    JSTypeExpression expression = type.getJsDoc().getParameter("x").getType();
    JSType jsType = util.evaluate(expression);
    return parserFactory.create(linkFactoryBuilder.create(type)).parse(jsType);
  }

  private static TypeExpression numberType() {
    return TypeExpression.newBuilder()
        .setNamedType(NamedType.newBuilder()
            .setExtern(true)
            .setName("number")
            .setHref("https://developer.mozilla.org/en-US/docs/Web/" +
                "JavaScript/Reference/Global_Objects/Number"))
        .build();
  }

  private static TypeExpression stringType() {
    return TypeExpression.newBuilder()
        .setNamedType(NamedType.newBuilder()
            .setExtern(true)
            .setName("string")
            .setHref("https://developer.mozilla.org/en-US/docs/Web/" +
                "JavaScript/Reference/Global_Objects/String"))
        .build();
  }

  private static TypeExpression namedTypeExpression(String text, String href) {
    return TypeExpression.newBuilder().setNamedType(namedType(text, href)).build();
  }

  private static NamedType namedType(String name, String href) {
    return NamedType.newBuilder()
        .setName(name)
        .setHref(href)
        .build();
  }

  private static NamedType namedType(String name, String qualifiedName, String href) {
    return NamedType.newBuilder()
        .setName(name)
        .setQualifiedName(qualifiedName)
        .setHref(href)
        .build();
  }
}
