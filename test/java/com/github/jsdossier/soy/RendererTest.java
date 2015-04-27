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

package com.github.jsdossier.soy;

import static com.github.jsdossier.soy.ProtoMessageSoyType.toSoyValue;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.transformValues;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jsdossier.CommentParser;
import com.github.jsdossier.Linker;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Deprecation;
import com.github.jsdossier.proto.Enumeration;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.JsType;
import com.github.jsdossier.proto.Property;
import com.github.jsdossier.proto.Resources;
import com.github.jsdossier.proto.SourceLink;
import com.github.jsdossier.proto.Tags;
import com.github.jsdossier.proto.TypeLink;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.protobuf.GeneratedMessage;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.tofu.SoyTofu;
import org.hamcrest.Matcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

@RunWith(JUnit4.class)
public class RendererTest {

  private static SoyTofu tofu;

  private final CommentParser parser = new CommentParser(true);
  private Linker mockLinker;

  @BeforeClass
  public static void createTofu() {
    tofu = new Renderer().getTofu();
  }

  @Before
  public void setUpMocks() {
    mockLinker = mock(Linker.class);
  }

  @Test
  public void renderDeprecationNotice() {
    assertEquals("", render("dossier.soy.deprecationNotice", null));
    assertEquals("", render("dossier.soy.deprecationNotice", new HashMap<String, Object>()));

    assertThat(
        render("dossier.soy.deprecationNotice",
            "deprecation", toSoyValue(parseDeprecation("Hello, world!"))),
        isHtml("<p><b>Deprecated: </b>Hello, world!</p>"));

    assertThat(
        render("dossier.soy.deprecationNotice", ImmutableMap.of(
            "deprecation", toSoyValue(parseDeprecation("<strong>Hello, world!</strong>")))),
        isHtml("<p><b>Deprecated: </b><strong>Hello, world!</strong></p>"));
  }

  @Test
  public void renderPageHeader() {
    assertThat(
        render("dossier.soy.pageHeader", ImmutableMap.of(
            "title", "Foo.Bar",
            "resources", Resources.newBuilder()
                .addTailScript("one")
                .addTailScript("two")
                .addCss("apples")
                .addCss("oranges")
                .build()
        )),
        isHtml(
            "<!DOCTYPE html>",
            "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, ",
            "initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no\">",
            "<meta http-equiv=\"Content-Language\" content=\"en\">",
            "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">",
            "<title>Foo.Bar</title>",
            "<link href=\"apples\" rel=\"stylesheet\" type=\"text/css\">",
            "<link href=\"oranges\" rel=\"stylesheet\" type=\"text/css\">",
            "<header><div><form><div>",
            "<input type=\"search\" placeholder=\"Search\">",
            "</div></form></div></header>"));
  }

  @Test
  public void renderSourceLink() {
    assertThat(
        render("dossier.soy.sourceLink", ImmutableMap.of(
            "text", "foo",
            "source", SourceLink.newBuilder().setPath("").build())),
        is("foo"));
    assertThat(
        render("dossier.soy.sourceLink", ImmutableMap.of(
            "text", "foo",
            "source", SourceLink.newBuilder().setPath("foo.bar").build())),
        is("<a href=\"foo.bar\">foo</a>"));
    assertThat(
        render("dossier.soy.sourceLink", ImmutableMap.of(
            "text", "foo",
            "source", SourceLink.newBuilder().setPath("foo.bar").setLine(123).build())),
        is("<a href=\"foo.bar#l123\">foo</a>"));
  }

