/*
 Copyright 2013-2015 Jason Leyba

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

import com.github.jsdossier.annotations.Args;
import com.github.jsdossier.jscomp.DossierCompiler;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

/**
 * Defines the bindings for the compiler.
 */
public final class CompilerModule extends AbstractModule {

  private final String[] compilerArgs;

  public CompilerModule() {
    this(new String[0]);
  }

  public CompilerModule(String[] compilerArgs) {
    this.compilerArgs = compilerArgs;
  }

  @Override
  protected void configure() {
    bind(Key.get(new TypeLiteral<String[]>(){}, Args.class)).toInstance(compilerArgs);
    bind(DossierCompiler.class).in(Scopes.SINGLETON);
    bind(TypeRegistry.class).in(Scopes.SINGLETON);
  }

  @Provides
  JSTypeRegistry provideJsTypeRegistry(DossierCompiler compiler) {
    return compiler.getTypeRegistry();
  }

  @Provides
  CompilerOptions provideCompilerOptions(
      ProvidedSymbolsCollectionPass providedNamespacesPass,
      DocPass docPass) {
    CompilerOptions options = new CompilerOptions();

    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    // IDE mode must be enabled or all of the jsdoc info will be stripped from the AST.
    options.setIdeMode(true);
    options.setPreserveJsDocWhitespace(true);

    // For easier debugging.
    options.setPrettyPrint(true);

    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, providedNamespacesPass);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS, docPass);

    return options;
  }
}
