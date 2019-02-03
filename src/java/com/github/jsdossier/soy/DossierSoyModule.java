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

import com.github.jsdossier.proto.Dossier;
import com.github.jsdossier.proto.Expression;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.template.soy.SoyModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Module for configuring all of Dossier's soy rendering. This module will install the standard
 * {@link SoyModule}.
 */
public final class DossierSoyModule extends AbstractModule {

  @Override
  protected void configure() {
    install(new SoyModule());

    Multibinder<SoyFunction> binder = Multibinder.newSetBinder(binder(), SoyFunction.class);
    binder.addBinding().to(ExternLinkFunction.class);
    binder.addBinding().to(SanitizeHtmlFunction.class);
  }

  @Provides
  @Singleton
  ImmutableSet<Descriptors.GenericDescriptor> provideProtoDescriptors() {
    return ImmutableSet.copyOf(
        collectDescriptors(
            DescriptorProtos.getDescriptor(), Dossier.getDescriptor(), Expression.getDescriptor()));
  }

  private static Set<Descriptors.GenericDescriptor> collectDescriptors(
      Descriptors.FileDescriptor... fileDescriptors) {
    Set<Descriptors.GenericDescriptor> set = new HashSet<>();
    for (Descriptors.FileDescriptor file : fileDescriptors) {
      set.addAll(file.getEnumTypes());
      for (Descriptors.Descriptor descriptor : file.getMessageTypes()) {
        collectDescriptors(set, descriptor);
      }
    }
    return set;
  }

  private static void collectDescriptors(
      Set<Descriptors.GenericDescriptor> set, Descriptors.Descriptor descriptor) {
    if (!set.add(descriptor)) {
      return;
    }

    set.addAll(descriptor.getEnumTypes());
    for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
      if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
        collectDescriptors(set, field.getMessageType());
      }
    }
  }
}
