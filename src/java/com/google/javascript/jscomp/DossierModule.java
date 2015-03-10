package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.javascript.rhino.Node;

import java.nio.file.Path;

/**
 * Describes a CommonJS module.
 */
public class DossierModule {

  private static final String PATH_SEPARATOR = "$";
  private static final String PREFIX = "dossier$$module__";
  private static final String EXTERN_PREFIX = "dossier$$extern__";

  private final Node scriptNode;
  private final Path modulePath;
  private final String varName;

  /**
   * Creates a new module descriptor.
   *
   * @param script the SCRIPT node for the module.
   * @param modulePath path to this module's source file.
   */
  public DossierModule(Node script, Path modulePath) {
    checkArgument(script.isScript());
    checkArgument(script.getSourceFileName() != null);

    this.scriptNode = script;
    this.modulePath = modulePath;
    this.varName = guessModuleName(modulePath);
  }

  public Node getScriptNode() {
    return scriptNode;
  }

  public String getVarName() {
    return varName;
  }

  public Path getModulePath() {
    return modulePath;
  }

  public static boolean isExternModule(String name) {
    return name.startsWith(EXTERN_PREFIX);
  }

  public static String externToOriginalName(String name) {
    checkArgument(isExternModule(name));
    return name.substring(EXTERN_PREFIX.length());
  }

  /**
   * Mangles an extern module's ID so it may be used as a global variable with Closure's type
   * system.
   */
  static String externModuleName(String id) {
    return EXTERN_PREFIX + id;
  }

  /**
   * Guesses the name of the global variable to use with Closure's type system for a module
   * with the given source path.
   */
  static String guessModuleName(Path modulePath) {
    String baseName = Files.getNameWithoutExtension(modulePath.getFileName().toString());
    Path pseudoPath = modulePath.resolveSibling(baseName);
    String fileName = modulePath.getFileName().toString();
    if ((fileName.equals("index.js") || fileName.equals("index"))
        && pseudoPath.getParent() != null) {
      pseudoPath = pseudoPath.getParent();
    }
    String name = PREFIX +
        (modulePath.isAbsolute() ? PATH_SEPARATOR : "") +
        Joiner.on(PATH_SEPARATOR).join(pseudoPath.iterator());
    return name.replace('-', '_');
  }
}
