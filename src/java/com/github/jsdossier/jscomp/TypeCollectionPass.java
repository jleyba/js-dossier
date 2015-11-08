/*
 Copyright 2013-2015 Jason Leyba
 
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.javascript.jscomp.NodeTraversal.traverseEs6;

import com.github.jsdossier.annotations.Input;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.NoType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.PrototypeObjectType;
import com.google.javascript.rhino.jstype.ProxyObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Compiler pass responsible for collecting the types to be documented.
 */
public final class TypeCollectionPass implements CompilerPass {

  private static final String INTERNAL_NAMESPACE_VAR = "$jscomp";
  private static final Logger log = Logger.getLogger(TypeCollectionPass.class.getName());

  private final DossierCompiler compiler;
  private final TypeRegistry typeRegistry;
  private final FileSystem inputFs;

  @Inject
  TypeCollectionPass(
      DossierCompiler compiler,
      TypeRegistry typeRegistry,
      @Input FileSystem inputFs) {
    this.compiler = compiler;
    this.typeRegistry = typeRegistry;
    this.inputFs = inputFs;
  }

  @Override
  public void process(Node externsRoot, Node root) {
    if (compiler.getErrorCount() > 0) {
      return;
    }

    Externs externs = new Externs();
    traverseEs6(compiler, externsRoot, new ExternCollector(externs));
    traverseEs6(compiler, root, new TypeCollector(externs));
  }

  private static final class Externs {
    private final Map<String, JSType> byName = new HashMap<>();
    private final Set<JSType> types = new HashSet<>();

    public void addExtern(String name, JSType type) {
      byName.put(name, type);
      types.add(type);
    }

    public boolean isExtern(JSType type) {
      return types.contains(type);
    }
  }

  private static void logfmt(String msg, Object... args) {
    if (log.isLoggable(Level.FINE)) {
      log.fine(String.format(msg, args));
    }
  }

  private class ExternCollector implements NodeTraversal.Callback, Visitor<Object> {
    private final Externs externs;

    private final Joiner joiner = Joiner.on('.');
    private final Set<JSType> seen = new HashSet<>();
    private final Deque<String> names = new ArrayDeque<>();

    private ExternCollector(Externs externs) {
      this.externs = externs;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return n.isBlock() && parent == null;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      verify(t.getScope().isGlobal());
      for (Var var : t.getScope().getAllSymbols()) {
        @Nullable JSType type = var.getNameNode().getJSType();
        if (type != null) {
          crawl(var.getName(), type);
        }
      }
    }

    private void crawl(String name, JSType type) {
      names.addLast(name);
      if (!seen.contains(type)) {
        type.visit(this);
      }

      if ((type.isNominalType() && !type.isInstanceType())
          || type.isNominalConstructor()
          || type.isEnumType()) {
        String qualifiedName = joiner.join(names);
        externs.addExtern(qualifiedName, type);
      }
      names.removeLast();
    }

    @Override
    public Object caseFunctionType(FunctionType type) {
      for (String name : type.getOwnPropertyNames()) {
        if (!"apply".equals(name)
            && !"bind".equals(name)
            && !"call".equals(name)
            && !"prototype".equals(name)) {
          crawl(name, type.getPropertyType(name));
        }
      }
      return null;
    }

    @Override
    public Object caseObjectType(ObjectType type) {
      if (type.isGlobalThisType()) {
        return null;
      }

      for (String name : type.getOwnPropertyNames()) {
        if (type.getPropertyType(name).isEnumElementType()) {
          continue;
        }
        crawl(name, type.getPropertyType(name));
      }
      return null;
    }

    @Override public Object caseProxyObjectType(ProxyObjectType type) { return null; }
    @Override public Object caseNoType(NoType type) { return null; }
    @Override public Object caseEnumElementType(EnumElementType type) { return null; }
    @Override public Object caseAllType() { return null; }
    @Override public Object caseBooleanType() { return null; }
    @Override public Object caseNoObjectType() { return null; }
    @Override public Object caseUnknownType() { return null; }
    @Override public Object caseNullType() { return null; }
    @Override public Object caseNamedType(NamedType type) { return null; }
    @Override public Object caseNumberType() { return null; }
    @Override public Object caseStringType() { return null; }
    @Override public Object caseVoidType() { return null; }
    @Override public Object caseUnionType(UnionType type) { return null; }
    @Override public Object caseTemplatizedType(TemplatizedType type) { return null; }
    @Override public Object caseTemplateType(TemplateType templateType) { return null; }
  }
  
  private class TypeCollector implements NodeTraversal.Callback, Visitor<Void> {
    
    private final Externs externs;
    private final Deque<NominalType2> types = new ArrayDeque<>();

