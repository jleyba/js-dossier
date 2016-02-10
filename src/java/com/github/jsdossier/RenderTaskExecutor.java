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

import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import com.github.jsdossier.Annotations.NumThreads;
import com.github.jsdossier.annotations.DocumentationScoped;
import com.github.jsdossier.jscomp.NominalType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * Executes a collection of rendering tasks.
 */
@DocumentationScoped
final class RenderTaskExecutor {

  private static final Logger logger = Logger.getLogger(RenderTaskExecutor.class.getName());

  private final DossierFileSystem dfs;
  private final RenderDocumentationTaskSupplierFactory documentationTaskSupplierFactory;
  private final RenderResourceTaskFactory resourceTaskFactory;
  private final RenderMarkdownTaskFactory markdownTaskFactory;
  private final RenderSourceFileTaskFactory sourceFileTaskFactory;
  private final RenderIndexTask indexTask;
  private final RenderTypeIndexTask typeIndexTask;

  private final ListeningExecutorService executorService;

  private final List<ListenableFuture<Path>> submittedTasks = new ArrayList<>();

  @Inject
  RenderTaskExecutor(
      @NumThreads int numThreads,
      DossierFileSystem dfs,
      RenderDocumentationTaskSupplierFactory documentationTaskSupplierFactory,
      RenderResourceTaskFactory resourceTaskFactory,
      RenderMarkdownTaskFactory markdownTaskFactory,
      RenderSourceFileTaskFactory sourceFileTaskFactory,
      RenderIndexTask indexTask,
      RenderTypeIndexTask typeIndexTask) {
    this.dfs = dfs;
    this.documentationTaskSupplierFactory = documentationTaskSupplierFactory;
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
  public RenderTaskExecutor renderDocumentation(Iterable<NominalType> types) {
    // First, group all types by output paths, forced to lower case strings.
    // Any types with a collision must be rendered together to ensure no data mysteriously
    // disappears when running on a case insensitive file system (OSX is case-insensitive,
    // but case-preserving).
    Multimap<String, NominalType> normalizedPathToTypes =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (NominalType type : types) {
      String normalized = dfs.getPath(type).toAbsolutePath().normalize().toString().toLowerCase();
      normalizedPathToTypes.put(normalized, type);
    }

    for (String path : normalizedPathToTypes.keySet()) {
      RenderDocumentationTaskSupplier supplier =
          documentationTaskSupplierFactory.create(
              ImmutableList.copyOf(normalizedPathToTypes.get(path)));
      for (Callable<Path> task : supplier.get()) {
        submit(task);
      }
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

  /**
   * Signals that no further tasks will be submitted and the executor should wait for existing tasks
   * to complete.
   *
   * @return a future that will resolve to a list of all rendered files.
   */
  public ListenableFuture<List<Path>> awaitTermination() {
    executorService.shutdown();

    final SettableFuture<List<Path>> completedTasks = SettableFuture.create();
    final int numTasks = submittedTasks.size();
    FutureCallback<Path> callback = new FutureCallback<Path>() {
      private final AtomicBoolean loggedError = new AtomicBoolean(false);
      private final List<Path> completed = new ArrayList<>();

      @Override public synchronized void onSuccess(Path result) {
        completed.add(result);
        if (completed.size() >= numTasks) {
          completedTasks.set(completed);
        }
      }

      @Override public void onFailure(Throwable t) {
        // Only log an error once since the hard shutdown will likely cause other failures.
        if (loggedError.compareAndSet(false, true)) {
          logger.log(Level.SEVERE, "An error occurred", t);
          completedTasks.setException(t);
        }
        executorService.shutdownNow();
      }
    };

    for (ListenableFuture<Path> task : submittedTasks) {
      Futures.addCallback(task, callback);
    }

    return transform(completedTasks, new AsyncFunction<List<Path>, List<Path>>() {
      @Override
      public ListenableFuture<List<Path>> apply(@Nonnull List<Path> input) throws IOException {
        input.add(typeIndexTask.call());
        return Futures.immediateFuture(input);
      }
    }, directExecutor());
  }
}
