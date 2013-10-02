// Copyright 2013 Jason Leyba
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.github.jleyba.dossier;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

class DocPass  implements CompilerPass {

  private final AbstractCompiler compiler;
  private final DocRegistry docRegistry;

  DocPass(AbstractCompiler compiler, DocRegistry docRegistry) {
    this.compiler = compiler;
    this.docRegistry = docRegistry;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, new ExternCollector());
    NodeTraversal.traverse(compiler, root, new TypeCollector());
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
          return;
        }

        docRegistry.addType(descriptor);

      } else if (!descriptor.isObject() && !descriptor.isFunction()) {
        return;
      }

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
          continue;
        }

        // Don't bother collecting type info from properties that are new instances of other types.
        if (node.isGetProp()
            && node.getParent() != null
            && node.getParent().isAssign()
            && node.getNext() != null
            && node.getNext().isNew()) {
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

      if (!docRegistry.isKnownType(descriptor.getFullName())
          && (registry.hasNamespace(descriptor.getFullName()) || descriptor.isNamespace())) {
        docRegistry.addType(descriptor);
      }
    }
  }

}
