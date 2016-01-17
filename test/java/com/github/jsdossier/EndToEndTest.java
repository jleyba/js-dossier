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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.write;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.common.jimfs.Jimfs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RunWith(Parameterized.class)
public class EndToEndTest {

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() throws IOException {
    ImmutableList.Builder<Object[]> data = ImmutableList.builder();
    data.add(new Object[]{new Scenario("out/")});
    data.add(new Object[]{new Scenario("out.zip")});
    return data.build();
  }

  private final Scenario scenario;
  private Path outDir;

  public EndToEndTest(Scenario scenario) {
    this.scenario = scenario;
  }

  @Override
  public String toString() {
    return "EndToEndTest::" + scenario;
  }

  @Before
  public void initOutputDir() throws Exception {
    outDir = scenario.init();
  }

  @Test
  public void checkCopiesAllSourceFiles() {
    assertExists(outDir.resolve("source/closure_module.js.src.html"));
    assertExists(outDir.resolve("source/globals.js.src.html"));
    assertExists(outDir.resolve("source/json.js.src.html"));
    assertExists(outDir.resolve("source/example/index.js.src.html"));
    assertExists(outDir.resolve("source/subdir/emptyenum.js.src.html"));
  }

  @Test
  public void checkSourceFileRendering() throws IOException {
    Document document = load(outDir.resolve("source/subdir/emptyenum.js.src.html"));
    compareWithGoldenFile(
        querySelector(document, "article.srcfile"),
        "source/subdir/emptyenum.js.src.html");
    checkHeader(document);
    compareWithGoldenFile(querySelectorAll(document, "nav"), "source/subdir/nav.html");
    compareWithGoldenFile(
        querySelectorAll(document, "main ~ footer, main ~ script"), "source/subdir/footer.html");
  }

