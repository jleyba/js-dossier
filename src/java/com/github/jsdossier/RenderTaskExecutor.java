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

import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.github.jsdossier.Annotations.NumThreads;
import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.jscomp.NominalType2;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

/**
 * Executes a collection of rendering tasks.
 */
@DocumentationScoped
final class RenderTaskExecutor {
  
  private static final Logger logger = Logger.getLogger(RenderTaskExecutor.class.getName());

  private final RenderDocumentationTaskFactory documentationTaskFactory;
  private final RenderResourceTaskFactory resourceTaskFactory;
  private final RenderMarkdownTaskFactory markdownTaskFactory;
  private final RenderSourceFileTaskFactory sourceFileTaskFactory;
  private final RenderIndexTask indexTask;
  private final RenderTypeIndexTask typeIndexTask;

  private final ListeningExecutorService executorService;

  private final List<ListenableFuture<Path>> documentationTasks = new ArrayList<>();
  private final List<ListenableFuture<Path>> submittedTasks = new ArrayList<>();

  @Inject
  RenderTaskExecutor(
      @NumThreads int numThreads,
      RenderDocumentationTaskFactory documentationTaskFactory,
      RenderResourceTaskFactory resourceTaskFactory,
      RenderMarkdownTaskFactory markdownTaskFactory,
      RenderSourceFileTaskFactory sourceFileTaskFactory,
      RenderIndexTask indexTask,
      RenderTypeIndexTask typeIndexTask) {
    this.documentationTaskFactory = documentationTaskFactory;
    this.resourceTaskFactory = resourceTaskFactory;
    this.markdownTaskFactory = markdownTaskFactory;
    this.sourceFileTaskFactory = sourceFileTaskFactory;
    this.indexTask = indexTask;
    this.typeIndexTask = typeIndexTask;
    this.executorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(numThreads));
  }

  /**
   * Submits the task to render the main index page.
   *
   * @return a self reference.
   */
  public RenderTaskExecutor renderIndex() {
    submit(indexTask);
    return this;
  }

  /**
   * Submits tasks to render documentation for the given types.
   *
   * @param types the types to generate documentation for.
   * @return a self reference.
   */
  public RenderTaskExecutor renderDocumentation(Iterable<NominalType2> types) {
    for (NominalType2 type : types) {
      submit(documentationTaskFactory.create(type));
    }
    return this;
  }

  /**
   * Submits tasks to render the given markdown pages.
   *
   * @param pages the pages to render.
   * @return a self reference.
   */
  public RenderTaskExecutor renderMarkdown(Iterable<MarkdownPage> pages) {
    for (MarkdownPage page : pages) {
      renderMarkdown(page);
    }
    return this;
  }

  /**
   * Submits a task to render a markdown page.
   *
   * @param page the page to render.
   * @return a self reference.
   */
  public RenderTaskExecutor renderMarkdown(MarkdownPage page) {
    submit(markdownTaskFactory.create(page));
    return this;
  }

  /**
   * Copies a list of resource files.
   *
   * @param files the files to copy.
   * @return a self reference.
   */
  public RenderTaskExecutor renderResources(Iterable<TemplateFile> files) {
    for (TemplateFile file : files) {
      renderResource(file);
    }
    return this;
  }

  /**
   * Submits a task to copy a resource file to the output directory.
   *
   * @param file the file to copy.
   * @return a self reference.
   */
  public RenderTaskExecutor renderResource(TemplateFile file) {
    submit(resourceTaskFactory.create(file));
    return this;
  }

  /**
   * Renders a list of source files.
   *
   * @param paths the files to render.
   * @return a self reference.
   */
  public RenderTaskExecutor renderSourceFiles(Iterable<Path> paths) {
    for (Path path : paths) {
      renderSourceFile(path);
    }
    return this;
  }

  /**
   * Submits a task to render a source file.
   *
   * @param path path to the file to render.
   * @return a self reference.
   */
  public RenderTaskExecutor renderSourceFile(Path path) {
    submit(sourceFileTaskFactory.create(path));
    return this;
  }

  private void submit(Callable<Path> task) {
    submittedTasks.add(executorService.submit(task));
  }
  
  private AsyncFunction<Object, Path> renderTypeIndex() {
    return new AsyncFunction<Object, Path>() {
      @Override
      public ListenableFuture<Path> apply(Object input) throws IOException {
        return Futures.immediateFuture(typeIndexTask.call());
      }
    };
  }

  /**
   * Signals that no further tasks will be submitted and the executor should wait for existing tasks
   * to complete.
   *
   * @return a future that will resolve to a list of all rendered files.
   */
  public ListenableFuture<List<Path>> awaitTermination() {
    executorService.shutdown();

    // Once all documentation rendering tasks have finished, the type index will be built and may
    // be rendered.
    ListenableFuture<?> docs = allAsList(documentationTasks);
    ListenableFuture<Path> indexJsTask = transform(docs, renderTypeIndex(), directExecutor());

    submittedTasks.add(indexJsTask);
    ListenableFuture<List<Path>> list = allAsList(submittedTasks);
    Futures.addCallback(list, new FutureCallback<List<Path>>() {
      @Override public void onSuccess(List<Path> result) {}
      @Override public void onFailure(Throwable t) {
        logger.log(Level.SEVERE, "An error occurred", t);
        executorService.shutdownNow();
      }
    });
    return list;
  }
}
