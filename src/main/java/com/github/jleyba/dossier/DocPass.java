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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A {@link CompilerPass} that collects the symbols the symbols to generate documentation for.
 */
class DocPass  implements CompilerPass {

  private final AbstractCompiler compiler;
  private final DocRegistry docRegistry;
  private final Set<String> providedSymbols;

  DocPass(AbstractCompiler compiler, DocRegistry docRegistry, Set<String> providedSymbols) {
    this.compiler = compiler;
    this.docRegistry = docRegistry;
    this.providedSymbols = providedSymbols;
  }

  @Override
  public void process(Node externs, Node root) {
    if (compiler.getErrorManager().getErrorCount() > 0) {
      return;
    }
    NodeTraversal.traverse(compiler, externs, new ExternCollector());
    NodeTraversal.traverse(compiler, root, new TypeCollector());
  }

  @Nullable
  private static JSType getJSType(Scope scope, Scope.Var var, JSTypeRegistry registry) {
    @Nullable JSType type = var.getType();
    if (null == type) {
      JSDocInfo info = var.getJSDocInfo();
      type = registry.getType(var.getName());
      if (null == type && null != info && null != info.getType()) {
        type = info.getType().evaluate(scope, registry);
      }

      if (null == type && null != var.getInitialValue()) {
        type = var.getInitialValue().getJSType();
      }
    }
    return type;
  }

  @Nullable
  private static JSDocInfo getJSDocInfo(Scope.Var var, @Nullable JSType type) {
    @Nullable JSDocInfo info = var.getJSDocInfo();
    if (null == info && null != type) {
      info = type.getJSDocInfo();
    }
    return info;
  }

  /**
   * Traverses the root of the extern tree to gather all external type definitions.
   */
  private class ExternCollector implements NodeTraversal.ScopedCallback {

    @Override
    public void enterScope(NodeTraversal t) {
      Scope scope = t.getScope();
      for (Scope.Var var : scope.getAllSymbols()) {
        @Nullable JSType type = getJSType(scope, var, t.getCompiler().getTypeRegistry());
        @Nullable JSDocInfo info = getJSDocInfo(var, type);
        docRegistry.addExtern(new Descriptor(var.getName(), type, info));
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
    @Override public boolean shouldTraverse(NodeTraversal t, Node n, @Nullable Node parent) {
      if (null == parent && n.isBlock()) {
        return true;
      }

      if (n.isScript() && null != parent && parent.isBlock()) {
        if (null != n.getJSDocInfo()) {
          Path path = FileSystems.getDefault().getPath(n.getSourceFileName());
          String comment = CommentUtil.getMarkerDescription(n.getJSDocInfo(), "fileoverview");
          docRegistry.addFileOverview(path, comment);
        }
      }
      return false;
    }

    @Override public void exitScope(NodeTraversal t) {}
    @Override public void visit(NodeTraversal t, Node n, Node parent) {}

    @Override
    public void enterScope(NodeTraversal t) {
      JSTypeRegistry registry = t.getCompiler().getTypeRegistry();

      Scope scope = t.getScope();
      for (Scope.Var var : scope.getAllSymbols()) {
        String name = var.getName();
        if (docRegistry.isExtern(name)) {
          continue;
        }

        @Nullable JSType type = getJSType(scope, var, registry);
        @Nullable JSDocInfo info = getJSDocInfo(var, type);

        if (null == type || (null == info && !type.isFunctionType())) {
          continue;
        }

        Descriptor descriptor = new Descriptor(name, type, info);
        traverseType(descriptor, registry);
      }
    }

    private void traverseType(Descriptor descriptor, JSTypeRegistry registry) {
      JSType type = checkNotNull(descriptor.getType(), "Null type: %s", descriptor.getFullName());
      ObjectType obj = ObjectType.cast(type);
      if (obj == null) {
        return;
      }

      docRegistry.addType(descriptor);
      for (String prop : obj.getOwnPropertyNames()) {
        String propName = descriptor.getFullName() + "." + prop;
        if (shouldSkipProperty(obj, prop)) {
          continue;
        }

        JSType propType = obj.getPropertyType(prop);
        JSDocInfo propInfo  = obj.getOwnPropertyJSDocInfo(prop);

        if (registry.hasNamespace(propName)
            || isDocumentableType(propInfo)
            || isDocumentableType(propType)
            || isProvidedSymbol(propName)) {
          traverseType(new Descriptor(propName, propType, propInfo), registry);
        }
      }
    }
  }

  private static boolean isDocumentableType(JSDocInfo info) {
    return info != null && (info.isConstructor() || info.isInterface()
        || info.getEnumParameterType() != null);
  }

  private static boolean isDocumentableType(JSType type) {
    return type != null
        && (type.isConstructor() || type.isInterface() || type.isEnumType());
  }

  private boolean shouldSkipProperty(ObjectType object, String prop) {
    // Skip node-less properties and properties that are just new instances of other types.
    Node node = object.getPropertyNode(prop);
    if (node == null
        || (node.isGetProp()
        && node.getParent() != null
        && node.getParent().isAssign()
        && node.getNext() != null
        && node.getNext().isNew())) {
      return true;
    }

    // Skip unknown types. Also skip prototypes and enum values (this info is collected
    // separately).
    JSType propType = object.getPropertyType(prop);
    if (propType == null || propType.isFunctionPrototypeType() || propType.isEnumElementType()) {
      return true;
    }

    // Sometimes the JSCompiler picks up the builtin call and apply functions off of a
    // function object.  We should always skip these.
    if (object.isFunctionType() && propType.isFunctionType()
        && ("apply".equals(prop) || "bind".equals(prop) || "call".equals(prop))) {
      return true;
    }

    return propType.isGlobalThisType() || isPrimitive(propType);
  }

  private boolean isProvidedSymbol(String name) {
    return providedSymbols.contains(name);
  }

  private static boolean isPrimitive(JSType type) {
    return type.isBooleanValueType()
        || type.isBooleanObjectType()
        || type.isNumber()
        || type.isNumberValueType()
        || type.isNumberObjectType()
        || type.isString()
        || type.isStringObjectType()
        || type.isStringValueType()
        || type.isArrayType();
  }
}
