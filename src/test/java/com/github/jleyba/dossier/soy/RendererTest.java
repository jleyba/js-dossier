package com.github.jleyba.dossier.soy;

import static com.github.jleyba.dossier.soy.ProtoMessageSoyType.toSoyValue;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.transformValues;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jleyba.dossier.CommentUtil;
import com.github.jleyba.dossier.Linker;
import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.base.Function;
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
    Dossier.Deprecation.Builder builder = Dossier.Deprecation.newBuilder();

    assertEquals("", render("dossier.soy.deprecationNotice", null));
    assertEquals("", render("dossier.soy.deprecationNotice", new HashMap<String, Object>()));

    builder.setNotice(parseComment("Hello, world!"));
    assertThat(
        render("dossier.soy.deprecationNotice",
            "deprecation", toSoyValue(builder.build())),
        isHtml("<p><b>Deprecated: </b>Hello, world!</p>"));

    builder.setNotice(parseComment("<strong>Hello, world!</strong>"));
    assertThat(
        render("dossier.soy.deprecationNotice", ImmutableMap.of(
            "deprecation", toSoyValue(builder.build()))),
        isHtml("<p><b>Deprecated: </b><strong>Hello, world!</strong></p>"));
  }

  @Test
  public void renderPageHeader() {
    assertThat(
        render("dossier.soy.pageHeader", ImmutableMap.of(
            "title", "Foo.Bar",
            "resources", Dossier.Resources.newBuilder()
                .addTailScript("one")
                .addTailScript("two")
                .addCss("apples")
                .addCss("oranges")
                .build()
        )),
        isHtml(
            "<!DOCTYPE html>",
            "<meta charset=\"UTF-8\">",
            "<meta http-equiv=\"Content-Language\" content=\"en\" />",
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
            "source", Dossier.SourceLink.newBuilder().setPath("").build())),
        is("foo"));
    assertThat(
        render("dossier.soy.sourceLink", ImmutableMap.of(
            "text", "foo",
            "source", Dossier.SourceLink.newBuilder().setPath("foo.bar").build())),
        is("<a href=\"foo.bar\">foo</a>"));
    assertThat(
        render("dossier.soy.sourceLink", ImmutableMap.of(
            "text", "foo",
            "source", Dossier.SourceLink.newBuilder().setPath("foo.bar").setLine(123).build())),
        is("<a href=\"foo.bar#l123\">foo</a>"));
  }

  @Test
  public void renderClassInheritance() {
    List<Dossier.Comment> types = new LinkedList<>();
    assertThat(render("dossier.soy.classInheritance", ImmutableMap.of("types", types)),
        is(""));

    types.add(Dossier.Comment.newBuilder()
        .addToken(Dossier.Comment.Token.newBuilder()
            .setHref("foo.link")
            .setText("Foo"))
        .build());
    assertThat(render("dossier.soy.classInheritance", ImmutableMap.of("types", types)),
        is(""));

    types.add(Dossier.Comment.newBuilder()
        .addToken(Dossier.Comment.Token.newBuilder().setText("Bar"))
        .build());
    assertThat(render("dossier.soy.classInheritance", "types", types),
        isHtml(
            "<pre class=\"inheritance\">",
            "<a href=\"foo.link\">Foo</a>",
            "\n  &#x2514; Bar",
            "</pre>"));

    types.add(Dossier.Comment.newBuilder()
        .addToken(Dossier.Comment.Token.newBuilder()
            .setHref("baz.link")
            .setText("Baz"))
        .build());
    types.add(Dossier.Comment.newBuilder()
        .addToken(Dossier.Comment.Token.newBuilder()
            .setHref("")
            .setText("NoLink"))
        .build());
    types.add(Dossier.Comment.newBuilder()
        .addToken(Dossier.Comment.Token.newBuilder()
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
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("name")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Dossier.Tags.getDefaultInstance())
        .build();

    assertThat(render("dossier.soy.printInterfaces", "type", type), is(""));
  }

  @Test
  public void printInterfaces_interface() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("name")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Dossier.Tags.newBuilder().setIsInterface(true))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
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
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("name")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setMainFunction(Dossier.Function.newBuilder()
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(Dossier.SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description"))))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("type-two")
                .setText("Goodbye")))
        .setTags(Dossier.Tags.getDefaultInstance())
        .build();

    assertThat(render("dossier.soy.printInterfaces", "type", type),
        isHtml(
            "<dt>All implemented interfaces:<dd>",
            "<code><a href=\"type-one\">Hello</a></code>, ",
            "<code><a href=\"type-two\">Goodbye</a></code>"));
  }

  @Test
  public void printInterfaces_missingHref() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("name")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Dossier.Tags.newBuilder().setIsInterface(true))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setText("Hello")))
        .build();

    assertThat(render("dossier.soy.printInterfaces", "type", type),
        isHtml(
            "<dt>All extended interfaces:<dd>",
            "<code>Hello</code>"));
  }

  @Test
  public void renderTypeHeader_simpleModule() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setTags(Dossier.Tags.newBuilder().setIsModule(true))
        .setSource(Dossier.SourceLink.newBuilder().setPath("source-file"))
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
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Dossier.Tags.newBuilder().setIsInterface(true))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
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
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Dossier.Tags.newBuilder().setIsInterface(true))
        .setDeprecation(Dossier.Deprecation.newBuilder())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), isHtml(
        "<h1 class=\"deprecated\">interface Foo</h1>"));
    assertThat(document.select("div.deprecation-notice").isEmpty(), is(true));
  }

  @Test
  public void renderTypeHeader_deprecatedInterface_hasNoticeText() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Dossier.Tags.newBuilder().setIsInterface(true))
        .setDeprecation(Dossier.Deprecation.newBuilder()
            .setNotice(parseComment("<i>Goodbye</i>, world!")))
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), isHtml(
        "<h1 class=\"deprecated\">interface Foo</h1>"));
    assertThat(querySelector(document, "h1 + p").toString(), isHtml(
        "<p><b>Deprecated: </b><i>Goodbye</i>, world!</p>"));
  }

  @Test
  public void renderTypeHeader_interfaceHasTemplateNames() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setTags(Dossier.Tags.newBuilder().setIsInterface(true))
        .setMainFunction(Dossier.Function.newBuilder()
            .addTemplateName("K")
            .addTemplateName("V")
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(Dossier.SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description"))))
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), isHtml(
        "<h1>interface Foo&lt;K, V&gt;</h1>"));
  }

  @Test
  public void renderTypeHeader_classHasTemplateNames() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setMainFunction(Dossier.Function.newBuilder()
            .setIsConstructor(true)
            .addTemplateName("K")
            .addTemplateName("V")
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(Dossier.SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description"))))
        .setTags(Dossier.Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), isHtml(
        "<h1>class Foo&lt;K, V&gt;</h1>"));
  }

  @Test
  public void renderTypeHeader_classInModule() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setMainFunction(Dossier.Function.newBuilder()
            .setIsConstructor(true)
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(Dossier.SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description"))))
        .setParent(Dossier.JsType.ParentLink.newBuilder()
            .setIsModule(true)
            .setLink(Dossier.TypeLink.newBuilder()
                .setText("path/to/module")
                .setHref("module-source")))
        .setTags(Dossier.Tags.getDefaultInstance())
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
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source-file"))
        .setDescription(parseComment("description"))
        .setMainFunction(Dossier.Function.newBuilder()
            .setIsConstructor(true)
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(Dossier.SourceLink.newBuilder().setPath("ctor-source"))
                .setDescription(parseComment("ctor-description")))
            .addTemplateName("T"))
        .addExtendedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("super-one")
                .setText("SuperClass1")))
        .addExtendedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("super-two")
                .setText("SuperClass2")))
        .addExtendedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setText("Foo")))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("type-two")
                .setText("Goodbye")))
        .setTags(Dossier.Tags.getDefaultInstance())
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
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setTags(Dossier.Tags.newBuilder().setIsModule(true))
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source-file"))
        .setDescription(parseComment("description"))
        .setMainFunction(Dossier.Function.newBuilder()
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource(Dossier.SourceLink.newBuilder().setPath("ctor-file"))
                .setDescription(parseComment("ctor-description")))
            .addTemplateName("T"))
        .addExtendedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("super-one")
                .setText("SuperClass1")))
        .addExtendedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("super-two")
                .setText("SuperClass2")))
        .addExtendedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setText("Foo")))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setHref("type-one")
                .setText("Hello")))
        .addImplementedType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
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
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source-file"))
        .setDescription(parseComment("description"))
        .setTags(Dossier.Tags.getDefaultInstance())
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
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath("source"))
        .setDescription(parseComment("description"))
        .setEnumeration(Dossier.Enumeration.newBuilder()
            .setType(Dossier.Comment.newBuilder()
                .addToken(Dossier.Comment.Token.newBuilder()
                    .setText("{color: string}"))))
        .setTags(Dossier.Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.typeHeader", "type", type);

    assertThat(querySelector(document, "h1").toString(), is("<h1>enum Foo</h1>"));
    assertThat(querySelector(document, "dl").toString(), is(
        "<dl><dt>Type<code>{color: string}</code></dt></dl>"));
  }

  @Test
  public void renderEnumValues() {
    Dossier.Enumeration e = Dossier.Enumeration.newBuilder()
        .setType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder()
                .setText("enum-type")))
        .addValue(Dossier.Enumeration.Value.newBuilder()
            .setName("ONE"))
        .addValue(Dossier.Enumeration.Value.newBuilder()
            .setName("TWO")
            .setDescription(parseComment("")))  // Empty description.
        .addValue(Dossier.Enumeration.Value.newBuilder()
            .setName("RED")
            .setDescription(parseComment("<strong>the color red</strong>")))
        .addValue(Dossier.Enumeration.Value.newBuilder()
            .setName("GREEN")
            .setDeprecation(Dossier.Deprecation.newBuilder())  // No deprecation text.
            .setDescription(parseComment("<i>the color green</i>")))
        .addValue(Dossier.Enumeration.Value.newBuilder()
            .setName("BLUE")
            .setDeprecation(Dossier.Deprecation.newBuilder()
                .setNotice(parseComment("This value is deprecated"))))
        .build();

    Document document = renderDocument("dossier.soy.enumValues", ImmutableMap.of("enumeration", e));

    assertThat(querySelector(document, "h2").toString(), is("<h2>Values and Descriptions</h2>"));
    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<h2>Values and Descriptions</h2>",
        "<dl>",
        "<dt><a id=\"ONE\"></a>ONE</dt>",
        "<dt><a id=\"TWO\"></a>TWO</dt>",
        "<dt><a id=\"RED\"></a>RED</dt>",
        "<dd><strong>the color red</strong></dd>",
        "<dt><a id=\"GREEN\"></a>GREEN</dt>",
        "<dd><i>the color green</i></dd>",
        "<dt class=\"deprecated\"><a id=\"BLUE\"></a>BLUE</dt>",
        "<dd>",
        "<b>Deprecated: </b>This value is deprecated",
        "</dd>",
        "</dl>",
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
        Dossier.JsType.TypeSummary.newBuilder()
            .setName("Foo")
            .setHref("foo-link")
            .setSummary(parseComment("foo summary"))
            .build(),
        Dossier.JsType.TypeSummary.newBuilder()
            .setName("Bar")
            .setHref("bar-link")
            .setSummary(parseComment("<strong>bar summary <i>has html</i></strong>"))
            .build(),
        Dossier.JsType.TypeSummary.newBuilder()
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
        "<div class=\"type-summary\">",
        "<table><tbody><tr><td><dl>",
        "<dt><a href=\"foo-link\">Foo</a></dt><dd>foo summary</dd>",
        "<dt><a href=\"bar-link\">Bar</a></dt>",
        "<dd><strong>bar summary <i>has html</i></strong></dd>",
        "<dt><a href=\"baz-link\">Baz</a></dt>",
        "<dd>No Description.</dd>",
        "</dl></td></tr></tbody></table>",
        "</div>"));
  }

  @Test
  public void renderFunctionDetails_basicFunction() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .build();
    assertThat("No details to render",
        render("dossier.soy.fnDetails", "fn", function), is(""));
  }

  @Test
  public void renderFunctionDetails_hasParameters() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("b")
            .setDescription(parseComment("<i>b</i> awesome")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("c")
            .setType(Dossier.Comment.newBuilder()
                .addToken(Dossier.Comment.Token.newBuilder()
                    .setHtml("<b>Object</b>"))))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("d")
            .setType(Dossier.Comment.newBuilder()
                .addToken(Dossier.Comment.Token.newBuilder()
                    .setText("Error")))
            .setDescription(parseComment("goodbye")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("who am i")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><table><tbody>",
        "<tr><th>Parameters</th></tr>",
        "<tr><td><dl>",
        "<dt>a</dt>",
        "<dt>b</dt>",
        "<dd><i>b</i> awesome</dd>",
        "<dt>c<code><b>Object</b></code></dt>",
        "<dt>d<code>Error</code></dt>",
        "<dd>goodbye</dd>",
        "<dt></dt>",
        "<dd>who am i</dd>",
        "</dl></td></tr>",
        "</tbody></table></div>"));
  }

  @Test
  public void renderFunctionDetails_hasThrownTypes() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .addThrown(Dossier.Function.Detail.newBuilder().setName("Error"))
        .addThrown(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><table><tbody>",
        "<tr><th>Throws</th></tr>",
        "<tr><td><dl>",
        "<dt>Error</dt>",
        "<dt></dt>",
        "<dd>randomly</dd>",
        "</dl></td></tr>",
        "</tbody></table></div>"));
  }

  @Test
  public void renderFunctionDetails_hasReturnWithoutDescription() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .setReturn(Dossier.Function.Detail.newBuilder())
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(0));
  }

  @Test
  public void renderFunctionDetails_hasReturn() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><table><tbody>",
        "<tr><th>Returns</th></tr>",
        "<tr><td><p>randomly</p></td></tr>",
        "</tbody></table></div>"));
  }

  @Test
  public void renderFunctionDetails_hasReturn_constructor() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .setIsConstructor(true)
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><table><tbody>",
        "<tr><th>Parameters</th></tr>",
        "<tr><td><dl><dt>a</dt></dl></td></tr>",
        "</tbody></table></div>"));  }

  @Test
  public void renderFunctionDetails_fullyDefined() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("b")
            .setDescription(parseComment("<i>b</i> awesome")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("c")
            .setType(Dossier.Comment.newBuilder()
                .addToken(Dossier.Comment.Token.newBuilder()
                    .setHtml("<b>Object</b>"))))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("d")
            .setType(Dossier.Comment.newBuilder()
                .addToken(Dossier.Comment.Token.newBuilder()
                    .setText("Error")))
            .setDescription(parseComment("goodbye")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("who am i")))
        .addThrown(Dossier.Function.Detail.newBuilder()
            .setName("Error")
            .setDescription(parseComment("randomly")))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("something")))
        .build();

    Document document = renderDocument("dossier.soy.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div><table><tbody>",
        "<tr><th>Parameters</th></tr>",
        "<tr><td><dl>",
        "<dt>a</dt><dt>b</dt><dd><i>b</i> awesome</dd>",
        "<dt>c<code><b>Object</b></code></dt>",
        "<dt>d<code>Error</code></dt><dd>goodbye</dd>",
        "<dt></dt><dd>who am i</dd>",
        "</dl></td></tr>",
        "<tr><th>Returns</th></tr><tr><td><p>something</p></td></tr>",
        "<tr><th>Throws</th></tr><tr><td><dl><dt>Error</dt><dd>randomly</dd></dl></td></tr>",
        "</tbody></table></div>"));
  }

  @Test
  public void renderMainFunction_constructor() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath(""))
            .setDescription(parseComment("")))
        .setIsConstructor(true)
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .addParameter(Dossier.Function.Detail.newBuilder().setName("b"))
        .build();
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource(Dossier.SourceLink.newBuilder().setPath(""))
        .setDescription(Dossier.Comment.getDefaultInstance())
        .setMainFunction(function)
        .setTags(Dossier.Tags.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.soy.mainFunction", "type", type);
    assertThat(document.body().children().size(), is(2));
    assertThat(document.body().child(0).toString(),
        isHtml("<h2 class=\"main\">new Foo(a, b)</h2>"));
  }

  @Test
  public void renderDeprecatedFunctionProperty() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("Bar")
            .setSource(Dossier.SourceLink.newBuilder().setPath("bar.link"))
            .setDescription(parseComment("description here\n<p>second paragraph"))
            .setDeprecation(Dossier.Deprecation.newBuilder()
                .setNotice(parseComment("is old"))))
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .build();

    ImmutableMap<String, ?> data = ImmutableMap.of("fn", function);

    Document document = renderDocument("dossier.soy.printFunction", data);
    assertThat(querySelector(document, "body").toString(), isHtml(
        "<body>",
        "<h3>",
        "<a id=\"Bar\"></a>Bar(a)",
        "<span class=\"codelink\"><a href=\"bar.link\">code &raquo;</a></span>",
        "</h3>",
        "<p>description here\n</p>",
        "<p>second paragraph</p>",
        "<p><b>Deprecated: </b>is old</p>",
        "<div><table><tbody>",
        "<tr><th>Parameters</th></tr>",
        "<tr><td><dl><dt>a</dt></dl></td></tr>",
        "</tbody></table></div>",
        "</body>"));
  }

  @Test
  public void renderProperty() {
    Dossier.Property property = Dossier.Property.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo")
            .setSource(Dossier.SourceLink.newBuilder().setPath("foo-source"))
            .setDescription(parseComment("foo description")))
        .setType(Dossier.Comment.newBuilder()
            .addToken(Dossier.Comment.Token.newBuilder().setText("string")))
        .build();

    Document document = renderDocument("dossier.soy.printProperty", "prop", property);
    Element details = querySelector(document, "body");
    assertThat(details.className(), is(""));

    assertThat(details.child(0).toString(), isHtml(
        "<dt><a id=\"foo\"></a><a href=\"foo-source\">foo</a><code>string</code></dt>"));
  }

  @Test
  public void renderComment_unterminatedInlineTaglet() {
    Dossier.Comment comment = parseComment("Hello {@code world");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p>Hello {@code world</p></body>"));
  }

  @Test
  public void renderComment_withCodeTaglet() {
    Dossier.Comment comment = parseComment("Hello, {@code world}!");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p>Hello, <code>world</code>!</p></body>"));
  }

  @Test
  public void renderComment_withAdjacentInlineCodeTaglets() {
    Dossier.Comment comment = parseComment("{@code hello}{@code world}");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p><code>hello</code><code>world</code></p></body>"));
  }

  @Test
  public void renderComment_codeTagletsAreHtmlEscaped() {
    Dossier.Comment comment = parseComment("{@code 1 < 2 && 4 > 3}");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "<code>1 &lt; 2 &amp;&amp; 4 &gt; 3</code>",
        "</p></body>"));
  }

  @Test
  public void renderComment_literalTagletsAreHtmlEscaped() {
    Dossier.Comment comment = parseComment("{@literal 1 < 2 && 4 > 3}");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>1 &lt; 2 &amp;&amp; 4 &gt; 3</p></body>"));
  }

  @Test
  public void renderComment_unresolvedLink() {
    Dossier.Comment comment = parseComment("An {@link Foo unknown} type");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "An <code><a class=\"unresolved-link\">unknown</a></code>",
        " type</p></body>"));
  }

  @Test
  public void renderComment_resolvedLink() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(
        Dossier.TypeLink.newBuilder()
            .setText("")
            .setHref("/path/to/foo").build());

    Dossier.Comment comment = parseComment(
        "A {@link foo.Bar milk & cookies} snack");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "A <code>",
        "<a href=\"/path/to/foo\">milk &amp; cookies</a></code>",
        " snack</p></body>"));
  }

  @Test
  public void renderComment_plainLink() {
    when(mockLinker.getLink("foo.Bar")).thenReturn(
        Dossier.TypeLink.newBuilder()
            .setText("")
            .setHref("/path/to/foo").build());


    Dossier.Comment comment = parseComment(
        "A {@linkplain foo.Bar milk & cookies} snack");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "A <a href=\"/path/to/foo\">milk &amp; cookies</a> snack",
        "</p></body>"));
  }

  @Test
  public void renderComment_doesNotEscapePlainTextContent() {
    Dossier.Comment comment = parseComment(
        "A <strong>strongly</strong> worded letter");

    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>A <strong>strongly</strong> worded letter</p></body>"));
  }

  @Test
  public void renderComment_omittingLeadingParagraphTag() {
    Dossier.Comment comment = parseComment(
        "A <strong>strongly</strong> worded letter");

    Document document = renderDocument("dossier.soy.comment",
        ImmutableMap.of("comment", comment, "omitLeadingTag", true));
    assertThat(document.body().toString(), isHtml(
        "<body>A <strong>strongly</strong> worded letter</body>"));
  }

  @Test
  public void renderComment_emptyComment() {
    Dossier.Comment comment = parseComment("");
    Document document = renderDocument("dossier.soy.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml("<body></body>"));
  }

  @Test
  public void renderComment_nullComment() {
    Dossier.Comment comment = parseComment(null);
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

  private Dossier.Comment parseComment(String comment) {
    return CommentUtil.parseComment(comment, mockLinker);
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
        .setData(data == null ? null : transformValues(data, new Function<Object, Object>() {
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
