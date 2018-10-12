/*
Copyright 2013-2018 Jason Leyba

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

package com.github.jsdossier.testing;

import com.github.jsdossier.jscomp.Module;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.javascript.rhino.JSDocInfo;
import javax.annotation.Nullable;

public final class ModuleSubject extends Subject<ModuleSubject, Module> {

  static final SubjectFactory<ModuleSubject, Module> FACTORY =
      new SubjectFactory<ModuleSubject, Module>() {
        @Override
        public ModuleSubject getSubject(FailureStrategy fs, Module that) {
          return new ModuleSubject(fs, that);
        }
      };

  public ModuleSubject(FailureStrategy failureStrategy, @Nullable Module actual) {
    super(failureStrategy, actual);
    if (actual != null) {
      named("Module<%s>", actual.getId().getCompiledName());
    }
  }

  public StringSubject hasJsdocCommentThat() {
    Truth.assertThat(actual()).isNotNull();
    JSDocInfo info = actual().getJsDoc().getInfo();
    if (info == null) {
      failWithoutActual("has attached jsdoc");
      throw new AssertionError(); // This line will never execute, just here for the IDE.
    }
    return Truth.assertThat(info.getOriginalCommentString())
        .named("%s jsdoc comment", internalCustomName());
  }
}
