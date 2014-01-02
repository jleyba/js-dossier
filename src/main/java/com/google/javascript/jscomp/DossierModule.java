package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.javascript.rhino.Node;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

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
  private final Set<Scope.Var> internalVars = new HashSet<>();

  /**
   * Creates a new module descriptor.
   *
   * @param script the SCRIPT node for the module.
   */
  public DossierModule(Node script) {
    checkArgument(script.isScript());
    checkArgument(script.getSourceFileName() != null);

    this.scriptNode = script;
    this.modulePath = FileSystems.getDefault().getPath(
        script.getSourceFileName());
    this.varName = guessModuleName(scriptNode.getSourceFileName());
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

  public Iterable<Scope.Var> getInternalVars() {
    return Iterables.unmodifiableIterable(internalVars);
  }

  public boolean isInternalVar(Scope.Var var) {
    return internalVars.contains(var);
  }

  public boolean registerInternalVar(Scope.Var var) {
    checkArgument(var.isGlobal(), "Not a global var: %s", var.getName());
    if (isInternalVar(var)) {
      return true;
    }

    Node script = getScriptNode(var.getNameNode());
    if (script == this.scriptNode) {
      String name = var.getName();
      var.getNameNode().putProp(Node.ORIGINALNAME_PROP, name);
      var.getNameNode().setString(name + "$$_" + varName);
      internalVars.add(var);
    }
    return false;
  }

  @Nullable
  private static Node getScriptNode(Node node) {
    while (node != null && !node.isScript()) {
      node = node.getParent();
    }
    return node;
  }

  public static String guessModuleName(String sourceName) {
    Path path = FileSystems.getDefault().getPath(sourceName);
    return guessModuleName(path);
  }

  public static String guessModuleName(Path modulePath) {
    String baseName = Files.getNameWithoutExtension(modulePath.getFileName().toString());
    Path pseudoPath = modulePath.resolveSibling(baseName);
    String name = PREFIX +
        (modulePath.isAbsolute() ? PATH_SEPARATOR : "") +
        Joiner.on(PATH_SEPARATOR).join(pseudoPath.iterator());
    return name.replace('-', '_');
  }
}
