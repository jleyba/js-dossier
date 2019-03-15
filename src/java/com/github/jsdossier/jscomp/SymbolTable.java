/*
Copyright 2013-2018 Jason Leyba

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.github.jsdossier.jscomp;

import static com.github.jsdossier.jscomp.Nodes.getGoogScopeBlock;
import static com.github.jsdossier.jscomp.Nodes.isGoogScopeCall;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.unmodifiableCollection;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Range;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.logging.Logger;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Tracks symbols found in source code before any name changes applied by the compiler. This is used
 * to resolve type references in JSDoc comments to properly link to the type tracked by the compiler
 */
public final class SymbolTable implements StaticScope {

  private static final Logger log = Logger.getLogger(SymbolTable.class.getName());

  @Nullable private final SymbolTable parent;

  @Nullable private final Node root;

  private final Map<String, Module> modulesById;
  private final Map<Path, Module> modulesByPath;
  private final Map<String, Module> closureModulesById;
  private final Map<Node, Module> modulesByRoot;
  private final ListMultimap<String, ScopedRegion> regions;

  private final Map<String, Symbol> symbols = new HashMap<>();

  private SymbolTable(@Nullable SymbolTable parent, @Nullable Node root) {
    checkArgument(
        (parent == null) == (root == null),
        "symbol table must have a root node IFF it is not global");

    this.parent = parent;
    this.root = root;

    if (this.parent == null) {
      modulesByPath = new HashMap<>();
      modulesById = new HashMap<>();
      modulesByRoot = new HashMap<>();
      closureModulesById = new HashMap<>();
      regions = MultimapBuilder.hashKeys().arrayListValues().build();
    } else {
      modulesByPath = ImmutableMap.of();
      modulesById = ImmutableMap.of();
      modulesByRoot = ImmutableMap.of();
      closureModulesById = ImmutableMap.of();
      regions = ImmutableListMultimap.of();
    }
  }

  public static SymbolTable createGlobalSymbolTable() {
    return new SymbolTable(null, null);
  }

  SymbolTable newChildTable(Node root) {
    checkState(getParentScope() == null, "only global symbol table may create child tables");
    return new SymbolTable(this, root);
  }

  SymbolTable newGoogScopeTable(Node root) {
    checkArgument(isGoogScopeCall(root), "not a goog.scope call: %s", root);
    checkState(
        getParentScope() == null, "only the global symbol table may contain goog.scope tables");

    SymbolTable st = newChildTable(root);
    add(ScopedRegion.forGoogScope(root, st));
    return st;
  }

  @VisibleForTesting
  SymbolTable findTableFor(Node node) {
    Position pos = Position.of(node);
    for (ScopedRegion region : regions.get(node.getSourceFileName())) {
      if (region.getRange().contains(pos)) {
        return region.getSymbolTable();
      }
    }
    SymbolTable root = this;
    while (root.getParentScope() != null) {
      root = root.getParentScope();
    }
    return root;
  }

  private void add(ScopedRegion region) {
    checkState(getParentScope() == null, "only the global scope may track regions");
    List<ScopedRegion> list = regions.get(region.getPath());
    if (list.isEmpty()) {
      list.add(region);
    } else {
      checkState(list instanceof RandomAccess); // Check assumptions about performance.
      ScopedRegion last = list.get(list.size() - 1);
      checkArgument(
          last.getRange().intersection(region.getRange()).isEmpty(),
          "Unexpected region intersection for %s: first=%s second=%s",
          region.getPath(),
          last.getRange(),
          region.getRange());
      list.add(region);
    }
  }

  @Override
  @Nullable
  public Node getRootNode() {
    return root;
  }

  @Override
  @Nullable
  public SymbolTable getParentScope() {
    return parent;
  }

