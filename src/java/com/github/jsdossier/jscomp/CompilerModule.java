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

import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.jscomp.Annotations.Internal;
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
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import java.io.IOException;
import java.nio.file.Path;
import javax.inject.Provider;

/** Defines the bindings for the compiler. */
public final class CompilerModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(DossierCompiler.class).in(Scopes.SINGLETON);
  }

  @Provides
  StaticTypedScope<JSType> provideGlobalScope(DossierCompiler compiler) {
    return compiler.getTopScope();
  }

  @Provides
  JSTypeRegistry provideJsTypeRegistry(DossierCompiler compiler) {
    return compiler.getTypeRegistry();
  }

  @Provides
  @Internal
  ImmutableList<DossierCompilerPass> providePasses(
      FileVisibilityPass visibilityPass,
      Es6ModulePass es6ModulePass,
      @Modules ImmutableSet<Path> modulePaths,
      Provider<NodeModulePass> nodeModulePassProvider) {
    ImmutableList.Builder<DossierCompilerPass> passes =
        ImmutableList.<DossierCompilerPass>builder().add(visibilityPass).add(es6ModulePass);

    // Transform CommonJS style node modules into Closure's goog.module syntax.
    // TODO(jleyba): do we still need to control this transformation?
    if (!modulePaths.isEmpty()) {
      passes.add(nodeModulePassProvider.get());
    }

    return passes.build();
  }

  @Provides
  CompilerOptions provideCompilerOptions(
      AliasTransformListener transformListener,
      ModuleCollectionPass moduleCollectionPass,
      ProvidedSymbolPass providedSymbolPass,
      TypeCollectionPass typeCollectionPass,
      @Modules ImmutableSet<Path> modulePaths) {
    CompilerOptions options = new CompilerOptions();

    if (modulePaths.isEmpty()) {
      options.setEnvironment(CompilerOptions.Environment.BROWSER);
      options.setModuleResolutionMode(ModuleLoader.ResolutionMode.BROWSER);
    } else {
      options.setEnvironment(CompilerOptions.Environment.CUSTOM);
      options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);
    }

    options.setModuleRoots(ImmutableList.of());

    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    options.setChecksOnly(true);
    options.setContinueAfterErrors(true);
    options.setAllowHotswapReplaceScript(true);
    options.setPreserveDetailedSourceInfo(true);
    options.setParseJsDocDocumentation(Config.JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE);

    // For easier debugging.
    options.setPrettyPrint(true);

    options.setAliasTransformationHandler(transformListener);

    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, moduleCollectionPass);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, providedSymbolPass);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS, typeCollectionPass);

    return options;
  }
}
