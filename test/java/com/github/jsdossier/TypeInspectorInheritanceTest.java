/*
Copyright 2013-2016 Jason Leyba

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

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.TypeExpression;
import com.google.common.truth.extensions.proto.IterableOfProtosSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests inheritance processing for the {@link TypeInspector}. */
@RunWith(JUnit4.class)
public class TypeInspectorInheritanceTest extends AbstractTypeInspectorTest {

  private static final NamedType TYPE_A = namedType("A", "A.html");
  private static final NamedType TYPE_B = namedType("B", "B.html");
  private static final NamedType TYPE_C = namedType("C", "C.html");
  private static final NamedType TYPE_D = namedType("D", "D.html");
  private static final NamedType TYPE_E = namedType("E", "E.html");

  @Test
  public void interfaceThatExtendsOneInterface() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/**",
        " * @interface",
        " * @extends {A}",
        " */",
        "function B() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
    assertSubtypes(a).containsExactly(TYPE_B);

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).isEmpty();
  }

  @Test
  public void interfaceThatExtendsOneInterface_es6() {
    compile("/** @interface */ class A {}", "/** @interface */ class B extends A {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
    assertSubtypes(a).containsExactly(TYPE_B);

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).isEmpty();
  }

  @Test
  public void interfaceThatExtendsMultipleInterfaces() {
    compile(
        "/** @interface */ function A() {}",
        "/** @interface */ function B() {}",
        "",
        "/**",
        " * @interface",
        " * @extends {A}",
        " * @extends {B}",
        " */",
        "function C() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
    assertSubtypes(a).containsExactly(TYPE_C);

    assertImplementedTypes(b).isEmpty();
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).containsExactly(TYPE_C);

    assertImplementedTypes(c).containsExactly(TYPE_A, TYPE_B);
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).isEmpty();
  }

  @Test
  public void multipleLevelsOfInterfaceExtension_es5() {
    compile(
        "/** @interface */ function A() {}",
        "/** @interface @extends {A} */ function B() {}",
        "/** @interface @extends {B} */ function C() {}",
        "/** @interface @extends {C} */ function D() {}");

    checkMultipleLevelsOfInterfaceExtension();
  }

  @Test
  public void multipleLevelsOfInterfaceExtension_es6() {
    compile(
        "/** @interface */ class A {}",
        "/** @interface */ class B extends A {}",
        "/** @interface */ class C extends B {}",
        "/** @interface */ class D extends C {}");

    checkMultipleLevelsOfInterfaceExtension();
  }

  private void checkMultipleLevelsOfInterfaceExtension() {
    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");
    NominalType d = typeRegistry.getType("D");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
    assertSubtypes(a).containsExactly(TYPE_B, TYPE_C, TYPE_D);

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).containsExactly(TYPE_C, TYPE_D);

    assertImplementedTypes(c).containsExactly(TYPE_A, TYPE_B);
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).containsExactly(TYPE_D);

    assertImplementedTypes(d).containsExactly(TYPE_A, TYPE_B, TYPE_C);
    assertKnownImplementations(d).isEmpty();
    assertSubtypes(d).isEmpty();
  }

  @Test
  public void multipleBranchesOfInterfaceExtension() {
    compile(
        "/** @interface */ class A {}",
        "/** @interface */ class B extends A {}",
        "/** @interface */ class C extends A {}",
        "/** @interface @extends {B} */ class D extends C {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");
    NominalType d = typeRegistry.getType("D");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
    assertSubtypes(a).containsExactly(TYPE_B, TYPE_C, TYPE_D);

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).containsExactly(TYPE_D);

    assertImplementedTypes(c).containsExactly(TYPE_A);
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).containsExactly(TYPE_D);

    assertImplementedTypes(d).containsExactly(TYPE_A, TYPE_B, TYPE_C);
    assertKnownImplementations(d).isEmpty();
    assertSubtypes(d).isEmpty();
  }

  @Test
  public void classImplementsOneInterface() {
    compile("/** @interface */ class A {}", "/** @implements {A} */ class B {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_B);
    assertSubtypes(a).isEmpty();

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).isEmpty();
  }

  @Test
  public void templateTypeImplementsTemplateInterface() {
    compile(
        "/**",
        " * @template T",
        " * @interface",
        " */",
        "function A() {}",
        "",
        "/**",
        " * @constructor",
        " * @template TYPE",
        " * @implements {A<TYPE>}",
        " */",
        "function B() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");

    assertImplementedTypes(a).isEmpty();
    assertSubtypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_B);

    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).isEmpty();
    assertImplementedTypes(b)
        .containsExactly(addTemplateTypes(TYPE_A, namedTypeExpression("TYPE")));
  }

  @Test
  public void typeImplementsTemplatizedType() {
    compile(
        "/**",
        " * @template T",
        " * @interface",
        " */",
        "function A() {}",
        "",
        "/**",
        " * @constructor",
        " * @template TYPE",
        " * @implements {A<string>}",
        " */",
        "function B() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");

    assertImplementedTypes(a).isEmpty();
    assertSubtypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_B);

    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).isEmpty();
    assertImplementedTypes(b).containsExactly(addTemplateTypes(TYPE_A, stringTypeExpression()));
  }

  @Test
  public void implementedTypesForAnInterfaceDoesNotIncludeTheInterfaceItself() {
    compile("/**", " * @interface", " */", "function A() {}");

    NominalType a = typeRegistry.getType("A");
    assertImplementedTypes(a).isEmpty();
    assertSubtypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
  }

  @Test
  public void implementsMultipleInterfaces() {
    compile(
        "/** @interface */ function A() {}",
        "/** @interface */ function B() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {A}",
        " * @implements {B}",
        " */",
        "function C() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_C);
    assertSubtypes(a).isEmpty();

    assertImplementedTypes(b).isEmpty();
    assertKnownImplementations(b).containsExactly(TYPE_C);
    assertSubtypes(b).isEmpty();

    assertImplementedTypes(c).containsExactly(TYPE_A, TYPE_B);
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).isEmpty();
  }

  @Test
  public void templateTypeImplementsMultipleTemplateInterfaces() {
    compile(
        "/**",
        " * @template A_TYPE",
        " * @interface",
        " */",
        "function A() {}",
        "",
        "/**",
        " * @template B_TYPE",
        " * @interface",
        " */",
        "function B() {}",
        "",
        "/**",
        " * @constructor",
        " * @template SPECIFIC_TYPE",
        " * @implements {A<SPECIFIC_TYPE>}",
        " * @implements {B<SPECIFIC_TYPE>}",
        " */",
        "function C() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_C);
    assertSubtypes(a).isEmpty();

    assertImplementedTypes(b).isEmpty();
    assertKnownImplementations(b).containsExactly(TYPE_C);
    assertSubtypes(b).isEmpty();

    assertImplementedTypes(c)
        .containsExactly(
            addTemplateTypes(TYPE_A, namedTypeExpression("SPECIFIC_TYPE")),
            addTemplateTypes(TYPE_B, namedTypeExpression("SPECIFIC_TYPE")));
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).isEmpty();
  }

  @Test
  public void typeImplementsMultipleTemplateInterfacesWithSpecificTypes() {
    compile(
        "/**",
        " * @template A_TYPE",
        " * @interface",
        " */",
        "function A() {}",
        "",
        "/**",
        " * @template B_TYPE",
        " * @interface",
        " */",
        "function B() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {A<string>}",
        " * @implements {B<number>}",
        " */",
        "function C() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_C);
    assertSubtypes(a).isEmpty();

    assertImplementedTypes(b).isEmpty();
    assertKnownImplementations(b).containsExactly(TYPE_C);
    assertSubtypes(b).isEmpty();

    assertImplementedTypes(c)
        .containsExactly(
            addTemplateTypes(TYPE_A, stringTypeExpression()),
            addTemplateTypes(TYPE_B, numberTypeExpression()));
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).isEmpty();
  }

  @Test
  public void implementsInterfaceThatExtendsAnother_oneLevel() {
    compile(
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
        "function C() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_C);
    assertSubtypes(a).containsExactly(TYPE_B);

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).containsExactly(TYPE_C);
    assertSubtypes(b).isEmpty();

    assertImplementedTypes(c).containsExactly(TYPE_A, TYPE_B);
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).isEmpty();
  }

  @Test
  public void implementsInterfaceThatExtendsAnother_multipleLevels() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface @extends {A} */",
        "function B() {}",
        "",
        "/** @interface @extends {B} */",
        "function C() {}",
        "",
        "/** @interface @extends {C} */",
        "function D() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {D}",
        " */",
        "function E() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");
    NominalType d = typeRegistry.getType("D");
    NominalType e = typeRegistry.getType("E");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_E);
    assertSubtypes(a).containsExactly(TYPE_B, TYPE_C, TYPE_D);

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).containsExactly(TYPE_E);
    assertSubtypes(b).containsExactly(TYPE_C, TYPE_D);

    assertImplementedTypes(c).containsExactly(TYPE_A, TYPE_B);
    assertKnownImplementations(c).containsExactly(TYPE_E);
    assertSubtypes(c).containsExactly(TYPE_D);

    assertImplementedTypes(d).containsExactly(TYPE_A, TYPE_B, TYPE_C);
    assertKnownImplementations(d).containsExactly(TYPE_E);
    assertSubtypes(d).isEmpty();

    assertImplementedTypes(e).containsExactly(TYPE_A, TYPE_B, TYPE_C, TYPE_D);
    assertKnownImplementations(e).isEmpty();
    assertSubtypes(e).isEmpty();
  }

  @Test
  public void implementsInterfaceThatExtendsAnother_multipleBranches() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface @extends {A} */",
        "function B() {}",
        "",
        "/** @interface @extends {B} @extends {IThenable<string>} */",
        "function C() {}",
        "",
        "/** @interface @extends {C} */",
        "function D() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {D}",
        " */",
        "function E() {}",
        "/** @override */ E.prototype.then = function() {};");

    NamedType thenable =
        namedType("IThenable")
            .toBuilder()
            .setExtern(true)
            .addTemplateType(stringTypeExpression())
            .build();

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");
    NominalType d = typeRegistry.getType("D");
    NominalType e = typeRegistry.getType("E");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_E);
    assertSubtypes(a).containsExactly(TYPE_B, TYPE_C, TYPE_D);

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).containsExactly(TYPE_E);
    assertSubtypes(b).containsExactly(TYPE_C, TYPE_D);

    assertImplementedTypes(c).containsExactly(TYPE_A, TYPE_B, thenable);
    assertKnownImplementations(c).containsExactly(TYPE_E);
    assertSubtypes(c).containsExactly(TYPE_D);

    assertImplementedTypes(d).containsExactly(TYPE_A, TYPE_B, TYPE_C, thenable);
    assertKnownImplementations(d).containsExactly(TYPE_E);
    assertSubtypes(d).isEmpty();

    assertImplementedTypes(e).containsExactly(TYPE_A, TYPE_B, TYPE_C, TYPE_D, thenable);
    assertKnownImplementations(e).isEmpty();
    assertSubtypes(e).isEmpty();
  }

  @Test
  public void implementsInterfaceThatExtendsAnother_templatizedImplementations() {
    compile(
        "/**",
        " * @interface",
        " * @template ATYPE",
        " */",
        "function A() {}",
        "",
        "/**",
        " * @interface",
        " * @extends {A<BTYPE>}",
        " * @template BTYPE",
        " */",
        "function B() {}",
        "",
        "/** @constructor */",
        "function C() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {B<!C>}",
        " */",
        "function D() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType d = typeRegistry.getType("D");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_D);
    assertSubtypes(a).containsExactly(TYPE_B);

    assertImplementedTypes(b)
        .containsExactly(addTemplateTypes(TYPE_A, namedTypeExpression("BTYPE")));
    assertKnownImplementations(b).containsExactly(TYPE_D);
    assertSubtypes(b).isEmpty();

    assertImplementedTypes(d)
        .containsExactly(
            addTemplateTypes(TYPE_A, TypeExpression.newBuilder().setNamedType(TYPE_C).build()),
            addTemplateTypes(TYPE_B, TypeExpression.newBuilder().setNamedType(TYPE_C).build()));
    assertKnownImplementations(d).isEmpty();
    assertSubtypes(d).isEmpty();
  }

  @Test
  public void implementsInterfaceThatExtendsAnother_templatizedImplementations_ithenableCheck() {
    compile(
        "/**",
        " * @interface",
        " * @extends {IThenable<ATYPE>}",
        " * @template ATYPE",
        " */",
        "function A() {}",
        "",
        "/**",
        " * @interface",
        " * @extends {A<BTYPE>}",
        " * @template BTYPE",
        " */",
        "function B() {}",
        "",
        "/** @constructor */",
        "function C() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {B<!C>}",
        " */",
        "function D() {}",
        "/** @override */D.prototype.then = function() {};");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType d = typeRegistry.getType("D");

    NamedType thenable = namedType("IThenable").toBuilder().setExtern(true).build();

    assertImplementedTypes(a)
        .containsExactly(addTemplateTypes(thenable, namedTypeExpression("ATYPE")));
    assertKnownImplementations(a).containsExactly(TYPE_D);
    assertSubtypes(a).containsExactly(TYPE_B);

    assertImplementedTypes(b)
        .containsExactly(
            addTemplateTypes(TYPE_A, namedTypeExpression("BTYPE")),
            addTemplateTypes(thenable, namedTypeExpression("BTYPE")));
    assertKnownImplementations(b).containsExactly(TYPE_D);
    assertSubtypes(b).isEmpty();

    assertImplementedTypes(d)
        .containsExactly(
            addTemplateTypes(TYPE_A, TypeExpression.newBuilder().setNamedType(TYPE_C).build()),
            addTemplateTypes(TYPE_B, TypeExpression.newBuilder().setNamedType(TYPE_C).build()),
            addTemplateTypes(thenable, TypeExpression.newBuilder().setNamedType(TYPE_C).build()));
    assertKnownImplementations(d).isEmpty();
    assertSubtypes(d).isEmpty();
  }

  @Test
  public void implementsMultipleInterfacesThatExtendOthers() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/** @interface @extends {A} */",
        "function B() {}",
        "",
        "/** @interface*/",
        "function C() {}",
        "",
        "/** @interface @extends {C} */",
        "function D() {}",
        "",
        "/**",
        " * @constructor",
        " * @implements {B}",
        " * @implements {D}",
        " */",
        "function E() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");
    NominalType d = typeRegistry.getType("D");
    NominalType e = typeRegistry.getType("E");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).containsExactly(TYPE_E);
    assertSubtypes(a).containsExactly(TYPE_B);

    assertImplementedTypes(b).containsExactly(TYPE_A);
    assertKnownImplementations(b).containsExactly(TYPE_E);
    assertSubtypes(b).isEmpty();

    assertImplementedTypes(c).isEmpty();
    assertKnownImplementations(c).containsExactly(TYPE_E);
    assertSubtypes(c).containsExactly(TYPE_D);

    assertImplementedTypes(d).containsExactly(TYPE_C);
    assertKnownImplementations(d).containsExactly(TYPE_E);
    assertSubtypes(d).isEmpty();

    assertImplementedTypes(e).containsExactly(TYPE_A, TYPE_B, TYPE_C, TYPE_D);
    assertKnownImplementations(e).isEmpty();
    assertSubtypes(e).isEmpty();
  }

  @Test
  public void detectsImplementationsFromSuperType() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/** @implements {A} */",
        "class B {}",
        "class C extends B {}",
        "class D extends C {}");

    NominalType d = typeRegistry.getType("D");
    assertImplementedTypes(d).containsExactly(TYPE_A);

    NominalType a = typeRegistry.getType("A");
    assertKnownImplementations(a).containsExactly(TYPE_B, TYPE_C, TYPE_D);
  }

  @Test
  public void detectsImplementationsFromSuperType_duplicateImplementDeclarations() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/** @implements {A} */",
        "class B {}",
        "",
        "/** @implements {A} */",
        "class C extends B {}",
        "class D extends C {}");

    NominalType d = typeRegistry.getType("D");
    assertImplementedTypes(d).containsExactly(TYPE_A);
  }

  @Test
  public void detectsImplementationsFromSuperType_multipleImplementations() {
    compile(
        "/** @interface */ class A {}",
        "/** @interface */ class B {}",
        "",
        "/** @implements {A} */ class C {}",
        "/** @implements {B} */ class D extends C {}",
        "class E extends D {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType e = typeRegistry.getType("E");

    assertKnownImplementations(a).containsExactly(TYPE_C, TYPE_D, TYPE_E);
    assertKnownImplementations(b).containsExactly(TYPE_D, TYPE_E);
    assertImplementedTypes(e).containsExactly(TYPE_A, TYPE_B);
  }

  @Test
  public void detectsImplementationsFromSuperType_multipleTemplatizedImplementations() {
    compile(
        "/**",
        " * @template A_TYPE",
        " * @interface",
        " */",
        "class A {}",
        "",
        "/**",
        " * @template B_TYPE",
        " * @interface",
        " */",
        "class B {}",
        "",
        "/** @implements {A<string>} */ class C {}",
        "/** @implements {B<number>} */ class D extends C {}",
        "class E extends D {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType e = typeRegistry.getType("E");

    assertKnownImplementations(a).containsExactly(TYPE_C, TYPE_D, TYPE_E);
    assertKnownImplementations(b).containsExactly(TYPE_D, TYPE_E);
    assertImplementedTypes(e)
        .containsExactly(
            addTemplateTypes(TYPE_A, stringTypeExpression()),
            addTemplateTypes(TYPE_B, numberTypeExpression()));
  }

  @Test
  public void detectsImplementationsFromSuperType_cascadingTemplateTypes() {
    compile(
        "/**",
        " * @template A_TYPE",
        " * @interface",
        " */",
        "class A {}",
        "",
        "/**",
        " * @template B_TYPE",
        " * @interface",
        " */",
        "class B {}",
        "",
        "/**",
        " * @template C_TYPE",
        " * @implements {A<C_TYPE>}",
        " */",
        "class C {}",
        "",
        "/**",
        " * @template D_TYPE",
        " * @extends {C<D_TYPE>}",
        " * @implements {B<D_TYPE>}",
        " */",
        "class D extends C {}",
        "",
        "/**",
        " * @template E_TYPE",
        " * @extends {D<E_TYPE>}",
        " */",
        "class E extends D {}");

    NominalType e = typeRegistry.getType("E");

    assertImplementedTypes(e)
        .containsExactly(
            addTemplateTypes(TYPE_A, namedTypeExpression("E_TYPE")),
            addTemplateTypes(TYPE_B, namedTypeExpression("E_TYPE")));
  }

  @Test
  public void getHierarchyForBaseClass() {
    compile("class A {}");

    NominalType type = typeRegistry.getType("A");
    assertTypeHierarchy(type).containsExactly(TYPE_A);
  }

  @Test
  public void getHierarchyForOneLevelOfInheritance() {
    compile("class A {}", "class B extends A {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");

    assertTypeHierarchy(a).containsExactly(TYPE_A);
    assertSubtypes(a).containsExactly(TYPE_B);

    assertTypeHierarchy(b).containsExactly(TYPE_B, TYPE_A);
    assertSubtypes(b).isEmpty();
  }

  @Test
  public void getHierarchyForMultipleLevels() {
    compile("class A {}", "class B extends A {}", "class C extends B {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertTypeHierarchy(a).containsExactly(TYPE_A);
    assertSubtypes(a).containsExactly(TYPE_B);

    assertTypeHierarchy(b).containsExactly(TYPE_B, TYPE_A);
    assertSubtypes(b).containsExactly(TYPE_C);

    assertTypeHierarchy(c).containsExactly(TYPE_C, TYPE_B, TYPE_A);
    assertSubtypes(c).isEmpty();
  }

  @Test
  public void getHierarchyForMultipleLevels_es5Constructors() {
    compile(
        "/** @constructor */ function A() {}",
        "/** @constructor @extends {A} */ function B() {}",
        "/** @constructor @extends {B} */ function C() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertTypeHierarchy(a).containsExactly(TYPE_A);
    assertSubtypes(a).containsExactly(TYPE_B);

    assertTypeHierarchy(b).containsExactly(TYPE_B, TYPE_A);
    assertSubtypes(b).containsExactly(TYPE_C);

    assertTypeHierarchy(c).containsExactly(TYPE_C, TYPE_B, TYPE_A);
    assertSubtypes(c).isEmpty();
  }

  @Test
  public void getHierarchyForTemplatizedTypes_es5Constructors() {
    compile(
        "/**",
        " * @constructor",
        " * @template A_TYPE",
        " */",
        "function A() {}",
        "/**",
        " * @constructor",
        " * @extends {A<B_TYPE>}",
        " * @template B_TYPE",
        " */",
        "function B() {}",
        "/**",
        " * @constructor",
        " * @extends {B<C_TYPE>}",
        " * @template C_TYPE",
        " */",
        "function C() {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
    assertSubtypes(a).containsExactly(addTemplateTypes(TYPE_B, namedTypeExpression("B_TYPE")));
    assertTypeHierarchy(a).containsExactly(addTemplateTypes(TYPE_A, namedTypeExpression("A_TYPE")));

    assertImplementedTypes(b).isEmpty();
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).containsExactly(addTemplateTypes(TYPE_C, namedTypeExpression("C_TYPE")));
    assertTypeHierarchy(b)
        .containsExactly(
            addTemplateTypes(TYPE_B, namedTypeExpression("B_TYPE")),
            addTemplateTypes(TYPE_A, namedTypeExpression("B_TYPE")));

    assertImplementedTypes(c).isEmpty();
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).isEmpty();
    assertTypeHierarchy(c)
        .containsExactly(
            addTemplateTypes(TYPE_C, namedTypeExpression("C_TYPE")),
            addTemplateTypes(TYPE_B, namedTypeExpression("C_TYPE")),
            addTemplateTypes(TYPE_A, namedTypeExpression("C_TYPE")));
  }

  @Test
  public void getHierarchyForTemplatizedTypes_es6Classes() {
    compile(
        "/**",
        " * @template A_TYPE",
        " */",
        "class A {}",
        "/**",
        " * @extends {A<B_TYPE>}",
        " * @template B_TYPE",
        " */",
        "class B extends A {}",
        "/**",
        " * @extends {B<C_TYPE>}",
        " * @template C_TYPE",
        " */",
        "class C extends B {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
    assertSubtypes(a).containsExactly(addTemplateTypes(TYPE_B, namedTypeExpression("B_TYPE")));
    assertTypeHierarchy(a).containsExactly(addTemplateTypes(TYPE_A, namedTypeExpression("A_TYPE")));

    assertImplementedTypes(b).isEmpty();
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).containsExactly(addTemplateTypes(TYPE_C, namedTypeExpression("C_TYPE")));
    assertTypeHierarchy(b)
        .containsExactly(
            addTemplateTypes(TYPE_B, namedTypeExpression("B_TYPE")),
            addTemplateTypes(TYPE_A, namedTypeExpression("B_TYPE")));

    assertImplementedTypes(c).isEmpty();
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).isEmpty();
    assertTypeHierarchy(c)
        .containsExactly(
            addTemplateTypes(TYPE_C, namedTypeExpression("C_TYPE")),
            addTemplateTypes(TYPE_B, namedTypeExpression("C_TYPE")),
            addTemplateTypes(TYPE_A, namedTypeExpression("C_TYPE")));
  }

  @Test
  public void getHierarchyForTemplatizedTypes_leafHasConcreteType() {
    compile(
        "/**",
        " * @template A_TYPE",
        " */",
        "class A {}",
        "/**",
        " * @extends {A<B_TYPE>}",
        " * @template B_TYPE",
        " */",
        "class B extends A {}",
        "/**",
        " * @extends {B<C_TYPE>}",
        " * @template C_TYPE",
        " */",
        "class C extends B {}",
        "",
        "/** @extends {C<string>} */",
        "class D extends C {}");

    NominalType a = typeRegistry.getType("A");
    NominalType b = typeRegistry.getType("B");
    NominalType c = typeRegistry.getType("C");
    NominalType d = typeRegistry.getType("D");

    assertImplementedTypes(a).isEmpty();
    assertKnownImplementations(a).isEmpty();
    assertSubtypes(a).containsExactly(addTemplateTypes(TYPE_B, namedTypeExpression("B_TYPE")));
    assertTypeHierarchy(a).containsExactly(addTemplateTypes(TYPE_A, namedTypeExpression("A_TYPE")));

    assertImplementedTypes(b).isEmpty();
    assertKnownImplementations(b).isEmpty();
    assertSubtypes(b).containsExactly(addTemplateTypes(TYPE_C, namedTypeExpression("C_TYPE")));
    assertTypeHierarchy(b)
        .containsExactly(
            addTemplateTypes(TYPE_B, namedTypeExpression("B_TYPE")),
            addTemplateTypes(TYPE_A, namedTypeExpression("B_TYPE")));

    assertImplementedTypes(c).isEmpty();
    assertKnownImplementations(c).isEmpty();
    assertSubtypes(c).containsExactly(TYPE_D);
    assertTypeHierarchy(c)
        .containsExactly(
            addTemplateTypes(TYPE_C, namedTypeExpression("C_TYPE")),
            addTemplateTypes(TYPE_B, namedTypeExpression("C_TYPE")),
            addTemplateTypes(TYPE_A, namedTypeExpression("C_TYPE")));

    assertImplementedTypes(d).isEmpty();
    assertKnownImplementations(d).isEmpty();
    assertSubtypes(d).isEmpty();
    assertTypeHierarchy(d)
        .containsExactly(
            TYPE_D,
            addTemplateTypes(TYPE_C, stringTypeExpression()),
            addTemplateTypes(TYPE_B, stringTypeExpression()),
            addTemplateTypes(TYPE_A, stringTypeExpression()));
  }

  @Test
  public void tracksSubTypesFromDifferentModulesWithSameBaseName() {
    util.compile(
        createSourceFile(fs.getPath("/src/modules/one.js"), "export class A {}"),
        createSourceFile(
            fs.getPath("/src/modules/two.js"),
            "import {A} from './one';",
            "export class B extends A {}"),
        createSourceFile(
            fs.getPath("/src/modules/three.js"),
            "import {A} from './one';",
            "export class B extends A {}"));

    NominalType a = typeRegistry.getType("module$src$modules$one.A");
    assertSubtypes(a)
        .containsExactly(
            TYPE_B
                .toBuilder()
                .setQualifiedName("three.B")
                .setLink(typeLink("three_exports_B.html"))
                .build(),
            TYPE_B
                .toBuilder()
                .setQualifiedName("two.B")
                .setLink(typeLink("two_exports_B.html"))
                .build());
  }

  private IterableOfProtosSubject<?, NamedType, Iterable<NamedType>> assertImplementedTypes(
      NominalType type) {
    return assertThat(typeInspectorFactory.create(type).getImplementedTypes());
  }

  private IterableOfProtosSubject<?, NamedType, Iterable<NamedType>> assertKnownImplementations(
      NominalType type) {
    return assertThat(typeInspectorFactory.create(type).getKnownImplementations());
  }

  private IterableOfProtosSubject<?, NamedType, Iterable<NamedType>> assertSubtypes(
      NominalType type) {
    return assertThat(typeInspectorFactory.create(type).getSubtypes());
  }

  private IterableOfProtosSubject<?, NamedType, Iterable<NamedType>> assertTypeHierarchy(
      NominalType type) {
    return assertThat(typeInspectorFactory.create(type).getTypeHierarchy());
  }
}