    private TypeCollector(Externs externs) {
      this.externs = externs;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return null == parent && n.isBlock();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!t.getScope().isGlobal()) {
        System.out.println("Skipping no global scope");
        return;
      }
      
      for (Var var : t.getScope().getAllSymbols()) {
        String name = var.getName();
        if (name.startsWith(INTERNAL_NAMESPACE_VAR)) {
          System.out.printf("Skipping internal compiler namespace %s\n", name);
          continue;
        }
        
        Node node = var.getNameNode();
        if (node == null) {
          System.out.printf("Skipping type without a source node: %s\n", name);
          continue;
        } else if (node.getJSType() == null) {
          System.out.printf("Unable to determine type for %s; skipping\n", name);
          continue;
        } else if (node.getJSType().isGlobalThisType()) {
          System.out.println("Skipping global this: " + name);
          continue;
        } else if (externs.isExtern(node.getJSType())) {
          System.out.println("Skipping extern alias: " + name);
          continue;
        }

        JSDocInfo info = var.getJSDocInfo();
        if (info == null || isNullOrEmpty(info.getOriginalCommentString())) {
          System.out.printf("For %s, using jsdoc from node type\n", name);
          info = node.getJSType().getJSDocInfo();
        }
        
        if (isPrimitive(node.getJSType()) && (info == null || info.getTypedefType() == null)) {
          System.out.printf(
              "Skipping primitive type assigned to %s: %s\n", name, node.getJSType());
          continue;
        }
        
        Path path = inputFs.getPath(node.getSourceFileName());
        NominalType2 nominalType = NominalType2.builder()
            .setName(name)
            .setType(node.getJSType())
            .setJsDoc(info)
            .setSourceFile(path)
            .setSourcePosition(Position.of(node.getLineno(), node.getCharno()))
            .setModule(getModule(node))
            .build();

        recordType(nominalType);
      }
    }
    
    private Optional<Module> getModule(Node node) {
      Path path = inputFs.getPath(node.getSourceFileName());
      return typeRegistry.isModule(path)
          ? Optional.of(typeRegistry.getModule(path))
          : Optional.<Module>absent();
    }

    private void recordType(NominalType2 type) {
      if (externs.isExtern(type.getType())) {
        System.out.println("Skipping extern alias: " + type.getName());
        return;
      }
      
      if (type.getName().contains("$$")) {
        int index = type.getName().indexOf("$$");
        String id = type.getName().substring(index + 2);
        if (typeRegistry.isModule(id)) {
          Module module = typeRegistry.getModule(id);
          if (module.getType() == Module.Type.ES6) {
            for (AliasRegion region : typeRegistry.getAliasRegions(type.getSourceFile())) {
              if (region.getRange().contains(type.getSourcePosition())) {
                String alias = type.getName().substring(0, index);
                region.addAlias(alias, type.getName());
              }
            }
          }
          System.out.println("Skipping module alias: " + type.getName());
          return;
        }
      }
      
      if (type.getName().endsWith(".default")) {
        String id = type.getName().substring(0, type.getName().length() - ".default".length());
        if (typeRegistry.isModule(id)) {
          System.out.println("Skipping default module export: " + type.getName());
          return;
        }
      }
      
      JSType jsType = type.getType();
      if (!type.getModule().isPresent()
          && !jsType.isConstructor()
          && !jsType.isInterface()
          && !jsType.isEnumType()
          && !type.getJsDoc().isTypedef()
          && !typeRegistry.isProvided(type.getName())) {
        System.out.println("Ignoring undeclared namespace " + type.getName());
        return;
      }

      if (!typeRegistry.getTypes(type.getType()).isEmpty()) {
        System.out.printf("Found type alias: %s\n", type.getName());
        typeRegistry.addType(type);
        return;
      }

      typeRegistry.addType(type);
      types.push(type);
      jsType.visit(this);
      types.pop();
    }
    
