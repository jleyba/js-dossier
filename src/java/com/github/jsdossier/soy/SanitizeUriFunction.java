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

import static com.google.template.soy.data.SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.restricted.Sanitizers;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Function for sanitize a relative URL.
 */
@Singleton
final class SanitizeUriFunction extends AbstractSoyJavaFunction implements SoyJsSrcFunction {
  private static final Pattern PERMISSIBLE_URI_PREFIX_PATTERN =
      Pattern.compile("^((?:\\.{1,2}/)+)");

  @Inject
  SanitizeUriFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    String arg = getStringArgument(args, 0);
    if (arg.isEmpty()) {
      return args.get(0);
    }
    Matcher matcher = PERMISSIBLE_URI_PREFIX_PATTERN.matcher(arg);
    if (matcher.find()) {
      String prefix = matcher.group(1);
      String rest = arg.substring(matcher.end());
      arg = prefix + Sanitizers.filterNormalizeUri(rest);
    } else {
      arg = Sanitizers.filterNormalizeUri(arg);
    }
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(arg, TRUSTED_RESOURCE_URI);
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg0 = args.get(0);
    return new JsExpr(
        "dossier.soyplugins.sanitizeUri(" + arg0.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }
}
