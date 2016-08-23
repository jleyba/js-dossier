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

package com.github.jsdossier.soy;

import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Link;
import com.github.jsdossier.proto.SourceLink;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Link rendering tests.
 */
@RunWith(JUnit4.class)
public class LinkRenderTest {

  private static final SoyTofu TOFU = SoyFileSet.builder()
      .add(
          Joiner.on("\n").join(
              "{namespace test}",
              "",
              "/** Renders a TypeLink */",
              "{template .typeLink}",
              "  {@param link: dossier.Link}",
              "  <a href=\"{$link.href}\">{$link.text}</a>",
              "{/template}",
              "",
              "/** Renders a SourceLink */",
              "{template .sourceLink}",
              "  {@param link: dossier.SourceLink}",
              "  <a href=\"{$link.path}\">test</a>",
              "{/template}",
              "",
              "/** Renders a Comment.Token */",
              "{template .commentToken}",
              "  {@param token: dossier.Comment.Token}",
              "  <a href=\"{$token.href}\">test</a>",
              "{/template}"),
          "test.soy")
      .setLocalTypeRegistry(
          new SoyTypeRegistry(ImmutableSet.of((SoyTypeProvider) new DossierSoyTypeProvider())))
      .build()
      .compileToTofu();

  @Test
  public void renderLink() {
    assertThat(renderLink("foo", "bar.html"))
        .isEqualTo("<a href=\"bar.html\">foo</a>");
    assertThat(renderLink("foo", "./bar.html"))
        .isEqualTo("<a href=\"./bar.html\">foo</a>");
    assertThat(renderLink("foo", "../bar.html"))
        .isEqualTo("<a href=\"../bar.html\">foo</a>");
    assertThat(renderLink("foo", "../../bar.html"))
        .isEqualTo("<a href=\"../../bar.html\">foo</a>");
    assertThat(renderLink("foo", "/bar.html"))
        .isEqualTo("<a href=\"/bar.html\">foo</a>");
  }

  @Test
  public void renderSourceLink() {
    assertThat(renderSourceLink("bar.html")).isEqualTo("<a href=\"bar.html\">test</a>");
    assertThat(renderSourceLink("./bar.html")).isEqualTo("<a href=\"./bar.html\">test</a>");
    assertThat(renderSourceLink("../bar.html")).isEqualTo("<a href=\"../bar.html\">test</a>");
    assertThat(renderSourceLink("../../bar.html")).isEqualTo("<a href=\"../../bar.html\">test</a>");
    assertThat(renderSourceLink("/bar.html")).isEqualTo("<a href=\"/bar.html\">test</a>");
  }

  @Test
  public void renderCommentTokenLink() {
    assertThat(renderCommentToken("bar.html")).isEqualTo("<a href=\"bar.html\">test</a>");
    assertThat(renderCommentToken("./bar.html")).isEqualTo("<a href=\"./bar.html\">test</a>");
    assertThat(renderCommentToken("../bar.html")).isEqualTo("<a href=\"../bar.html\">test</a>");
    assertThat(renderCommentToken("../../bar.html"))
        .isEqualTo("<a href=\"../../bar.html\">test</a>");
    assertThat(renderCommentToken("/bar.html")).isEqualTo("<a href=\"/bar.html\">test</a>");
  }

  @Test
  public void sanitizerDisallowsDangerousProtocols() {
    assertThat(renderLink("foo", "ftp://bar.html")).isEqualTo("<a href=\"#zSoyz\">foo</a>");
    assertThat(renderCommentToken("ftp://bar.html")).isEqualTo("<a href=\"#zSoyz\">test</a>");
    assertThat(renderSourceLink("ftp://bar.html")).isEqualTo("<a href=\"#zSoyz\">test</a>");
  }

  private static String renderLink(String text, String href) {
    StringBuilder builder = new StringBuilder();
    Link link = Link.newBuilder()
        .setHref(href)
        .setText(text)
        .build();
    TOFU.newRenderer("test.typeLink")
        .setData(new SoyMapData("link", ProtoMessageSoyType.toSoyValue(link)))
        .render(builder);
    return builder.toString();
  }

  private static String renderSourceLink(String path) {
    StringBuilder builder = new StringBuilder();
    SourceLink link = SourceLink.newBuilder()
        .setPath(path)
        .build();
    TOFU.newRenderer("test.sourceLink")
        .setData(new SoyMapData("link", ProtoMessageSoyType.toSoyValue(link)))
        .render(builder);
    return builder.toString();
  }

  private static String renderCommentToken(String href) {
    StringBuilder builder = new StringBuilder();
    Comment.Token token = Comment.Token.newBuilder()
        .setHref(href)
        .build();
    TOFU.newRenderer("test.commentToken")
        .setData(new SoyMapData("token", ProtoMessageSoyType.toSoyValue(token)))
        .render(builder);
    return builder.toString();
  }
}
