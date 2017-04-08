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

import static com.google.common.collect.Iterables.concat;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.github.jsdossier.Annotations.PostRenderingTasks;
import com.github.jsdossier.Annotations.RenderingTasks;
import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.SourceUrlTemplate;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.inject.Qualifier;

/** Module responsible for providing the bindings for all rendering tasks. */
final class RenderTaskModule extends AbstractModule {
  @Override
  protected void configure() {}

  @Provides
  @DocumentationScoped
  @PostRenderingTasks
  List<RenderTask> providePostRenderingTasks(RenderTypeIndexTask task) {
    return ImmutableList.of(task);
  }

  @Provides
  @DocumentationScoped
  @RenderingTasks
  List<RenderTask> provideRenderingTasks(
      RenderIndexTask indexTask,
      @CustomPageTasks List<RenderTask> customPageTasks,
      @DocumentableTypeTasks List<RenderTask> documentableTypeTasks,
      @ResourceFileTasks List<RenderTask> resourceTasks,
      @SourceFileTasks List<RenderTask> sourceFileTasks) {
    List<RenderTask> result =
        new ArrayList<>(
            customPageTasks.size()
                + documentableTypeTasks.size()
                + resourceTasks.size()
                + sourceFileTasks.size()
                + 1); // indexTask.

    result.add(indexTask);
    result.addAll(customPageTasks);
    result.addAll(documentableTypeTasks);
    result.addAll(resourceTasks);
    result.addAll(sourceFileTasks);

    return result;
  }

  @Provides
  @DocumentationScoped
  @CustomPageTasks
  List<RenderTask> provideCustomPageRenderingTasks(
      ImmutableSet<MarkdownPage> pages, RenderMarkdownTaskFactory factory) {
    return pages.stream().map(factory::create).collect(toList());
  }

  @Provides
  @DocumentationScoped
  @DocumentableTypeTasks
  List<RenderTask> provideRenderTypeTasks(
      DossierFileSystem dfs,
      TypeRegistry registry,
      DocumentableTypePredicate predicate,
      RenderDocumentationTaskSupplierFactory factory) {
    // First, group all types by output paths, forced to lower case strings.
    // Any types with a collision must be rendered together to ensure no data mysteriously
    // disappears when running on a case insensitive file system (OSX is case-insensitive,
    // but case-preserving).
    return registry
        .getAllTypes()
        .stream()
        .filter(predicate)
        .collect(
            groupingBy(
                type -> dfs.getPath(type).toAbsolutePath().normalize().toString().toLowerCase()))
        .values()
        .stream()
        .map(ImmutableList::copyOf)
        .map(factory::create)
        .map(Supplier::get)
        .flatMap(List::stream)
        .collect(toList());
  }

  @Provides
  @DocumentationScoped
  @ResourceFileTasks
  List<RenderTask> provideResourceFileTasks(
      DocTemplate template, RenderResourceTaskFactory factory) {
    Iterable<TemplateFile> templateFiles =
        concat(
            template.getAdditionalFiles(),
            template.getCss(),
            template.getHeadJs(),
            template.getTailJs());
    return StreamSupport.stream(templateFiles.spliterator(), false)
        .map(factory::create)
        .collect(toList());
  }

  @Provides
  @DocumentationScoped
  @SourceFileTasks
  List<RenderTask> provieSourceFileTasks(
      @SourceUrlTemplate Optional<String> template,
      @Input ImmutableSet<Path> sourceFiles,
      RenderSourceFileTaskFactory factory) {
    if (template.isPresent()) {
      return ImmutableList.of();
    }
    return StreamSupport.stream(sourceFiles.spliterator(), false)
        .map(factory::create)
        .collect(toList());
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  private @interface CustomPageTasks {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  private @interface DocumentableTypeTasks {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  private @interface ResourceFileTasks {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  private @interface SourceFileTasks {}
}
