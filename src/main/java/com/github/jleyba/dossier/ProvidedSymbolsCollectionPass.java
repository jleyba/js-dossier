package com.github.jleyba.dossier;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * A compiler pass that collects all of the provided symbols in the input sources.
 */
class ProvidedSymbolsCollectionPass implements CompilerPass {

  private final Set<String> symbols = new HashSet<>();

  private final AbstractCompiler compiler;

  ProvidedSymbolsCollectionPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new NodeTraversal.AbstractShallowCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        CodingConvention convention = compiler.getCodingConvention();
        if (n.isCall()) {
          String name = convention.extractClassNameIfProvide(n, parent);
          if (name != null) {
            symbols.add(name);
            while (name.indexOf('.') > 0) {
              name = name.substring(0, name.lastIndexOf('.'));
              symbols.add(name);
            }
          }
        }
      }
    });
  }

  public Set<String> getSymbols() {
    return symbols;
  }
}
