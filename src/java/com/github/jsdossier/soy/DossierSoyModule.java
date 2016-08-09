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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.shared.restricted.SoyFunction;

/**
 * Module for configuring all of Dossier's soy rendering. This module will install the standard
 * {@link SoyModule}.
 */
public final class DossierSoyModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new SoyModule());

    Multibinder<SoyFunction> binder = Multibinder.newSetBinder(binder(), SoyFunction.class);
    binder.addBinding().to(ArrayTypeFunction.class);
    binder.addBinding().to(IsSanitizedHtmlFunction.class);
    binder.addBinding().to(IsSanitizedUriFunction.class);
    binder.addBinding().to(ToLowerCamelCaseFunction.class);
    binder.addBinding().to(TypeNameFunction.class);
  }
}
