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

import static com.github.jleyba.dossier.Descriptor.isTheObjectType;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DossierCompiler;
import com.google.javascript.jscomp.DossierModuleRegistry;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A {@link CompilerPass} that collects the symbols the symbols to generate documentation for.
 */
class DocPass  implements CompilerPass {

  private static final String INTERNAL_NAMESPACE_VAR = "$jscomp";

  private final AbstractCompiler compiler;
  private final DocRegistry docRegistry;
  private final Set<String> providedSymbols;
  private final DossierModuleRegistry moduleRegistry;
  private final FileSystem fileSystem;

  DocPass(DossierCompiler compiler, DocRegistry docRegistry, Set<String> providedSymbols,
      FileSystem fileSystem) {
    this.compiler = compiler;
    this.docRegistry = docRegistry;
    this.providedSymbols = providedSymbols;
    this.moduleRegistry = compiler.getModuleRegistry();
    this.fileSystem = fileSystem;
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

      if (null == type) {
        type = var.getNameNode().getJSType();
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
        if (type == null) {
          continue;
        }
        @Nullable JSDocInfo info = getJSDocInfo(var, type);
        docRegistry.addExtern(new Descriptor(var.getName(), var.getNameNode(), type, info));
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
          Path path = fileSystem.getPath(n.getSourceFileName());
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
      if (!t.getScope().isGlobal()) {
        return;
      }

      JSTypeRegistry registry = t.getCompiler().getTypeRegistry();

      Scope scope = t.getScope();
      for (Scope.Var var : scope.getAllSymbols()) {
        String name = var.getName();
        if (name.startsWith(INTERNAL_NAMESPACE_VAR)
            || docRegistry.isExtern(name)) {
          continue;
        }

        @Nullable JSType type = getJSType(scope, var, registry);
        @Nullable JSDocInfo info = getJSDocInfo(var, type);

        if (null == type || type.isGlobalThisType() || isPrimitive(type)) {
          continue;
        }

        ModuleDescriptor moduleDescriptor = null;
        Descriptor descriptor = new Descriptor(name, var.getNameNode(), type, info);

        if (moduleRegistry.hasModuleNamed(var.getName())) {
          moduleDescriptor = new ModuleDescriptor(
              descriptor, moduleRegistry.getModuleNamed(var.getName()));
          docRegistry.addModule(moduleDescriptor);
        }

        traverseType(moduleDescriptor, descriptor, registry);
      }
    }

    private void traverseType(
        @Nullable ModuleDescriptor module, Descriptor descriptor, JSTypeRegistry registry) {
      JSType type = checkNotNull(descriptor.getType(), "Null type: %s", descriptor.getFullName());
      ObjectType obj = ObjectType.cast(type);
      if (obj == null) {
        return;
      }

      if (module == null
          && (descriptor.isConstructor()
          || descriptor.isInterface()
          || descriptor.isEnum()
          || registry.hasNamespace(descriptor.getFullName()))) {
        docRegistry.addType(descriptor);
      }

      if (obj.isInstanceType()) {
        obj = obj.getConstructor();
      }

      boolean exportingApi = module != null && module.getDescriptor() == descriptor;
      for (String prop : obj.getOwnPropertyNames()) {
        if (shouldSkipProperty(obj, prop)) {
          continue;
        }

        String propName = exportingApi ? prop : descriptor.getFullName() + "." + prop;
        JSType propType = obj.getPropertyType(prop);
        JSDocInfo propInfo  = obj.getOwnPropertyJSDocInfo(prop);
        if (propInfo == null && !isTheObjectType(propType)) {
          propInfo = propType.getJSDocInfo();
        }

        if (exportingApi
            || registry.hasNamespace(propName)
            || isDocumentableType(propInfo)
            || isDocumentableType(propType)
            || isProvidedSymbol(propName)) {
          Descriptor propDescriptor = new Descriptor(
              propName, obj.getPropertyNode(prop), propType, propInfo);
          if (module != null) {
            module.addExportedProperty(propDescriptor);
          }
          traverseType(module, propDescriptor, registry);
        }
      }
    }
  }

  private static boolean isDocumentableType(@Nullable JSDocInfo info) {
    return info != null && (info.isConstructor() || info.isInterface()
        || info.getEnumParameterType() != null);
  }

  private static boolean isDocumentableType(@Nullable JSType type) {
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

    // Skip recursive type references (encountered when processing singletons):
    //     /** @constructor */
    //     function Foo() {};
    //     /** @type {Foo} */
    //     Foo.instance_ = null;
    if (object.isConstructor() && object.getTypeOfThis() == propType) {
      return true;
    }

    // Sometimes the JSCompiler picks up the builtin call and apply functions off of a
    // function object.  We should always skip these.
    return object.isFunctionType()
        && propType.isFunctionType()
        && ("apply".equals(prop)
        || "bind".equals(prop)
        || "call".equals(prop))
        || propType.isGlobalThisType()
        || isPrimitive(propType);

  }

  private boolean isProvidedSymbol(String name) {
    return providedSymbols.contains(name);
  }

  private static boolean isPrimitive(JSType type) {
    return !type.isEnumElementType()
        && (type.isBooleanValueType()
        || type.isBooleanObjectType()
        || type.isNumber()
        || type.isNumberValueType()
        || type.isNumberObjectType()
        || type.isString()
        || type.isStringObjectType()
        || type.isStringValueType()
        || type.isArrayType());
  }
}
