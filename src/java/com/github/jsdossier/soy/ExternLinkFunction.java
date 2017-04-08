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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/** Generates the links to know extern types.This */
final class ExternLinkFunction extends AbstractSoyJavaFunction implements SoyJsSrcFunction {

  private static final String MDN = "https://developer.mozilla.org/en-US/docs/Web/JavaScript/";
  private static final String MDN_PREFIX = MDN + "Reference/";
  private static final String CLOSURE_COMPILER_PREFIX =
      "https://github.com/google/closure-compiler/wiki/Special-types-in-the-Closure-Type-System#";

  private static final ImmutableMap<String, String> EXTERN_LINKS = createLinkMap();

  @Inject
  ExternLinkFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    String arg = getStringArgument(args, 0);
    if (EXTERN_LINKS.containsKey(arg)) {
      return StringData.forValue(EXTERN_LINKS.get(arg));
    }
    return NullData.INSTANCE;
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg0 = args.get(0);
    return new JsExpr(
        "dossier.soyplugins.getExternLink(" + arg0.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  private static ImmutableMap<String, String> createLinkMap() {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    map.put("Arguments", MDN_PREFIX + "Functions/arguments");
    map.put("Array", MDN_PREFIX + "Global_Objects/Array");
    map.put("Boolean", MDN_PREFIX + "Global_Objects/Boolean");
    map.put("Date", MDN_PREFIX + "Global_Objects/Date");
    map.put("Error", MDN_PREFIX + "Global_Objects/Error");
    map.put("Function", MDN_PREFIX + "Global_Objects/Function");
    map.put("Generator", MDN_PREFIX + "Global_Objects/Generaor");
    map.put("IArrayLike", CLOSURE_COMPILER_PREFIX + "iarraylike");
    map.put("IObject", CLOSURE_COMPILER_PREFIX + "iobject");
    map.put("IThenable", CLOSURE_COMPILER_PREFIX + "ithenable");
    map.put("Infinity", MDN_PREFIX + "Global_Objects/Infinity");
    map.put("Iterable", MDN_PREFIX + "Global_Objects/Symbol/iterator");
    map.put("Iterator", MDN + "Guide/The_Iterator_protocol");
    map.put("Map", MDN_PREFIX + "Global_Objects/Map");
    map.put("Math", MDN_PREFIX + "Global_Objects/Math");
    map.put("NaN", MDN_PREFIX + "Global_Objects/NaN");
    map.put("Number", MDN_PREFIX + "Global_Objects/Number");
    map.put("Object", MDN_PREFIX + "Global_Objects/Object");
    map.put("Promise", MDN_PREFIX + "Global_Objects/Promise");
    map.put("RangeError", MDN_PREFIX + "Global_Objects/RangeError");
    map.put("ReferenceError", MDN_PREFIX + "Global_Objects/ReferenceError");
    map.put("RegExp", MDN_PREFIX + "Global_Objects/RegExp");
    map.put("Set", MDN_PREFIX + "Global_Objects/Set");
    map.put("Symbol", MDN_PREFIX + "Global_Objects/Symbol");
    map.put("String", MDN_PREFIX + "Global_Objects/String");
    map.put("SyntaxError", MDN_PREFIX + "Global_Objects/SyntaxError");
    map.put("TypeError", MDN_PREFIX + "Global_Objects/TypeError");
    map.put("URIError", MDN_PREFIX + "Global_Objects/URIError");
    map.put("arguments", MDN_PREFIX + "Functions/arguments");
    map.put("boolean", MDN_PREFIX + "Global_Objects/Boolean");
    map.put("null", MDN_PREFIX + "Global_Objects/Null");
    map.put("number", MDN_PREFIX + "Global_Objects/Number");
    map.put("string", MDN_PREFIX + "Global_Objects/String");
    map.put("undefined", MDN_PREFIX + "Global_Objects/Undefined");
    map.put("WeakMap", MDN_PREFIX + "Global_Objects/WeakMap");
    map.put("WeakSet", MDN_PREFIX + "Global_Objects/WeakSet");
    return map.build();
  }
}
