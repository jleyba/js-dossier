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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.base.Predicate;
import com.google.javascript.jscomp.CompilerOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;

import javax.inject.Inject;

/**
 * Tests for {@link TypeCollectionPass}.
 */
@RunWith(JUnit4.class)
public class TypeCollectionPassTest {
  
  @Rule
  public GuiceRule guice = GuiceRule.builder(this)
      .setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT6)
      .build();

  @Inject @Input private FileSystem fs;
  @Inject private CompilerUtil util;
  @Inject private TypeRegistry2 typeRegistry;
  
  @Test
  public void collectsGlobalClasses_functionDeclaration() {
    util.compile(fs.getPath("foo.js"),
        "/** @constructor */",
        "function One() {}");
    NominalType2 type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 2, 9);
  }

  @Test
  public void collectsGlobalClasses_functionExpression() {
    util.compile(fs.getPath("foo.js"),
        "/** @constructor */",
        "var One = function() {};",
        "",
        "/** @constructor */",
        "let Two = function() {};",
        "",
        "/** @constructor */",
        "const Three = function() {};");
    NominalType2 type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 2, 4);

    type = typeRegistry.getType("Two");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 5, 4);

    type = typeRegistry.getType("Three");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 8, 6);
  }

  @Test
  public void collectsGlobalClasses_classDeclaration() {
    util.compile(fs.getPath("foo.js"),
        "class One {}");
    NominalType2 type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 1, 6);
  }

  @Test
  public void collectsGlobalClasses_classExpression() {
    util.compile(fs.getPath("foo.js"),
        "var One = class {};",
        "let Two = class {};",
        "const Three = class {};");
    NominalType2 type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 1, 4);

    type = typeRegistry.getType("Two");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 2, 4);

    type = typeRegistry.getType("Three");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 3, 6);
  }
  
  @Test
  public void collectsGlobalClasses_namedFunctionExpression() {
    util.compile(fs.getPath("foo.js"),
        "/** @constructor */",
        "var One = function Two() {};");

    NominalType2 type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 2, 4);
    
    assertThat(typeRegistry.isType("Two")).isFalse();
  }
  
  @Test
  public void collectsGlobalClasses_namedClassExpression() {
    util.compile(fs.getPath("foo.js"),
        "var One = class Two {};");

    NominalType2 type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 1, 4);
    
    assertThat(typeRegistry.isType("Two")).isFalse();
  }
  
  @Test
  public void collectsGlobalClasses_aliasedType() {
    util.compile(fs.getPath("foo.js"),
        "const One = class {};",
        "const Two = One;");

    NominalType2 one = typeRegistry.getType("One");
    assertConstructor(one);
    assertNoModule(one);
    assertPath(one, "foo.js");
    assertPosition(one, 1, 6);

    NominalType2 two = typeRegistry.getType("Two");
    assertConstructor(two);
    assertNoModule(two);
    assertPath(two, "foo.js");
    assertPosition(two, 2, 6);
    
    assertThat(typeRegistry.getTypes(one.getType()))
        .containsExactly(one, two)
        .inOrder();
  }
  
  @Test
  public void collectsGlobalClasses_googDefinedClass() {
    util.compile(fs.getPath("foo.js"),
        "const One = goog.defineClass(null, {constructor: function() {}});");

    NominalType2 one = typeRegistry.getType("One");
    assertConstructor(one);
    assertNoModule(one);
    assertPath(one, "foo.js");
    assertPosition(one, 1, 6);
  }
  
  @Test
  public void doesNotRecordExternAliasAsANominalType() {
    util.compile(fs.getPath("foo.js"),
        "const One = Date;",
        "var Two = Date;",
        "let Three = Date;",
        "/** @type {function(new: Date)} */",
        "let Four = Date;");
    
    assertThat(typeRegistry.isType("One")).isFalse();
    assertThat(typeRegistry.isType("Two")).isFalse();
    assertThat(typeRegistry.isType("Three")).isFalse();
    assertThat(typeRegistry.isType("Four")).isFalse();
  }
  
  @Test
  public void collectsGlobalInterfaces() {
    util.compile(fs.getPath("foo.js"),
        "/** @interface */",
        "const One = function() {};",
        "/** @interface */",
        "const Two = goog.defineClass(null, {});",
        "/** @interface */",
        "const Three = class {};");
    
    assertInterface(typeRegistry.getType("One"));
    assertInterface(typeRegistry.getType("Two"));
    assertInterface(typeRegistry.getType("Three"));
  }
  
  @Test
  public void recordsGlobalEnums() {
    util.compile(fs.getPath("foo/bar.js"),
        "/** @enum */",
        "const Foo = {};");
    NominalType2 foo = typeRegistry.getType("Foo");
    assertEnum(foo);
    assertNoModule(foo);
    assertPath(foo, "foo/bar.js");
    assertPosition(foo, 2, 6);
  }
  
  @Test
  public void recordsGlobalTypedefs() {
    util.compile(fs.getPath("foo/bar.js"),
        "/** @typedef {string} */",
        "var Foo;");
    NominalType2 foo = typeRegistry.getType("Foo");
    assertTypedef(foo);
    assertNoModule(foo);
    assertPath(foo, "foo/bar.js");
    assertPosition(foo, 2, 4);
  }

  @Test
  public void doesNotRecordUnprovidedObjectsAsANominalType() {
    util.compile(fs.getPath("foo/bar.js"), "const foo = {};");
    assertThat(typeRegistry.isType("foo")).isFalse();
  }
  
  @Test
  public void recordsGoogModuleExportsAsNominalType() {
    util.compile(fs.getPath("foo/bar.js"),
        "goog.module('foo');",
        "exports.Bar = class {};");
    
    NominalType2 type = typeRegistry.getType("foo");
    assertNamespace(type);
    assertPath(type, "foo/bar.js");
    assertPosition(type, 1, 13);
    assertModule(type, Module.Type.CLOSURE, "foo", "foo/bar.js");
  }
  
  @Test
  public void recordsNodeModuleExportsAsNominalType() {
    defineInputModules("modules", "foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"),
        "exports.Bar = {}");

    NominalType2 type = typeRegistry.getType("dossier$$module__modules$foo$bar");
    assertNamespace(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 1, 1);
    assertModule(type, Module.Type.NODE, "dossier$$module__modules$foo$bar", "modules/foo/bar.js");
  }
  
  @Test
  public void recordsEs6ModuleExportsAsNominalType1() {
    testRecordsEs6ModuleExportsAsNominalType("export function foo() {}");
  }
  
  @Test
  public void recordsEs6ModuleExportsAsNominalType2() {
    testRecordsEs6ModuleExportsAsNominalType("export default class {}");
  }
  
  @Test
  public void recordsEs6ModuleExportsAsNominalType3() {
    testRecordsEs6ModuleExportsAsNominalType(
        "export default class {}",
        "export class A {}");
  }
  
  @Test
  public void recordsEs6ModuleExportsAsNominalType4() {
    testRecordsEs6ModuleExportsAsNominalType(
        "export function each() {}",
        "export { each as forEach }");
  }
  
  @Test
  public void recordsEs6ModuleExportsAsNominalType5() {
    testRecordsEs6ModuleExportsAsNominalType(
        "export function each() {}",
        "export { each as default }");
  }
  
  private void testRecordsEs6ModuleExportsAsNominalType(String... source) {
    util.compile(fs.getPath("foo/bar.js"), source);

    NominalType2 type = typeRegistry.getType("module$foo$bar");
    assertNamespace(type);
    assertPath(type, "foo/bar.js");
    assertPosition(type, 1, 1);
    assertModule(type, Module.Type.ES6, "module$foo$bar", "foo/bar.js");
  }
  
  @Test
  public void doesNotDoubleRecordEs6ModulesAsNodeModules() {
    defineInputModules("modules", "foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"),
        "export function foo() {};");

    NominalType2 type = typeRegistry.getType("module$modules$foo$bar");
    assertNamespace(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 1, 1);
    assertModule(type, Module.Type.ES6, "module$modules$foo$bar", "modules/foo/bar.js");
  }
  
  @Test
  public void recordsGoogProvidedType() {
    util.compile(fs.getPath("types.js"),
        "goog.provide('foo.bar.Baz');",
        "foo.bar.Baz = class {}");

    assertNamespace(typeRegistry.getType("foo"));
    assertNamespace(typeRegistry.getType("foo.bar"));
    assertConstructor(typeRegistry.getType("foo.bar.Baz"));
  }
  
  @Test
  public void documentsNestedTypes() {
    util.compile(fs.getPath("foo/bar.js"),
        "goog.provide('foo.bar');",
        "/** @constructor */",
        "foo.bar.Bim = function() {};",
        "/** @constructor */",
        "foo.bar.Bim.Baz = function() {};");

    assertNamespace(typeRegistry.getType("foo"));
    assertNamespace(typeRegistry.getType("foo.bar"));
    assertConstructor(typeRegistry.getType("foo.bar.Bim"));
    assertConstructor(typeRegistry.getType("foo.bar.Bim.Baz"));
  }
  
  @Test
  public void functionVariablesAreNotDocumentedAsConstructors() {
    util.compile(fs.getPath("foo/bar.js"),
        "goog.provide('foo');",
        "/** @type {!Function} */",
        "foo.bar = function() {};",
        "/** @type {!Function} */",
        "foo.baz = function() {};");
    
    assertNamespace(typeRegistry.getType("foo"));
    assertThat(typeRegistry.isType("foo.bar")).isFalse();
    assertThat(typeRegistry.isType("foo.baz")).isFalse();
  }
  
  @Test
  public void functionInstancesAreNotDocumentedAsConstructors() {
    util.compile(fs.getPath("foo/bar.js"),
        "goog.provide('foo');",
        "/** @type {!Function} */",
        "foo.bar = Function;");
    
    assertNamespace(typeRegistry.getType("foo"));
    assertThat(typeRegistry.isType("foo.bar")).isFalse();
  }
  
  @Test
  public void onlyDocumentsExportedModuleTypes_closure() {
    util.compile(fs.getPath("foo/bar.js"),
        "goog.module('foo');",
        "class InternalClass {}",
        "exports.Foo = class {};");
    assertNamespace(typeRegistry.getType("foo"));
    assertConstructor(typeRegistry.getType("foo.Foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);
  }
  
  @Test
  public void onlyDocumentsExportedModuleTypes_node() {
    defineInputModules("modules", "foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"),
        "class InternalClass {}",
        "exports.Foo = class {};");
    assertNamespace(typeRegistry.getType("dossier$$module__modules$foo$bar"));
    assertConstructor(typeRegistry.getType("dossier$$module__modules$foo$bar.Foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);
  }
  
  @Test
  public void onlyDocumentsExportedModuleTypes_es6() {
    defineInputModules("modules", "foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"),
        "class InternalClass {}",
        "export { InternalClass as Foo }");
    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertConstructor(typeRegistry.getType("module$modules$foo$bar.Foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);
  }
  
  @Test
  public void documentsEs6DefaultExports1() {
    defineInputModules("modules", "foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"),
        "class InternalClass {}",
        "export { InternalClass as default }");
    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);
  }
  
  @Test
  public void documentsEs6DefaultExports2() {
    defineInputModules("modules", "foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"),
        "class InternalClass {}",
        "export default InternalClass;");
    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);
  }

  @Test
  public void doesNotDocumentCtorReferencesAsNestedTypes() {
    util.compile(fs.getPath("module.js"),
        "goog.provide('foo');",
        "",
        "/** @constructor */",
        "foo.Bar = function() {};",
        "",
        "/** @type {function(new: foo.Bar)} */",
        "foo.Baz = foo.Bar",
        "",
        "/** @private {function(new: foo.Bar)} */",
        "foo.PrivateBar = foo.Bar",
        "",
        "/** @protected {function(new: foo.Bar)} */",
        "foo.ProtectedBar = foo.Bar",
        "",
        "/** @public {function(new: foo.Bar)} */",
        "foo.PublicBar = foo.Bar");
    assertNamespace(typeRegistry.getType("foo"));
    assertConstructor(typeRegistry.getType("foo.Bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);
  }
  
  @Test
  public void functionAliasDetection() {
    util.compile(fs.getPath("foo/bar.js"),
        // Provide everything so dossier consider them namespaces worth documenting.
        "goog.provide('foo.one');",
        "goog.provide('foo.two');",
        "goog.provide('foo.three');",
        "goog.provide('foo.four');",
        "goog.provide('foo.five');",
        "goog.provide('foo.six');",
        "",
        "foo.one = function() {};",
        "foo.one.a = {b: 123};",
        "",
        "foo.two = function() {};",
        "foo.two.a = {b: 'abc'};",
        "",
        "foo.three = function() {};",
        "foo.three.a = {b: 123};",
        "",
        "foo.four = function() {};",
        "foo.four.a = {b: 123};",
        "",
        "foo.five = foo.four;",
        "",
        "foo.six = function() {};",
        "foo.six.a = foo.four.a;",
        "");
    
    NominalType2 one = typeRegistry.getType("foo.one");
    assertThat(typeRegistry.getTypes(one.getType())).containsExactly(one);

    NominalType2 two = typeRegistry.getType("foo.two");
    assertThat(typeRegistry.getTypes(two.getType())).containsExactly(two);

    NominalType2 three = typeRegistry.getType("foo.three");
    assertWithMessage(
        "Even though foo.three duck-types to foo.one, the" +
            " compiler should detect that foo.three.a.b != foo.one.a.b")
        .that(typeRegistry.getTypes(three.getType())).containsExactly(three);

    NominalType2 four = typeRegistry.getType("foo.four");
    NominalType2 five = typeRegistry.getType("foo.five");
    assertWithMessage("foo.five is a straight alias of foo.four")
        .that(typeRegistry.getTypes(four.getType())).containsExactly(four, five);
    assertWithMessage("foo.five is a straight alias of foo.four")
        .that(typeRegistry.getTypes(five.getType())).containsExactly(four, five);

    NominalType2 six = typeRegistry.getType("foo.six");
    assertWithMessage("foo.six.a === foo.four.a, but foo.six !== foo.four")
        .that(typeRegistry.getTypes(six.getType())).containsExactly(six);
  }

  @Test
  public void namespaceFunctionsAreRecordedAsNominalTypesAndPropertiesOfParentNamespace() {
    util.compile(fs.getPath("foo/bar.js"),
        "goog.provide('foo.bar');",
        "foo.bar = function() {};",
        "foo.bar.baz = function() {};");

    NominalType2 foo = typeRegistry.getType("foo");
    assertNamespace(foo);

    NominalType2 bar = typeRegistry.getType("foo.bar");
    assertNamespace(bar);
    assertThat(bar.getType().isFunctionType()).isTrue();
  }
  
  @Test
  public void doesNotRegisterFilteredTypes() {
    guice.toBuilder()
        .setTypeNameFilter(new Predicate<String>() {
          @Override
          public boolean apply(String input) {
            return input.startsWith("one.") || input.contains("two");
          }
        })
        .build()
        .createInjector()
        .injectMembers(this);
    util.compile(fs.getPath("foo.js"),
        "goog.provide('one.a.two.b');",
        "goog.provide('foo.bar.two.baz');");
    assertNamespace(typeRegistry.getType("one"));
    assertThat(typeRegistry.isType("one.a")).isFalse();
    assertThat(typeRegistry.isType("one.a.two")).isFalse();
    assertThat(typeRegistry.isType("one.a.two.b")).isFalse();

    assertNamespace(typeRegistry.getType("foo"));
    assertNamespace(typeRegistry.getType("foo.bar"));
    assertThat(typeRegistry.isType("foo.bar.two")).isFalse();
    assertThat(typeRegistry.isType("foo.bar.two.baz")).isFalse();
  }
  
  @Test
  public void recordsNamespacesWithNoChildTypes() {
    util.compile(fs.getPath("foo.js"),
        "goog.provide('util.array');",
        "util.array.forEach = function() {};");

    assertNamespace(typeRegistry.getType("util"));
    assertNamespace(typeRegistry.getType("util.array"));
    assertThat(typeRegistry.isType("util.array.forEach")).isFalse();
  }
  
  @Test
  public void doesNotRecordInternalEs6VarsAsTypes1() {
    util.compile(fs.getPath("foo.js"),
        "/** Hello */function greet() {}",
        "export {greet}");
    
    assertNamespace(typeRegistry.getType("module$foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);
  }
  
  @Test
  public void doesNotRecordInternalEs6VarsAsTypes2() {
    util.compile(fs.getPath("foo.js"),
        "/** Hello, world! */",
        "function greet() {}",
        "export {greet}");

    assertNamespace(typeRegistry.getType("module$foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);
  }
  
  @Test
  public void doesNotRecordInternalEs6VarsAsTypes3() {
    defineInputModules("modules", "foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"),
        "/** Hello, world! */",
        "function greet() {}",
        "export {greet}");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);
  }

  private void defineInputModules(String prefix, String... modules) {
    guice.toBuilder()
        .setModulePrefix(prefix)
        .setModules(modules)
        .build()
        .createInjector()
        .injectMembers(this);
  }

  private static void assertNamespace(NominalType2 type) {
    assertThat(type.getType().isObject()).isTrue();
    assertThat(type.getType().isConstructor()).isFalse();
    assertThat(type.getType().isInterface()).isFalse();
    assertThat(type.getType().isEnumType()).isFalse();
    assertThat(type.getJsDoc().isTypedef()).isFalse();
  }

  private static void assertConstructor(NominalType2 type) {
    assertThat(type.getType().isConstructor()).isTrue();
    assertThat(type.getType().isInterface()).isFalse();
    assertThat(type.getType().isEnumType()).isFalse();
    assertThat(type.getJsDoc().isTypedef()).isFalse();
  }

  private static void assertInterface(NominalType2 type) {
    assertThat(type.getType().isConstructor()).isFalse();
    assertThat(type.getType().isInterface()).isTrue();
    assertThat(type.getType().isEnumType()).isFalse();
    assertThat(type.getJsDoc().isTypedef()).isFalse();
  }

  private static void assertEnum(NominalType2 type) {
    assertThat(type.getType().isConstructor()).isFalse();
    assertThat(type.getType().isInterface()).isFalse();
    assertThat(type.getType().isEnumType()).isTrue();
    assertThat(type.getJsDoc().isTypedef()).isFalse();
  }

  private static void assertTypedef(NominalType2 type) {
    assertThat(type.getType().isConstructor()).isFalse();
    assertThat(type.getType().isInterface()).isFalse();
    assertThat(type.getType().isEnumType()).isFalse();
    assertThat(type.getJsDoc().isTypedef()).isTrue();
  }

  private static void assertPath(NominalType2 type, String expected) {
    assertThat(type.getSourceFile().toString()).isEqualTo(expected);
  }
  
  private static void assertPosition(NominalType2 type, int line, int col) {
    assertThat(type.getSourcePosition()).isEqualTo(Position.of(line, col));
  }
  
  private static void assertNoModule(NominalType2 type) {
    assertThat(type.getModule()).isAbsent();
  }
  
  private static void assertModule(
      NominalType2 type, Module.Type moduleType, String id, String path) {
    Module module = type.getModule().get();
    assertThat(module.getType()).isEqualTo(moduleType);
    assertThat(module.getId()).isEqualTo(id);
    assertThat(module.getPath().toString()).isEqualTo(path);
  }
}
