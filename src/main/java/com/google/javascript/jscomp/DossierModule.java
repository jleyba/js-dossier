package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.javascript.rhino.Node;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
  private final Map<String, String> typeToAlias = new HashMap<>();
  private final Map<String, String> aliasToType = new HashMap<>();
  private final Set<Scope.Var> internalVars = new HashSet<>();

  /**
   * Creates a new module descriptor.
   *
   * @param script the SCRIPT node for the module.
   */
  DossierModule(Node script) {
    checkArgument(script.isScript());
    checkArgument(script.getSourceFileName() != null);

    this.scriptNode = script;
    this.modulePath = FileSystems.getDefault().getPath(
        script.getSourceFileName());
    this.varName = guessModuleName(scriptNode.getSourceFileName());

    // Define automatic aliases.
    this.typeToAlias.put("module", varName);
    this.typeToAlias.put("exports", varName + ".exports");
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

  /**
   * Registers a global variable under this module's script node as internal to the module
   * itself. These variables should be not documented.
   */
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
      return true;
    }
    return false;
  }

  /**
   * Registers a type aliased within this module. An alias is defined when a global type
   * is assigned to a module's internal variable: {@code var Foo = global.Foo}, or when
   * a type exported by another module is assigned to a module's internal variable:
   * {@code var Foo = require('othermodule').Foo}.
   *
   * @param name the original type name.
   * @param alias the alias name.
   */
  public void defineAlias(String name, String alias) {
    typeToAlias.put(checkNotNull(name, "null name"), checkNotNull(alias, "null alias"));
    aliasToType.put(alias, name);
  }

  /**
   * Returns whether this module defines an alias for the given type {@code name}.
   */
  public boolean hasAlias(String name) {
    return typeToAlias.containsKey(name);
  }

  /**
   * Returns this module's alias for the given type. If this module does not define an alias
   * for the type, this method will return the type itself.
   */
  public String getAlias(String typeName) {
    return typeToAlias.containsKey(typeName)
        ? typeToAlias.get(typeName)
        : typeName;
  }

  /**
   * Resolves an alias defined when this module. If the given name is <i>not</i> an alias,
   * this method will trivially return that name.
   */
  public String resolveAlias(String alias) {
    while (aliasToType.containsKey(alias)) {
      alias = aliasToType.get(alias);
    }
    return alias;
  }

  @Nullable
  private static Node getScriptNode(Node node) {
    while (node != null && !node.isScript()) {
      node = node.getParent();
    }
    return node;
  }

  /**
   * Guesses the name of the global variable to use with Closure's type system for a module
   * with the given source path.
   */
  static String guessModuleName(String sourceName) {
    Path path = FileSystems.getDefault().getPath(sourceName);
    return guessModuleName(path);
  }

  /**
   * Guesses the name of the global variable to use with Closure's type system for a module
   * with the given source path.
   */
  static String guessModuleName(Path modulePath) {
    String baseName = Files.getNameWithoutExtension(modulePath.getFileName().toString());
    Path pseudoPath = modulePath.resolveSibling(baseName);
    if (modulePath.getFileName().toString().equals("index.js")
        && pseudoPath.getParent() != null) {
      pseudoPath = pseudoPath.getParent();
    }
    String name = PREFIX +
        (modulePath.isAbsolute() ? PATH_SEPARATOR : "") +
        Joiner.on(PATH_SEPARATOR).join(pseudoPath.iterator());
    return name.replace('-', '_');
  }
}
