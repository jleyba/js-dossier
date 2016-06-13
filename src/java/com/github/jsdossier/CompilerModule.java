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

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.jscomp.AliasTransformListener;
import com.github.jsdossier.jscomp.DossierCompiler;
import com.github.jsdossier.jscomp.ProvidedSymbolPass;
import com.github.jsdossier.jscomp.TypeCollectionPass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Defines the bindings for the compiler.
 */
public class CompilerModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(DossierCompiler.class).in(Scopes.SINGLETON);
  }

  @Provides
  JSTypeRegistry provideJsTypeRegistry(DossierCompiler compiler) {
    return compiler.getTypeRegistry();
  }

  @Provides
  CompilerOptions provideCompilerOptions(
      AliasTransformListener transformListener,
      ProvidedSymbolPass providedSymbolPass,
      TypeCollectionPass typeCollectionPass,
      @Input LanguageMode mode,
      @Modules ImmutableSet<Path> modulePaths) throws IOException {
    CompilerOptions options = new CompilerOptions();

    if (!modulePaths.isEmpty()) {
      // Prevents browser-specific externs from being loaded.
      options.setEnvironment(CompilerOptions.Environment.CUSTOM);
    }

    options.setModuleRoots(ImmutableList.<String>of());

    options.setLanguageIn(mode);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    // IDE mode must be enabled or all of the jsdoc info will be stripped from the AST.
    options.setIdeMode(true);
    options.setParseJsDocDocumentation(Config.JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE);

    // For easier debugging.
    options.setPrettyPrint(true);

    options.setAliasTransformationHandler(transformListener);

    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, providedSymbolPass);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS, typeCollectionPass);

    return options;
  }
}