    private void crawlProperty(Property property) {
      checkState(!types.isEmpty());

      NominalType2 parent = types.peek();
      Node node = property.getNode();
      JSDocInfo info = property.getJSDocInfo();
      if (info == null && !isTheObjectType(property.getType())) {
        info = property.getType().getJSDocInfo();
      }
      JsDoc jsdoc = JsDoc.from(info);

      if (jsdoc.isTypedef()) {
        typeRegistry.addType(NominalType2.builder()
            .setName(parent.getName() + "." + property.getName())
            .setModule(getModule(node))
            .setJsDoc(jsdoc)
            .setType(property.getType())
            .setSourceFile(inputFs.getPath(node.getSourceFileName()))
            .setSourcePosition(Position.of(node.getLineno(), node.getCharno()))
            .build());
        return;
      }
      
      JSType propertyType = property.getType();
      if (propertyType.isInstanceType() && jsdoc.isConstructor()) {
        JSType ctor = ((PrototypeObjectType) propertyType).getConstructor();
        if (ctor != null && parent.getType().equals(ctor)) {
          propertyType = ctor;
        }
      }
      
      NominalType2 nt = NominalType2.builder()
          .setName(parent.getName() + "." + property.getName())
          .setModule(getModule(node))
          .setJsDoc(jsdoc)
          .setType(propertyType)
          .setSourceFile(inputFs.getPath(node.getSourceFileName()))
          .setSourcePosition(Position.of(node.getLineno(), node.getCharno()))
          .build();
      
      if (propertyType.isConstructor()) {
        // If jsdoc is present and says this is not a constructor, we've found a
        // constructor reference, which should not be documented as a unique nominal type:
        //     /** @type {function(new: Foo)} */ var x;
        //     /** @private {function(new: Foo)} */ var x;
        //
        // We do not check jsdoc.isConstructor() since the Closure compiler may create a stub
        // JSDocInfo entry as part of one of its passes, i.e. rewriting a goog.module and an
        // exported property is an internal class:
        //     goog.module('foo');
        //     /** @constructor */
        //     function Internal() {}
        //     exports.Public = Internal;
        //
        // The exception to the rule is if this property is exporting a constructor as part of
        // a CommonJS module's public API - then we document the symbol as a type.
        if (parent.isModuleExports() || isConstructorTypeDefinition(propertyType, jsdoc)) {
          recordType(nt);
        }
      } else if (propertyType.isInterface() || propertyType.isEnumType()) {
        recordType(nt);

      } else if (!propertyType.isInstanceType()
          && propertyType instanceof PrototypeObjectType
          && (typeRegistry.isProvided(parent.getName() + "." + property.getName())
          || !typeRegistry.getTypes(propertyType).isEmpty())) {
        recordType(nt);
      }
    }

    @Override
    public Void caseFunctionType(FunctionType type) {
      for (String name : type.getOwnPropertyNames()) {
        if (!"apply".equals(name)
            && !"bind".equals(name)
            && !"call".equals(name)
            && !"prototype".equals(name)) {
          crawlProperty(type.getOwnSlot(name));
        }
      }
      return null;
    }

    @Override
    public Void caseObjectType(ObjectType type) {
      if (type.isGlobalThisType()) {
        return null;
      }
      for (String name : type.getOwnPropertyNames()) {
        if (!"prototype".equals(name)) {
          crawlProperty(type.getOwnSlot(name));
        }
      }
      return null;
    }

    @Override public Void caseNoType(NoType type) { return null; }
    @Override public Void caseEnumElementType(EnumElementType type) { return null; }
    @Override public Void caseAllType() { return null; }
    @Override public Void caseBooleanType() { return null; }
    @Override public Void caseNoObjectType() { return null; }
    @Override public Void caseUnknownType() { return null; }
    @Override public Void caseNullType() { return null; }
    @Override public Void caseNamedType(NamedType type) { return null; }
    @Override public Void caseProxyObjectType(ProxyObjectType type) { return null; }
    @Override public Void caseNumberType() { return null; }
    @Override public Void caseStringType() { return null; }
    @Override public Void caseVoidType() { return null; }
    @Override public Void caseUnionType(UnionType type) { return null; }
    @Override public Void caseTemplatizedType(TemplatizedType type) { return null; }
    @Override public Void caseTemplateType(TemplateType templateType) { return null; }
  }

  private static boolean isTheObjectType(JSType type) {
    if (!type.isInstanceType()) {
      return false;
    }
    ObjectType obj = type.toObjectType();
    return obj.getConstructor().isNativeObjectType()
        && "Object".equals(obj.getConstructor().getReferenceName());
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
        || type.isVoidType()
        || type.isArrayType());
  }

  private static boolean isConstructorTypeDefinition(JSType type, JsDoc jsdoc) {
    if (type.isConstructor()) {
      return jsdoc.isConstructor()
          || jsdoc.isConst()
          && !hasTypeExpression(jsdoc.getMarker(JsDoc.Annotation.TYPE))
          && !hasTypeExpression(jsdoc.getMarker(JsDoc.Annotation.PUBLIC))
          && !hasTypeExpression(jsdoc.getMarker(JsDoc.Annotation.PROTECTED)) 
          && !hasTypeExpression(jsdoc.getMarker(JsDoc.Annotation.PRIVATE));
    }
    return false;
  }

  private static boolean hasTypeExpression(Optional<JSDocInfo.Marker> marker) {
    return marker.isPresent() && marker.get().getType() != null;
  }
}
