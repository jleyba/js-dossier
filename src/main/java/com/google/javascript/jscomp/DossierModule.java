package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Describes a CommonJS module.
 */
public class DossierModule {

  private static final String PATH_SEPARATOR = "$";
  private static final String PREFIX = "dossier$$module__";

  private final Node scriptNode;
  private final Path modulePath;
  private final String varName;

  private final Map<String, JSDocInfo> exportedFunctionDocs = new HashMap<>();

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

  /**
   * Saves a reference to the JSDocs for one of this module's internal functions that is exported
   * as part of its public API without extra documentation:
   *
   *     /** Comment here. *\
   *     function foo() {}
   *     exports.foo = foo;
   *
   * @param name the function name.
   * @param docs the function's JS docs.
   */
  public void addExportedFunctionDocs(String name, JSDocInfo docs) {
    checkArgument(!exportedFunctionDocs.containsKey(name),
        "Function already registered in module %s: %s", modulePath, name);
    exportedFunctionDocs.put(name, docs);
  }

  @Nullable
  public JSDocInfo getFunctionDocs(String name) {
    return exportedFunctionDocs.get(name);
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
