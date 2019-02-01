package com.github.jsdossier.soy;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Function for sanitize a relative URL. */
@Singleton
final class SanitizeHtmlFunction extends AbstractSoyJavaFunction implements SoyJsSrcFunction {
  private final Provider<SoyFileSet.Builder> sfsBuilder;

  @Inject
  SanitizeHtmlFunction(Provider<SoyFileSet.Builder> sfsBuilder) {
    this.sfsBuilder = sfsBuilder;
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg0 = args.get(0);
    return new JsExpr("dossier.soyplugins.sanitizeHtml(" + arg0.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    // Yes, this is a gross hack to generate HTML content that will not be HTML-escaped by
    // strict soy templates.
    return sfsBuilder
        .get()
        .add(
            "{namespace dossier.generate}{template .html}{literal}"
                + getStringArgument(args, 0)
                + "{/literal}{/template}",
            "<synthetic>")
        .build()
        .compileToTofu()
        .newRenderer("dossier.generate.html")
        .setContentKind(ContentKind.HTML)
        .renderStrict();
  }
}
