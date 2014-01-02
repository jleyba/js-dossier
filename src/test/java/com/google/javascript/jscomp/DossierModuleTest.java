package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.DossierModule.guessModuleName;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DossierModule}.
 */
@RunWith(JUnit4.class)
public class DossierModuleTest {

  @Test
  public void canGuessModuleName() {
    assertEquals("dossier$$module__foo", guessModuleName("foo"));
    assertEquals("dossier$$module__foo$bar", guessModuleName("foo/bar"));
    assertEquals("dossier$$module__foo$bar$baz", guessModuleName("foo/bar/baz.js"));
    assertEquals("dossier$$module__$absolute$path$file", guessModuleName("/absolute/path/file.js"));
    assertEquals("dossier$$module__$absolute$path$index", guessModuleName("/absolute/path/index.js"));
    assertEquals("dossier$$module__foo$index", guessModuleName("foo/index"));
    assertEquals("dossier$$module__index", guessModuleName("index"));
  }
}
