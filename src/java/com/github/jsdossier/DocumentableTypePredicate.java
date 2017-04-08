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

package com.github.jsdossier;

import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import java.util.function.Predicate;
import javax.inject.Inject;

/** Predicate that accepts types to include in generated documentation. */
final class DocumentableTypePredicate implements Predicate<NominalType> {
  private final TypeRegistry typeRegistry;
  private final TypeInspectorFactory typeInspectorFactory;

  @Inject
  DocumentableTypePredicate(TypeRegistry typeRegistry, TypeInspectorFactory typeInspectorFactory) {
    this.typeRegistry = typeRegistry;
    this.typeInspectorFactory = typeInspectorFactory;
  }

  @Override
  public boolean test(NominalType input) {
    if (input.getJsDoc().isTypedef()) {
      return false;
    }

    if (typeRegistry.isImplicitNamespace(input.getName())) {
      TypeInspector.Report report = typeInspectorFactory.create(input).inspectType();
      return !report.getCompilerConstants().isEmpty()
          || !report.getFunctions().isEmpty()
          || !report.getProperties().isEmpty();
    }

    return true;
  }
}
