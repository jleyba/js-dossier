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

import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.annotations.Externs;
import com.github.jsdossier.annotations.IncludeTestOnly;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.ModuleExterns;
import com.github.jsdossier.annotations.ModuleFilter;
import com.github.jsdossier.annotations.ModulePrefix;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Output;
import com.github.jsdossier.annotations.Readme;
import com.github.jsdossier.annotations.SourcePrefix;
import com.github.jsdossier.annotations.SourceUrlTemplate;
import com.github.jsdossier.annotations.Stderr;
import com.github.jsdossier.annotations.Stdout;
import com.github.jsdossier.annotations.StrictMode;
import com.github.jsdossier.annotations.TypeFilter;
import com.github.jsdossier.jscomp.Environment;
import com.github.jsdossier.soy.DossierSoyModule;
import com.github.jsdossier.soy.Renderer;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scope;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

/** Provides the main configuration bindings. */
final class ConfigModule extends AbstractModule {

  private final Flags flags;
  private final Config config;
  private final Path outputDir;
  private final Scope documentationScope;

  ConfigModule(Flags flags, Config config, Path outputDir, Scope documentationScope) {
    this.flags = flags;
    this.config = config;
    this.outputDir = outputDir;
    this.documentationScope = documentationScope;
  }

  @Override
  protected void configure() {
    install(new DossierSoyModule());

    bindScope(DocumentationScoped.class, documentationScope);

    bindConstant().annotatedWith(Annotations.NumThreads.class).to(flags.numThreads);

    bind(PrintStream.class).annotatedWith(Stderr.class).toInstance(System.err);
    bind(PrintStream.class)
        .annotatedWith(Stdout.class)
        .toInstance(new PrintStream(ByteStreams.nullOutputStream()));

    bind(new Key<Optional<Path>>(Readme.class) {}).toInstance(config.getReadme());

    ImmutableSet<Path> allInputs =
        ImmutableSet.<Path>builder()
            .addAll(config.getSources())
            .addAll(config.getModules())
            .build();
    bind(new Key<ImmutableSet<Path>>(Input.class) {}).toInstance(allInputs);
    bind(new Key<ImmutableSet<Path>>(Externs.class) {}).toInstance(config.getExterns());
    bind(new Key<ImmutableSet<Path>>(Modules.class) {}).toInstance(config.getModules());
    bind(new Key<ImmutableSet<Path>>(ModuleExterns.class) {}).toInstance(config.getExternModules());
    bind(new Key<ImmutableSet<MarkdownPage>>() {}).toInstance(config.getCustomPages());

    bind(new Key<Optional<Path>>(ModulePrefix.class) {}).toInstance(config.getModulePrefix());
    bind(Key.get(Path.class, ModulePrefix.class))
        .toProvider(ModulePrefixProvider.class)
        .in(DocumentationScoped.class);
    bind(Path.class).annotatedWith(SourcePrefix.class).toInstance(config.getSrcPrefix());

    bind(Path.class).annotatedWith(Output.class).toInstance(outputDir);
    bind(FileSystem.class).annotatedWith(Output.class).toInstance(outputDir.getFileSystem());
    bind(FileSystem.class).annotatedWith(Input.class).toInstance(config.getFileSystem());

    bind(ModuleNamingConvention.class).toInstance(config.getModuleNamingConvention());

    bind(DocTemplate.class).to(DefaultDocTemplate.class).in(DocumentationScoped.class);
    bind(Renderer.class).in(DocumentationScoped.class);
  }

  @Provides
  Environment provideEnvironment() {
    return config.getEnvironment();
  }

  @Provides
  @SourceUrlTemplate
  Optional<String> provideUrlTemplate() {
    return config.getSourceUrlTemplate();
  }

  @Provides
  @ModuleFilter
  Predicate<Path> provideModulePathFilter() {
    return config::isFilteredModule;
  }

  @Provides
  @TypeFilter
  Predicate<String> provideTypeNameFilter() {
    return config::isFilteredType;
  }

  @Provides
  @StrictMode
  boolean provideStrictMode() {
    return config.isStrict();
  }

  @Provides
  @IncludeTestOnly
  boolean provideIncludeTestOnly() {
    return config.getIncludeTestOnly();
  }
}
