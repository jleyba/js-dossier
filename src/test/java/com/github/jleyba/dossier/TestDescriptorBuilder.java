package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.DossierModule;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import org.mockito.Mockito;

import java.nio.file.FileSystems;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

class TestDescriptorBuilder {

  // Required variables we don't actuall care about.
  private static final Node SRC_NODE = null;
  private static final JSType JS_TYPE = mock(JSType.class);
  private static final JSDocInfo JS_DOC = mock(JSDocInfo.class);

  private final String name;
  private final List<Descriptor> staticProperties = new LinkedList<>();
  private final Set<Descriptor> instanceProperties = new HashSet<>();
  private final List<Scope.Var> internalVars = new LinkedList<>();

  private Type type = Type.NAMESPACE;
  private Optional<ModuleDescriptor> module = Optional.absent();
  private Optional<JsDoc> jsDoc = Optional.absent();
  @Nullable private String source = null;
  private int lineNum = -1;

  TestDescriptorBuilder(String name) {
    this.name = name;
  }

  TestDescriptorBuilder setType(Type type) {
    this.type = type;
    return this;
  }

  TestDescriptorBuilder setModule(ModuleDescriptor module) {
    this.module = Optional.of(module);
    return this;
  }

  TestDescriptorBuilder setSource(@Nullable String source) {
    this.source = source;
    return this;
  }

  TestDescriptorBuilder setLineNum(int lineNum) {
    this.lineNum = Math.max(lineNum, -1);
    return this;
  }

  TestDescriptorBuilder setJsDoc(@Nullable JsDoc jsDoc) {
    this.jsDoc = Optional.fromNullable(jsDoc);
    return this;
  }

  TestDescriptorBuilder addStaticProperty(Descriptor property) {
    checkArgument(property.getFullName().startsWith(name + "."));
    staticProperties.add(property);
    return this;
  }

  TestDescriptorBuilder addStaticProperty(TestDescriptorBuilder propertyBuilder) {
    return addStaticProperty(propertyBuilder.build());
  }

  TestDescriptorBuilder addInstanceProperty(Descriptor property) {
    checkArgument(property.getFullName().startsWith(name + ".prototype."));
    instanceProperties.add(property);
    return this;
  }

  TestDescriptorBuilder addInstanceProperty(TestDescriptorBuilder propertyBuilder) {
    return addInstanceProperty(propertyBuilder.build());
  }

  TestDescriptorBuilder addInternalVar(String name) {
    Node node = IR.name(name);
    Scope.Var var = mock(Scope.Var.class);
    when(var.getNameNode()).thenReturn(node);
    internalVars.add(var);
    return this;
  }

  Descriptor build() {
    Descriptor d = Mockito.spy(new Descriptor(name, SRC_NODE, JS_TYPE, JS_DOC));
    doReturn(ImmutableList.copyOf(staticProperties)).when(d).getProperties();
    doReturn(ImmutableSet.copyOf(instanceProperties)).when(d).getInstanceProperties();
    doReturn(source).when(d).getSource();
    doReturn(lineNum).when(d).getLineNum();
    doReturn(jsDoc.orNull()).when(d).getJsDoc();

    if (module.isPresent()) {
      doReturn(module).when(d).getModule();
    }

    switch (type) {
      case CLASS:
        doReturn(true).when(d).isConstructor();
        break;
      case INTERFACE:
        doReturn(true).when(d).isInterface();
        break;
      case ENUM:
        doReturn(true).when(d).isEnum();
        break;
      case NAMESPACE:
      default:
        // Do nothing; this is the default.
        break;
    }

    return d;
  }

  ModuleDescriptor buildModule() {
    checkState(name.endsWith(".exports"));
    checkState(!module.isPresent());

    String moduleName = name.substring(0, name.length() - ".exports".length());
    DossierModule module = mock(DossierModule.class);
    when(module.getVarName()).thenReturn(moduleName);
    when(module.getModulePath()).thenReturn(
        source == null ? null : FileSystems.getDefault().getPath(source));
    when(module.getInternalVars()).thenReturn(internalVars);

    return new ModuleDescriptor(build(), module);
  }

  static enum Type {
    CLASS,
    INTERFACE,
    ENUM,
    NAMESPACE
  }
}
