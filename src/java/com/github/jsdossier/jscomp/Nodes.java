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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.io.StringWriter;
import javax.annotation.Nullable;

/** Utility class for working with AST. */
final class Nodes {
  private Nodes() {}

  public static void printTree(Node n) {
    StringWriter sw = new StringWriter();
    try {
      n.appendStringTree(sw);
      System.err.println(sw.toString());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static String getQualifiedName(@Nullable Node n) {
    return n == null ? "" : nullToEmpty(n.getQualifiedName());
  }

  public static String getStringParam(Node n) {
    checkArgument(n != null && n.isCall(), "not a call node: %s", n);
    Node child = n.getSecondChild();
    checkArgument(isString(child), "second child is not a string: %s", child);
    return child.getString();
  }

  public static boolean isCall(@Nullable Node n, String name) {
    return n != null
        && n.isCall()
        && n.getFirstChild() != null
        && n.getFirstChild().matchesQualifiedName(name);
  }

  public static boolean isDestructuringLhs(@Nullable Node n) {
    return n != null && n.isDestructuringLhs();
  }

  public static boolean isExprResult(@Nullable Node n) {
    return n != null && n.isExprResult();
  }

  public static boolean isGetProp(@Nullable Node n) {
    return n != null && n.isGetProp();
  }

  public static boolean isGoogProvideCall(@Nullable Node n) {
    return n != null && isExprResult(n.getParent()) && isCall(n, "goog.provide");
  }

  public static boolean isGoogRequireCall(@Nullable Node n) {
    return n != null && isCall(n, "goog.require");
  }

  public static boolean isGoogScopeCall(@Nullable Node n) {
    return n != null
        && isCall(n, "goog.scope")
        && n.getSecondChild() != null
        && n.getSecondChild().isFunction();
  }

  public static boolean isGoogSetTestOnly(@Nullable Node n) {
    return isCall(n, "goog.setTestOnly");
  }

  public static Node getGoogScopeBlock(Node n) {
    checkArgument(isGoogScopeCall(n));
    return n.getSecondChild().getLastChild();
  }

  public static boolean isRequireCall(Node n) {
    return n != null
        && isCall(n, "require")
        && n.getSecondChild() != null
        && n.getSecondChild().isString();
  }

  public static boolean isModuleBody(@Nullable Node n) {
    return n != null && n.isModuleBody();
  }

  public static boolean isName(@Nullable Node n) {
    return n != null && n.isName();
  }

  public static boolean isObjectLit(@Nullable Node n) {
    return n != null && n.isObjectLit();
  }

  public static boolean isObjectPattern(@Nullable Node n) {
    return n != null && n.isObjectPattern();
  }

  public static boolean isScript(@Nullable Node n) {
    return n != null && n.isScript();
  }

  public static boolean isString(@Nullable Node n) {
    return n != null && n.isString();
  }

  public static boolean isTopLevelAssign(Node n) {
    if (n.isAssign() && n.getParent() != null) {
      if (n.getParent().isComma()) {
        return isExprResult(n.getGrandparent())
            && (isScript(greatGrandparent(n)) || isModuleBody(greatGrandparent(n)));
      }
      return isExprResult(n.getParent())
          && (isScript(n.getGrandparent()) || isModuleBody(n.getGrandparent()));
    }
    return false;
  }

  public static boolean isUnnamedClass(Node n) {
    return n != null && n.isClass() && n.getFirstChild() != null && n.getFirstChild().isEmpty();
  }

  public static boolean isUnnamedFunction(Node n) {
    return n != null
        && n.isFunction()
        && n.getFirstChild() != null
        && getQualifiedName(n.getFirstChild()).isEmpty();
  }

  @Nullable
  private static Node greatGrandparent(Node n) {
    Node gp = n.getGrandparent();
    return gp == null ? null : gp.getParent();
  }
}
