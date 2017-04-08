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

package com.github.jsdossier.jscomp;

import com.google.javascript.jscomp.deps.ModuleNames;
import com.google.javascript.rhino.JSDocInfo.Marker;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import java.nio.file.Path;
import java.util.ConcurrentModificationException;
import java.util.Optional;

/** Utilities for working with JavaScript types. */
public final class Types {
  private Types() {} // Utility class.

  private static final String DOSSIER_INTERNAL_VAR_PREFIX =
      "_2ebeecd9_43c9_4616_a2dc_9cf4237e1f78_";

  private static final String MODULE_CONTENTS_PREFIX = "module$contents$";

  private static final Object EVALUATION_LOCK = new Object();

  /**
   * Returns whether the given variable name could be a defined within a CommonJS or Closure module.
   * This decisions is based entirely on renaming used by the compiler's ClosureRewriteModule pass
   * and does not check if there is actually a module with the variable defined.
   */
  public static boolean isModuleContentsVar(String name) {
    return name.startsWith(MODULE_CONTENTS_PREFIX)
        && name.indexOf('_') > MODULE_CONTENTS_PREFIX.length();
  }

  /** Converts a file path to its internal module ID. */
  public static String getModuleId(Path path) {
    return ModuleNames.fileToModuleName(path.normalize().toString());
  }

  /**
   * Determines if the jsdoc on a type indicates a type is actually a constructor or just a
   * constructor reference:
   *
   * <pre><code>
   *   /** @constructor *\ function Clazz() {}              // This is a constructor.
   *   /** @type {function(new: Clazz)} *\ const newClazz;  // This is a constructor reference.
   * </code></pre>
   *
   * @param type the type to inspect.
   * @param jsdoc the JSDoc for the type.
   * @return whether the type is an actual constructor.
   */
  public static boolean isConstructorTypeDefinition(JSType type, JsDoc jsdoc) {
    return type.isConstructor()
        && (jsdoc.isConstructor()
            || jsdoc.isConst()
                && !hasTypeExpression(jsdoc.getMarker(JsDoc.Annotation.TYPE))
                && !hasTypeExpression(jsdoc.getMarker(JsDoc.Annotation.PUBLIC))
                && !hasTypeExpression(jsdoc.getMarker(JsDoc.Annotation.PROTECTED))
                && !hasTypeExpression(jsdoc.getMarker(JsDoc.Annotation.PRIVATE)));
  }

  private static boolean hasTypeExpression(Optional<Marker> marker) {
    return marker.isPresent() && marker.get().getType() != null;
  }

  /**
   * Returns whether a property name is for a property defined on every function.
   *
   * @param type the type the property is defined on.
   * @param propertyName the property name.
   * @return whether the property is a function built-in property. Returns false if the given type
   *     is not a function.
   */
  public static boolean isBuiltInFunctionProperty(JSType type, String propertyName) {
    return type.isFunctionType()
        && ("apply".equals(propertyName)
            || "bind".equals(propertyName)
            || "call".equals(propertyName)
            || "prototype".equals(propertyName));
  }

  /** Prepends a name to create a new variable name for internal dossier use. */
  static String toInternalVar(String name) {
    return DOSSIER_INTERNAL_VAR_PREFIX + name;
  }

  /** Returns whether the name is for an internal variable created by dossier. */
  static boolean isInternalVar(String name) {
    return name.startsWith(DOSSIER_INTERNAL_VAR_PREFIX);
  }

  /**
   * Evaluates the given {@code expression}. Internally this method will synchronize evaluations to
   * avoid {@link ConcurrentModificationException} in case evaluating the expression modifies the
   * internal state of the registry (this appears to happen with unknown properties).
   *
   * @see "https://github.com/jleyba/js-dossier/issues/78"
   */
  public static JSType evaluate(
      JSTypeExpression expression, StaticTypedScope<JSType> scope, TypeIRegistry registry) {
    synchronized (EVALUATION_LOCK) {
      return expression.evaluate(scope, registry);
    }
  }
}
