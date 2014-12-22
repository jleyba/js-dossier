package com.github.jleyba.dossier;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.rhino.Node;

/**
 * A compiler pass that collects all of the provided symbols in the input sources.
 */
class ProvidedSymbolsCollectionPass implements CompilerPass {

  private final TypeRegistry typeRegistry;
  private final AbstractCompiler compiler;

  ProvidedSymbolsCollectionPass(AbstractCompiler compiler, TypeRegistry typeRegistry) {
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
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
            typeRegistry.recordGoogProvide(name);
          }
        }
      }
    });
  }
}
