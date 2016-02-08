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

import com.github.jsdossier.annotations.Args;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.jscomp.AliasTransformListener;
import com.github.jsdossier.jscomp.DossierCompiler;
import com.github.jsdossier.jscomp.ProvidedSymbolPass;
import com.github.jsdossier.jscomp.TypeCollectionPass;
import com.google.common.collect.ImmutableSet;
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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Defines the bindings for the compiler.
 */
public class CompilerModule extends AbstractModule {

  private final String[] compilerArgs;
  private final CompilerOptions.LanguageMode languageIn;
  private final CompilerOptions.LanguageMode languageOut;
  private final boolean newTypeInference;

  private CompilerModule(Builder builder) {
    this.compilerArgs = builder.args;
    this.languageIn = builder.languageIn;
    this.languageOut = builder.languageOut;
    this.newTypeInference = builder.newTypeInference;
  }

  @Override
  protected void configure() {
    bind(Key.get(new TypeLiteral<String[]>(){}, Args.class)).toInstance(compilerArgs);
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
      @Modules ImmutableSet<Path> modulePaths) throws IOException {
    CompilerOptions options = new CompilerOptions();

    if (!modulePaths.isEmpty()) {
      // Prevents browser-specific externs from being loaded.
      options.setEnvironment(CompilerOptions.Environment.CUSTOM);
    }

    options.setNewTypeInference(newTypeInference);
    options.setLanguageIn(languageIn);
    options.setLanguageOut(languageOut);

    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    // IDE mode must be enabled or all of the jsdoc info will be stripped from the AST.
    options.setIdeMode(true);
    options.setPreserveJsDocWhitespace(true);

    // For easier debugging.
    options.setPrettyPrint(true);

    options.setAliasTransformationHandler(transformListener);

    options.addCustomPass(CustomPassExecutionTime.BEFORE_CHECKS, providedSymbolPass);
    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS, typeCollectionPass);

    return options;
  }

  public static final class Builder {
    private String[] args = new String[0];
    private CompilerOptions.LanguageMode languageIn = CompilerOptions.LanguageMode.ECMASCRIPT5;
    private CompilerOptions.LanguageMode languageOut = CompilerOptions.LanguageMode.ECMASCRIPT5;
    private boolean newTypeInference = false;

    public Builder setArgs(String[] args) {
      this.args = args;
      return this;
    }

    public Builder setLanguageIn(CompilerOptions.LanguageMode in) {
      this.languageIn = in;
      return this;
    }

    public Builder setNewTypeInference(boolean set) {
      this.newTypeInference = set;
      return this;
    }

    public CompilerModule build() {
      return new CompilerModule(this);
    }
  }
}
