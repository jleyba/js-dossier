package com.google.javascript.jscomp;

import static org.junit.Assert.assertEquals;

import com.google.common.jimfs.Jimfs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;

/**
 * Tests for {@link DossierModule}.
 */
@RunWith(JUnit4.class)
public class DossierModuleTest {

  private final FileSystem fileSystem = Jimfs.newFileSystem();

  @Test
  public void canGuessModuleName() {
    assertEquals("dossier$$module__foo", guessModuleName("foo"));
    assertEquals("dossier$$module__foo$bar", guessModuleName("foo/bar"));
    assertEquals("dossier$$module__foo$bar$baz", guessModuleName("foo/bar/baz.js"));
    assertEquals("dossier$$module__$absolute$path$file", guessModuleName("/absolute/path/file.js"));
    assertEquals("dossier$$module__$absolute$path", guessModuleName("/absolute/path/index.js"));
    assertEquals("dossier$$module__foo", guessModuleName("foo/index"));
    assertEquals("dossier$$module__index", guessModuleName("index"));
  }

  private String guessModuleName(String path) {
    return DossierModule.guessModuleName(fileSystem.getPath(path));
  }
}