  @Test
  public void renderClassInheritance() {
    List<Comment> types = new LinkedList<>();
    assertThat(render("dossier.soy.classInheritance", ImmutableMap.of("types", types)),
        is(""));

    types.add(Comment.newBuilder()
        .addToken(Comment.Token.newBuilder()
            .setHref("foo.link")
            .setText("Foo"))
        .build());
    assertThat(render("dossier.soy.classInheritance", ImmutableMap.of("types", types)),
        is(""));

    types.add(Comment.newBuilder()
        .addToken(Comment.Token.newBuilder().setText("Bar"))
        .build());
    assertThat(render("dossier.soy.classInheritance", "types", types),
        isHtml(
            "<pre class=\"inheritance\">",
            "<a href=\"foo.link\">Foo</a>",
            "\n  &#x2514; Bar",
            "</pre>"));

    types.add(Comment.newBuilder()
        .addToken(Comment.Token.newBuilder()
            .setHref("baz.link")
            .setText("Baz"))
        .build());
    types.add(Comment.newBuilder()
        .addToken(Comment.Token.newBuilder()
            .setHref("")
            .setText("NoLink"))
        .build());
    types.add(Comment.newBuilder()
        .addToken(Comment.Token.newBuilder()
            .setHref("quux.link")
            .setText("Quux"))
        .build());
    assertThat(render("dossier.soy.classInheritance", "types", types),
        isHtml(
            "<pre class=\"inheritance\">",
            "<a href=\"foo.link\">Foo</a>",
            "\n  &#x2514; Bar",
            "\n      &#x2514; <a href=\"baz.link\">Baz</a>",
            "\n          &#x2514; NoLink",
            "\n              &#x2514; <a href=\"quux.link\">Quux</a>",
            "</pre>"));
  }

  @Test
  public void printInterfaces_emptyList() {
    JsType type = JsType.newBuilder()
        .setName("name")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Tags.getDefaultInstance())
        .build();

