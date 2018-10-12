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

import static com.github.jsdossier.jscomp.Nodes.isGoogProvideCall;

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.rhino.Node;
import javax.inject.Inject;

/**
 * Compiler pass used to identify symbols declared with "goog.provide" statements. This pass ignores
 * the externs tree.
 */
public final class ProvidedSymbolPass implements CompilerPass {

  private final DossierCompiler compiler;
  private final TypeRegistry typeRegistry;

  @Inject
  ProvidedSymbolPass(DossierCompiler compiler, TypeRegistry typeRegistry) {
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
  }

  @VisibleForTesting
  static String nameToId(String name) {
    // See transformations in
    // com.google.javascript.jscomp.ClosureRewriteModule
    return "module$exports$" + name.replace('.', '$');
  }

  @Override
  public void process(Node ignored, Node root) {
    if (!root.isFromExterns()) {
      scan(root);
    }
  }

  private void scan(Node root) {
    switch (root.getToken()) {
      case ROOT:
      case SCRIPT:
        for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
          scan(child);
        }
        break;

      case EXPR_RESULT:
        if (isGoogProvideCall(root.getFirstChild())) {
          Node call = root.getFirstChild();
          assert call.getSecondChild() != null;
          typeRegistry.recordProvide(call.getSecondChild().getString());
        }
        break;

      default:
        break; // Do nothing.
    }
  }
}
