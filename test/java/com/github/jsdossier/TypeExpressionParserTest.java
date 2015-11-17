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

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.NominalType2;
import com.github.jsdossier.jscomp.TypeRegistry2;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.jscomp.CompilerOptions;
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
  @Inject private TypeRegistry2 typeRegistry;
  @Inject private TypeExpressionParserFactory parserFactory;
  
  @Test
  public void parseTypeDefinition() {
    util.compile(fs.getPath("foo.js"), "/** @typedef {{name: string, age: number}} */var Person;");
    
    NominalType2 type = typeRegistry.getType("Person");
    TypeExpressionParser parser = parserFactory.create(type);

    Comment comment = parser.parse(type.getJsDoc().getInfo().getTypedefType());
    assertThat(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("{age: "))
            .addToken(numberLink())
            .addToken(text(", name: "))
            .addToken(stringLink())
            .addToken(text("}"))
            .build());
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

    NominalType2 type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(type);
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    Comment comment = parser.parse(expression);
    assertThat(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("function(new: "))
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
    
    NominalType2 type = typeRegistry.getType("module$source$modules$two.Greeter");
    TypeExpressionParser parser = parserFactory.create(type);
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    Comment comment = parser.parse(expression);
    assertThat(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("!"))
            .addToken(link("Foo", "one_exports_Foo.html"))
            .build());
    
    parser = parserFactory.create(typeRegistry.getType("Person"));
    comment = parser.parse(expression);
    assertThat(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("!"))
            .addToken(link("Person", "Person.html"))
            .build());
  }
  
  @Test
  public void redundantNonNullQualifierOnPrimitiveIsIncluded() {
    util.compile(fs.getPath("foo.js"), "/** @typedef {!string} */var Name;");

    NominalType2 type = typeRegistry.getType("Name");
    TypeExpressionParser parser = parserFactory.create(type);

    Comment comment = parser.parse(type.getJsDoc().getInfo().getTypedefType());
    assertThat(comment).isEqualTo(
        Comment.newBuilder()
            .addToken(text("!"))
            .addToken(stringLink())
            .build());
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
}
