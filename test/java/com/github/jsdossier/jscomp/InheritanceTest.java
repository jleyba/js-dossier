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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import javax.inject.Inject;

/**
 * Tests related to handling class and interface inheritance.
 */
@RunWith(JUnit4.class)
public class InheritanceTest {

  @Rule
  public GuiceRule guice = GuiceRule.builder(this).build();

  @Inject CompilerUtil util;
  @Inject TypeRegistry typeRegistry;
  @Inject JSTypeRegistry jsRegistry;

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

    NominalType2 runnable = typeRegistry.getType("Runnable");
    NominalType2 runnableImpl = typeRegistry.getType("RunnableImpl");
    assertThat(typeRegistry.getImplementedTypes(runnableImpl, jsRegistry))
        .containsExactly(getInstanceType(runnable));
  }

  @Test
  public void implementedTypesForAnInterfaceDoesNotIncludeTheInterfaceItself() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "A.prototype.a = function() {};");

    NominalType2 a = typeRegistry.getType("A");
    assertThat(typeRegistry.getImplementedTypes(a, jsRegistry)).isEmpty();
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

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 impl = typeRegistry.getType("Impl");

    assertThat(typeRegistry.getImplementedTypes(impl, jsRegistry))
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

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    assertThat(typeRegistry.getImplementedTypes(a, jsRegistry)).isEmpty();
    assertThat(typeRegistry.getImplementedTypes(b, jsRegistry))
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

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 c = typeRegistry.getType("C");
    NominalType2 d = typeRegistry.getType("D");
    assertThat(typeRegistry.getImplementedTypes(a, jsRegistry)).isEmpty();
    assertThat(typeRegistry.getImplementedTypes(b, jsRegistry))
        .containsExactly(getInstanceType(a));
    assertThat(typeRegistry.getImplementedTypes(c, jsRegistry))
        .containsExactly(
            getInstanceType(a),
            getInstanceType(b));
    assertThat(typeRegistry.getImplementedTypes(d, jsRegistry))
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

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 c = typeRegistry.getType("C");
    assertThat(typeRegistry.getImplementedTypes(a, jsRegistry)).isEmpty();
    assertThat(typeRegistry.getImplementedTypes(b, jsRegistry)).isEmpty();
    assertThat(typeRegistry.getImplementedTypes(c, jsRegistry))
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

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 impl = typeRegistry.getType("Impl");

    assertThat(typeRegistry.getImplementedTypes(impl, jsRegistry))
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

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 impl = typeRegistry.getType("Impl");

    assertThat(typeRegistry.getImplementedTypes(impl, jsRegistry))
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

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 c = typeRegistry.getType("C");
    NominalType2 impl = typeRegistry.getType("Impl");

    assertThat(typeRegistry.getImplementedTypes(impl, jsRegistry))
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

    NominalType2 a = typeRegistry.getType("A");

    assertThat(typeRegistry.getTypeHierarchy(a.getType(), jsRegistry))
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


    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");

    assertThat(typeRegistry.getTypeHierarchy(b.getType(), jsRegistry))
        .containsExactly(
            getInstanceType(b),
            getInstanceType(a))
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


    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 c = typeRegistry.getType("C");
    NominalType2 d = typeRegistry.getType("D");

    assertThat(typeRegistry.getTypeHierarchy(d.getType(), jsRegistry))
        .containsExactly(
            getInstanceType(d),
            getInstanceType(c),
            getInstanceType(b),
            getInstanceType(a))
        .inOrder();
  }
  
  @Test
  public void getDeclaredInterfaces_classHasNoInterfaces() {
    util.compile(path("foo.js"),
        "/** @constructor */",
        "function Clazz() {}");

    NominalType2 clazz = typeRegistry.getType("Clazz");
    assertThat(typeRegistry.getDeclaredInterfaces(clazz.getType(), jsRegistry)).isEmpty();
  }
  
  @Test
  public void getDeclaredInterfaces_classImplementsInterface() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface */",
        "function B() {}",
        "",
        "/** @interface */",
        "function C() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {C}",
        " * @implements {B}",
        " * @implements {A}",
        " */",
        "function Clazz() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {A}",
        " * @implements {B}",
        " */",
        "function OtherClazz() {}");

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 c = typeRegistry.getType("C");
    NominalType2 clazz = typeRegistry.getType("Clazz");

    assertThat(typeRegistry.getDeclaredInterfaces(clazz.getType(), jsRegistry))
        .containsExactly(
            getInstanceType(c),
            getInstanceType(b),
            getInstanceType(a))
        .inOrder();
  }
  
  @Test
  public void getDeclaredInterfaces_doesNotReturnInterfacesDeclaredOnSuperClass() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface */",
        "function B() {}",
        "",
        "/** @interface */",
        "function C() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {C}",
        " * @implements {B}",
        " * @implements {A}",
        " */",
        "function Clazz() {}",
        "",
        "/**",
        " * @constructor",
        " * @extends {Clazz}",
        " */",
        "function OtherClazz() {}");

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 c = typeRegistry.getType("C");
    NominalType2 clazz = typeRegistry.getType("Clazz");

    assertThat(typeRegistry.getDeclaredInterfaces(clazz.getType(), jsRegistry))
        .containsExactly(
            getInstanceType(c),
            getInstanceType(b),
            getInstanceType(a))
        .inOrder();

    NominalType2 otherClazz = typeRegistry.getType("OtherClazz");
    assertThat(typeRegistry.getDeclaredInterfaces(otherClazz.getType(), jsRegistry)).isEmpty();
  }
  
  @Test
  public void getDeclaredInterfaces_leafInterface() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function Foo() {}");

    NominalType2 type = typeRegistry.getType("Foo");
    assertThat(typeRegistry.getDeclaredInterfaces(type.getType(), jsRegistry)).isEmpty();
  }
  
  @Test
  public void getDeclaredInterfaces_interfaceHasSuperInterface() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface */",
        "function B() {}",
        "",
        "/** @interface */",
        "function C() {}",
        "",
        "/**",
        " * @interface",
        " * @extends {C}",
        " * @extends {B}",
        " * @extends {A}",
        " */",
        "function Foo() {}");

    NominalType2 a = typeRegistry.getType("A");
    NominalType2 b = typeRegistry.getType("B");
    NominalType2 c = typeRegistry.getType("C");
    NominalType2 type = typeRegistry.getType("Foo");

    assertThat(typeRegistry.getDeclaredInterfaces(type.getType(), jsRegistry))
        .containsExactly(
            getInstanceType(c),
            getInstanceType(b),
            getInstanceType(a))
        .inOrder();
  }
  
  @Test
  public void getDeclaredInterfaces_doesNotReturnDeclarationsFromSuperInterface() {
    util.compile(path("foo.js"),
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface */",
        "function B() {}",
        "",
        "/** @interface @extends {A} @extends {B}*/",
        "function C() {}",
        "",
        "/**",
        " * @interface",
        " * @extends {C}",
        " */",
        "function Foo() {}");

    NominalType2 c = typeRegistry.getType("C");
    NominalType2 type = typeRegistry.getType("Foo");

    assertThat(typeRegistry.getDeclaredInterfaces(type.getType(), jsRegistry))
        .containsExactly(getInstanceType(c));
  }

  private static JSType getInstanceType(NominalType2 nominalType) {
    JSType jsType = nominalType.getType();
    checkArgument(jsType.isConstructor() || jsType.isInterface());
    return ((FunctionType) jsType).getInstanceType();
  }

  private static Path path(String first, String... remaining) {
    return FileSystems.getDefault().getPath(first, remaining);
  }
}
