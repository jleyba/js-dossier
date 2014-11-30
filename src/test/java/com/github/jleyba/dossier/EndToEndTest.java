package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.write;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class EndToEndTest {

  private static FileSystem fileSystem;
  private static Path tmpDir;
  private static Path srcDir;
  private static Path outDir;

  @Rule public TestName name = new TestName();

  @BeforeClass
  public static void setUpOnce() throws IOException {
    fileSystem = FileSystems.getDefault();

    tmpDir = fileSystem.getPath(System.getProperty("java.io.tmpdir"));
    tmpDir = createTempDirectory(tmpDir, "dossier.e2e");
    srcDir = tmpDir.resolve("src");
    outDir = tmpDir.resolve("out");

    System.out.println("Generating output in " + outDir);
    createDirectories(outDir);
    createDirectories(srcDir.resolve("main"));

    copyResource("resources/SimpleReadme.md", srcDir.resolve("SimpleReadme.md"));
    copyResource("resources/globals.js", srcDir.resolve("main/globals.js"));
    copyResource("resources/json.js", srcDir.resolve("main/json.js"));

    Path config = createTempFile(tmpDir, "config", ".json");
    writeConfig(config, new Config() {{
      setOutput(outDir);
      setReadme(srcDir.resolve("SimpleReadme.md"));
      addSource(srcDir.resolve("main/globals.js"));
      addSource(srcDir.resolve("main/json.js"));
    }});

    Main.main(new String[]{"-c", config.toAbsolutePath().toString()});
  }

  @Test
  public void checkMarkdownIndexProcessing() throws IOException {
    Document document = load(outDir.resolve("index.html"));
    compareWithGoldenFile(querySelector(document, "article.indexfile"), "index.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkGlobalClass() throws IOException {
    Document document = load(outDir.resolve("class_GlobalCtor.html"));
    compareWithGoldenFile(querySelector(document, "article"), "class_GlobalCtor.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkDeprecatedClass() throws IOException {
    Document document = load(outDir.resolve("class_DeprecatedFoo.html"));
    compareWithGoldenFile(querySelector(document, "article"), "class_DeprecatedFoo.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  @Test
  public void checkFunctionNamespace() throws IOException {
    Document document = load(outDir.resolve("namespace_sample_json.html"));
    compareWithGoldenFile(querySelector(document, "article"), "namespace_sample_json.html");
    checkHeader(document);
    checkNav(document);
    checkFooter(document);
  }

  private void checkHeader(Document document) throws IOException {
    compareWithGoldenFile(querySelector(document, "header"), "header.html");
  }

  private void checkFooter(Document document) throws IOException {
    compareWithGoldenFile(querySelectorAll(document, "main ~ *"), "footer.html");
  }

  private void checkNav(Document document) throws IOException {
    compareWithGoldenFile(querySelectorAll(document, "nav"), "nav.html");
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
    assertEquals(golden.trim(), actual.trim());
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
    InputStream resource = EndToEndTest.class.getResourceAsStream(from);
    checkNotNull(resource, "Resource not found: %s", from);
    copy(resource, to);
  }

  private static class Config {
    private final JsonObject json = new JsonObject();
    private final JsonArray sources = new JsonArray();

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

    @Override
    public String toString() {
      return json.toString();
    }
  }
}
