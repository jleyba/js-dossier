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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertWithMessage;

import com.github.jsdossier.TypeInspector.InstanceProperty;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Comment.Token;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.SourceLink;
import com.github.jsdossier.proto.TypeExpression;
import com.github.jsdossier.proto.TypeLink;
import com.github.jsdossier.proto.UnionType;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.html.types.testing.HtmlConversions;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.nio.file.FileSystem;
import java.util.Arrays;
import javax.inject.Inject;
import org.junit.Rule;

/** Abstract base class for tests for {@link com.github.jsdossier.TypeInspector}. */
public abstract class AbstractTypeInspectorTest {

  protected static final String DEFINE_INHERITS =
      "function inherits(/** !Function */ childCtor, /** !Function */ parentCtor) {"
          + "  /** @constructor */ function tempCtor() {}\n"
          + "  tempCtor.prototype = parentCtor.prototype;\n"
          + "  childCtor.prototype = new tempCtor();\n"
          + "  /** @override */ childCtor.prototype.constructor = childCtor;\n"
          + "}\n";

  private static final FileSystem FILE_SYSTEM = Jimfs.newFileSystem();

  @Rule
  public GuiceRule guice =
      GuiceRule.builder(this)
          .setModulePrefix("/src/modules")
          .setModules("foo/bar.js", "foo/baz.js")
          .setUseNodeLibrary(false)
          .setSourcePrefix("/src")
          .setOutputDir("/out")
          .build();

  @Inject protected CompilerUtil util;
  @Inject protected TypeRegistry typeRegistry;
  @Inject protected JSTypeRegistry jsTypeRegistry;
  @Inject @Input protected FileSystem fs;
  @Inject protected TypeInspectorFactory typeInspectorFactory;

  protected void compile(String... lines) {
    util.compile(FILE_SYSTEM.getPath(("/src/foo.js")), lines);
  }

  protected static SourceLink sourceFile(String path, int line) {
    return SourceLink.newBuilder()
        .setPath(HtmlConversions.newSafeUrlProtoForTest(path))
        .setLine(line)
        .build();
  }

  protected static Comment linkComment(String text, String href) {
    return Comment.newBuilder()
        .addToken(Token.newBuilder().setText(text).setLink(typeLink(href)))
        .build();
  }

  protected static Comment htmlComment(String html) {
    return Comment.newBuilder().addToken(Token.newBuilder().setHtml(html)).build();
  }

  protected static TypeExpression unionType(TypeExpression... expressions) {
    checkArgument(expressions.length > 0);
    return TypeExpression.newBuilder()
        .setUnionType(UnionType.newBuilder().addAllType(Arrays.asList(expressions)))
        .build();
  }

  protected static TypeExpression nullableErrorTypeExpression() {
    NamedType error = namedType("Error").toBuilder().setExtern(true).build();
    return unionType(
        TypeExpression.newBuilder().setNamedType(error).build(), TypeExpressions.NULL_TYPE);
  }

  protected static TypeExpression numberTypeExpression() {
    NamedType number = namedType("number").toBuilder().setExtern(true).build();
    return TypeExpression.newBuilder().setNamedType(number).build();
  }

  protected static TypeExpression stringTypeExpression() {
    NamedType string = namedType("string").toBuilder().setExtern(true).build();
    return TypeExpression.newBuilder().setNamedType(string).build();
  }

  protected static TypeExpression namedTypeExpression(String name) {
    return TypeExpression.newBuilder().setNamedType(namedType(name)).build();
  }

  protected static TypeExpression namedTypeExpression(String name, String href) {
    return TypeExpression.newBuilder().setNamedType(namedType(name, href)).build();
  }

  protected static TypeExpression namedTypeExpression(
      String name, String qualifiedName, String href) {
    return TypeExpression.newBuilder().setNamedType(namedType(name, qualifiedName, href)).build();
  }

  protected static TypeExpression nullableNamedTypeExpression(String name) {
    return unionType(
        TypeExpression.newBuilder().setNamedType(namedType(name)).build(),
        TypeExpressions.NULL_TYPE);
  }

  protected static TypeExpression nullableNamedTypeExpression(String name, String href) {
    return unionType(
        TypeExpression.newBuilder().setNamedType(namedType(name, href)).build(),
        TypeExpressions.NULL_TYPE);
  }

  protected static TypeExpression nullableNamedTypeExpression(
      String name, String qualifiedName, String href) {
    return unionType(
        TypeExpression.newBuilder().setNamedType(namedType(name, qualifiedName, href)).build(),
        TypeExpressions.NULL_TYPE);
  }

  protected static NamedType namedType(String name) {
    return NamedType.newBuilder().setName(name).build();
  }

  protected static NamedType namedType(String name, String href) {
    return NamedType.newBuilder().setName(name).setLink(typeLink(href)).build();
  }

  protected static NamedType namedType(String name, String qualifiedName, String href) {
    return NamedType.newBuilder()
        .setName(name)
        .setQualifiedName(qualifiedName)
        .setLink(typeLink(href))
        .build();
  }

  protected static TypeLink typeLink(String href) {
    return TypeLink.newBuilder().setHref(HtmlConversions.newSafeUrlProtoForTest(href)).build();
  }

  protected static NamedType addTemplateTypes(
      NamedType namedType, TypeExpression... templateTypes) {
    return namedType.toBuilder().addAllTemplateType(Arrays.asList(templateTypes)).build();
  }

  protected static InstancePropertySubject assertInstanceProperty(final InstanceProperty property) {
    return assertAbout(
            new SubjectFactory<InstancePropertySubject, InstanceProperty>() {
              @Override
              public InstancePropertySubject getSubject(
                  FailureStrategy failureStrategy, InstanceProperty property) {
                return new InstancePropertySubject(failureStrategy, property);
              }
            })
        .that(property);
  }

  protected static final class InstancePropertySubject
      extends Subject<InstancePropertySubject, InstanceProperty> {
    public InstancePropertySubject(FailureStrategy failureStrategy, InstanceProperty subject) {
      super(failureStrategy, subject);
    }

    public void isNamed(String name) {
      assertWithMessage("wrong name").that(actual().getName()).isEqualTo(name);
    }

    public void hasType(JSType type) {
      assertWithMessage("wrong type").that(actual().getType()).isEqualTo(type);
    }

    public void isInstanceMethod(JSType typeOfThis) {
      JSType type = actual().getType();
      assertWithMessage("not a function").that(type.isFunctionType()).isTrue();

      if (typeOfThis.isConstructor() || typeOfThis.isInterface()) {
        typeOfThis = ((FunctionType) typeOfThis).getInstanceType();
      }
      FunctionType function = (FunctionType) type;
      assertWithMessage("wrong type of this").that(function.getTypeOfThis()).isEqualTo(typeOfThis);
    }

    public void isDefinedOn(JSType type) {
      assertWithMessage("wrong defining type").that(actual().getDefinedByType()).isEqualTo(type);
    }
  }
}
