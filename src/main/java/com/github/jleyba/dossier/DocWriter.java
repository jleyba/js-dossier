package com.github.jleyba.dossier;

import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates documentation for {@link Descriptor} objects.
 */
interface DocWriter {

  void generateDocs(JSTypeRegistry registry) throws IOException;
  void copySourceFiles() throws IOException;
}
