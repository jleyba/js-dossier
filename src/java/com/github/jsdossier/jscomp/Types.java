/*
 Copyright 2013-2015 Jason Leyba
 
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

import com.google.common.base.Optional;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSType;

/**
 * Utilities for working with JavaScript types.
 */
public final class Types {
  private Types() {}  // Utility class.

  /**
   * Determines if the jsdoc on a type indicates a type is actually a constructor or just a
   * constructor reference:
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

  private static boolean hasTypeExpression(Optional<JSDocInfo.Marker> marker) {
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
}
