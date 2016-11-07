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
import com.github.jsdossier.proto.TypeLink;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;

/**
 * Link rendering tests.
 */
@RunWith(JUnit4.class)
public class LinkRenderTest {

  @Inject
  private SoyTofu tofu;

  @Before
  public void setUp() {
    Guice.createInjector(
        new DossierSoyModule(),
        new AbstractModule() {
          @Override protected void configure() {}

          @Provides
          SoyTofu provideSoyTofu(
              SoyFileSet.Builder filesetBuilder,
              SoyTypeProvider typeProvider) {
            return filesetBuilder
                .add(
                    Joiner.on("\n").join(
                        "{namespace test}",
                        "",
                        "/** Renders a TypeLink */",
                        "{template .typeLink}",
                        "  {@param link: dossier.Link}",
                        "  <a href=\"{sanitizeUri($link.href)}\">{$link.text}</a>",
                        "{/template}",
                        "",
                        "/** Renders a SourceLink */",
                        "{template .sourceLink}",
                        "  {@param link: dossier.SourceLink}",
                        "  <a href=\"{sanitizeUri($link.path)}\">test</a>",
                        "{/template}",
                        "",
                        "/** Renders a Comment.Token */",
                        "{template .commentToken}",
                        "  {@param token: dossier.Comment.Token}",
                        "  <a href=\"{sanitizeUri($token.link.href)}\">test</a>",
                        "{/template}"),
                    "test.soy")
                .setLocalTypeRegistry(new SoyTypeRegistry(ImmutableSet.of(typeProvider)))
                .build()
                .compileToTofu();
          }
        })
        .injectMembers(this);
  }

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
    assertThat(renderLink("foo", "ftp://bar.html"))
        .isEqualTo("<a href=\"about:invalid#zSoyz\">foo</a>");
    assertThat(renderCommentToken("ftp://bar.html"))
        .isEqualTo("<a href=\"about:invalid#zSoyz\">test</a>");
    assertThat(renderSourceLink("ftp://bar.html"))
        .isEqualTo("<a href=\"about:invalid#zSoyz\">test</a>");
  }

  private String renderLink(String text, String href) {
    StringBuilder builder = new StringBuilder();
    Link link = Link.newBuilder()
        .setHref(href)
        .setText(text)
        .build();
    tofu.newRenderer("test.typeLink")
        .setData(ImmutableMap.of("link", link))
        .render(builder);
    return builder.toString();
  }

  private String renderSourceLink(String path) {
    StringBuilder builder = new StringBuilder();
    SourceLink link = SourceLink.newBuilder()
        .setPath(path)
        .build();
    tofu.newRenderer("test.sourceLink")
        .setData(ImmutableMap.of("link", link))
        .render(builder);
    return builder.toString();
  }

  private String renderCommentToken(String href) {
    StringBuilder builder = new StringBuilder();
    Comment.Token token = Comment.Token.newBuilder()
        .setLink(TypeLink.newBuilder().setHref(href))
        .build();
    tofu.newRenderer("test.commentToken")
        .setData(ImmutableMap.of("token", token))
        .render(builder);
    return builder.toString();
  }
}
