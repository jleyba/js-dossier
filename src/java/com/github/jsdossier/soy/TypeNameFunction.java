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

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSet.Builder;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Renders the name of a protobuf enum or message type. */
@Singleton
final class TypeNameFunction extends AbstractSoyJavaFunction {
  private final Provider<Builder> sfsBuilder;

  @Inject
  TypeNameFunction(Provider<Builder> sfsBuilder) {
    this.sfsBuilder = sfsBuilder;
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    String arg = getStringArgument(args, 0);
    String type = arg.startsWith(".") ? arg.substring(1) : arg;
    return sfsBuilder
        .get()
        .add(
            "{namespace dossier.generated}"
                + "{template .type kind=\"js\"}proto."
                + type
                + "{/template}",
            "<synthetic type name>")
        .build()
        .compileToTofu()
        .newRenderer("dossier.generated.type")
        .setContentKind(ContentKind.JS)
        .renderStrict();
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }
}
