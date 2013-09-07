package com.github.jleyba.dossier;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Set;

class DocPass  implements CompilerPass {

  private final Config config;
  private final AbstractCompiler compiler;
  private final DocWriterFactory writerFactory;

  private final DocRegistry docRegistry = new DocRegistry();

  DocPass(Config config, AbstractCompiler compiler) {
    this.compiler = compiler;
    this.config = config;

    LinkResolver linkResolver = new LinkResolver(config.outputDir, docRegistry);
    this.writerFactory = new DocWriterFactory(linkResolver);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, new ExternCollector());
    NodeTraversal.traverse(compiler, root, new TypeCollector());

    DocWriter writer = writerFactory.createDocWriter(config, docRegistry, null);
    try {
      writer.copySourceFiles();
      copyTypes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void copyTypes() throws IOException {
    for (Descriptor descriptor : docRegistry.getTypes()) {
      writerFactory.createDocWriter(config, docRegistry, descriptor)
          .generateDocs(compiler.getTypeRegistry());
    }
  }

//
//  /**
//   * Traverses the AST, collecting {@code {@literal @}fileoverview} and
//   * {@code {@literal @}license} information.
//   */
//  private class FileOverviewCollector extends NodeTraversal.AbstractShallowStatementCallback {
//
//    @Override
//    public void visit(NodeTraversal nodeTraversal, Node node, Node parent) {
//      JSDocInfo info = node.getJSDocInfo();
//      if (node.isScript() && node.getSourceFileName() != null
//          && info != null && info.getFileOverview() != null) {
//        File file = new File(node.getSourceFileName());
////        docRoot.addFileNamespace(new FileNamespace(file, info));
//      }
//    }
//  }

  /**
   * Traverses the root of the extern tree to gather all external type definitions.
   */
  private class ExternCollector implements NodeTraversal.ScopedCallback {

    @Override
    public void enterScope(NodeTraversal t) {
      Scope scope = t.getScope();
      for (Scope.Var var : scope.getAllSymbols()) {
        docRegistry.addExtern(new Descriptor(var.getName(), var.getType(), var.getJSDocInfo()));
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {}

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return false;
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {}
  }

  /**
   * Traverses the object graph collecting all type definitions.
   */
  private class TypeCollector implements NodeTraversal.ScopedCallback {

    @Override
    public void enterScope(NodeTraversal t) {
      JSTypeRegistry registry = t.getCompiler().getTypeRegistry();

      Scope scope = t.getScope();
      for (Scope.Var var : scope.getAllSymbols()) {
        String name = var.getName();
        if (docRegistry.isExtern(name)) {
          continue;
        }

        JSDocInfo info = var.getJSDocInfo();
        JSType type = var.getType();
        if (null == type) {
          type = registry.getType(name);
          if (null == type && null != info && null != info.getType()) {
            type = info.getType().evaluate(scope, registry);
          }

          if (null == type && null != var.getInitialValue()) {
            type = var.getInitialValue().getJSType();
          }
        }

        if (null == info && null != type) {
          info = type.getJSDocInfo();
        }

        Descriptor descriptor = new Descriptor(name, type, info, null);
        traverseType(descriptor, registry);
      }
    }

    @Override public void exitScope(NodeTraversal t) {}
    @Override public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) { return false; }
    @Override public void visit(NodeTraversal t, Node n, Node parent) {}

    private void traverseType(Descriptor descriptor, JSTypeRegistry registry) {
      // TODO(jleyba): Convert JSTypeExprssion back into original string.
      JSType type = descriptor.getType();
      if (null == type || !type.isObject() || type.isGlobalThisType()) {
        return;
      }

      if (descriptor.isConstructor()
          || descriptor.isInterface()
          || descriptor.isEnum()) {

        // This descriptor might be an alias for another type, so avoid documenting it twice:
        // TODO(jleyba): do we really want to do this? Should we just alias it?
        // \** @constructor */
        // var Foo = function() {};
        // var FooAlias = Foo;
        if (registry.getType(descriptor.getFullName()) == null) {
          System.out.println("SKIPPING " + descriptor.getFullName());
          return;
        }

        System.out.println("Found type: " + descriptor.getFullName());
        docRegistry.addType(descriptor);

      } else if (!descriptor.isObject() && !descriptor.isFunction()) {
        return;
      }

      System.out.println("------- scanning " + descriptor.getFullName());
      ObjectType obj = descriptor.toObjectType();
      for (String prop : obj.getOwnPropertyNames()) {
        Node node = obj.getPropertyNode(prop);
        if (null == node) {
          continue;
        }

        JSDocInfo info = node.getJSDocInfo();
        if (null == info && null != node.getParent() && node.getParent().isAssign()) {
          info = node.getParent().getJSDocInfo();
        }

        // Sometimes the JSCompiler picks up the builtin call and apply functions off of a
        // function object.  We should always skip these.
        if (type.isFunctionType() && ("apply".equals(prop) || "call".equals(prop))) {
          continue;
        }

        // We're building an index of types, so do not traverse prototypes or enum values.
        JSType propType = obj.getPropertyType(prop);
        if (propType.isFunctionPrototypeType() || propType.isEnumElementType()) {
//          System.err.println("Skipping proto/enum value " + descriptor.getFullName() + "." + prop);
          continue;
        }

        // Don't bother collecting type info from properties that are new instances of other types.
        if (node.isGetProp()
            && node.getParent() != null
            && node.getParent().isAssign()
            && node.getNext() != null
            && node.getNext().isNew()) {
//          System.err.println("Skipping new " + descriptor.getFullName() + "." + prop);
          continue;
        }

        Descriptor propDescriptor = new Descriptor(prop, propType, info, descriptor);
        traverseType(propDescriptor, registry);
        if (propDescriptor.isFunction()
            || propDescriptor.isNamespace()
            || docRegistry.isKnownType(propDescriptor.getFullName())) {
          descriptor.setIsNamespace(true);
        }
      }

      if (!docRegistry.isKnownType(descriptor.getFullName()) && descriptor.isNamespace()) {
        System.out.println("Found namespace: " + descriptor.getFullName());
        docRegistry.addType(descriptor);
      }
//
//      Iterable<Descriptor> children = descriptor.getChildren();
//      if (children.iterator().hasNext()) {
//        System.out.println("___" + descriptor.getFullName());
//        for (Descriptor child : children) {
//          System.out.println("______" + child.getFullName());
//        }
//      }
    }
  }

}
