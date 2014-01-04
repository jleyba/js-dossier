package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.Node;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which source files should be treated as CommonJS modules during a compiler run.
 */
public class DossierModuleRegistry {

  private final Set<String> commonJsModulePaths;

  private final Map<Node, DossierModule> scriptToModule = new HashMap<>();
  private final Map<String, DossierModule> nameToModule = new HashMap<>();

  /**
   * Creates a new registry.
   *
   * @param commonJsModulePaths the set of source files that should be treated as a CommonJS
   *     modules. All other source files will be treated as normal JS files.
   */
  public DossierModuleRegistry(Iterable<Path> commonJsModulePaths) {
    this.commonJsModulePaths = FluentIterable.from(commonJsModulePaths)
        .transform(Functions.toStringFunction())
        .toSet();
  }

  /**
   * Returns whether there is a module whose global variable matches the given {@code name}.
   */
  public boolean hasModuleNamed(String name) {
    return nameToModule.containsKey(name);
  }

  /**
   * Returns the module with the given {@code name}.
   *
   * @throws IllegalArgumentException if there is no match module.
   */
  public DossierModule getModuleNamed(String name) {
    checkArgument(hasModuleNamed(name), "No such module: %s", name);
    return nameToModule.get(name);
  }

  /**
   * Returns whether there is a known CommonJS module with the given source file {@code path}.
   */
  boolean hasModuleWithPath(String path) {
    return commonJsModulePaths.contains(path);
  }

  /**
   * Registers the main script node for a CommonJS module, returning a {@link DossierModule}
   * wrapper that may be used to track individual properties of the module.
   */
  DossierModule registerScriptForModule(Node script) {
    checkArgument(script.isScript(), "Not a script node: %s", script);
    checkArgument(hasModuleWithPath(script.getSourceFileName()),
        "Not a CommonJS module: %s", script.getSourceFileName());

    DossierModule module = scriptToModule.get(script);
    if (module == null) {
      module = new DossierModule(script);
      scriptToModule.put(script, module);
      nameToModule.put(module.getVarName(), module);
    } else {
      checkArgument(module.getScriptNode() == script,
          "A script node has already been registered for %s. Existing script: %s, new script: %s",
          module.getScriptNode(), script);
    }
    return module;
  }

  public Iterable<DossierModule> getModules() {
    return Iterables.unmodifiableIterable(scriptToModule.values());
  }
}
