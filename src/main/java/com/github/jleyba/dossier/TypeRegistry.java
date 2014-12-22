package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.DossierModule;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.NoType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.ProxyObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class TypeRegistry {

  private static final String INTERNAL_NAMESPACE_VAR = "$jscomp";

  private final JSTypeRegistry jsTypeRegistry;

  private final Set<String> providedSymbols = new HashSet<>();
  private final Set<String> implicitNamespaces = new HashSet<>();
  private final Map<Path, JsDoc> fileOverviews = new HashMap<>();
  private final Map<String, NominalType> nominalTypes = new HashMap<>();
  private final Map<String, NominalType> moduleExports = new HashMap<>();
  private final Map<DossierModule, NominalType> moduleToExports = new IdentityHashMap<>();
  private final Map<JSType, NominalType.TypeDescriptor> descriptorsByJsType = new HashMap<>();

  private final Map<String, JSType> externsByName = new HashMap<>();
  private final Set<JSType> externs = new HashSet<>();

  public TypeRegistry(JSTypeRegistry jsTypeRegistry) {
    this.jsTypeRegistry = checkNotNull(jsTypeRegistry, "null JSTypeRegistry");
  }

  public static boolean isInternalNamespaceVar(String name) {
    return name.startsWith(INTERNAL_NAMESPACE_VAR);
  }

  public void recordGoogProvide(String name) {
    providedSymbols.add(name);
    implicitNamespaces.add(name);
    for (int i = name.lastIndexOf('.'); i != -1; i = name.lastIndexOf('.')) {
      name = name.substring(0, i);
      implicitNamespaces.add(name);
    }
  }

  public Set<String> getProvidedSymbols() {
    return Collections.unmodifiableSet(providedSymbols);
  }

  public Set<String> getImplicitNamespaces() {
    return Collections.unmodifiableSet(implicitNamespaces);
  }

  public boolean hasNamespace(String name) {
    return implicitNamespaces.contains(name);
  }

  public void addExtern(String name, JSType type) {
    externsByName.put(name, type);
    externs.add(type);
  }

  public Set<String> getExternNames() {
    return Collections.unmodifiableSet(externsByName.keySet());
  }

  public boolean isExtern(JSType type) {
    return externs.contains(type);
  }

  public void recordFileOverview(Path path, JsDoc jsdoc) {
    fileOverviews.put(path, jsdoc);
  }

  @Nullable
  public JsDoc getFileOverview(Path path) {
    return fileOverviews.get(path);
  }

  public void addType(NominalType type) {
    if (type.getJsdoc() == null || !type.getJsdoc().isTypedef()) {
      NominalType.TypeDescriptor descriptor = type.getTypeDescriptor();
      NominalType.TypeDescriptor replaced =  descriptorsByJsType.put(type.getJsType(), descriptor);

      if (replaced != null && replaced != descriptor) {
        throw new AssertionError("Replacing " + replaced + " with " + descriptor
            + " when adding type " + type.getQualifiedName());
      }
    }

    if (type.isModuleExports()) {
      checkArgument(!moduleToExports.containsKey(type.getModule()),
          "Module already registerd %s", type.getModule().getVarName());
      moduleToExports.put(type.getModule(), type);
      moduleExports.put(type.getQualifiedName(), type);
    } else if (type.getModule() == null) {
      nominalTypes.put(type.getQualifiedName(), type);
    }
  }

  @Nullable
  public NominalType.TypeDescriptor getTypeDescriptor(JSType type) {
    return descriptorsByJsType.get(type);
  }

  @VisibleForTesting Map<String, NominalType> getNominalTypeMap() {
    return nominalTypes;
  }

  @VisibleForTesting Map<String, NominalType> getModuleExportsMap() {
    return moduleExports;
  }

  public Collection<NominalType> getNominalTypes() {
    return Collections.unmodifiableCollection(nominalTypes.values());
  }

  @Nullable
  public NominalType getNominalType(String name) {
    return nominalTypes.get(name);
  }

  public Collection<NominalType> getModules() {
    return Collections.unmodifiableCollection(moduleExports.values());
  }

  @Nullable
  public NominalType resolve(JSType type) {
    if (type.isConstructor() || type.isInterface()) {
      String nameOfThis = ((FunctionType) type).getTypeOfThis().toString();
      return nominalTypes.get(nameOfThis);
    }
    return null;
  }

  /**
   * Returns the type hierarchy for the given type as a stack with the type at the
   * bottom and the root ancestor at the top (Object is excluded as it is implied).
   */
  public LinkedList<JSType> getTypeHierarchy(JSType type) {
    LinkedList<JSType> stack = new LinkedList<>();
    for (; type != null; type = getBaseType(type)) {
      if (type.isInstanceType()) {
        type = ((ObjectType) type).getConstructor();
      }
      stack.push(type);
    }
    return stack;
  }

  @Nullable
  private JSType getBaseType(JSType type) {
    JSDocInfo info = type.getJSDocInfo();
    if (info == null) {
      return null;
    }
    JSTypeExpression baseType = info.getBaseType();
    if (baseType == null) {
      return null;
    }
    return baseType.evaluate(null, jsTypeRegistry);
  }

  public ImmutableSet<JSType> getImplementedTypes(NominalType nominalType) {
    JSType type = nominalType.getJsType();
    ImmutableSet.Builder<JSType> builder = ImmutableSet.builder();
    if (type.isConstructor()) {
      for (JSType jsType : getTypeHierarchy(type)) {
        if (jsType.getJSDocInfo() != null) {
          JSDocInfo info = type.getJSDocInfo();
          for (JSTypeExpression expr : info.getImplementedInterfaces()) {
            builder.add(expr.evaluate(null, jsTypeRegistry));
          }
        }
      }
    } else if (type.isInterface() && nominalType.getJsdoc() != null) {
      builder.addAll(getExtendedInterfaces(nominalType.getJsdoc().getInfo()));
    }
    return builder.build();
  }

  private Set<JSType> getExtendedInterfaces(JSDocInfo info) {
    Set<JSType> interfaces = new HashSet<>();
    for (JSTypeExpression expr : info.getExtendedInterfaces()) {
      JSType type = expr.evaluate(null, jsTypeRegistry);
      if (interfaces.add(type) && type.getJSDocInfo() != null) {
        interfaces.addAll(getExtendedInterfaces(type.getJSDocInfo()));
      }
    }
    return interfaces;
  }

  public static boolean ignoreProperty(ObjectType obj, String propName) {
    return obj.isFunctionType()
        && ("apply".equals(propName) || "bind".equals(propName) || "call".equals(propName))
        || "prototype".equals(propName);
  }

  public static boolean isTheObjectType(JSType type) {
    if (!type.isInstanceType()) {
      return false;
    }
    ObjectType obj = type.toObjectType();
    return obj.getConstructor().isNativeObjectType()
        && "Object".equals(obj.getConstructor().getReferenceName());
  }

  private class TypeCrawler implements Visitor<Object> {

    @Override public Object caseFunctionType(FunctionType type) { return null; }
    @Override public Object caseObjectType(ObjectType type) { return null; }
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
}
