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
import static com.google.common.io.Files.getFileExtension;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;

import com.github.jsdossier.Annotations.PostRenderingTasks;
import com.github.jsdossier.Annotations.RenderingTasks;
import com.github.jsdossier.jscomp.DossierCommandLineRunner;
import com.github.jsdossier.jscomp.DossierCompiler;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.joda.time.Instant;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatterBuilder;

final class Main {
  private Main() {}

  private static final Logger log = Logger.getLogger(Main.class.getName());

  private static void print(Config config) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String header = " Configuration  ";
    int len = header.length();
    String pad = Strings.repeat("=", len / 2);

    System.err.println(pad + header + pad);
    System.err.println(gson.toJson(config.toJson()));
    System.err.println(Strings.repeat("=", 79));
  }

  private static void configureLogging() {
    Logger log = Logger.getLogger(Main.class.getPackage().getName());
    log.setLevel(Level.WARNING);
    log.addHandler(
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            System.err.printf(
                "[%s][%s] %s\n", record.getLevel(), record.getLoggerName(), record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        });
  }

  @VisibleForTesting
  static int run(String[] args, FileSystem fileSystem) throws IOException {
    Flags flags = Flags.parse(args, fileSystem);
    Config config = Config.fromFlags(flags, fileSystem);

    if (flags.printConfig) {
      print(config);
      return 1;
    }

    Path output = config.getOutput();
    if ("zip".equals(getFileExtension(output.toString()))) {
      try (FileSystem outputFs = openZipFileSystem(output)) {
        output = outputFs.getPath("/");
        return run(flags, config, output);
      }
    }
    return run(flags, config, output);
  }

  private static FileSystem openZipFileSystem(Path zip) throws IOException {
    ImmutableMap<String, String> attributes =
        ImmutableMap.of("create", "true", "encoding", UTF_8.displayName());
    if (zip.getFileSystem() == FileSystems.getDefault()) {
      URI uri = URI.create("jar:file:" + zip.toAbsolutePath());
      return FileSystems.newFileSystem(uri, attributes);
    }
    // An in-memory file system used for testing.
    return zip.getFileSystem().provider().newFileSystem(zip, attributes);
  }

  private static ListenableFuture<List<Path>> submitRenderingTasks(
      ListeningExecutorService executor, Injector injector, Class<? extends Annotation> qualifier)
      throws InterruptedException {
    List<RenderTask> tasks = injector.getInstance(new Key<List<RenderTask>>(qualifier) {});

    @SuppressWarnings("unchecked") // Safe by the contract of invokeAll().
    List<ListenableFuture<List<Path>>> stage1 = (List) executor.invokeAll(tasks);
    ListenableFuture<List<List<Path>>> stage2 = allAsList(stage1);
    return transform(stage2, lists -> lists.stream().flatMap(List::stream).collect(toList()));
  }

  private static int run(Flags flags, Config config, Path outputDir) throws IOException {
    configureLogging();

    ExplicitScope documentationScope = new ExplicitScope();

    Injector injector =
        Guice.createInjector(
            new CompilerModule(),
            new ConfigModule(flags, config, outputDir, documentationScope),
            new RenderTaskModule());

    DossierCommandLineRunner runner = injector.getInstance(DossierCommandLineRunner.class);
    if (!runner.shouldRunCompiler()) {
      return -1;
    }

    Instant start = Instant.now();
    System.out.println("Generating documentation...");

    int result = runner.call();
    if (result != 0) {
      System.out.println("Compilation failed; aborting...");
      return result;
    }

    TypeRegistry typeRegistry = injector.getInstance(TypeRegistry.class);
    DossierCompiler compiler = injector.getInstance(DossierCompiler.class);
    typeRegistry.computeTypeRelationships(compiler.getTopScope(), compiler.getTypeRegistry());

    ListeningExecutorService executor = null;
    try {
      documentationScope.enter();
      createDirectories(outputDir);

      executor = listeningDecorator(newFixedThreadPool(flags.numThreads));

      List<Path> stage1Results =
          submitRenderingTasks(executor, injector, RenderingTasks.class).get();
      List<Path> stage2Results =
          submitRenderingTasks(executor, injector, PostRenderingTasks.class).get();

      if (log.isLoggable(Level.FINER)) {
        log.fine("Rendered:\n  " + Joiner.on("\n  ").join(concat(stage1Results, stage2Results)));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Rendering was interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      } else if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException(e.getCause());
    } finally {
      if (executor != null) {
        executor.shutdownNow();
      }
      documentationScope.exit();
    }

    Instant stop = Instant.now();
    String output =
        new PeriodFormatterBuilder()
            .appendHours()
            .appendSuffix("h") // I hope not...
            .appendSeparator(" ")
            .appendMinutes()
            .appendSuffix("m")
            .appendSeparator(" ")
            .appendSecondsWithOptionalMillis()
            .appendSuffix("s")
            .toFormatter()
            .print(new Period(start, stop));

    System.out.println("Finished in " + output);
    return 0;
  }

  public static void main(String[] args) {
    int exitCode;
    try {
      exitCode = run(args, FileSystems.getDefault());
    } catch (IOException e) {
      e.printStackTrace(System.err);
      exitCode = 2;
    }
    System.exit(exitCode);
  }
}
