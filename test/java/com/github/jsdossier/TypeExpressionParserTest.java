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
import static com.github.jsdossier.TypeExpressionParser.ANY_TYPE;
import static com.github.jsdossier.TypeExpressionParser.UNKNOWN_TYPE;
import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.FunctionType;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.RecordType;
import com.github.jsdossier.proto.TypeExpression;
import com.github.jsdossier.proto.UnionType;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
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

  @Inject @Input private FileSystem fs;
  @Inject private CompilerUtil util;
  @Inject private TypeRegistry typeRegistry;
  @Inject private LinkFactoryBuilder linkFactoryBuilder;
  @Inject private TypeExpressionParserFactory parserFactory;

  @Test
  public void parseTypeDefinition() {
    util.compile(fs.getPath("foo.js"), "/** @typedef {{name: string, age: number}} */var Person;");

    NominalType type = typeRegistry.getType("Person");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));

    Comment comment = parser.parse(type.getJsDoc().getInfo().getTypedefType());
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("{name: "))
            .addToken(stringLink())
            .addToken(text(", age: "))
            .addToken(numberLink())
            .addToken(text("}"))
            .build());

    TypeExpression expression = parser.parseExpression(type.getJsDoc().getInfo().getTypedefType());
    assertMessage(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setRecordType(
                    RecordType.newBuilder()
                        .addEntry(
                            RecordType.Entry.newBuilder()
                                .setKey("name")
                                .setValue(stringType()))
                        .addEntry(
                            RecordType.Entry.newBuilder()
                                .setKey("age")
                                .setValue(numberType()))));
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
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    Comment comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("function(new: "))
            .addToken(link("Person", "Person.html"))
            .addToken(text(")"))
            .build());
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
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    Comment comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("function(this: "))
            .addToken(link("Person", "Person.html"))
            .addToken(text(")"))
            .build());
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
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    Comment comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("function(): "))
            .addToken(link("Person", "Person.html"))
            .build());
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

    TypeExpression typeExpression = parser.parseExpression(expression);
    assertMessage(typeExpression).isEqualTo(
        TypeExpression.newBuilder()
        .setFunctionType(FunctionType.newBuilder()
            .addParameter(TypeExpression.newBuilder()
                .setIsVarargs(true)
                .setNamedType(namedType("Person", "Person.html")))));

    Comment comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("function(...!"))
            .addToken(link("Person", "Person.html"))
            .addToken(text(")"))
            .build());
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
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    Comment comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("function(this: "))
            .addToken(link("Person", "Person.html"))
            .addToken(text(", ...!"))
            .addToken(link("Person", "Person.html"))
            .addToken(text(")"))
            .build());
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
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    Comment comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("!"))
            .addToken(link("Foo", "one_exports_Foo.html"))
            .build());

    parser = parserFactory.create(
        linkFactoryBuilder.create(typeRegistry.getType("Person")));
    comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("!"))
            .addToken(link("Person", "Person.html"))
            .build());
  }

  @Test
  public void redundantNonNullQualifierOnPrimitiveIsIncluded() {
    util.compile(fs.getPath("foo.js"), "/** @typedef {!string} */var Name;");

    NominalType type = typeRegistry.getType("Name");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));

    Comment comment = parser.parse(type.getJsDoc().getInfo().getTypedefType());
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("!"))
            .addToken(stringLink())
            .build());
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
    JSTypeExpression expression = info.getReturnType();

    TypeExpressionParser parser = parserFactory.create(
        linkFactoryBuilder.create(type).withTypeContext(type));
    Comment comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("!"))
            .addToken(link("Container", "Container.html"))
            .addToken(text("<"))
            .addToken(stringLink())
            .addToken(text(">"))
            .build());
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
    JSTypeExpression expression = info.getReturnType();

    TypeExpressionParser parser = parserFactory.create(
        linkFactoryBuilder.create(type).withTypeContext(type));
    Comment comment = parser.parse(expression);
    assertMessage(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("!"))
            .addToken(link("Container", "one_exports_Container.html"))
            .addToken(text("<"))
            .addToken(stringLink())
            .addToken(text(">"))
            .build());
  }

  @Test
  public void parseExpression_unknownType() {
    TypeExpression expression = compileExpression("?");
    assertMessage(expression).isEqualTo(UNKNOWN_TYPE);
  }

  @Test
  public void parseExpression_anyType() {
    TypeExpression expression = compileExpression("*");
    assertMessage(expression).isEqualTo(ANY_TYPE);
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
    return parserFactory.create(linkFactoryBuilder.create(type)).parseExpression(expression);
  }

  private static TypeExpression numberType() {
    return TypeExpression.newBuilder()
        .setNamedType(NamedType.newBuilder()
            .setName("number")
            .setHref("https://developer.mozilla.org/en-US/docs/Web/" +
                "JavaScript/Reference/Global_Objects/Number"))
        .build();
  }

  private static TypeExpression stringType() {
    return TypeExpression.newBuilder()
        .setNamedType(NamedType.newBuilder()
            .setName("string")
            .setHref("https://developer.mozilla.org/en-US/docs/Web/" +
                "JavaScript/Reference/Global_Objects/String"))
        .build();
  }

  private static Comment.Token text(String text) {
    return Comment.Token.newBuilder().setText(text).build();
  }

  private static Comment.Token numberLink() {
    return link("number",
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number");
  }

  private static Comment.Token stringLink() {
    return link("string",
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String");
  }

  private static Comment.Token link(String text, String href) {
    return Comment.Token.newBuilder()
        .setText(text)
        .setHref(href)
        .build();
  }

  private static NamedType namedType(String text, String href) {
    return NamedType.newBuilder()
        .setName(text)
        .setHref(href)
        .build();
  }
}
