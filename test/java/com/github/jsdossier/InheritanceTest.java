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

package com.github.jsdossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.annotations.Modules;
import com.github.jsdossier.annotations.Stderr;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import javax.inject.Inject;

/**
 * Tests related to handling class and interface inheritance.
 */
@RunWith(JUnit4.class)
public class InheritanceTest {

  @Rule
  public GuiceRule guice = new GuiceRule(this, new AbstractModule() {
    @Override protected void configure() {
      install(new CompilerModule());
    }
    @Provides @Input FileSystem provideFs() { return fileSystem; }
    @Provides @Stderr PrintStream provideStderr() { return System.err; }
    @Provides @Modules ImmutableSet<Path> provideModules() { return ImmutableSet.of(); }
  });

  private final FileSystem fileSystem = Jimfs.newFileSystem();

  @Inject CompilerUtil util;
  @Inject TypeRegistry typeRegistry;

  @Test
  public void canGetImplementedTypesFromTypeRegistry_implementsOneInterface() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function Runnable() {}",
        "Runnable.prototype.run = function() {};",
        "",
        "/**",
        " * @constructor",
        " * @implements {Runnable}",
        " */",
        "function RunnableImpl() {}",
        "/** @override */",
        "RunnableImpl.prototype.run = function() {};");

    NominalType runnable = typeRegistry.getNominalType("Runnable");
    NominalType runnableImpl = typeRegistry.getNominalType("RunnableImpl");
    assertThat(typeRegistry.getImplementedTypes(runnableImpl))
        .containsExactly(getInstanceType(runnable));
  }

  @Test
  public void implementedTypesForAnInterfaceDoesNotIncludeTheInterfaceItself() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "A.prototype.a = function() {};");

    NominalType a = typeRegistry.getNominalType("A");
    assertThat(typeRegistry.getImplementedTypes(a)).isEmpty();
  }

  @Test
  public void canGetImplementedTypesFromTypeRegistry_implementsMultipleInterfaces() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "A.prototype.a = function() {};",
        "",
        "/** @interface */",
        "function B() {}",
        "B.prototype.b = function() {};",
        "",
        "/**",
        " * @constructor",
        " * @implements {A}",
        " * @implements {B}",
        " */",
        "function Impl() {}",
        "Impl.prototype.a = function() {};",
        "Impl.prototype.b = function() {};");

    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");
    NominalType impl = typeRegistry.getNominalType("Impl");

    assertThat(typeRegistry.getImplementedTypes(impl))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b));
  }

  @Test
  public void implementedTypesForAnInterfaceThatExtendsAnother_oneLevel() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "/** @interface @extends {A} */",
        "function B() {}");

    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");
    assertThat(typeRegistry.getImplementedTypes(a)).isEmpty();
    assertThat(typeRegistry.getImplementedTypes(b))
        .containsExactly(getInstanceType(a));
  }

  @Test
  public void implementedTypesForAnInterfaceThatExtendsAnother_multipleLevels() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "/** @interface @extends {A} */",
        "function B() {}",
        "/** @interface @extends {B} */",
        "function C() {}",
        "/** @interface @extends {C} */",
        "function D() {}");

    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");
    NominalType c = typeRegistry.getNominalType("C");
    NominalType d = typeRegistry.getNominalType("D");
    assertThat(typeRegistry.getImplementedTypes(a)).isEmpty();
    assertThat(typeRegistry.getImplementedTypes(b))
        .containsExactly(getInstanceType(a));
    assertThat(typeRegistry.getImplementedTypes(c))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b));
    assertThat(typeRegistry.getImplementedTypes(d))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b),
            getInstanceType(c));
  }

  @Test
  public void implementedTypesForAnInterfaceThatExtendsMultipleInterfaces() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "/** @interface */",
        "function B() {}",
        "/**",
        " * @interface",
        " * @extends {A}",
        " * @extends {B}",
        " */",
        "function C() {}");

    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");
    NominalType c = typeRegistry.getNominalType("C");
    assertThat(typeRegistry.getImplementedTypes(a)).isEmpty();
    assertThat(typeRegistry.getImplementedTypes(b)).isEmpty();
    assertThat(typeRegistry.getImplementedTypes(c))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b));
  }

  @Test
  public void canGetImplementedTypesFromTypeRegistry_implementsIFaceThatExtendsAnother() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface @extends {A} */",
        "function B() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {B}",
        " */",
        "function Impl() {}");

    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");
    NominalType impl = typeRegistry.getNominalType("Impl");

    assertThat(typeRegistry.getImplementedTypes(impl))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b));
  }

  @Test
  public void implementedInterfaceSpecifiedBySuperType() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface @extends {A} */",
        "function B() {}",
        "",
        "/** @constructor @implements {B} */",
        "function C() {}",
        "",
        "/**",
        " * @constructor",
        " * @extends {C}",
        " */",
        "function Impl() {}");

    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");
    NominalType impl = typeRegistry.getNominalType("Impl");

    assertThat(typeRegistry.getImplementedTypes(impl))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b));
  }

  @Test
  public void typeImplementsAdditionalInterfacesFromItsSuperType() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface @extends {A} */",
        "function B() {}",
        "",
        "/** @interface */",
        "function C() {}",
        "",
        "/** @constructor @implements {B} */",
        "function SuperType() {}",
        "",
        "/**",
        " * @constructor",
        " * @extends {SuperType}",
        " * @implements {C}",
        " */",
        "function Impl() {}");

    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");
    NominalType c = typeRegistry.getNominalType("C");
    NominalType impl = typeRegistry.getNominalType("Impl");

    assertThat(typeRegistry.getImplementedTypes(impl))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b),
            getInstanceType(c));
  }

  @Test
  public void getTypeHierarchy_classHasNoSuperType() {
    util.compile(path("foo.js"),
        "/** @constructor */",
        "function A() {}");

    NominalType a = typeRegistry.getNominalType("A");

    assertThat(typeRegistry.getTypeHierarchy(a.getJsType()))
        .containsExactly(getInstanceType(a))
        .inOrder();
  }

  @Test
  public void getTypeHierarchy_oneAncestor() {
    util.compile(path("foo.js"),
        "/** @constructor */",
        "function A() {}",
        "",
        "/** @constructor @extends {A}*/",
        "function B() {}");


    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");

    assertThat(typeRegistry.getTypeHierarchy(b.getJsType()))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b))
        .inOrder();
  }

  @Test
  public void getTypeHierarchy_severalAncestors() {
    util.compile(path("foo.js"),
        "/** @constructor */",
        "function A() {}",
        "/** @constructor @extends {A}*/",
        "function B() {}",
        "/** @constructor @extends {B}*/",
        "function C() {}",
        "/** @constructor @extends {C}*/",
        "function D() {}");


    NominalType a = typeRegistry.getNominalType("A");
    NominalType b = typeRegistry.getNominalType("B");
    NominalType c = typeRegistry.getNominalType("C");
    NominalType d = typeRegistry.getNominalType("D");

    assertThat(typeRegistry.getTypeHierarchy(d.getJsType()))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b),
            getInstanceType(c),
            getInstanceType(d))
        .inOrder();
  }

  private static JSType getInstanceType(NominalType nominalType) {
    JSType jsType = nominalType.getJsType();
    checkArgument(jsType.isConstructor() || jsType.isInterface());
    return ((FunctionType) jsType).getInstanceType();
  }

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }
}
