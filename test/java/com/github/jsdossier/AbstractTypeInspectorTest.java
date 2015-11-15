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
import static com.google.common.truth.Truth.assertWithMessage;

import com.github.jsdossier.TypeInspector.InstanceProperty;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.TypeRegistry2;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Comment.Token;
import com.github.jsdossier.proto.SourceLink;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Rule;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import javax.inject.Inject;

/**
 * Abstract base class for tests for {@link com.github.jsdossier.TypeInspector}.
 */
public abstract class AbstractTypeInspectorTest {

  @Rule
  public GuiceRule guice = GuiceRule.builder(this)
      .setModulePrefix("/src/modules")
      .setModules("foo/bar.js", "foo/baz.js")
      .setSourcePrefix("/src")
      .setOutputDir("/out")
      .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT)
      .build();

  @Inject protected CompilerUtil util;
  @Inject protected TypeInspector typeInspector;
  @Inject protected TypeRegistry2 typeRegistry;
  @Inject protected JSTypeRegistry jsTypeRegistry;
  @Inject @Input protected FileSystem fs;
  
  protected void compile(String... lines) {
    util.compile(path("/src/foo.js"), lines);
  }

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }

  protected static SourceLink sourceFile(String path, int line) {
    return SourceLink.newBuilder().setPath(path).setLine(line).build();
  }

  protected static Comment linkComment(String text, String href) {
    return Comment.newBuilder()
        .addToken(Token.newBuilder().setText(text).setHref(href))
        .build();
  }

  protected static Comment htmlComment(String html) {
    return Comment.newBuilder()
        .addToken(Token.newBuilder().setHtml(html))
        .build();
  }

  protected static Comment textComment(String text) {
    return Comment.newBuilder()
        .addToken(Token.newBuilder().setText(text))
        .build();
  }

  protected static Comment errorTypeComment() {
    return linkComment("Error",
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error");
  }

  protected static Comment numberTypeComment() {
    return linkComment("number",
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number");
  }

  protected static Comment stringTypeComment() {
    return linkComment("string",
        "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String");
  }

  protected static InstancePropertySubject assertInstanceProperty(final InstanceProperty property) {
    return assertAbout(new SubjectFactory<InstancePropertySubject, InstanceProperty>() {
      @Override
      public InstancePropertySubject getSubject(
          FailureStrategy failureStrategy, InstanceProperty property) {
        return new InstancePropertySubject(failureStrategy, property);
      }
    }).that(property);
  }
  
  protected static final class InstancePropertySubject
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
      assertWithMessage("wrong defining type").that(getSubject().getDefinedByType())
          .isEqualTo(type);
    }
  }
}