  @Test
  public void checkMarkdownIndexProcessing() throws IOException {
    Document document = load(outDir.resolve("index.html"));
    compareWithGoldenFile(querySelector(document, "article.page"), "index.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkGlobalClass() throws IOException {
    Document document = load(outDir.resolve("GlobalCtor.html"));
    compareWithGoldenFile(querySelector(document, "article"), "GlobalCtor.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkGlobalEnum() throws IOException {
    Document document = load(outDir.resolve("GlobalEnum.html"));
    compareWithGoldenFile(querySelector(document, "article"), "GlobalEnum.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkEmptyGlobalEnum() throws IOException {
    Document document = load(outDir.resolve("EmptyEnum.html"));
    compareWithGoldenFile(querySelector(document, "article"), "EmptyEnum.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkGlobalUndefinedEnum() throws IOException {
    Document document = load(outDir.resolve("UndefinedEnum.html"));
    compareWithGoldenFile(querySelector(document, "article"), "UndefinedEnum.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkDeprecatedClass() throws IOException {
    Document document = load(outDir.resolve("DeprecatedFoo.html"));
    compareWithGoldenFile(querySelector(document, "article"), "DeprecatedFoo.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkFunctionNamespace() throws IOException {
    Document document = load(outDir.resolve("sample.json.html"));
    compareWithGoldenFile(querySelector(document, "article"), "sample.json.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkInterfaceThatExtendsOtherInterfaces() throws IOException {
    Document document = load(outDir.resolve("sample.inheritance.LeafInterface.html"));
    compareWithGoldenFile(querySelector(document, "article"),
        "sample.inheritance.LeafInterface.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkExportedApiOfClosureModule() throws IOException {
    Document document = load(outDir.resolve("closure.module.html"));
    compareWithGoldenFile(querySelector(document, "article"), "closure.module.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkClassDefiendOnClosureModuleExportsObject() throws IOException {
    Document document = load(outDir.resolve("closure.module.Clazz.html"));
    compareWithGoldenFile(querySelector(document, "article"), "closure.module.Clazz.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkClassExportedByClosureModule() throws IOException {
    Document document = load(outDir.resolve("closure.module.PubClass.html"));
    compareWithGoldenFile(querySelector(document, "article"), "closure.module.PubClass.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkClassExtendsTemplateClass() throws IOException {
    Document document = load(outDir.resolve("sample.inheritance.NumberClass.html"));
    compareWithGoldenFile(querySelector(document, "article"),
        "sample.inheritance.NumberClass.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkGoogDefinedClass() throws IOException {
    Document document = load(outDir.resolve("sample.inheritance.StringClass.html"));
    compareWithGoldenFile(querySelector(document, "article"),
        "sample.inheritance.StringClass.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkDeprecatedClassWithSuperTypes() throws IOException {
    Document document = load(outDir.resolve("sample.inheritance.DeprecatedFinalClass.html"));
    compareWithGoldenFile(querySelector(document, "article"),
        "sample.inheritance.DeprecatedFinalClass.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkPackageIndexCommonJsModule() throws IOException {
    Document document = load(outDir.resolve("module/example/index.html"));
    compareWithGoldenFile(querySelector(document, "article"), "module/example/index.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkCommonJsModule() throws IOException {
    Document document = load(outDir.resolve("module/example/nested.html"));
    compareWithGoldenFile(querySelector(document, "article"), "module/example/nested.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkCommonJsModuleThatIsExportedConstructor() throws IOException {
    Document document = load(outDir.resolve("module/example/worker.html"));
    compareWithGoldenFile(querySelector(document, "article"), "module/example/worker.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkCommonJsModuleClassAlias() throws IOException {
    Document document = load(outDir.resolve("module/example/index_exports_Greeter.html"));
    compareWithGoldenFile(
        querySelector(document, "article"), "module/example/index_exports_Greeter.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkCommonJsModuleExportedInterface() throws IOException {
    Document document = load(outDir.resolve("module/example/nested_exports_IdGenerator.html"));
    compareWithGoldenFile(querySelector(document, "article"),
        "module/example/nested_exports_IdGenerator.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkCommonJsModuleInterfaceImplementation() throws IOException {
    Document document = load(outDir.resolve(
        "module/example/nested_exports_IncrementingIdGenerator.html"));
    compareWithGoldenFile(querySelector(document, "article"),
        "module/example/nested_exports_IncrementingIdGenerator.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkClassThatExtendsExternType() throws IOException {
    Document document = load(outDir.resolve(
        "sample.inheritance.RunnableError.html"));
    compareWithGoldenFile(querySelector(document, "article"),
        "sample.inheritance.RunnableError.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkNamespaceWithFilteredTypes() throws IOException {
    Document document = load(outDir.resolve("foo.html"));
    compareWithGoldenFile(querySelector(document, "article"), "foo.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkUnfilteredAliasOfFilteredClass() throws IOException {
    Document document = load(outDir.resolve("foo.quot.OneBarAlias.html"));
    compareWithGoldenFile(querySelector(document, "article"), "foo.quot.OneBarAlias.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkEs6Class() throws IOException {
    Document document = load(outDir.resolve("Calculator.html"));
    compareWithGoldenFile(querySelector(document, "article"), "Calculator.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkTypesWithCaseInsensitiveOutputFileNameCollision() throws IOException {
    for (String file : ImmutableList.of("test.Registry.html", "test.registry.html")) {
      Document document = load(outDir.resolve(file));
      compareWithGoldenFile(querySelector(document, "main"), "Registry.html");
      checkHeader(document);
      checkNav(document);
      checkFooter(document);
    }
  }

  @Test
  public void checkEs6Module() throws IOException {
    Document document = load(outDir.resolve("module/example/net.html"));
    compareWithGoldenFile(querySelector(document, "article"), "module/example/net.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkEs6ModuleWithImportButNoExports() throws IOException {
    Document document = load(outDir.resolve("module/example/empty.html"));
    compareWithGoldenFile(querySelector(document, "article"), "module/example/empty.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkEs6ModuleExportedClass() throws IOException {
    Document document = load(outDir.resolve("module/example/net_exports_HttpClient.html"));
    compareWithGoldenFile(querySelector(document, "article"),
        "module/example/net_exports_HttpClient.html");
    checkHeader(document);
    checkModuleNav(document);
    checkModuleFooter(document);
  }

  @Test
  public void checkGeneratedTypeIndex() throws IOException {
    URL url = EndToEndTest.class.getResource("resources/golden/types.json");
    String expectedContent = Resources.toString(url, UTF_8);

    String actualContent = new String(readAllBytes(outDir.resolve("types.js")), UTF_8);
    actualContent = actualContent.substring("var TYPES = ".length());
    actualContent = actualContent.substring(0, actualContent.length() - 1);

    Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    @SuppressWarnings("unchecked")
    TreeMap<String, Object> map = gson.fromJson(actualContent, TreeMap.class);
    sortIndexMap(map);

    assertThat(gson.toJson(map).trim()).isEqualTo(expectedContent.trim());
  }

  @SuppressWarnings("unchecked")
  private void sortIndexMap(TreeMap<String, Object> root) {
    root.put("modules", sortTypeList((List<Map<String, Object>>) root.get("modules")));
    root.put("types", sortTypeList((List<Map<String, Object>>) root.get("types")));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> sortTypeList(List<Map<String, Object>> list) {
    return FluentIterable.from(list)
        .transform(new Function<Map<String, Object>, Map<String, Object>>() {
          @Override
          public Map<String, Object> apply(Map<String, Object> item) {
            if (item.containsKey("types")) {
              item.put("types",  sortTypeList((List<Map<String, Object>>) item.get("types")));
            }
            if (item.containsKey("members")) {
              Collections.sort((List<String>) item.get("members"));
            }
            if (item.containsKey("statics")) {
              Collections.sort((List<String>) item.get("statics"));
            }
            return new TreeMap<>(item);
          }
        })
        .toSortedList(new Comparator<Map<String, Object>>() {
          @Override
          public int compare(Map<String, Object> o1, Map<String, Object> o2) {
            String name1 = (String) o1.get("name");
            String name2 = (String) o2.get("name");
            return name1.compareTo(name2);
          }
        });
  }

  private void checkHeader(Document document) throws IOException {
    compareWithGoldenFile(querySelector(document, "header"), "header.html");
  }

  private void checkFooter(Document document) throws IOException {
    Elements elements = querySelectorAll(document, "main ~ footer, main ~ script");
    compareWithGoldenFile(elements, "footer.html");
  }

  private void checkModuleFooter(Document document) throws IOException {
    Elements elements = querySelectorAll(document, "main ~ footer, main ~ script");
    compareWithGoldenFile(elements, "module/example/footer.html");
  }

  private void checkNav(Document document) throws IOException {
    compareWithGoldenFile(querySelectorAll(document, ".dossier-nav"), "nav.html");
  }

  private void checkModuleNav(Document document) throws IOException {
    compareWithGoldenFile(querySelectorAll(document, ".dossier-nav"), "module/example/nav.html");
  }

  private void compareWithGoldenFile(Element element, String goldenPath) throws IOException {
    compareWithGoldenFile(element.toString(), goldenPath);
  }

  private void compareWithGoldenFile(Elements elements, String goldenPath) throws IOException {
    compareWithGoldenFile(elements.toString(), goldenPath);
  }

  private void compareWithGoldenFile(String actual, String goldenPath) throws IOException {
    goldenPath = "resources/golden/" + goldenPath;
    String golden = Resources.toString(EndToEndTest.class.getResource(goldenPath), UTF_8);
    assertEquals(golden.trim(), actual.replace(" \n", "\n").trim());
  }

  private static Document load(Path path) throws IOException {
    String html = toString(path);
    Document document = Jsoup.parse(html);
    document.outputSettings()
        .prettyPrint(true)
        .indentAmount(2);
    return document;
  }

  private static Element querySelector(Document document, String selector) {
    Elements elements = document.select(selector);
    checkState(!elements.isEmpty(),
        "Selector %s not found in %s", selector, document);
    return Iterables.getOnlyElement(elements);
  }

  private static Elements querySelectorAll(Document document, String selector) {
    return document.select(selector);
  }

  private static String toString(Path path) throws IOException {
    return new String(readAllBytes(path), UTF_8);
  }

  private static void writeConfig(Path path, Config config) throws IOException {
    write(path, ImmutableList.of(config.toString()), UTF_8);
  }

  private static void copyResource(String from, Path to) throws IOException {
    createDirectories(to.getParent());

    InputStream resource = EndToEndTest.class.getResourceAsStream(from);
    checkNotNull(resource, "Resource not found: %s", from);
    copy(resource, to, StandardCopyOption.REPLACE_EXISTING);
  }

  private static void assertExists(Path path) {
    assertWithMessage("Expected to exist: " + path).that(Files.exists(path)).isTrue();
  }

  private static class Config {
    private final JsonObject json = new JsonObject();
    private final JsonArray customPages = new JsonArray();
    private final JsonArray moduleFilters = new JsonArray();
    private final JsonArray typeFilters = new JsonArray();
    private final JsonArray sources = new JsonArray();
    private final JsonArray modules = new JsonArray();

    void addCustomPage(String name, Path path) {
      JsonObject spec = new JsonObject();
      spec.addProperty("name", name);
      spec.addProperty("path", path.toString());
      customPages.add(spec);
      json.add("customPages", customPages);
      json.addProperty("moduleNamingConvention", "NODE");
    }

    void addFilter(String name) {
      typeFilters.add(new JsonPrimitive(name));
      json.add("typeFilters", typeFilters);
    }

    void addModuleFilter(String path) {
      moduleFilters.add(new JsonPrimitive(path));
      json.add("moduleFilters", moduleFilters);
    }

    void setOutput(Path path) {
      json.addProperty("output", path.toString());
    }

    void setReadme(Path path) {
      json.addProperty("readme", path.toString());
    }

    void addSource(Path path) {
      sources.add(new JsonPrimitive(path.toString()));
      json.add("sources", sources);
    }

    void addModule(Path path) {
      modules.add(new JsonPrimitive(path.toString()));
      json.add("modules", modules);
    }

    void setLanguage(String language) {
      json.addProperty("language", language);
    }

    @Override
    public String toString() {
      return json.toString();
    }
  }

  private static class Scenario {
    private final String outputPath;

    private Path tmpDir;
    private Path srcDir;
    private Path outDir;
    private Exception initFailure;

    private Scenario(String outputPath) {
      this.outputPath = outputPath;
    }

    @Override
    public String toString() {
      return outputPath;
    }

    public Path init() throws Exception {
      if (outDir != null) {
        return outDir;
      }

      if (initFailure != null) {
        throw initFailure;
      }

      try {
        initFileSystem();
        outDir = generateData();
        return outDir;
      } catch (Exception e) {
        initFailure = e;
        throw e;
      }
    }

    public static boolean useDefaultFileSystem() {
      return Boolean.getBoolean("dossier.e2e.useDefaultFileSystem");
    }

    private void initFileSystem() throws IOException {
      FileSystem fileSystem;
      if (outputPath.endsWith(".zip") || useDefaultFileSystem()) {
        fileSystem = FileSystems.getDefault();

        String baseDir = System.getProperty("dossier.e2e.useDir");
        if (!isNullOrEmpty(baseDir)) {
          tmpDir = fileSystem.getPath(baseDir);
          createDirectories(tmpDir);
        } else {
          tmpDir = fileSystem.getPath(System.getProperty("java.io.tmpdir"));
          tmpDir = createTempDirectory(tmpDir, "dossier.e2e");
        }
      } else {
        fileSystem = Jimfs.newFileSystem();
        tmpDir = fileSystem.getPath("/tmp");
        createDirectories(tmpDir);
      }

      srcDir = tmpDir.resolve("src");

      copyResource("resources/SimpleReadme.md", srcDir.resolve("SimpleReadme.md"));
      copyResource("resources/Custom.md", srcDir.resolve("Custom.md"));
      copyResource("resources/closure_module.js", srcDir.resolve("main/closure_module.js"));
      copyResource("resources/es2015.js", srcDir.resolve("main/es2015.js"));
      copyResource("resources/filter.js", srcDir.resolve("main/filter.js"));
      copyResource("resources/globals.js", srcDir.resolve("main/globals.js"));
      copyResource("resources/json.js", srcDir.resolve("main/json.js"));
      copyResource("resources/inheritance.js", srcDir.resolve("main/inheritance.js"));
      copyResource("resources/registry.js", srcDir.resolve("main/registry.js"));
      copyResource("resources/emptyenum.js", srcDir.resolve("main/subdir/emptyenum.js"));
      copyResource("resources/module/index.js", srcDir.resolve("main/example/index.js"));
      copyResource("resources/module/nested.js", srcDir.resolve("main/example/nested.js"));
      copyResource("resources/module/es6/empty.js", srcDir.resolve("main/example/empty.js"));
      copyResource("resources/module/es6/filter.js", srcDir.resolve("main/example/filter.js"));
      copyResource("resources/module/es6/net.js", srcDir.resolve("main/example/net.js"));
      copyResource("resources/module/worker.js", srcDir.resolve("main/example/worker.js"));
    }

    private Path generateData() throws IOException {
      final Path output = tmpDir.resolveSibling(outputPath);
      System.out.println("Generating output in " + output);

      Path config = createTempFile(tmpDir, "config", ".json");
      writeConfig(config, new Config() {{
        setOutput(output);
        setReadme(srcDir.resolve("SimpleReadme.md"));
        setLanguage("ES6_STRICT");

        addCustomPage("Custom Page", srcDir.resolve("Custom.md"));

        addFilter("^foo.\\w+_.*");
        addFilter("foo.FilteredClass");
        addFilter("foo.bar");

        addModuleFilter(".*/main/example/filter.js$");

        addSource(srcDir.resolve("main/closure_module.js"));
        addSource(srcDir.resolve("main/es2015.js"));
        addSource(srcDir.resolve("main/filter.js"));
        addSource(srcDir.resolve("main/globals.js"));
        addSource(srcDir.resolve("main/json.js"));
        addSource(srcDir.resolve("main/inheritance.js"));
        addSource(srcDir.resolve("main/registry.js"));
        addSource(srcDir.resolve("main/subdir/emptyenum.js"));

        // NB: this is explicitly declared as a normal source input to test that Dossier detects
        // the import statement and registers it as a module.
        addSource(srcDir.resolve("main/example/empty.js"));

        addModule(srcDir.resolve("main/example/filter.js"));
        addModule(srcDir.resolve("main/example/index.js"));
        addModule(srcDir.resolve("main/example/nested.js"));
        addModule(srcDir.resolve("main/example/net.js"));
        addModule(srcDir.resolve("main/example/worker.js"));
      }});

      Main.run(new String[]{"-c", config.toAbsolutePath().toString()}, srcDir.getFileSystem());

      if (output.toString().endsWith(".zip")) {
        FileSystem fs;
        if (output.getFileSystem() == FileSystems.getDefault()) {
          URI uri = URI.create("jar:file:" + output.toAbsolutePath());
          fs = FileSystems.newFileSystem(uri, ImmutableMap.<String, Object>of());
        } else {
          fs = output.getFileSystem()
              .provider()
              .newFileSystem(output, ImmutableMap.<String, Object>of());
        }
        return fs.getPath("/");
      }
      return output;
    }
  }
}
