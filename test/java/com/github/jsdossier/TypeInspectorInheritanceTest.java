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

import static com.github.jsdossier.ProtoTruth.assertMessages;

import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.proto.TypeExpression;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests inheritance processing for the {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorInheritanceTest extends AbstractTypeInspectorTest {
  @Test
  public void getImplementedTypes_interfaceExtendsOneInterface() {
    compile(
        "/** @interface */",
        "function A() {}",
        "",
        "/**",
        " * @interface",
        " * @extends {A}",
        " */",
        "function B() {}");

    NominalType impl = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(namedType("A", "A.html")));
  }

  @Test
  public void getImplementedTypes_interfaceExtendsOneInterface_es6() {
    compile(
        "/** @interface */ class A {}",
        "/** @interface */ class B extends A {}");

    NominalType impl = typeRegistry.getType("B");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(namedType("A", "A.html")));
  }

  @Test
  public void getImplementedTypes_interfaceExtendsMultipleInterfaces() {
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

    NominalType impl = typeRegistry.getType("C");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")),
        TypeExpression.newBuilder().setNamedType(namedType("B", "B.html")));
  }

  @Test
  public void getImplementedTypes_implementsOneInterface() {
    compile(
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

    NominalType impl = typeRegistry.getType("RunnableImpl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(namedType("Runnable", "Runnable.html")));
  }

  @Test
  public void templateTypeImplementsTemplateInterface() {
    compile(
        "/**",
        " * @template T",
        " * @interface",
        " */",
        "function Container() {}",
        "",
        "/**",
        " * @constructor",
        " * @template TYPE",
        " * @implements {Container<TYPE>}",
        " */",
        "function ContainerImpl() {}");

    NominalType impl = typeRegistry.getType("ContainerImpl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(
                namedType("Container", "Container.html")
                    .toBuilder()
                    .addTemplateType(namedTypeExpression("TYPE"))));
  }

  @Test
  public void typeImplementsTemplatizedType() {
    compile(
        "/**",
        " * @template T",
        " * @interface",
        " */",
        "function Container() {}",
        "",
        "/**",
        " * @constructor",
        " * @template TYPE",
        " * @implements {Container<string>}",
        " */",
        "function ContainerImpl() {}");

    NominalType impl = typeRegistry.getType("ContainerImpl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(
                namedType("Container", "Container.html")
                    .toBuilder()
                    .addTemplateType(stringTypeExpression())));
  }

  @Test
  public void implementedTypesForAnInterfaceDoesNotIncludeTheInterfaceItself() {
    compile(
        "/**",
        " * @interface",
        " */",
        "function Container() {}");

    NominalType impl = typeRegistry.getType("Container");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);
    assertMessages(typeInspector.getImplementedTypes()).isEmpty();
  }

  @Test
  public void implementsMultipleInterfaces() {
    compile(
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

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")),
        TypeExpression.newBuilder().setNamedType(namedType("B", "B.html")));
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
        "function Impl() {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(namedType("A", "A.html")
                .toBuilder()
                .addTemplateType(namedTypeExpression("SPECIFIC_TYPE"))),
        TypeExpression.newBuilder()
            .setNamedType(namedType("B", "B.html")
                .toBuilder()
                .addTemplateType(namedTypeExpression("SPECIFIC_TYPE"))));
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
        "function Impl() {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(namedType("A", "A.html")
                .toBuilder()
                .addTemplateType(stringTypeExpression())),
        TypeExpression.newBuilder()
            .setNamedType(namedType("B", "B.html")
                .toBuilder()
                .addTemplateType(numberTypeExpression())));
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
        "function Impl() {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("B", "B.html")),
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")));
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
        "function Impl() {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("D", "D.html")),
        TypeExpression.newBuilder().setNamedType(namedType("C", "C.html")),
        TypeExpression.newBuilder().setNamedType(namedType("B", "B.html")),
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")));
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
        "function Impl() {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("D", "D.html")),
        TypeExpression.newBuilder().setNamedType(namedType("C", "C.html")),
        TypeExpression.newBuilder().setNamedType(namedType("B", "B.html")),
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")),
        TypeExpression.newBuilder()
            .setNamedType(
                namedType(
                    "IThenable",
                    "https://github.com/google/closure-compiler/wiki/" +
                        "Special-types-in-the-Closure-Type-System#ithenable")
                    .toBuilder()
                    .addTemplateType(stringTypeExpression())));
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
        "function Impl() {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("B", "B.html")),
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")),
        TypeExpression.newBuilder().setNamedType(namedType("D", "D.html")),
        TypeExpression.newBuilder().setNamedType(namedType("C", "C.html")));
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
        "class Impl extends C {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")));
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
        "class Impl extends C {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")));
  }

  @Test
  public void detectsImplementationsFromSuperType_multipleImplementations() {
    compile(
        "/** @interface */ class A {}",
        "/** @interface */ class B {}",
        "",
        "/** @implements {A} */ class One {}",
        "/** @implements {B} */ class Two extends One {}",
        "class Impl extends Two {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder().setNamedType(namedType("B", "B.html")),
        TypeExpression.newBuilder().setNamedType(namedType("A", "A.html")));
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
        "/** @implements {A<string>} */ class One {}",
        "/** @implements {B<number>} */ class Two extends One {}",
        "class Impl extends Two {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(namedType("B", "B.html")
                .toBuilder()
                .addTemplateType(numberTypeExpression())),
        TypeExpression.newBuilder()
            .setNamedType(namedType("A", "A.html")
                .toBuilder()
                .addTemplateType(stringTypeExpression())));
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
        " * @template ONE_TYPE",
        " * @implements {A<ONE_TYPE>}",
        " */",
        "class One {}",
        "",
        "/**",
        " * @template TWO_TYPE",
        " * @extends {One<TWO_TYPE>}",
        " * @implements {B<TWO_TYPE>}",
        " */",
        "class Two extends One {}",
        "",
        "/**",
        " * @template IMPL_TYPE",
        " * @extends {Two<IMPL_TYPE>}",
        " */",
        "class Impl extends Two {}");

    NominalType impl = typeRegistry.getType("Impl");
    TypeInspector typeInspector = typeInspectorFactory.create(impl);

    assertMessages(typeInspector.getImplementedTypes()).containsExactly(
        TypeExpression.newBuilder()
            .setNamedType(namedType("B", "B.html")
                .toBuilder()
                .addTemplateType(namedTypeExpression("IMPL_TYPE"))),
        TypeExpression.newBuilder()
            .setNamedType(namedType("A", "A.html")
                .toBuilder()
                .addTemplateType(namedTypeExpression("IMPL_TYPE"))));
  }

  @Test
  public void getHierarchyForBaseClass() {
    compile("class Base {}");

    NominalType type = typeRegistry.getType("Base");
    TypeInspector typeInspector = typeInspectorFactory.create(type);

    assertMessages(typeInspector.getTypeHierarchy()).containsExactly(
        namedTypeExpression("Base", "Base.html"));
  }

  @Test
  public void getHierarchyForOneLevelOfInheritance() {
    compile(
        "class Base {}",
        "class Type extends Base {}");

    NominalType type = typeRegistry.getType("Type");
    TypeInspector typeInspector = typeInspectorFactory.create(type);

    assertMessages(typeInspector.getTypeHierarchy()).containsExactly(
        namedTypeExpression("Type", "Type.html"),
        namedTypeExpression("Base", "Base.html"));
  }

  @Test
  public void getHierarchyForMultipleLevels() {
    compile(
        "class Base {}",
        "class Two extends Base {}",
        "class Three extends Two {}");

    NominalType type = typeRegistry.getType("Three");
    TypeInspector typeInspector = typeInspectorFactory.create(type);

    assertMessages(typeInspector.getTypeHierarchy()).containsExactly(
        namedTypeExpression("Three", "Three.html"),
        namedTypeExpression("Two", "Two.html"),
        namedTypeExpression("Base", "Base.html"));
  }

  @Test
  public void getHierarchyForMultipleLevels_es5Constructors() {
    compile(
        "/** @constructor */ function Base() {}",
        "/** @constructor @extends {Base} */ function Two() {}",
        "/** @constructor @extends {Two} */ function Three() {}");

    NominalType type = typeRegistry.getType("Three");
    TypeInspector typeInspector = typeInspectorFactory.create(type);

    assertMessages(typeInspector.getTypeHierarchy()).containsExactly(
        namedTypeExpression("Three", "Three.html"),
        namedTypeExpression("Two", "Two.html"),
        namedTypeExpression("Base", "Base.html"));
  }

  @Test
  public void getHierarchyForTemplatizedTypes_es5Constructors() {
    compile(
        "/**",
        " * @constructor",
        " * @template BASE_TYPE",
        " */",
        "function Base() {}",
        "/**",
        " * @constructor",
        " * @extends {Base<TWO_TYPE>}",
        " * @template TWO_TYPE",
        " */",
        "function Two() {}",
        "/**",
        " * @constructor",
        " * @extends {Two<TYPE>}",
        " * @template TYPE",
        " */",
        "function Three() {}");

    NominalType type = typeRegistry.getType("Three");
    TypeInspector typeInspector = typeInspectorFactory.create(type);

    assertMessages(typeInspector.getTypeHierarchy()).containsExactly(
        namedTypeExpression("Three", "Three.html"),
        addTemplateTypes(
            namedTypeExpression("Two", "Two.html"),
            namedTypeExpression("TYPE")),
        addTemplateTypes(
            namedTypeExpression("Base", "Base.html"),
            namedTypeExpression("TYPE")));
  }

  @Test
  public void getHierarchyForTemplatizedTypes_es6Classes() {
    compile(
        "/**",
        " * @template BASE_TYPE",
        " */",
        "class Base {}",
        "/**",
        " * @extends {Base<TWO_TYPE>}",
        " * @template TWO_TYPE",
        " */",
        "class Two extends Base {}",
        "/**",
        " * @extends {Two<TYPE>}",
        " * @template TYPE",
        " */",
        "class Three extends Two {}");

    NominalType type = typeRegistry.getType("Three");
    TypeInspector typeInspector = typeInspectorFactory.create(type);

    assertMessages(typeInspector.getTypeHierarchy()).containsExactly(
        namedTypeExpression("Three", "Three.html"),
        addTemplateTypes(
            namedTypeExpression("Two", "Two.html"),
            namedTypeExpression("TYPE")),
        addTemplateTypes(
            namedTypeExpression("Base", "Base.html"),
            namedTypeExpression("TYPE")));
  }

  @Test
  public void getHierarchyForTemplatizedTypes_rootHasConcreteType() {
    compile(
        "/**",
        " * @template BASE_TYPE",
        " */",
        "class Base {}",
        "/**",
        " * @extends {Base<TWO_TYPE>}",
        " * @template TWO_TYPE",
        " */",
        "class Two extends Base {}",
        "/**",
        " * @extends {Two<TYPE>}",
        " * @template TYPE",
        " */",
        "class Three extends Two {}",
        "",
        "/** @extends {Three<string>} */",
        "class Four extends Three {}");

    NominalType type = typeRegistry.getType("Four");
    TypeInspector typeInspector = typeInspectorFactory.create(type);

    assertMessages(typeInspector.getTypeHierarchy()).containsExactly(
        namedTypeExpression("Four", "Four.html"),
        addTemplateTypes(
            namedTypeExpression("Three", "Three.html"),
            stringTypeExpression()),
        addTemplateTypes(
            namedTypeExpression("Two", "Two.html"),
            stringTypeExpression()),
        addTemplateTypes(
            namedTypeExpression("Base", "Base.html"),
            stringTypeExpression()));
  }
}