  @Override
  @Nullable
  public Symbol getSlot(String name) {
    for (SymbolTable table = this; table != null; table = table.getParentScope()) {
      Symbol symbol = table.getOwnSlot(name);
      if (symbol != null) {
        return symbol;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Symbol getOwnSlot(String name) {
    return symbols.get(name);
  }

  void add(Symbol symbol) {
    Symbol prev = symbols.get(symbol.getName());
    if (prev == null) {
      log.info("recording " + symbol);
      symbol.setScope(this);
      symbols.put(symbol.getName(), symbol);

    } else if (prev.isGoogProvideOnly()
        && !symbol.isGoogProvide()
        && prev.getFile().equals(symbol.getFile())) {
      log.info("updating " + symbol + " for non-provide");
      symbol =
          prev.toBuilder()
              .setGoogProvideOnly(false)
              .setFile(symbol.getFile())
              .setPosition(symbol.getPosition())
              .build();
      symbol.setScope(this);
      symbols.put(symbol.getName(), symbol);

    } else {
      // Don't log an error for stuff like:
      //     goog.module('Foo');
      //     class Foo {}
      //     exports = Foo;
      if (symbol.isModuleExports() != prev.isModuleExports()
          && prev.getFile().equals(symbol.getFile())) {
        return;
      }

      // Special case goog.module from the closure library (only impacts logging, not behavior).
      if ("goog.module".equals(symbol.getName())
          && prev.getFile().endsWith("goog/module/module.js")
          && symbol.getFile().endsWith("goog/base.js")) {
        return;
      }

      log.severe(
          "not recording duplicate symbol: "
              + symbol
              + ";\nPreviously recorded at "
              + prev.getFile()
              + "@"
              + prev.getPosition()
              + ";\nNew record at          "
              + symbol.getFile()
              + "@"
              + symbol.getPosition());
    }
  }

  void replace(Symbol symbol) {
    checkState(getParentScope() == null, "may only replace symbols in global symbol table");
    symbol.setScope(this);
    symbols.put(symbol.getName(), symbol);
  }

  void add(Module module) {
    checkState(parent == null, "Only the global symbol table may have modules");
    modulesById.put(module.getId().getCompiledName(), module);
    modulesByPath.put(module.getPath(), module);
    modulesByRoot.put(module.getInternalSymbolTable().getRootNode(), module);
    if (module.isClosure()) {
      closureModulesById.put(module.getId().getOriginalName(), module);
    }
    add(ScopedRegion.forModule(module));
    add(Symbol.forExports(module));
  }

  @Nullable
  @CheckReturnValue
  public Module getClosureModuleById(String id) {
    return closureModulesById.get(id);
  }

  @Nullable
  @CheckReturnValue
  public Module getModuleById(String id) {
    return modulesById.get(id);
  }

  @Nullable
  @CheckReturnValue
  public Module getModule(Path path) {
    return modulesByPath.get(path);
  }

  public boolean isModule(Path path) {
    return modulesByPath.containsKey(path);
  }

  /** Returns the module associated with the given root node. */
  @Nullable
  public Module getModule(Node node) {
    return modulesByRoot.get(node);
  }

  public Collection<Module> getAllModules() {
    return unmodifiableCollection(modulesByPath.values());
  }

  public Collection<Symbol> getAllSymbols() {
    return unmodifiableCollection(symbols.values());
  }

  /** Returns whether this table contains symbols for a {@code goog.scope()} block. */
  boolean isGoogScope() {
    return isGoogScopeCall(root);
  }

  /** Defines a scope region in source code that has its own symbol table. */
  @AutoValue
  abstract static class ScopedRegion {
    ScopedRegion() {}

    private static ScopedRegion forModule(Module m) {
      return new AutoValue_SymbolTable_ScopedRegion(
          m.getPath().toString(), Range.all(), m.getInternalSymbolTable());
    }

    private static ScopedRegion forGoogScope(Node root, SymbolTable table) {
      Node endNode = root.getNext();
      if (endNode == null && root.getParent() != null) {
        endNode = root.getParent().getNext();
      }
      Position start = Position.of(getGoogScopeBlock(root));
      Range<Position> range =
          endNode == null ? Range.atLeast(start) : Range.closed(start, Position.of(endNode));
      return new AutoValue_SymbolTable_ScopedRegion(root.getSourceFileName(), range, table);
    }

    /** Returns the path to the file defines this region. */
    public abstract String getPath();

    /** Returns the bounded region in the file that defines the region. */
    public abstract Range<Position> getRange();

    /** Returns the symbol table for this region. */
    public abstract SymbolTable getSymbolTable();
  }
}
