package com.github.jsdossier.soy;

import static com.google.template.soy.data.SanitizedContent.ContentKind.HTML;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Function for sanitize a relative URL.
 */
@Singleton
final class SanitizeHtmlFunction extends AbstractSoyJavaFunction implements SoyJsSrcFunction {
  @Inject
  SanitizeHtmlFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    String arg = getStringArgument(args, 0);
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(arg, HTML);
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg0 = args.get(0);
    return new JsExpr(
        "dossier.soyplugins.sanitizeHtml(" + arg0.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }
}
