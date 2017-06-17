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

import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import javax.inject.Inject;

/** A special compiler pass that collects the default visibility settings for each file. */
final class FileVisibilityPass implements DossierCompilerPass {

  private final TypeRegistry typeRegistry;
  private final FileSystem inputFs;

  @Inject
  FileVisibilityPass(TypeRegistry typeRegistry, @Input FileSystem inputFs) {
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
  }

  @Override
  public void process(DossierCompiler compiler, Node root) {
    traverseEs6(
        compiler,
        root,
        new NodeTraversal.AbstractShallowCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isScript()) {
              JsDoc docs = JsDoc.from(n.getJSDocInfo());
              Visibility visibility = docs.getVisibility();
              if (visibility != null && visibility != Visibility.INHERITED) {
                Path path = inputFs.getPath(n.getSourceFileName());
                typeRegistry.setDefaultVisibility(path, visibility);
              }
            }
          }
        });
  }
}