    assertThat(render("dossier.soy.printInterfaces", "type", type), is(""));
  }

  @Test
  public void printInterfaces_interface() {
    JsType type = JsType.newBuilder()
        .setName("name")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Tags.newBuilder().setIsInterface(true))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-two")
                .setText("Goodbye")))
        .build();

    assertThat(render("dossier.soy.printInterfaces", "type", type),
        isHtml(
            "<dt>All extended interfaces:<dd>",
            "<code><a href=\"type-one\">Hello</a></code>, ",
            "<code><a href=\"type-two\">Goodbye</a></code>"));
  }

  @Test
  public void printInterfaces_class() {
    JsType type = JsType.newBuilder()
        .setName("name")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setMainFunction(Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description"))))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-two")
                .setText("Goodbye")))
        .setTags(Tags.getDefaultInstance())
        .build();

    assertThat(render("dossier.soy.printInterfaces", "type", type),
        isHtml(
            "<dt>All implemented interfaces:<dd>",
            "<code><a href=\"type-one\">Hello</a></code>, ",
            "<code><a href=\"type-two\">Goodbye</a></code>"));
  }

  @Test
  public void printInterfaces_missingHref() {
    JsType type = JsType.newBuilder()
        .setName("name")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Tags.newBuilder().setIsInterface(true))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setText("Hello")))
        .build();

    assertThat(render("dossier.soy.printInterfaces", "type", type),
        isHtml(
            "<dt>All extended interfaces:<dd>",
            "<code>Hello</code>"));
  }

  @Test
  public void renderTypeHeader_simpleModule() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setTags(Tags.newBuilder().setIsModule(true))
        .setSource(SourceLink.newBuilder().setPath("source-file"))
        .setDescription(parseComment("description"))
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<div class=\"codelink\"><a href=\"source-file\">View Source</a></div>",
        "<h1>module Foo</h1>",
        "</body>"));
  }

  @Test
  public void renderTypeHeader_interface() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Tags.newBuilder().setIsInterface(true))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-two")
                .setText("Goodbye")))
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    Element h1 = querySelector(document, "body > h1");
    assertEquals("<h1>interface Foo</h1>", h1.toString());
    assertThat(querySelector(document, "dl > dt").toString(),
        is("<dt>All extended interfaces:</dt>"));
    assertThat(document.select("div.deprecation-notice").isEmpty(), is(true));
  }

  @Test
  public void renderTypeHeader_deprecatedInterface_noNoticeText() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Tags.newBuilder().setIsInterface(true))
        .setDeprecation(Deprecation.newBuilder())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), isHtml(
        "<h1 class=\"deprecated\">interface Foo</h1>"));
    assertThat(document.select("div.deprecation-notice").isEmpty(), is(true));
  }

  @Test
  public void renderTypeHeader_deprecatedInterface_hasNoticeText() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Tags.newBuilder().setIsInterface(true))
        .setDeprecation(parseDeprecation("<i>Goodbye</i>, world!"))
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), isHtml(
        "<h1 class=\"deprecated\">interface Foo</h1>"));
    assertThat(querySelector(document, "h1 + p").toString(), isHtml(
        "<p><b>Deprecated: </b><i>Goodbye</i>, world!</p>"));
  }

  @Test
  public void renderTypeHeader_interfaceHasTemplateNames() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Tags.newBuilder().setIsInterface(true))
        .setMainFunction(Function.newBuilder()
            .addTemplateName("K")
            .addTemplateName("V")
            .setBase(BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description"))))
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), isHtml(
        "<h1>interface Foo&lt;K, V&gt;</h1>"));
  }

  @Test
  public void renderTypeHeader_classHasTemplateNames() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setMainFunction(Function.newBuilder()
            .setIsConstructor(true)
            .addTemplateName("K")
            .addTemplateName("V")
            .setBase(BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description"))))
        .setTags(Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), isHtml(
        "<h1>class Foo&lt;K, V&gt;</h1>"));
  }

  @Test
  public void renderTypeHeader_classInModule() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setMainFunction(Function.newBuilder()
            .setIsConstructor(true)
            .setBase(BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description"))))
        .setParent(JsType.ParentLink.newBuilder()
            .setIsModule(true)
            .setLink(TypeLink.newBuilder()
                .setText("path/to/module")
                .setHref("module-source")))
        .setTags(Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<div class=\"parentlink\"><b>Module:</b> ",
        "<a href=\"module-source\">path/to/module</a></div>",
        "<div class=\"codelink\"><a href=\"source\">View Source</a></div>",
        "<h1>class Foo</h1>",
        "</body>"));
  }

  @Test
  public void renderTypeHeader_complexClass() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source-file"))
        .setDescription(parseComment("description"))
        .setMainFunction(Function.newBuilder()
            .setIsConstructor(true)
            .setBase(BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description")))
            .addTemplateName("T"))
        .addExtendedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("super-one")
                .setText("SuperClass1")))
        .addExtendedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("super-two")
                .setText("SuperClass2")))
        .addExtendedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setText("Foo")))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-two")
                .setText("Goodbye")))
        .setTags(Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);
    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<div class=\"codelink\"><a href=\"source-file\">View Source</a></div>",
        "<h1>class Foo&lt;T&gt;</h1>",
        "<pre class=\"inheritance\">",
        "<a href=\"super-one\">SuperClass1</a>",
        "\n  \u2514 <a href=\"super-two\">SuperClass2</a>",
        "\n      \u2514 Foo",
        "</pre>",
        "<dl><dt>All implemented interfaces:</dt><dd>",
        "<code><a href=\"type-one\">Hello</a></code>, ",
        "<code><a href=\"type-two\">Goodbye</a></code>",
        "</dd></dl>",
        "</body>"));
  }

  @Test
  public void renderTypeHeader_classAsModuleExports() {
    JsType type = JsType.newBuilder()
        .setTags(Tags.newBuilder().setIsModule(true))
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source-file"))
        .setDescription(parseComment("description"))
        .setMainFunction(Function.newBuilder()
            .setBase(BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(SourceLink.newBuilder().setPath("ctor-file"))
                .setDescription(parseComment("ctor-description")))
            .addTemplateName("T"))
        .addExtendedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("super-one")
                .setText("SuperClass1")))
        .addExtendedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("super-two")
                .setText("SuperClass2")))
        .addExtendedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setText("Foo")))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setHref("type-two")
                .setText("Goodbye")))
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);
    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body><div class=\"codelink\"><a href=\"source-file\">View Source</a></div>",
        "<h1>module Foo&lt;T&gt;</h1>",
        "<pre class=\"inheritance\"><a href=\"super-one\">SuperClass1</a>\n",
        "  └ <a href=\"super-two\">SuperClass2</a>\n",
        "      └ Foo</pre>",
        "<dl><dt>All implemented interfaces:</dt>",
        "<dd><code><a href=\"type-one\">Hello</a></code>, ",
        "<code><a href=\"type-two\">Goodbye</a></code></dd></dl></body>"));
  }

  @Test
  public void renderTypeHeader_namespace() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source-file"))
        .setDescription(parseComment("description"))
        .setTags(Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<div class=\"codelink\"><a href=\"source-file\">View Source</a></div>",
        "<h1>namespace Foo</h1>",
        "</body>"));
  }

  @Test
  public void renderTypeHeader_enumeration() {
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setEnumeration(Enumeration.newBuilder()
            .setType(Comment.newBuilder()
                .addToken(Comment.Token.newBuilder()
                    .setText("{color: string}"))))
        .setTags(Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), is("<h1>enum Foo</h1>"));
    assertThat(querySelector(document, "dl").toString(), is(
        "<dl><dt>Type<code>{color: string}</code></dt></dl>"));
  }

  @Test
  public void renderEnumValues() {
    Enumeration e = Enumeration.newBuilder()
        .setType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder()
                .setText("enum-type")))
        .addValue(Enumeration.Value.newBuilder()
            .setName("ONE"))
        .addValue(Enumeration.Value.newBuilder()
            .setName("TWO")
            .setDescription(parseComment("")))  // Empty description.
        .addValue(Enumeration.Value.newBuilder()
            .setName("RED")
            .setDescription(parseComment("<strong>the color red</strong>")))
        .addValue(Enumeration.Value.newBuilder()
            .setName("GREEN")
            .setDeprecation(Deprecation.newBuilder())  // No deprecation text.
            .setDescription(parseComment("<i>the color green</i>")))
        .addValue(Enumeration.Value.newBuilder()
            .setName("BLUE")
            .setDeprecation(parseDeprecation("This value is deprecated")))
        .build();

    Document document = renderDocument("dossier.soy.enumValues", ImmutableMap.of("enumeration", e));

    assertThat(querySelector(document, "h2").toString(), is("<h2>Values and Descriptions</h2>"));
    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<h2>Values and Descriptions</h2>",
        "<div id=\"ONE\" class=\"property\"><dl><dt>ONE</dt></dl></div>",
        "<div id=\"TWO\" class=\"property\"><dl><dt>TWO</dt></dl></div>",
        "<div id=\"RED\" class=\"property\">",
        "<dl>",
        "<dt>RED</dt>",
        "<dd><p><strong>the color red</strong></p></dd>",
        "</dl>",
        "</div>",
        "<div id=\"GREEN\" class=\"property\">",
        "<dl>",
        "<dt>GREEN</dt>",
        "<dd><p><i>the color green</i></p></dd>",
        "</dl>",
        "</div>",
        "<div id=\"BLUE\" class=\"property\">",
        "<dl>",
        "<dt class=\"deprecated\">BLUE</dt>",
        "<dd><p><b>Deprecated: </b>This value is deprecated</p></dd>",
        "</dl>",
        "</div>",
        "</body>"));
  }

  @Test
  public void renderNestedTypeSummaries_emptyList() {
    Document document = renderDocument("dossier.soy.nestedTypeSummaries",
        ImmutableMap.<String, Object>of(
            "title", "Interfaces",
            "types", ImmutableList.of())
    );
    assertTrue(document.select("section").isEmpty());
  }

  @Test
  public void renderNestedTypeSummaries() {
    List<GeneratedMessage> types = ImmutableList.<GeneratedMessage>of(
        JsType.TypeSummary.newBuilder()
            .setName("Foo")
            .setHref("foo-link")
            .setSummary(parseComment("foo summary"))
            .build(),
        JsType.TypeSummary.newBuilder()
            .setName("Bar")
            .setHref("bar-link")
            .setSummary(parseComment("<strong>bar summary <i>has html</i></strong>"))
            .build(),
        JsType.TypeSummary.newBuilder()
            .setName("Baz")
            .setHref("baz-link")
            .setSummary(parseComment(""))
            .build()
    );

    Document document = renderDocument("dossier.soy.nestedTypeSummaries",
        ImmutableMap.of(
            "title", "Interfaces",
            "types", toSoyValue(types)));
    assertThat(querySelector(document, "section > h2").toString(), is("<h2>Interfaces</h2>"));
    assertThat(querySelector(document, "section > h2 + .type-summary").toString(), isHtml(
        "<div class=\"type-summary\"><dl>",
        "<dt><a href=\"foo-link\">Foo</a></dt><dd><p>foo summary</p></dd>",
        "<dt><a href=\"bar-link\">Bar</a></dt>",
        "<dd><p><strong>bar summary <i>has html</i></strong></p></dd>",
        "<dt><a href=\"baz-link\">Baz</a></dt><dd>No Description.</dd>",
        "</dl></div>"));
  }

  @Test
  public void renderFunctionDetails_basicFunction() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .build();
    assertThat("No details to render",
        render("dossier.soy.fnDetails", "fn", function), is(""));
  }

  @Test
  public void renderFunctionDetails_hasParameters() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .addParameter(Function.Detail.newBuilder().setName("a"))
        .addParameter(Function.Detail.newBuilder()
            .setName("b")
            .setDescription(parseComment("<i>b</i> awesome")))
        .addParameter(Function.Detail.newBuilder()
            .setName("c")
            .setType(Comment.newBuilder()
                .addToken(Comment.Token.newBuilder()
                    .setHtml("<b>Object</b>"))))
        .addParameter(Function.Detail.newBuilder()
            .setName("d")
            .setType(Comment.newBuilder()
                .addToken(Comment.Token.newBuilder()
                    .setText("Error")))
            .setDescription(parseComment("goodbye")))
        .addParameter(Function.Detail.newBuilder()
            .setDescription(parseComment("who am i")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><div class=\"fn-details\">",
        "<div><b>Parameters</b></div>",
        "<dl>",
        "<dt>a</dt>",
        "<dt>b</dt>",
        "<dd><p><i>b</i> awesome</p></dd>",
        "<dt>c<code><b>Object</b></code></dt>",
        "<dt>d<code>Error</code></dt>",
        "<dd><p>goodbye</p></dd>",
        "<dt></dt>",
        "<dd><p>who am i</p></dd>",
        "</dl>",
        "</div></div>"));
  }

  @Test
  public void renderFunctionDetails_hasThrownTypes() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .addThrown(Function.Detail.newBuilder().setName("Error"))
        .addThrown(Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><div class=\"fn-details\">",
        "<div><b>Throws</b></div>",
        "<dl>",
        "<dt>Error</dt>",
        "<dt></dt>",
        "<dd><p>randomly</p></dd>",
        "</dl>",
        "</div></div>"));
  }

  @Test
  public void renderFunctionDetails_hasReturnWithoutDescription() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .setReturn(Function.Detail.newBuilder())
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(0));
  }

  @Test
  public void renderFunctionDetails_hasReturn() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .setReturn(Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><div class=\"fn-details\">",
        "<div><b>Returns</b></div>",
        "<p>randomly</p>",
        "</div></div>"));
  }

  @Test
  public void renderFunctionDetails_hasReturn_constructor() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .setIsConstructor(true)
        .addParameter(Function.Detail.newBuilder().setName("a"))
        .setReturn(Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><div class=\"fn-details\">",
        "<div><b>Parameters</b></div>",
        "<dl><dt>a</dt></dl>",
        "</div></div>"));
  }

  @Test
  public void renderFunctionDetails_fullyDefined() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .addParameter(Function.Detail.newBuilder().setName("a"))
        .addParameter(Function.Detail.newBuilder()
            .setName("b")
            .setDescription(parseComment("<i>b</i> awesome")))
        .addParameter(Function.Detail.newBuilder()
            .setName("c")
            .setType(Comment.newBuilder()
                .addToken(Comment.Token.newBuilder()
                    .setHtml("<b>Object</b>"))))
        .addParameter(Function.Detail.newBuilder()
            .setName("d")
            .setType(Comment.newBuilder()
                .addToken(Comment.Token.newBuilder()
                    .setText("Error")))
            .setDescription(parseComment("goodbye")))
        .addParameter(Function.Detail.newBuilder()
            .setDescription(parseComment("who am i")))
        .addThrown(Function.Detail.newBuilder()
            .setName("Error")
            .setDescription(parseComment("randomly")))
        .setReturn(Function.Detail.newBuilder()
            .setDescription(parseComment("something")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div>",
        "<div class=\"fn-details\">",
        "<div><b>Parameters</b></div>",
        "<dl>",
        "<dt>a</dt><dt>b</dt><dd><p><i>b</i> awesome</p></dd>",
        "<dt>c<code><b>Object</b></code></dt>",
        "<dt>d<code>Error</code></dt><dd><p>goodbye</p></dd>",
        "<dt></dt><dd><p>who am i</p></dd>",
        "</dl></div>",
        "<div class=\"fn-details\">",
        "<div><b>Returns</b></div>",
        "<p>something</p></div>",
        "<div class=\"fn-details\">",
        "<div><b>Throws</b></div>",
        "<dl><dt>Error</dt><dd><p>randomly</p></dd></dl>",
        "</div></div>"));
  }

  @Test
  public void renderMainFunction_constructor() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .setIsConstructor(true)
        .addParameter(Function.Detail.newBuilder().setName("a"))
        .addParameter(Function.Detail.newBuilder().setName("b"))
        .build();
    JsType type = JsType.newBuilder()
        .setName("Foo")
        .setSource(SourceLink.newBuilder().setPath(""))
        .setDescription(Comment.getDefaultInstance())
        .setMainFunction(function)
        .setTags(Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.mainFunction", "type", type);
    assertThat(document.body().children().size(), is(2));
    assertThat(document.body().child(0).toString(),
        isHtml("<h2 class=\"main\">new foo.Bar(<wbr />a, b)</h2>"));
  }

  @Test
  public void renderFunction_tagletsInDescription() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment(Joiner.on("\n").join(
                "First paragraph.",
                "<ul><li>bullet {@code with code}</li>",
                "<li>another bullet</ul>"))))
        .build();

    ImmutableMap<String, ?> data = ImmutableMap.of("fn", function);

    Document document = renderDocument("dossier.soy.printFunction", data);
    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<div id=\"foo.Bar\" class=\"function\"><div>",
        "<h3>foo.Bar()</h3>",
        "<p>First paragraph. </p>",
        "<ul><li>bullet <code>with code</code></li>",
        "<li>another bullet</li>",
        "</ul>",
        "</div></div>",
        "</body>"));
  }

  @Test
  public void renderDeprecatedFunctionProperty() {
    Function function = Function.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("Bar")
            .setSource(SourceLink.newBuilder().setPath("bar.link"))
            .setDescription(parseComment("description here\n<p>second paragraph"))
            .setDeprecation(parseDeprecation("is old")))
        .addParameter(Function.Detail.newBuilder().setName("a"))
        .build();

    ImmutableMap<String, ?> data = ImmutableMap.of("fn", function);

    Document document = renderDocument("dossier.soy.printFunction", data);
    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<div id=\"Bar\" class=\"function\"><div>",
        "<h3>",
        "Bar(<wbr />a)",
        "<span class=\"codelink\"><a href=\"bar.link\">code &raquo;</a></span>",
        "</h3>",
        "<p>description here </p>",
        "<p>second paragraph</p>",
        "<p><b>Deprecated: </b>is old</p>",
        "<div><div class=\"fn-details\">",
        "<div><b>Parameters</b></div><dl><dt>a</dt></dl></div></div>",
        "</div></div>",
        "</body>"));
  }

  @Test
  public void renderProperty() {
    Property property = Property.newBuilder()
        .setBase(BaseProperty.newBuilder()
            .setName("foo")
            .setSource(SourceLink.newBuilder().setPath("foo-source"))
            .setDescription(parseComment("foo description")))
        .setType(Comment.newBuilder()
            .addToken(Comment.Token.newBuilder().setText("string")))
        .build();

    Document document = renderDocument("dossier.soy.printProperty", "prop", property);
    Element details = querySelector(document, "body");
    assertThat(details.className(), is(""));

    assertThat(details.child(0).toString(), isHtml(
        "<div id=\"foo\" class=\"property\"><dl><dt><a href=\"foo-source\">foo</a>" +
            "<code>string</code></dt><dd><p>foo description</p></dd></dl></div>"));
  }

  @Test
  public void renderComment_unterminatedInlineTaglet() {
    Comment comment = parseComment("Hello {@code world");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p>Hello {@code world</p></body>"));
  }

  @Test
  public void renderComment_withCodeTaglet() {
    Comment comment = parseComment("Hello, {@code world}!");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p>Hello, <code>world</code>!</p></body>"));
  }

  @Test
  public void renderComment_withAdjacentInlineCodeTaglets() {
    Comment comment = parseComment("{@code hello}{@code world}");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p><code>hello</code><code>world</code></p></body>"));
  }

  @Test
  public void renderComment_codeTagletsAreHtmlEscaped() {
    Comment comment = parseComment("{@code 1 < 2 && 4 > 3}");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "<code>1 &lt; 2 &amp;&amp; 4 &gt; 3</code>",
        "</p></body>"));
  }

  @Test
  public void renderComment_literalTagletsAreHtmlEscaped() {
    Comment comment = parseComment("{@literal 1 < 2 && 4 > 3}");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>1 &lt; 2 &amp;&amp; 4 &gt; 3</p></body>"));
  }

  @Test
  public void renderComment_unresolvedLink() {
    Comment comment = parseComment("An {@link Foo unknown} type");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "An <code>unknown</code>",
        " type</p></body>"));
  }

  @Test
  public void renderComment_resolvedLink() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(
        TypeLink.newBuilder()
            .setText("")
            .setHref("/path/to/foo").build());

    Comment comment = parseComment(
        "A {@link foo.Bar milk & cookies} snack");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "A ",
        "<a href=\"/path/to/foo\"><code>milk &amp; cookies</code></a>",
        " snack</p></body>"));
  }

  @Test
  public void renderComment_plainLink() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(
        TypeLink.newBuilder()
            .setText("")
            .setHref("/path/to/foo").build());


    Comment comment = parseComment(
        "A {@linkplain foo.Bar milk & cookies} snack");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "A <a href=\"/path/to/foo\">milk &amp; cookies</a> snack",
        "</p></body>"));
  }

  @Test
  public void renderComment_doesNotEscapePlainTextContent() {
    Comment comment = parseComment(
        "A <strong>strongly</strong> worded letter");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>A <strong>strongly</strong> worded letter</p></body>"));
  }

  @Test
  public void renderComment_omittingLeadingParagraphTag() {
    Comment comment = parseComment(
        "A <strong>strongly</strong> worded letter");

    Document document = renderDocument("dossier.soy.comment",
        ImmutableMap.of("comment", comment, "omitLeadingTag", true));
    assertThat(document.body().toString(), isHtml(
        "<body><p>A <strong>strongly</strong> worded letter</p></body>"));
  }

  @Test
  public void renderComment_emptyComment() {
    Comment comment = parseComment("");
    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml("<body></body>"));
  }

  @Test
  public void renderComment_nullComment() {
    Comment comment = parseComment(null);
    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml("<body></body>"));
  }

  @Test
  public void renderComment_nullCommentMessage() {
    Document document = renderDocument("dossier.soy.comment", new HashMap<String, Object>() {{
      put("comment", null);
    }});
    assertThat(document.body().toString(), isHtml("<body></body>"));
  }

  @Test
  public void renderComment_simpleParagraph() {
    Document document = renderDocument("dossier.soy.comment", new HashMap<String, Object>() {{
      put("comment", parseComment("Hello, there!"));
    }});
    assertThat(document.body().toString(), isHtml("<body><p>Hello, there!</p></body>"));
  }

  @Test
  public void renderComment_commentHasOpenHtmlBlock() {
    Document document = renderDocument("dossier.soy.comment", new HashMap<String, Object>() {{
      put("comment", parseComment("<div>Hello, there!"));
    }});
    assertThat(document.body().toString(), isHtml("<body><p></p><div>Hello, there!</div></body>"));
  }

  private Comment parseComment(String comment) {
    return parser.parseComment(comment, mockLinker);
  }

  private Deprecation parseDeprecation(String comment) {
    return parser.parseDeprecation(comment, mockLinker);
  }

  private static Element querySelector(Document document, String selector) {
    Elements elements = document.select(selector);
    checkState(!elements.isEmpty(),
        "Selector %s not found in %s", selector, document);
    return Iterables.getOnlyElement(elements);
  }

  private Document renderDocument(String template, String key, GeneratedMessage value) {
    return renderDocument(template, ImmutableMap.of(key, value));
  }

  private Document renderDocument(String template, Map<String, ?> data) {
    String html = render(template, data);
    Document document = Jsoup.parse(html);
    document.outputSettings()
        .prettyPrint(false)
        .indentAmount(0);
    return document;
  }

  private String render(String template, String key, GeneratedMessage value) {
    return render(template, ImmutableMap.of(key, value));
  }

  private String render(String template, String key, Iterable<? extends GeneratedMessage> value) {
    return render(template, ImmutableMap.of(key, toSoyValue(value)));
  }

  private String render(String template, String key, SoyValue value) {
    return render(template, ImmutableMap.of(key, value));
  }

  private String render(String template, @Nullable Map<String, ?> data) {
    StringWriter sw = new StringWriter();
    tofu.newRenderer(template)
        .setData(data == null
            ? null
            : transformValues(data, new com.google.common.base.Function<Object, Object>() {
              @Nullable
              @Override
              public Object apply(@Nullable Object input) {
                if (input instanceof GeneratedMessage) {
                  return toSoyValue((GeneratedMessage) input);
                }
                return input;
              }
            }))
        .render(sw);
    return sw.toString();
  }

  private Matcher<String> isHtml(String... lines) {
    return is(Joiner.on("").join(lines));
  }
}
