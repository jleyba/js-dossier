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

package com.github.jsdossier.testing;

import static com.google.common.base.Preconditions.checkArgument;

import com.github.jsdossier.CompilerModule;
import com.github.jsdossier.NominalType;
import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.annotations.Stderr;
import com.github.jsdossier.annotations.TypeFilter;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.javascript.jscomp.CompilerOptions;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.nio.file.FileSystem;
import java.nio.file.Path;

/**
 * A simple rule for injecting necessary values into an object prior to evaluating a statement.
 */
@AutoValue
public abstract class GuiceRule implements TestRule {
  
  public static Builder builder(Object target, Module... modules) {
    Builder builder = new AutoValue_GuiceRule.Builder();
    return builder
        .setTarget(target)
        .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5)
        .setNewTypeInference(false)
        .setGuiceModules(ImmutableList.copyOf(modules))
        .setInputFs(Jimfs.newFileSystem())
        .setModulePrefix(Optional.<Path>absent())
        .setSourcePrefix(Optional.<Path>absent())
        .setModules(ImmutableSet.<Path>of())
        .setTypeFilter(Predicates.<NominalType>alwaysFalse())
        .setTypeNameFilter(Predicates.<String>alwaysFalse())

        .setOutputFs(Jimfs.newFileSystem())
        .setOutputDir(Optional.<Path>absent())
        ;
  }

  abstract Object getTarget();
  abstract ImmutableList<Module> getGuiceModules();

  abstract FileSystem getInputFs();
  abstract Optional<Path> getModulePrefix();
  abstract Optional<Path> getSourcePrefix();
  abstract ImmutableSet<Path> getModules();
  abstract Predicate<NominalType> getTypeFilter();
  abstract Predicate<String> getTypeNameFilter();
  
  abstract CompilerOptions.LanguageMode getLanguageIn();
  abstract boolean getNewTypeInference();

  abstract FileSystem getOutputFs();
  abstract Optional<Path> getOutputDir();
  
  public abstract Builder toBuilder();

  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        Injector injector = createInjector();
        injector.injectMembers(getTarget());
        base.evaluate();
      }
    };
  }
  
  public Injector createInjector() {
    ImmutableList<Module> modules = ImmutableList.<Module>builder()
        .addAll(getGuiceModules())
        .add(new CompilerModule.Builder()
            .setLanguageIn(getLanguageIn())
            .setNewTypeInference(getNewTypeInference())
            .build())
        .add(new AbstractModule() {
          @Override
          protected void configure() {
            bind(Path.class, ModulePrefix.class, getModulePrefix());
            bind(Path.class, SourcePrefix.class, getSourcePrefix());
            bind(Path.class, Output.class, getOutputDir());
            bindScope(DocumentationScoped.class, Scopes.NO_SCOPE);
          }

          @Provides
          @Stderr
          PrintStream provideStderr() {
            return System.err;
          }

          @Provides
          @Input
          FileSystem provideInputFs() {
            return getInputFs();
          }

          @Provides
          @Output
          FileSystem provideOutputFs() {
            return getOutputFs();
          }

          @Provides
          @TypeFilter
          Predicate<NominalType> provideTypeFilter() {
            return getTypeFilter();
          }

          @Provides
          @TypeFilter
          Predicate<String> provideTypeNameFilter() {
            return getTypeNameFilter();
          }

          @Provides
          @Modules
          ImmutableSet<Path> provideModuels() {
            return getModules();
          }

          private <T> void bind(
              Class<T> clazz, Class<? extends Annotation> ann, Optional<T> opt) {
            if (opt.isPresent()) {
              bind(Key.get(clazz, ann)).toInstance(opt.get());
            }
          }
        })
        .build();

    return Guice.createInjector(modules);
  }
  
  @AutoValue.Builder
  public static abstract class Builder {
    abstract Builder setTarget(Object target);
    abstract Builder setGuiceModules(ImmutableList<Module> modules);

    abstract Builder setModulePrefix(Optional<Path> path);
    abstract Optional<Path> getModulePrefix();

    abstract Builder setSourcePrefix(Optional<Path> path);
    abstract Builder setOutputDir(Optional<Path> out);
    
    public abstract Builder setNewTypeInference(boolean set);
    public abstract Builder setLanguageIn(CompilerOptions.LanguageMode languageIn);
    public abstract Builder setTypeFilter(Predicate<NominalType> filter);
    public abstract Builder setTypeNameFilter(Predicate<String> filter);
    public abstract Builder setInputFs(FileSystem fs);
    public abstract FileSystem getInputFs();
    
    public Builder setModulePrefix(String prefix) {
      return setModulePrefix(Optional.of(getInputFs().getPath(prefix)));
    }
    
    public Builder setSourcePrefix(String prefix) {
      return setSourcePrefix(Optional.of(getInputFs().getPath(prefix)));
    }

    public abstract Builder setModules(ImmutableSet<Path> modules);
    public Builder setModules(String... paths) {
      Optional<Path> opt = getModulePrefix();
      checkArgument(opt.isPresent(), "module prefix not set");
      
      Path prefix = opt.get();
      ImmutableSet.Builder<Path> modules = ImmutableSet.builder();
      for (String path : paths) {
        modules.add(prefix.resolve(path));
      }

      return setModules(modules.build());
    }

    public abstract Builder setOutputFs(FileSystem fs);
    public abstract FileSystem getOutputFs();
    
    public Builder setOutputDir(String path) {
      return setOutputDir(Optional.of(getOutputFs().getPath(path)));
    }

    public abstract GuiceRule build();
  }
}
