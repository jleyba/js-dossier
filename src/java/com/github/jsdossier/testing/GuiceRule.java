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

package com.github.jsdossier.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.github.jsdossier.MarkdownPage;
import com.github.jsdossier.ModuleNamingConvention;
import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.ModuleExterns;
import com.github.jsdossier.annotations.ModuleFilter;
import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.annotations.SourceUrlTemplate;
import com.github.jsdossier.annotations.Stderr;
import com.github.jsdossier.annotations.TypeFilter;
import com.github.jsdossier.jscomp.CompilerModule;
import com.github.jsdossier.jscomp.NodeLibrary;
import com.github.jsdossier.soy.DossierSoyModule;
import com.google.auto.value.AutoValue;
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
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.template.soy.exprtree.NullNode;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** A simple rule for injecting necessary values into an object prior to evaluating a statement. */
@AutoValue
public abstract class GuiceRule implements TestRule {

  public static Builder builder(Object target, Module... modules) {
    Builder builder = new AutoValue_GuiceRule.Builder();
    return builder
        .setTarget(target)
        .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5)
        .setModuleNamingConvention(ModuleNamingConvention.ES6)
        .setNewTypeInference(false)
        .setGuiceModules(ImmutableList.copyOf(modules))
        .setInputFs(Jimfs.newFileSystem())
        .setModulePrefix(Optional.empty())
        .setSourcePrefix(Optional.empty())
        .setModuleExterns(ImmutableSet.of())
        .setModules(ImmutableSet.of())
        .setModulePathFilter(path -> false)
        .setTypeNameFilter(path -> false)
        .setSourceUrlTemplate(Optional.empty())
        .setOutputFs(Jimfs.newFileSystem())
        .setOutputDir(Optional.empty());
  }

  abstract Object getTarget();

  abstract ImmutableList<Module> getGuiceModules();

  abstract FileSystem getInputFs();

  abstract Optional<Path> getModulePrefix();

  abstract Optional<Path> getSourcePrefix();

  abstract ImmutableSet<Path> getModules();

  abstract ImmutableSet<Path> getModuleExterns();

  abstract Predicate<Path> getModulePathFilter();

  abstract Predicate<String> getTypeNameFilter();

  abstract Optional<String> getSourceUrlTemplate();

  abstract ModuleNamingConvention getModuleNamingConvention();

  abstract CompilerOptions.LanguageMode getLanguageIn();

  abstract boolean getNewTypeInference();

  abstract FileSystem getOutputFs();

  abstract Optional<Path> getOutputDir();

  @Nullable
  abstract Class<? extends NodeLibrary> getNodeLibrary();

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
    ImmutableList<Module> modules =
        ImmutableList.<Module>builder()
            .addAll(getGuiceModules())
            .add(new CompilerModule())
            .add(new DossierSoyModule())
            .add(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(Path.class, ModulePrefix.class, getModulePrefix());
                    bind(Path.class, SourcePrefix.class, getSourcePrefix());
                    bind(Path.class, Output.class, getOutputDir());
                    bindScope(DocumentationScoped.class, Scopes.NO_SCOPE);
                    bind(ModuleNamingConvention.class).toInstance(getModuleNamingConvention());
                    bind(new Key<ImmutableSet<MarkdownPage>>() {}).toInstance(ImmutableSet.of());

                    if (getNodeLibrary() != null) {
                      bind(NodeLibrary.class).to(getNodeLibrary());
                    }
                  }

                  @Provides
                  @Input
                  LanguageMode provideInputLanguage() {
                    return getLanguageIn();
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
                  @ModuleFilter
                  Predicate<Path> provideModulePathFilter() {
                    return getModulePathFilter();
                  }

                  @Provides
                  @TypeFilter
                  Predicate<String> provideTypeNameFilter() {
                    return getTypeNameFilter();
                  }

                  @Provides
                  @SourceUrlTemplate
                  Optional<String> provideSourceUrlTemplate() {
                    return getSourceUrlTemplate();
                  }

                  @Provides
                  @Modules
                  ImmutableSet<Path> provideModules() {
                    return getModules();
                  }

                  @Provides
                  @ModuleExterns
                  ImmutableSet<Path> provideModuleExterns() {
                    return getModuleExterns();
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
  public abstract static class Builder {
    abstract Builder setTarget(Object target);

    abstract Builder setGuiceModules(ImmutableList<Module> modules);

    abstract Builder setModulePrefix(Optional<Path> path);

    abstract Optional<Path> getModulePrefix();

    abstract Builder setSourcePrefix(Optional<Path> path);

    abstract Builder setOutputDir(Optional<Path> out);

    public abstract Builder setNewTypeInference(boolean set);

    public abstract Builder setModuleNamingConvention(ModuleNamingConvention convention);

    public abstract Builder setLanguageIn(CompilerOptions.LanguageMode languageIn);

    public abstract Builder setModulePathFilter(Predicate<Path> filter);

    public abstract Builder setTypeNameFilter(Predicate<String> filter);

    public abstract Builder setInputFs(FileSystem fs);

    public abstract FileSystem getInputFs();

    abstract Builder setSourceUrlTemplate(Optional<String> pattern);

    public Builder setSourceUrlTemplate(String pattern) {
      return setSourceUrlTemplate(Optional.of(pattern));
    }

    public Builder setModulePrefix(String prefix) {
      return setModulePrefix(Optional.of(getInputFs().getPath(prefix)));
    }

    public Builder setSourcePrefix(String prefix) {
      return setSourcePrefix(Optional.of(getInputFs().getPath(prefix)));
    }

    public abstract Builder setModuleExterns(ImmutableSet<Path> externs);

    public Builder setModuleExterns(String... paths) {
      ImmutableSet.Builder<Path> externs = ImmutableSet.builder();
      for (String path : paths) {
        externs.add(getInputFs().getPath(path));
      }
      return setModuleExterns(externs.build());
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
    
    public Builder setUseNodeLibrary(boolean useLibrary) {
      if (useLibrary) {
        return setNodeLibrary(null);
      }
      return setNodeLibrary(NullNodeLibrary.class);
    }

    public abstract Builder setNodeLibrary(@Nullable Class<? extends NodeLibrary> library);

    public abstract GuiceRule build();
  }

  private static final class NullNodeLibrary implements NodeLibrary {}
}
