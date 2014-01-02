package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;

import com.github.jleyba.dossier.proto.Dossier;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.javascript.rhino.Node;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks.
 */
public class DossierModuleRegistry {

  private final Map<Node, DossierModule> scriptToModule = new HashMap<>();
  private final Map<String, DossierModule> nameToModule = new HashMap<>();
  private final Map<Path, DossierModule> pathToModule = new HashMap<>();

  public DossierModule register(Node script) {
    checkArgument(script.isScript());
    checkArgument(script.getSourceFileName() != null);

    DossierModule module = scriptToModule.get(script);
    if (module == null) {
      module = new DossierModule(script);
      scriptToModule.put(script, module);
      nameToModule.put(module.getVarName(), module);
      pathToModule.put(module.getModulePath(), module);
    }
    return module;
  }

  public boolean isModule(Node node) {
    return scriptToModule.containsKey(node);
  }

  public boolean isModuleVar(Scope.Var var) {
    return var.isGlobal() && nameToModule.containsKey(var.getName());
  }
}
