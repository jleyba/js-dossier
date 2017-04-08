package com.github.jsdossier.soy;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Function for sanitize a relative URL. */
@Singleton
final class SanitizeHtmlFunction implements SoyJsSrcFunction {
  @Inject
  SanitizeHtmlFunction() {}

  @Override
  public final String getName() {
    String name = getClass().getSimpleName();
    checkState(name.endsWith("Function"), "%s must end with 'Function'", name);

    name = name.substring(0, name.length() - "Function".length());
    name = UPPER_CAMEL.to(LOWER_CAMEL, name);
    return name;
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
}
