package com.github.jleyba.dossier;

import static com.github.jleyba.dossier.Renderer.toMap;
import static com.google.common.collect.Lists.transform;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.GeneratedMessage;
import org.hamcrest.Matcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Before;
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

  private Linker mockLinker;

  @Before
  public void setUpMocks() {
    mockLinker = mock(Linker.class);
  }

  @Test
  public void convertResourceMessageToMap() {
    Dossier.Resources resources = Dossier.Resources.newBuilder()
        .addScript("foo")
        .addScript("bar")
        .addCss("baz")
        .build();

    Map<String, Object> data = toMap(resources);
    assertEquals(
        Sets.newHashSet("script", "css"),
        data.keySet());

    assertEquals(Lists.newArrayList("foo", "bar"), data.get("script"));
    assertEquals(Lists.newArrayList("baz"), data.get("css"));
  }

  @Test
  public void convertSourceFileToMap() {
    Dossier.SourceFile sourceFile = Dossier.SourceFile.newBuilder()
        .setBaseName("baz.txt")
        .setPath("foo/bar/baz.txt")
        .addLines("one")
        .addLines("two")
        .build();

    Map<String, Object> data = toMap(sourceFile);
    assertEquals(
        Sets.newHashSet("baseName", "path", "lines"),
        data.keySet());

    assertEquals("baz.txt", data.get("baseName"));
    assertEquals("foo/bar/baz.txt", data.get("path"));
    assertEquals(Lists.newArrayList("one", "two"), data.get("lines"));
  }

  @Test
  public void convertLicenseFile() {
    Dossier.License message = Dossier.License.newBuilder()
        .setText("foo bar baz")
        .build();

    Map<String, Object> data = toMap(message);
    assertEquals(Sets.newHashSet("text"), data.keySet());
    assertEquals("foo bar baz", data.get("text"));
  }

  @Test
  public void convertingLicenseRenderSpec() {
    GeneratedMessage message = Dossier.LicenseRenderSpec.newBuilder()
        .setLicense(Dossier.License.newBuilder()
            .setText("foo bar baz")
            .build())
        .setResources(Dossier.Resources.newBuilder()
            .addScript("foo")
            .addScript("bar")
            .addCss("baz")
            .build())
        .build();

    Map<String, Object> data = toMap(message);
    assertEquals(Sets.newHashSet("resources", "license"), data.keySet());

    @SuppressWarnings("unchecked")
    Map<String, Object> resources = (Map<String, Object>) data.get("resources");
    assertEquals(Lists.newArrayList("foo", "bar"), resources.get("script"));
    assertEquals(Lists.newArrayList("baz"), resources.get("css"));

    @SuppressWarnings("unchecked")
    Map<String, Object> license = (Map<String, Object>) data.get("license");
    assertEquals("foo bar baz", license.get("text"));
  }

  @Test
  public void usesFalseyValuesForOmittedOptionalFieldsInConvertedMap() {
    Map<String, Object> data = toMap(Dossier.Deprecation.newBuilder().build());
    assertThat(data.get("notice"), is(nullValue()));

    data = toMap(Dossier.BaseProperty.newBuilder()
        .setName("name")
        .setSource("source")
        .setDescription(parseComment("description"))
        .build());
    assertThat(data.get("deprecation"), is(nullValue()));
  }

  @Test
  public void renderDeprecationNotice() {
    Dossier.Deprecation.Builder builder = Dossier.Deprecation.newBuilder();

    assertEquals("", render("dossier.deprecationNotice", null));
    assertEquals("", render("dossier.deprecationNotice", new HashMap<String, Object>()));

    builder.setNotice(parseComment("Hello, world!"));
    assertThat(
        render("dossier.deprecationNotice", ImmutableMap.of(
            "deprecation", toMap(builder.build()))),
        isHtml(
            "<div class=\"deprecation-notice\">Deprecated: ",
            "<span class=\"deprecation-reason\">Hello, world!</span>",
            "</div>"));

    builder.setNotice(parseComment("<strong>Hello, world!</strong>"));
    assertThat(
        render("dossier.deprecationNotice", ImmutableMap.of(
            "deprecation", toMap(builder.build()))),
        isHtml(
            "<div class=\"deprecation-notice\">Deprecated: ",
            "<span class=\"deprecation-reason\"><strong>Hello, world!</strong></span>",
            "</div>"));
  }

  @Test
  public void renderPageHeader() {
    assertThat(
        render("dossier.pageHeader", ImmutableMap.of(
            "title", "Foo.Bar",
            "resources", toMap(Dossier.Resources.newBuilder()
            .addScript("one")
            .addScript("two")
            .addCss("apples")
            .addCss("oranges")
            .build()))),
        isHtml(
            "<!DOCTYPE html>",
            "<meta charset=\"UTF-8\">",
            "<title>Foo.Bar</title>",
            "<link href=\"apples\" rel=\"stylesheet\" type=\"text/css\">",
            "<link href=\"oranges\" rel=\"stylesheet\" type=\"text/css\">"));
  }

  @Test
  public void renderSourceLink() {
    assertThat(
        render("dossier.sourceLink", ImmutableMap.<String, Object>of()),
        is(""));
    assertThat(
        render("dossier.sourceLink", ImmutableMap.<String, Object>of("href", "foo.bar")),
        is("<a class=\"source\" href=\"foo.bar\">code &raquo;</a>"));
  }

  @Test
  public void renderClassInheritance() {
    List<Map<String, ?>> types = new LinkedList<>();
    assertThat(render("dossier.classInheritance", ImmutableMap.of("types", types)),
        is(""));

    types.add(toMap(Dossier.TypeLink.newBuilder()
        .setHref("foo.link").setText("Foo")
        .build()));
    assertThat(render("dossier.classInheritance", ImmutableMap.of("types", types)),
        is(""));

    types.add(toMap(Dossier.TypeLink.newBuilder()
        .setHref("bar.link").setText("Bar")
        .build()));
    assertThat(render("dossier.classInheritance", ImmutableMap.of("types", types)),
        isHtml(
            "<pre><code>",
            "<a href=\"foo.link\">Foo</a>",
            "\n  &#x2514; Bar",
            "</code></pre>"));

    types.add(toMap(Dossier.TypeLink.newBuilder()
        .setHref("baz.link").setText("Baz")
        .build()));
    assertThat(render("dossier.classInheritance", ImmutableMap.of("types", types)),
        isHtml(
            "<pre><code>",
            "<a href=\"foo.link\">Foo</a>",
            "\n  &#x2514; <a href=\"bar.link\">Bar</a>",
            "\n      &#x2514; Baz",
            "</code></pre>"));
  }

  @Test
  public void printInterfaces_emptyList() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("name")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .build();

    assertThat(render("dossier.printInterfaces", "type", type), is(""));
  }

  @Test
  public void printInterfaces_interface() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("name")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .setIsInterface(true)
        .addImplementedType(Dossier.TypeLink.newBuilder()
            .setHref("type-one")
            .setText("Hello"))
        .addImplementedType(Dossier.TypeLink.newBuilder()
            .setHref("type-two")
            .setText("Goodbye"))
        .build();

    assertThat(render("dossier.printInterfaces", "type", type),
        isHtml(
            "<dl><dt>All extended interfaces:<dd>",
            "<code><a href=\"type-one\">Hello</a></code>, ",
            "<code><a href=\"type-two\">Goodbye</a></code>",
            "</dl>"));
  }

  @Test
  public void printInterfaces_class() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("name")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .setConstructor(Dossier.Function.newBuilder()
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource("ctor-source")
                .setDescription(parseComment("ctor-description"))))
        .addImplementedType(Dossier.TypeLink.newBuilder()
            .setHref("type-one")
            .setText("Hello"))
        .addImplementedType(Dossier.TypeLink.newBuilder()
            .setHref("type-two")
            .setText("Goodbye"))
        .build();

    assertThat(render("dossier.printInterfaces", "type", type),
        isHtml(
            "<dl><dt>All implemented interfaces:<dd>",
            "<code><a href=\"type-one\">Hello</a></code>, ",
            "<code><a href=\"type-two\">Goodbye</a></code>",
            "</dl>"));
  }

  @Test
  public void renderTypeHeader_interface() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setIsInterface(true)
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .addImplementedType(Dossier.TypeLink.newBuilder()
            .setHref("type-one")
            .setText("Hello"))
        .addImplementedType(Dossier.TypeLink.newBuilder()
            .setHref("type-two")
            .setText("Goodbye"))
        .build();

    Document document = renderDocument("dossier.typeHeader", "type", type);

    Element h1 = querySelector(document, "header > h1");
    assertEquals("<h1>Interface Foo</h1>", h1.toString());
    assertThat(querySelector(document, "dl > dt").toString(),
        is("<dt>All extended interfaces:</dt>"));
    assertThat(document.select("div.deprecation-notice").isEmpty(), is(true));
  }

  @Test
  public void renderTypeHeader_deprecatedInterface_noNoticeText() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setIsInterface(true)
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .setDeprecation(Dossier.Deprecation.newBuilder())
        .build();

    Document document = renderDocument("dossier.typeHeader", "type", type);

    assertThat(querySelector(document, "header > h1").toString(), isHtml(
        "<h1>Interface Foo<span class=\"deprecation-notice\"> ",
        "(deprecated)</span></h1>"));
    assertThat(document.select("div.deprecation-notice").isEmpty(), is(true));
  }

  @Test
  public void renderTypeHeader_deprecatedInterface_hasNoticeText() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setIsInterface(true)
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .setDeprecation(Dossier.Deprecation.newBuilder()
            .setNotice(parseComment("<i>Goodbye</i>, world!")))
        .build();

    Document document = renderDocument("dossier.typeHeader", "type", type);

    assertThat(querySelector(document, "header > h1").toString(), isHtml(
        "<h1>Interface Foo<span class=\"deprecation-notice\"> ",
        "(deprecated)</span></h1>"));
    assertThat(querySelector(document, ".deprecation-reason").toString(), isHtml(
        "<span class=\"deprecation-reason\"><i>Goodbye</i>, world!</span>"));
  }

  @Test
  public void renderTypeHeader_interfaceHasTemplateNames() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setIsInterface(true)
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .setConstructor(Dossier.Function.newBuilder()
            .addTemplateName("K")
            .addTemplateName("V")
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource("ctor-source")
                .setDescription(parseComment("ctor-description"))))
        .build();

    Document document = renderDocument("dossier.typeHeader", "type", type);

    assertThat(querySelector(document, "header > h1").toString(), isHtml(
        "<h1>Interface Foo.<code class=\"type\">&lt;K, V&gt;</code></h1>"));
  }

  @Test
  public void renderTypeHeader_classHasTemplateNames() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .setConstructor(Dossier.Function.newBuilder()
            .addTemplateName("K")
            .addTemplateName("V")
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource("ctor-source")
                .setDescription(parseComment("ctor-description"))))
        .build();

    Document document = renderDocument("dossier.typeHeader", "type", type);

    assertThat(querySelector(document, "header > h1").toString(), isHtml(
        "<h1>Class Foo.<code class=\"type\">&lt;K, V&gt;</code></h1>"));
  }

  @Test
  public void renderTypeHeader_complexClass() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource("source-file")
        .setDescription(parseComment("description"))
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .setConstructor(Dossier.Function.newBuilder()
            .setBase(Dossier.BaseProperty.newBuilder()
                .setName("ctor-name")
                .setSource("ctor-source")
                .setDescription(parseComment("ctor-description")))
            .addTemplateName("T"))
        .addExtendedType(Dossier.TypeLink.newBuilder()
            .setHref("super-one")
            .setText("SuperClass1"))
        .addExtendedType(Dossier.TypeLink.newBuilder()
            .setHref("super-two")
            .setText("SuperClass2"))
        .addExtendedType(Dossier.TypeLink.newBuilder()
            .setHref("#")
            .setText("Foo"))
        .addImplementedType(Dossier.TypeLink.newBuilder()
            .setHref("type-one")
            .setText("Hello"))
        .addImplementedType(Dossier.TypeLink.newBuilder()
            .setHref("type-two")
            .setText("Goodbye"))
        .build();

    Document document = renderDocument("dossier.typeHeader", "type", type);
    assertThat(querySelector(document, "header").toString(), isHtml(
        "<header>",
        "<h1>Class Foo.<code class=\"type\">&lt;T&gt;</code></h1>",
        "<a class=\"source\" href=\"source-file\">code &raquo;</a>",
        "<pre><code>",
        "<a href=\"super-one\">SuperClass1</a>",
        "\n  \u2514 <a href=\"super-two\">SuperClass2</a>",
        "\n      \u2514 Foo",
        "</code></pre>",
        "<dl><dt>All implemented interfaces:</dt><dd>",
        "<code><a href=\"type-one\">Hello</a></code>, ",
        "<code><a href=\"type-two\">Goodbye</a></code>",
        "</dd></dl></header>"));
  }

  @Test
  public void renderTypeHeader_namespace() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource("source-file")
        .setDescription(parseComment("description"))
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .build();

    Document document = renderDocument("dossier.typeHeader", "type", type);

    assertThat(querySelector(document, "header").toString(), isHtml(
        "<header>",
        "<h1>Namespace Foo</h1>",
        "<a class=\"source\" href=\"source-file\">code &raquo;</a>",
        "</header>"));
  }

  @Test
  public void renderTypeHeader_enumeration() {
    Dossier.JsType type = Dossier.JsType.newBuilder()
        .setName("Foo")
        .setSource("source")
        .setDescription(parseComment("description"))
        .setNested(Dossier.JsType.NestedTypes.getDefaultInstance())
        .setEnumeration(Dossier.Enumeration.newBuilder()
            .setTypeHtml("{color: string}"))
        .build();

    Document document = renderDocument("dossier.typeHeader", "type", type);

    assertThat(querySelector(document, "header > h1").toString(), is("<h1>Enum Foo</h1>"));
    assertThat(querySelector(document, "header > dl").toString(), is(
        "<dl><dt>Type: <code class=\"type\">{color: string}</code></dt></dl>"));
  }

  @Test
  public void renderEnumValues() {
    Dossier.Enumeration e = Dossier.Enumeration.newBuilder()
        .setTypeHtml("enum-type")
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

    Document document = renderDocument("dossier.enumValues",
        ImmutableMap.of("name", "foo.Bar", "enumeration", toMap(e)));

    assertThat(querySelector(document, "h2").toString(), is("<h2>Values and Descriptions</h2>"));
    assertThat(querySelector(document, "div.type-summary dl").toString(), isHtml(
        "<dl class=\"public\">",
        "<dt><a class=\"enum member\" name=\"foo.Bar.ONE\">ONE</a></dt>",
        "<dt><a class=\"enum member\" name=\"foo.Bar.TWO\">TWO</a></dt>",
        "<dt><a class=\"enum member\" name=\"foo.Bar.RED\">RED</a></dt>",
        "<dd><strong>the color red</strong></dd>",
        "<dt><a class=\"enum member deprecation-notice\" name=\"foo.Bar.GREEN\">GREEN</a></dt>",
        "<dd><i>the color green</i></dd>",
        "<dt><a class=\"enum member deprecation-notice\" name=\"foo.Bar.BLUE\">BLUE</a></dt>",
        "<dd>",
        "<div class=\"deprecation-notice\">Deprecated: ",
        "<span class=\"deprecation-reason\">This value is deprecated</span>",
        "</div>",
        "</dd>",
        "</dl>"));
  }

  @Test
  public void renderNestedTypeSummaries_emptyList() {
    Document document = renderDocument("dossier.nestedTypeSummaries",
        ImmutableMap.<String, Object>of(
            "title", "Interfaces",
            "types", ImmutableList.of()));
    assertTrue(document.select("section").isEmpty());
  }

  @Test
  public void renderNestedTypeSummaries() {
    List<GeneratedMessage> types = ImmutableList.<GeneratedMessage>of(
        Dossier.JsType.NestedTypes.TypeSummary.newBuilder()
            .setName("Foo")
            .setHref("foo-link")
            .setSummary("foo summary")
            .build(),
        Dossier.JsType.NestedTypes.TypeSummary.newBuilder()
            .setName("Bar")
            .setHref("bar-link")
            .setSummary("<strong>bar summary <i>has html</i></strong>")
            .build(),
        Dossier.JsType.NestedTypes.TypeSummary.newBuilder()
            .setName("Baz")
            .setHref("baz-link")
            .setSummary("")
            .build());

    Document document = renderDocument("dossier.nestedTypeSummaries",
        ImmutableMap.<String, Object>of(
            "title", "Interfaces",
            "types", transform(types, msgToMap())));
    assertThat(querySelector(document, "section > h2").toString(), is("<h2>Interfaces</h2>"));
    assertThat(querySelector(document, "section > h2 + .type-summary").toString(), isHtml(
        "<div class=\"type-summary\">",
        "<table><tbody><tr><td><dl>",
        "<dt><a href=\"foo-link\">Foo</a></dt><dd>foo summary</dd>",
        "<dt><a href=\"bar-link\">Bar</a></dt><dd>",
        "&lt;strong&gt;bar summary &lt;i&gt;has html&lt;/i&gt;&lt;/strong&gt;</dd>",
        "<dt><a href=\"baz-link\">Baz</a></dt>",
        "<dd>No Description.</dd>",
        "</dl></td></tr></tbody></table>",
        "</div>"));
  }

  @Test
  public void renderTypeDefs_emptyList() {
    assertThat(
        render("dossier.typedefs", ImmutableMap.of("typeDefs", ImmutableList.of())),
        isHtml(""));
  }

  @Test
  public void renderTypeDefs() {
    List<GeneratedMessage> typedefs = ImmutableList.<GeneratedMessage>of(
        Dossier.JsType.TypeDef.newBuilder()
            .setName("foo.Bar")
            .setTypeHtml("Array")
            .setHref("bar-source")
            .setDescription(parseComment("Bar is an <i>Array</i>"))
            .build(),
        Dossier.JsType.TypeDef.newBuilder()
            .setName("foo.Bim")
            .setHref("bim-source")
            .setTypeHtml("Object.&lt;<i>string</i>&gt;")
            .setDescription(parseComment(""))
            .setDeprecation(Dossier.Deprecation.getDefaultInstance())
            .build(),
        Dossier.JsType.TypeDef.newBuilder()
            .setName("foo.Baz")
            .setTypeHtml("string")
            .setHref("baz-source")
            .setDescription(parseComment("description text"))
            .setDeprecation(Dossier.Deprecation.newBuilder()
                .setNotice(parseComment("<strong>deprecated!</strong>")))
            .build());

    Document document = renderDocument("dossier.typedefs", "typeDefs", typedefs);
    Elements elements = document.select("summary");
    assertThat(elements.size(), is(3));

    assertThat(elements.get(0).toString(), isHtml(
        "<summary>",
        "<div><a class=\"source\" href=\"bar-source\">code &raquo;</a>",
        "<a class=\"member\" name=\"foo.Bar\">foo.Bar</a> : ",
        "<code class=\"type\">Array</code></div>",
        "<div>Bar is an <i>Array</i></div>",
        "</summary>"));
    assertThat(elements.get(1).toString(), isHtml(
        "<summary>",
        "<div><a class=\"source\" href=\"bim-source\">code &raquo;</a>",
        "<a class=\"member deprecation-notice\" name=\"foo.Bim\">foo.Bim</a> : ",
        "<code class=\"type\">Object.&lt;<i>string</i>&gt;</code></div>",
        "<div>No description.</div>",
        "</summary>"));
    assertThat(elements.get(2).toString(), isHtml(
        "<summary>",
        "<div><a class=\"source\" href=\"baz-source\">code &raquo;</a>",
        "<a class=\"member deprecation-notice\" name=\"foo.Baz\">foo.Baz</a> : ",
        "<code class=\"type\">string</code>",
        "<div class=\"deprecation-notice\">Deprecated: ",
        "<span class=\"deprecation-reason\"><strong>deprecated!</strong></span>",
        "</div></div>",
        "<div>description text</div>",
        "</summary>"));
  }

  @Test
  public void renderMemberSignature_globalProperty() {
    Dossier.Property property = Dossier.Property.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("Foo")
            .setSource("")
            .setDescription(parseComment("")))
        .setTypeHtml("string")
        .build();

    Document document = renderDocument("dossier.memberSignature", "member", property);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">",
        "<a name=\"Foo\">Foo</a> : <code class=\"type\">string</code></span>"));
  }

  @Test
  public void renderMemberSignature_staticProperty() {
    Dossier.Property property = Dossier.Property.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("Baz")
            .setSource("")
            .setDescription(parseComment("")))
        .setTypeHtml("string")
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "member", toMap(property),
        "parentName", "foo.Bar");

    Document document = renderDocument("dossier.memberSignature", data);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">",
        "<a name=\"foo.Bar.Baz\">foo.Bar.Baz</a> : <code class=\"type\">string</code></span>"));
  }

  @Test
  public void renderMemberSignature_deprecatedStaticProperty() {
    Dossier.Property property = Dossier.Property.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("Baz")
            .setSource("")
            .setDescription(parseComment(""))
            .setDeprecation(Dossier.Deprecation.getDefaultInstance()))
        .setTypeHtml("string")
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "member", toMap(property),
        "parentName", "foo.bar");

    Document document = renderDocument("dossier.memberSignature", data);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member deprecation-notice\">",
        "<a name=\"foo.bar.Baz\">foo.bar.Baz</a> : <code class=\"type\">string</code></span>"));
  }

  @Test
  public void renderMemberSignature_typelessProperty() {
    Dossier.Property property = Dossier.Property.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("Baz")
            .setSource("")
            .setDescription(parseComment("")))
        .setTypeHtml("")
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "member", toMap(property),
        "parentName", "foo.bar");

    Document document = renderDocument("dossier.memberSignature", data);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\"><a name=\"foo.bar.Baz\">foo.bar.Baz</a></span>"));
  }

  @Test
  public void renderMemberSignature_instanceProperty() {
    Dossier.Property property = Dossier.Property.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo")
            .setSource("")
            .setDescription(parseComment("")))
        .setTypeHtml("string")
        .build();

    Document document = renderDocument("dossier.memberSignature", "member", property);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\"><a name=\"foo\">foo</a>",
        " : <code class=\"type\">string</code></span>"));
  }

  @Test
  public void renderMemgerSignature_staticFunction() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("baz")
            .setSource("")
            .setDescription(parseComment("")))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setTypeHtml("<a href=\"#\">Foo</a>"))
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "member", toMap(function),
        "parentName", "foo.bar");

    Document document = renderDocument("dossier.memberSignature", data);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">",
        "<a name=\"foo.bar.baz\">foo.bar.baz</a> <span class=\"args\">()</span> \u21d2 ",
        "<code class=\"type\"><a href=\"#\">Foo</a></code>",
        "</span>"));
  }

  @Test
  public void renderMemberSignature_staticFunction_returnNotSpecified() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("baz")
            .setSource("")
            .setDescription(parseComment("")))
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "member", toMap(function),
        "parentName", "foo.bar");

    Document document = renderDocument("dossier.memberSignature", data);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">",
        "<a name=\"foo.bar.baz\">foo.bar.baz</a> <span class=\"args\">()</span></span>"));
  }

  @Test
  public void renderMemberSignature_staticFunction_returnTypeUnknown() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("baz")
            .setSource("")
            .setDescription(parseComment("")))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setTypeHtml("?"))
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "member", toMap(function),
        "parentName", "foo.bar");

    Document document = renderDocument("dossier.memberSignature", data);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">",
        "<a name=\"foo.bar.baz\">foo.bar.baz</a>",
        " <span class=\"args\">()</span></span>"));
  }

  @Test
  public void renderMemberSignature_staticFunction_returnsUndefined() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("baz")
            .setSource("")
            .setDescription(parseComment("")))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setTypeHtml("undefined"))
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "member", toMap(function),
        "parentName", "foo.bar");

    Document document = renderDocument("dossier.memberSignature", data);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">",
        "<a name=\"foo.bar.baz\">foo.bar.baz</a> <span class=\"args\">()</span></span>"));
  }

  @Test
  public void renderMemberSignature_function_hasParameters() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("baz")
            .setSource("")
            .setDescription(parseComment("")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("a"))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("b"))
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "member", toMap(function),
        "parentName", "foo.bar");

    Document document = renderDocument("dossier.memberSignature", data);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">",
        "<a name=\"foo.bar.baz\">foo.bar.baz</a> <span class=\"args\">(a, b)</span></span>"));
  }

  @Test
  public void renderMemberSignature_constructor() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource("")
            .setDescription(parseComment("")))
        .setIsConstructor(true)
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("a"))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("b"))
        // Constructor's should never have a return type, but even if they do,
        // it should not be included in the rendered HTML.
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setTypeHtml("string"))
        .build();

    Document document = renderDocument("dossier.memberSignature", "member", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">foo.Bar <span class=\"args\">(a, b)</span></span>"));
  }

  @Test
  public void renderMemberSignature_constructor_templateClass() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource("")
            .setDescription(parseComment("")))
        .setIsConstructor(true)
        // Template names should be excluded from the rendered constructor signature.
        .addTemplateName("K")
        .addTemplateName("V")
        // Constructor's should never have a return type, but even if they do,
        // it should not be included in the rendered HTML.
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setTypeHtml("string"))
        .build();

    Document document = renderDocument("dossier.memberSignature", "member", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<span class=\"member\">foo.Bar <span class=\"args\">()</span></span>"));
  }

  @Test
  public void renderMemberSignature_templateFunction() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource("")
            .setDescription(parseComment("")))
         // Template names should be excluded from the rendered constructor signature.
        .addTemplateName("K")
        .addTemplateName("V")
        // Constructor's should never have a return type, but even if they do,
        // it should not be included in the rendered HTML.
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setTypeHtml("string"))
        .build();

    Document document = renderDocument("dossier.memberSignature", "member", function);
    assertThat(document.body().toString(), isHtml(
        "<body>",
        "<code class=\"type\">&lt;K, V&gt;</code> ",
        "<span class=\"member\"><a name=\"foo.Bar\">foo.Bar</a> <span class=\"args\">()</span>",
        " \u21d2 <code class=\"type\">string</code>",
        "</span>",
        "</body>"));
  }

  @Test
  public void renderFunctionDetails_basicFunction() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource("")
            .setDescription(parseComment("")))
        .build();
    assertThat("No details to render",
        render("dossier.fnDetails", "fn", function), is(""));
  }

  @Test
  public void renderFunctionDetails_hasParameters() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource("")
            .setDescription(parseComment("")))
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("b")
            .setDescription(parseComment("<i>b</i> awesome")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("c")
            .setTypeHtml("<b>Object</b>"))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("d")
            .setTypeHtml("Error")
            .setDescription(parseComment("goodbye")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("who am i")))
        .build();

    Document document = renderDocument("dossier.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div class=\"info\"><table><tbody>",
        "<tr><th>Parameters</th></tr>",
        "<tr><td><dl>",
        "<dt>a</dt>",
        "<dt>b</dt>",
        "<dd><i>b</i> awesome</dd>",
        "<dt>c: <code class=\"type\"><b>Object</b></code></dt>",
        "<dt>d: <code class=\"type\">Error</code></dt>",
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
            .setSource("")
            .setDescription(parseComment("")))
        .addThrown(Dossier.Function.Detail.newBuilder().setName("Error"))
        .addThrown(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div class=\"info\"><table><tbody>",
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
            .setSource("")
            .setDescription(parseComment("")))
        .setReturn(Dossier.Function.Detail.newBuilder())
        .build();

    Document document = renderDocument("dossier.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(0));
  }

  @Test
  public void renderFunctionDetails_hasReturn() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource("")
            .setDescription(parseComment("")))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div class=\"info\"><table><tbody>",
        "<tr><th>Returns</th></tr>",
        "<tr><td><dl>randomly</dl></td></tr>",
        "</tbody></table></div>"));
  }

  @Test
  public void renderFunctionDetails_hasReturn_constructor() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource("")
            .setDescription(parseComment("")))
        .setIsConstructor(true)
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("randomly")))
        .build();

    Document document = renderDocument("dossier.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div class=\"info\"><table><tbody>",
        "<tr><th>Parameters</th></tr>",
        "<tr><td><dl><dt>a</dt></dl></td></tr>",
        "</tbody></table></div>"));  }

  @Test
  public void renderFunctionDetails_fullyDefined() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo.Bar")
            .setSource("")
            .setDescription(parseComment("")))
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("b")
            .setDescription(parseComment("<i>b</i> awesome")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("c")
            .setTypeHtml("<b>Object</b>"))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setName("d")
            .setTypeHtml("Error")
            .setDescription(parseComment("goodbye")))
        .addParameter(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("who am i")))
        .addThrown(Dossier.Function.Detail.newBuilder()
            .setName("Error")
            .setDescription(parseComment("randomly")))
        .setReturn(Dossier.Function.Detail.newBuilder()
            .setDescription(parseComment("something")))
        .build();

    Document document = renderDocument("dossier.fnDetails", "fn", function);
    assertThat(document.body().children().size(), is(1));
    assertThat(document.body().child(0).toString(), isHtml(
        "<div class=\"info\"><table><tbody>",
        "<tr><th>Parameters</th></tr>",
        "<tr><td><dl>",
        "<dt>a</dt>",
        "<dt>b</dt>",
        "<dd><i>b</i> awesome</dd>",
        "<dt>c: <code class=\"type\"><b>Object</b></code></dt>",
        "<dt>d: <code class=\"type\">Error</code></dt>",
        "<dd>goodbye</dd>",
        "<dt></dt>",
        "<dd>who am i</dd>",
        "</dl></td></tr>",
        "<tr><th>Returns</th></tr>",
        "<tr><td><dl>something</dl></td></tr>",
        "<tr><th>Throws</th></tr>",
        "<tr><td><dl>",
        "<dt>Error</dt>",
        "<dd>randomly</dd>",
        "</dl></td></tr>",
        "</tbody></table></div>"));
  }

  @Test
  public void renderFunctionProperty() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("Bar")
            .setSource("bar.link")
            .setDescription(parseComment("")))
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "prop", toMap(function),
        "parentName", "foo");

    Document document = renderDocument("dossier.printProperty", data);
    assertThat(querySelector(document, "details.function > summary").toString(), isHtml(
        "<summary><div>",
        "<a class=\"source\" href=\"bar.link\">code &raquo;</a>",
        "<span class=\"member\">",
        "<a name=\"foo.Bar\">foo.Bar</a> <span class=\"args\">(a)</span></span></div>",
        "</summary>"));

    assertThat(document.select("details.function > summary + div.info").size(), is(1));
  }

  @Test
  public void renderDeprecatedFunctionProperty() {
    Dossier.Function function = Dossier.Function.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("Bar")
            .setSource("bar.link")
            .setDescription(parseComment("description here\n<p>second paragraph"))
            .setDeprecation(Dossier.Deprecation.newBuilder()
                .setNotice(parseComment("is old"))))
        .addParameter(Dossier.Function.Detail.newBuilder().setName("a"))
        .build();

    ImmutableMap<String, Object> data = ImmutableMap.of(
        "prop", toMap(function),
        "parentName", "foo");

    Document document = renderDocument("dossier.printProperty", data);
    assertThat(querySelector(document, "details.function > summary").toString(), isHtml(
        "<summary><div>",
        "<a class=\"source\" href=\"bar.link\">code &raquo;</a>",
        "<span class=\"member deprecation-notice\">",
        "<a name=\"foo.Bar\">foo.Bar</a> <span class=\"args\">(a)</span></span></div>",
        "<div class=\"deprecation-notice\">Deprecated: ",
        "<span class=\"deprecation-reason\">is old</span>",
        "</div>",
        "<p>description here\n</p><p>second paragraph</p>",
        "</summary>"));

    assertThat(document.select("details.function > summary + div.info").size(), is(1));
  }

  @Test
  public void renderProperty() {
    Dossier.Property property = Dossier.Property.newBuilder()
        .setBase(Dossier.BaseProperty.newBuilder()
            .setName("foo")
            .setSource("foo-source")
            .setDescription(parseComment("foo description")))
        .setTypeHtml("string")
        .build();

    Document document = renderDocument("dossier.printProperty", "prop", property);
    Element details = querySelector(document, "details");
    assertThat(details.className(), is(""));

    assertThat(details.child(0).toString(), isHtml(
        "<summary>",
        "<div>",
        "<a class=\"source\" href=\"foo-source\">code &raquo;</a>",
        "<span class=\"member\"><a name=\"foo\">foo</a>",
        " : <code class=\"type\">string</code></span>",
        "</div>",
        "<p>foo description</p>",
        "</summary>"));
  }

  @Test
  public void renderComment_unterminatedInlineTaglet() {
    Dossier.Comment comment = parseComment("Hello {@code world");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p>Hello {@code world</p></body>"));
  }

  @Test
  public void renderComment_withCodeTaglet() {
    Dossier.Comment comment = parseComment("Hello, {@code world}!");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p>Hello, <code>world</code>!</p></body>"));
  }

  @Test
  public void renderComment_withAdjacentInlineCodeTaglets() {
    Dossier.Comment comment = parseComment("{@code hello}{@code world}");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(),
        isHtml("<body><p><code>hello</code><code>world</code></p></body>"));
  }

  @Test
  public void renderComment_codeTagletsAreHtmlEscaped() {
    Dossier.Comment comment = parseComment("{@code 1 < 2 && 4 > 3}");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "<code>1 &lt; 2 &amp;&amp; 4 &gt; 3</code>",
        "</p></body>"));
  }

  @Test
  public void renderComment_literalTagletsAreHtmlEscaped() {
    Dossier.Comment comment = parseComment("{@literal 1 < 2 && 4 > 3}");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>1 &lt; 2 &amp;&amp; 4 &gt; 3</p></body>"));
  }

  @Test
  public void renderComment_unresolvedLink() {
    Dossier.Comment comment = parseComment("An {@link Foo unknown} type");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "An <code class=\"type\"><a class=\"unresolved-link\">unknown</a></code>",
        " type</p></body>"));
  }

  @Test
  public void renderComment_resolvedLink() {
    when(mockLinker.getLink("foo.Bar")).thenReturn("/path/to/foo");

    Dossier.Comment comment = parseComment(
        "A {@link foo.Bar milk & cookies} snack");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "A <code class=\"type\">",
        "<a href=\"/path/to/foo\">milk &amp; cookies</a></code>",
        " snack</p></body>"));
  }

  @Test
  public void renderComment_plainLink() {
    when(mockLinker.getLink("foo.Bar")).thenReturn("/path/to/foo");

    Dossier.Comment comment = parseComment(
        "A {@linkplain foo.Bar milk & cookies} snack");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>",
        "A <a href=\"/path/to/foo\">milk &amp; cookies</a> snack",
        "</p></body>"));
  }

  @Test
  public void renderComment_doesNotEscapePlainTextContent() {
    Dossier.Comment comment = parseComment(
        "A <strong>strongly</strong> worded letter");

    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml(
        "<body><p>A <strong>strongly</strong> worded letter</p></body>"));
  }

  @Test
  public void renderComment_omittingLeadingParagraphTag() {
    Dossier.Comment comment = parseComment(
        "A <strong>strongly</strong> worded letter");

    Document document = renderDocument("dossier.comment",
        ImmutableMap.of("comment", toMap(comment), "omitLeadingTag", true));
    assertThat(document.body().toString(), isHtml(
        "<body>A <strong>strongly</strong> worded letter</body>"));
  }

  @Test
  public void renderComment_emptyComment() {
    Dossier.Comment comment = parseComment("");
    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml("<body></body>"));
  }

  @Test
  public void renderComment_nullComment() {
    Dossier.Comment comment = parseComment(null);
    Document document = renderDocument("dossier.comment", "comment", comment);
    assertThat(document.body().toString(), isHtml("<body></body>"));
  }

  @Test
  public void renderComment_nullCommentMessage() {
    Document document = renderDocument("dossier.comment", new HashMap<String, Object>() {{
      put("comment", null);
    }});
    assertThat(document.body().toString(), isHtml("<body></body>"));
  }

  private Dossier.Comment parseComment(String comment) {
    return CommentUtil.parseComment(comment, mockLinker);
  }

  private static Element querySelector(Document document, String selector) {
    Elements elements = document.select(selector);
    return Iterables.getOnlyElement(elements);
  }

  private Document renderDocument(String template, String key, GeneratedMessage value) {
    return renderDocument(template, ImmutableMap.<String, Object>of(key, toMap(value)));
  }

  private Document renderDocument(String template, String key, List<GeneratedMessage> value) {
    return renderDocument(template,
        ImmutableMap.<String, Object>of(key, transform(value, msgToMap())));
  }

  private Document renderDocument(String template, Map<String, Object> data) {
    String html = render(template, data);
    Document document = Jsoup.parse(html);
    document.outputSettings()
        .prettyPrint(false)
        .indentAmount(0);
    return document;
  }

  private String render(String template, String key, GeneratedMessage value) {
    return render(template, ImmutableMap.of(key, toMap(value)));
  }

  private String render(String template, @Nullable Map<String, ?> data) {
    StringWriter sw = new StringWriter();
    Renderer.Tofu.INSTANCE.newRenderer(template)
        .setData(data)
        .render(sw);
    return sw.toString();
  }

  private static Function<GeneratedMessage, Map<String, Object>> msgToMap() {
    return new Function<GeneratedMessage, Map<String, Object>>() {
      @Override
      public Map<String, Object> apply(GeneratedMessage input) {
        return toMap(input);
      }
    };
  }

  private Matcher<String> isHtml(String... lines) {
    return is(Joiner.on("").join(lines));
  }
}
