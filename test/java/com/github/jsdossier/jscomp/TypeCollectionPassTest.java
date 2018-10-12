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

package com.github.jsdossier.jscomp;

import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.github.jsdossier.testing.DossierTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.Bug;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.inject.Injector;
import com.google.javascript.rhino.jstype.JSType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TypeCollectionPass}. */
@RunWith(JUnit4.class)
public class TypeCollectionPassTest {

  @Rule public GuiceRule guice = GuiceRule.builder(this).build();

  @Inject @Input private FileSystem fs;
  @Inject private CompilerUtil util;
  @Inject private TypeRegistry typeRegistry;

  @Test
  public void collectsGlobalClasses_functionDeclaration() {
    util.compile(fs.getPath("foo.js"), "/** @constructor */", "function One() {}");
    NominalType type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 2, 0);
  }

  @Test
  public void collectsGlobalClasses_functionExpression() {
    util.compile(
        fs.getPath("foo.js"),
        "/** @constructor */",
        "var One = function() {};",
        "",
        "/** @constructor */",
        "let Two = function() {};",
        "",
        "/** @constructor */",
        "const Three = function() {};");
    NominalType type = typeRegistry.getType("One");
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
    util.compile(fs.getPath("foo.js"), "class One {}");
    NominalType type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 1, 0);
  }

  @Test
  public void collectsGlobalClasses_classExpression() {
    util.compile(
        fs.getPath("foo.js"),
        "var One = class {};",
        "let Two = class {};",
        "const Three = class {};");
    NominalType type = typeRegistry.getType("One");
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
    util.compile(fs.getPath("foo.js"), "/** @constructor */", "var One = function Two() {};");

    NominalType type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 2, 4);

    assertThat(typeRegistry.isType("Two")).isFalse();
  }

  @Test
  public void collectsGlobalClasses_namedClassExpression() {
    util.compile(fs.getPath("foo.js"), "var One = class Two {};");

    NominalType type = typeRegistry.getType("One");
    assertConstructor(type);
    assertNoModule(type);
    assertPath(type, "foo.js");
    assertPosition(type, 1, 4);

    assertThat(typeRegistry.isType("Two")).isFalse();
  }

  @Test
  public void collectsGlobalClasses_aliasedType() {
    util.compile(fs.getPath("foo.js"), "const One = class {};", "const Two = One;");

    NominalType one = typeRegistry.getType("One");
    assertConstructor(one);
    assertNoModule(one);
    assertPath(one, "foo.js");
    assertPosition(one, 1, 6);

    NominalType two = typeRegistry.getType("Two");
    assertConstructor(two);
    assertNoModule(two);
    assertPath(two, "foo.js");
    assertPosition(two, 2, 6);

    assertThat(typeRegistry.getTypes(one.getType())).containsExactly(one, two).inOrder();
  }

  @Test
  public void collectsGlobalClasses_googDefinedClass() {
    util.compile(
        fs.getPath("foo.js"), "const One = goog.defineClass(null, {constructor: function() {}});");

    NominalType one = typeRegistry.getType("One");
    assertConstructor(one);
    assertNoModule(one);
    assertPath(one, "foo.js");
    assertPosition(one, 1, 6);
  }

  @Test
  public void doesNotRecordExternAliasAsANominalType() {
    util.compile(
        fs.getPath("foo.js"),
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
    util.compile(
        fs.getPath("foo.js"),
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
  public void collectsGlobalStructuralInterfaces() {
    util.compile(
        fs.getPath("foo.js"),
        "/** @record */",
        "const One = function() {};",
        "/** @record */",
        "const Two = goog.defineClass(null, {});",
        "/** @record */",
        "const Three = class {};");

    assertInterface(typeRegistry.getType("One"));
    assertInterface(typeRegistry.getType("Two"));
    assertInterface(typeRegistry.getType("Three"));
  }

  @Test
  public void recordsGlobalEnums() {
    util.compile(fs.getPath("foo/bar.js"), "/** @enum */", "const Foo = {};");
    NominalType foo = typeRegistry.getType("Foo");
    assertEnum(foo);
    assertNoModule(foo);
    assertPath(foo, "foo/bar.js");
    assertPosition(foo, 2, 6);
  }

  @Test
  public void recordsGlobalTypedefs() {
    util.compile(fs.getPath("foo/bar.js"), "/** @typedef {string} */", "var Foo;");
    NominalType foo = typeRegistry.getType("Foo");
    assertTypedef(foo);
    assertNoModule(foo);
    assertPath(foo, "foo/bar.js");
    assertPosition(foo, 2, 4);
  }

  @Test
  public void recordsGoogModuleExportsAsNominalType() {
    util.compile(fs.getPath("foo/bar.js"), "goog.module('foo');", "exports.Bar = class {};");

    assertThat(typeRegistry.getAllTypes())
        .containsExactly(
            typeRegistry.getType("module$exports$foo"),
            typeRegistry.getType("module$exports$foo.Bar"));

    NominalType type = typeRegistry.getType("module$exports$foo");
    assertNamespace(type);
    assertPath(type, "foo/bar.js");
    assertPosition(type, 1, 0);
    assertModule(type, Module.Type.CLOSURE, "module$exports$foo", "foo/bar.js");
  }

  @Test
  public void recordsGoogModuleExportsAsNominalType_handlesLegacyNamespace() {
    util.compile(
        fs.getPath("foo/bar.js"),
        "goog.module('foo');",
        "goog.module.declareLegacyNamespace();",
        "exports.Bar = class {};");

    assertThat(typeRegistry.getAllTypes())
        .containsExactly(typeRegistry.getType("foo"), typeRegistry.getType("foo.Bar"));

    NominalType type = typeRegistry.getType("foo");
    assertNamespace(type);
    assertPath(type, "foo/bar.js");
    assertPosition(type, 1, 0);
    assertModule(type, Module.Type.CLOSURE, "foo", "foo/bar.js");
  }

  @Test
  public void recordsGoogModuleExportsAsNominalType_handlesLegacyNamespace_2() {
    util.compile(
        fs.getPath("foo/bar.js"),
        "goog.module('foo.bar');",
        "goog.module.declareLegacyNamespace();",
        "exports.Baz = class {};");

    assertThat(typeRegistry.getAllTypes())
        .containsExactly(typeRegistry.getType("foo.bar"), typeRegistry.getType("foo.bar.Baz"));

    NominalType type = typeRegistry.getType("foo.bar");
    assertNamespace(type);
    assertPath(type, "foo/bar.js");
    assertPosition(type, 1, 0);
    assertModule(type, Module.Type.CLOSURE, "foo.bar", "foo/bar.js");
  }

  @Test
  public void recordsNodeModuleExportsAsNominalType() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "exports.Bar = {}");

    NominalType type = typeRegistry.getType("module$exports$module$modules$foo$bar");
    assertNamespace(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 1, 0);
    assertModule(
        type, Module.Type.NODE, "module$exports$module$modules$foo$bar", "modules/foo/bar.js");
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
    testRecordsEs6ModuleExportsAsNominalType("export default class {}", "export class A {}");
  }

  @Test
  public void recordsEs6ModuleExportsAsNominalType4() {
    testRecordsEs6ModuleExportsAsNominalType(
        "export function each() {}", "export { each as forEach }");
  }

  @Test
  public void recordsEs6ModuleExportsAsNominalType5() {
    testRecordsEs6ModuleExportsAsNominalType(
        "export function each() {}", "export { each as default }");
  }

  private void testRecordsEs6ModuleExportsAsNominalType(String... source) {
    util.compile(fs.getPath("foo/bar.js"), source);

    NominalType type = typeRegistry.getType("module$foo$bar");
    assertNamespace(type);
    assertPath(type, "foo/bar.js");
    assertPosition(type, 1, 0);
    assertModule(type, Module.Type.ES6, "module$foo$bar", "foo/bar.js");
  }

  @Test
  public void doesNotDoubleRecordEs6ModulesAsNodeModules() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "export function foo() {};");

    NominalType type = typeRegistry.getType("module$modules$foo$bar");
    assertNamespace(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 1, 0);
    assertModule(type, Module.Type.ES6, "module$modules$foo$bar", "modules/foo/bar.js");
  }

  @Test
  public void recordsGoogProvidedType() {
    util.compile(fs.getPath("types.js"), "goog.provide('foo.bar.Baz');", "foo.bar.Baz = class {}");
    assertConstructor(typeRegistry.getType("foo.bar.Baz"));
  }

  @Test
  public void documentsNestedTypes() {
    util.compile(
        fs.getPath("foo/bar.js"),
        "goog.provide('foo.bar');",
        "/** @constructor */",
        "foo.bar.Bim = function() {};",
        "/** @constructor */",
        "foo.bar.Bim.Baz = function() {};");

    assertNamespace(typeRegistry.getType("foo.bar"));
    assertConstructor(typeRegistry.getType("foo.bar.Bim"));
    assertConstructor(typeRegistry.getType("foo.bar.Bim.Baz"));
  }

  @Test
  public void functionVariablesAreNotDocumentedAsConstructors() {
    util.compile(
        fs.getPath("foo/bar.js"),
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
    util.compile(
        fs.getPath("foo/bar.js"),
        "goog.provide('foo');",
        "/** @type {!Function} */",
        "foo.bar = Function;");

    assertNamespace(typeRegistry.getType("foo"));
    assertThat(typeRegistry.isType("foo.bar")).isFalse();
  }

  @Test
  public void onlyDocumentsExportedModuleTypes_closure() {
    util.compile(
        fs.getPath("foo/bar.js"),
        "goog.module('foo');",
        "class InternalClass {}",
        "exports.Foo = class {};");
    assertNamespace(typeRegistry.getType("module$exports$foo"));
    assertConstructor(typeRegistry.getType("module$exports$foo.Foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);
  }

  @Test
  public void onlyDocumentsExportedModuleTypes_node() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"), "class InternalClass {}", "exports.Foo = class {};");
    assertNamespace(typeRegistry.getType("module$exports$module$modules$foo$bar"));
    assertConstructor(typeRegistry.getType("module$exports$module$modules$foo$bar.Foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);
  }

  @Test
  public void onlyDocumentsExportedModuleTypes_es6() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "class InternalClass {}",
        "export { InternalClass as Foo }");
    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertConstructor(typeRegistry.getType("module$modules$foo$bar.Foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);
  }

  @Test
  public void documentsEs6DefaultExports1() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "class InternalClass {}",
        "export { InternalClass as default }");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertConstructor(typeRegistry.getType("module$modules$foo$bar.default"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).hasSize(1);
    assertThat(module.getExportedNames()).containsEntry("default", "InternalClass");
  }

  @Test
  public void documentsEs6DefaultExports2() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "class InternalClass {}",
        "export default InternalClass;");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertConstructor(typeRegistry.getType("module$modules$foo$bar.default"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).hasSize(1);
    assertThat(module.getExportedNames()).containsEntry("default", "InternalClass");
  }

  @Test
  public void documentsEs6DefaultExports3() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "export default class {};");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertConstructor(typeRegistry.getType("module$modules$foo$bar.default"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).isEmpty();
  }

  @Test
  public void documentsEs6DefaultExports4() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "class InternalClass {}",
        "export {InternalClass as default}");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertConstructor(typeRegistry.getType("module$modules$foo$bar.default"));
    assertThat(typeRegistry.getAllTypes()).hasSize(2);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).hasSize(1);
    assertThat(module.getExportedNames()).containsEntry("default", "InternalClass");
  }

  @Test
  public void documentEs6DefaultExports5() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"), "function internal() {}", "export {internal as default}");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).hasSize(1);
    assertThat(module.getExportedNames()).containsEntry("default", "internal");
  }

  @Test
  public void documentEs6DefaultExports6() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"), "function internal() {}", "export default internal");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).hasSize(1);
    assertThat(module.getExportedNames()).containsEntry("default", "internal");
  }

  @Test
  public void documentEs6DefaultExports7() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "export default function internal() {}");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).hasSize(1);
    assertThat(module.getExportedNames()).containsEntry("default", "internal");
  }

  @Test
  public void documentEs6DefaultExports8() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "export default function() {}");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).isEmpty();
  }

  @Test
  public void documentEs6DefaultExports9() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "export default function() {}");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).isEmpty();
  }

  @Test
  public void documentEs6DefaultExports10() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "export default 1;");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).isEmpty();
  }

  @Test
  public void documentEs6DefaultExports11() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "export default {};");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).isEmpty();
  }

  @Test
  public void documentEs6DefaultExports12() {
    defineInputModules("foo/bar.js");
    util.compile(fs.getPath("modules/foo/bar.js"), "const x = 1;", "export default x;");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);

    Module module = typeRegistry.getModule("module$modules$foo$bar");
    assertThat(module.getId().getType()).isEqualTo(Module.Type.ES6);
    assertThat(module.getExportedNames()).hasSize(1);
    assertThat(module.getExportedNames()).containsEntry("default", "x");
  }

  @Test
  public void doesNotDocumentCtorReferencesAsNestedTypes() {
    util.compile(
        fs.getPath("module.js"),
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
  public void doesNotRecordOrdinaryFunctionsAsTypes() {
    util.compile(
        fs.getPath("foo/bar.js"),
        "goog.provide('foo.one');",
        "goog.provide('foo.two');",
        "",
        // This is an ordinary function, but is explicitly goog.provided so should
        // be treated like a namespace object.
        "foo.one = function() {};",
        "/** @interface */ foo.one.a = function() {};",
        // This is an ordinary funciton that is not provided, so should not be
        // recorded as a type.
        "foo.one.b = function() {};",
        "foo.one.b.c = class {};",
        "",
        "/** @constructor */",
        "foo.two = function() {};",
        "foo.two.a = function() {};",
        "foo.two.a.b = class {};");

    assertThat(typeRegistry.getTypeNames())
        .containsExactly("foo.one", "foo.one.a", "foo.one.b.c", "foo.two", "foo.two.a.b");
  }

  @Test
  public void functionAliasDetection() {
    util.compile(
        fs.getPath("foo/bar.js"),
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

    NominalType one = typeRegistry.getType("foo.one");
    assertThat(typeRegistry.getTypes(one.getType())).containsExactly(one);

    NominalType two = typeRegistry.getType("foo.two");
    assertThat(typeRegistry.getTypes(two.getType())).containsExactly(two);

    NominalType three = typeRegistry.getType("foo.three");
    assertWithMessage(
            "Even though foo.three duck-types to foo.one, the"
                + " compiler should detect that foo.three.a.b != foo.one.a.b")
        .that(typeRegistry.getTypes(three.getType()))
        .containsExactly(three);

    NominalType four = typeRegistry.getType("foo.four");
    NominalType five = typeRegistry.getType("foo.five");
    assertWithMessage("foo.five is a straight alias of foo.four")
        .that(typeRegistry.getTypes(four.getType()))
        .containsExactly(four, five);
    assertWithMessage("foo.five is a straight alias of foo.four")
        .that(typeRegistry.getTypes(five.getType()))
        .containsExactly(four, five);

    NominalType six = typeRegistry.getType("foo.six");
    assertWithMessage("foo.six.a === foo.four.a, but foo.six !== foo.four")
        .that(typeRegistry.getTypes(six.getType()))
        .containsExactly(six);
  }

  @Test
  public void namespaceFunctionsAreRecordedAsNominalTypesAndPropertiesOfParentNamespace() {
    util.compile(
        fs.getPath("foo/bar.js"),
        "goog.provide('foo.bar');",
        "foo.bar = function() {};",
        "foo.bar.baz = function() {};");

    NominalType bar = typeRegistry.getType("foo.bar");
    assertNamespace(bar);
    assertThat(bar.getType().isFunctionType()).isTrue();
  }

  @Test
  public void recordsNamespacesWithNoChildTypes() {
    util.compile(
        fs.getPath("foo.js"), "goog.provide('util.array');", "util.array.forEach = function() {};");

    assertNamespace(typeRegistry.getType("util.array"));
    assertThat(typeRegistry.isType("util.array.forEach")).isFalse();
  }

  @Test
  public void doesNotRecordInternalEs6VarsAsTypes1() {
    util.compile(fs.getPath("foo.js"), "/** Hello */function greet() {}", "export {greet}");

    assertNamespace(typeRegistry.getType("module$foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);
  }

  @Test
  public void doesNotRecordInternalEs6VarsAsTypes2() {
    util.compile(
        fs.getPath("foo.js"), "/** Hello, world! */", "function greet() {}", "export {greet}");

    assertNamespace(typeRegistry.getType("module$foo"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);
  }

  @Test
  public void doesNotRecordInternalEs6VarsAsTypes3() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "/** Hello, world! */",
        "function greet() {}",
        "export {greet}");

    assertNamespace(typeRegistry.getType("module$modules$foo$bar"));
    assertThat(typeRegistry.getAllTypes()).hasSize(1);
  }

  @Test
  public void recordsNestedTypes() {
    util.compile(
        fs.getPath("foo.js"),
        "goog.provide('foo.bar.baz');",
        "foo.bar.baz.Clazz = class {};",
        "foo.Bar = class {};");

    NominalType barClass = typeRegistry.getType("foo.Bar");
    NominalType baz = typeRegistry.getType("foo.bar.baz");
    NominalType clazz = typeRegistry.getType("foo.bar.baz.Clazz");

    assertThat(typeRegistry.getNestedTypes(barClass)).isEmpty();
    assertThat(typeRegistry.getNestedTypes(baz)).containsExactly(clazz);
    assertThat(typeRegistry.getNestedTypes(clazz)).isEmpty();
  }

  @Test
  public void doesNotRegisterImplicitNamespacesFromClosureModules1() {
    util.compile(fs.getPath("foo.js"), "goog.module('foo.bar');", "exports.Baz = class {};");

    assertThat(typeRegistry.getAllTypes()).hasSize(2);

    NominalType bar = typeRegistry.getType("module$exports$foo$bar");
    assertThat(bar.getModule().isPresent()).isTrue();

    NominalType baz = typeRegistry.getType("module$exports$foo$bar.Baz");
    assertConstructor(baz);
  }

  @Test
  public void doesNotRegisterImplicitNamespacesFromClosureModules2() {
    util.compile(fs.getPath("foo.js"), "goog.module('foo.bar.baz');", "exports.Baz = class {};");

    assertThat(typeRegistry.getAllTypes()).hasSize(2);

    NominalType bazExports = typeRegistry.getType("module$exports$foo$bar$baz");
    assertThat(bazExports.getModule().isPresent()).isTrue();

    NominalType bazClass = typeRegistry.getType("module$exports$foo$bar$baz.Baz");
    assertConstructor(bazClass);
  }

  @Test
  public void canResolveNominalTypeFromConstructorAliases() {
    guice.toBuilder().setUseNodeLibrary(false).build().createInjector().injectMembers(this);
    util.compile(
        fs.getPath("foo.js"),
        "goog.provide('ns');",
        "ns.Foo = class {};",
        "ns.Bar = ns.Foo;",
        "/** @type {function(new: ns.Foo)} */ ns.F1 = ns.Foo;",
        "/** @type {function(new: ns.Foo): undefined} */ ns.F2 = ns.Foo;");

    NominalType ns = typeRegistry.getType("ns");
    NominalType foo = typeRegistry.getType("ns.Foo");
    NominalType bar = typeRegistry.getType("ns.Bar");

    assertThat(typeRegistry.getAllTypes()).containsExactly(ns, foo, bar);
    assertThat(typeRegistry.getTypes(foo.getType())).containsExactly(foo, bar);

    JSType nsType = ns.getType();
    JSType f1 = nsType.toObjectType().getOwnSlot("F1").getType();
    JSType f2 = nsType.toObjectType().getOwnSlot("F2").getType();

    assertThat(typeRegistry.findTypes(f1)).containsExactly(foo, bar);
    assertThat(typeRegistry.findTypes(f2)).containsExactly(foo, bar);
  }

  @Test
  public void fillsInMissingModuleTypesForModulesWithNoExports() {
    util.compile(
        createSourceFile(fs.getPath("foo.js"), "export class Foo {}"),
        createSourceFile(fs.getPath("bar.js"), "import * as foo from './foo.js';"));

    Module bar = typeRegistry.getModule(fs.getPath("bar.js"));
    NominalType type = typeRegistry.getType(bar.getId());
    assertThat(type.isModuleExports()).isTrue();
  }

  @Test
  public void doesNotRecordCompilerConstantAsType() {
    util.compile(
        fs.getPath("foo.js"),
        "/** @define {boolean} Hi. */",
        "var COMPILED = false;",
        "",
        "class One {};",
        "One.Two = class {};");

    assertThat(typeRegistry.getAllTypes())
        .containsExactly(typeRegistry.getType("One"), typeRegistry.getType("One.Two"));
  }

  @Test
  @Bug(53)
  public void usesAliasDocsIfModuleExportDoesNotHaveDocs_closureMode() {
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "goog.module('foo');",
        "/** A person. */",
        "class Person {}",
        "exports.Person = Person;");

    NominalType type = typeRegistry.getType("module$exports$foo.Person");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 4, 0);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("A person.");
  }

  @Test
  @Bug(53)
  public void usesAliasDocsIfModuleExportDoesNotHaveDocs_nodeModule() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "/** A person. */",
        "class Person {}",
        "exports.Person = Person;");

    NominalType type = typeRegistry.getType("module$exports$module$modules$foo$bar.Person");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 3, 0);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("A person.");
  }

  @Test
  @Bug(53)
  public void usesAliasDocsIfModuleExportDoesNotHaveDocs_es6Module() {
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "/** A person. */",
        "class Person {}",
        "export {Person};");

    NominalType type = typeRegistry.getType("module$modules$foo$bar.Person");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 3, 8);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("A person.");
  }

  @Test
  @Bug(53)
  public void usesAliasDocsIfProvided_closureModule() {
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "goog.module('foo');",
        "/** A person. */",
        "class Person {}",
        "/** An exported person. */",
        "exports.Person = Person;");

    NominalType type = typeRegistry.getType("module$exports$foo.Person");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 5, 0);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("An exported person.");
  }

  @Test
  @Bug(53)
  public void usesAliasDocsIfProvided_nodeModule() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "/** A person. */",
        "class Person {}",
        "/** An exported person. */",
        "exports.Person = Person;");

    NominalType type = typeRegistry.getType("module$exports$module$modules$foo$bar.Person");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 4, 0);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("An exported person.");
  }

  @Test
  @Bug(53)
  public void capturesConstructorDocsWhenExportedDirectly() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"), "/** A person. */", "exports.Person = class Person {};");

    NominalType type = typeRegistry.getType("module$exports$module$modules$foo$bar.Person");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 2, 0);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("A person.");
  }

  @Test
  @Bug(53)
  public void capturesConstructorDocsWhenTheDefaultExport() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"), "/** A person. */", "module.exports = class Person{};");

    NominalType type = typeRegistry.getType("module$exports$module$modules$foo$bar");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 2, 0);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("A person.");
  }

  @Test
  @Bug(54)
  public void tracksClassesExportedViaObjectDestructuring_nodeModule() {
    defineInputModules("foo/bar.js");
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "/** A person. */",
        "class Person{}",
        "/** A happy person. */",
        "class HappyPerson extends Person {}",
        "",
        "module.exports = {Person, /** Lorem ipsum. */HappyPerson};");

    NominalType type = typeRegistry.getType("module$exports$module$modules$foo$bar.Person");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 6, 18);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("A person.");

    type = typeRegistry.getType("module$exports$module$modules$foo$bar.HappyPerson");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 6, 45);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("Lorem ipsum.");
  }

  @Test
  @Bug(54)
  public void tracksClassesExportedViaObjectDestructuring_closureModule() {
    util.compile(
        fs.getPath("modules/foo/bar.js"),
        "goog.module('foo');",
        "",
        "/** A person. */",
        "class Person{}",
        "/** A happy person. */",
        "class HappyPerson extends Person {}",
        "",
        "exports = {Person, /** Lorem ipsum. */HappyPerson};");

    NominalType type = typeRegistry.getType("module$exports$foo.Person");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 8, 11);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("A person.");

    type = typeRegistry.getType("module$exports$foo.HappyPerson");
    assertConstructor(type);
    assertPath(type, "modules/foo/bar.js");
    assertPosition(type, 8, 38);
    assertThat(type.getJsDoc().getBlockComment()).isEqualTo("Lorem ipsum.");
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void exportedModuleAliasesAreRecordedAsTypes_closureModule() {
    util.compile(
        createSourceFile(fs.getPath("one.js"), "goog.module('one');", "exports.One = class {};"),
        createSourceFile(
            fs.getPath("two.js"),
            "goog.module('two');",
            "let one = goog.require('one');",
            "exports.one = one;"));

    NominalType one = typeRegistry.getType(("module$exports$one"));
    assertThat(one.isModuleExports()).isTrue();

    NominalType two = typeRegistry.getType(("module$exports$two"));
    assertThat(two.isModuleExports()).isTrue();

    NominalType twoOne = typeRegistry.getType(("module$exports$two.one"));
    assertThat(twoOne.getModule().isPresent()).isTrue();
    assertThat(twoOne.getModule().get()).isSameAs(two.getModule().get());
    assertThat(twoOne.getType()).isSameAs(one.getType());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void exportedModuleAliasesAreRecordedAsTypes_nodeModule() {
    defineInputModules("one.js", "two.js");
    util.compile(
        createSourceFile(fs.getPath("modules/one.js"), "exports.One = class {};"),
        createSourceFile(
            fs.getPath("modules/two.js"), "let one = require('./one');", "exports.one = one;"));

    NominalType one = typeRegistry.getType(("module$exports$module$modules$one"));
    assertThat(one.isModuleExports()).isTrue();

    NominalType two = typeRegistry.getType(("module$exports$module$modules$two"));
    assertThat(two.isModuleExports()).isTrue();

    NominalType twoOne = typeRegistry.getType(("module$exports$module$modules$two.one"));
    assertThat(twoOne.getModule().isPresent()).isTrue();
    assertThat(twoOne.getModule().get()).isSameAs(two.getModule().get());
    assertThat(twoOne.getType()).isSameAs(one.getType());
  }

  @Test
  public void exportedModuleAliasesAreNotRecordedAsTypes_es6Module() {
    util.compile(
        createSourceFile(fs.getPath("modules/one.js"), "export class One {}"),
        createSourceFile(
            fs.getPath("modules/two.js"), "import * as one from './one.js';", "export {one};"));

    assertThat(typeRegistry.getAllTypes())
        .containsExactly(
            typeRegistry.getType("module$modules$one"),
            typeRegistry.getType("module$modules$one.One"),
            typeRegistry.getType("module$modules$two"));
  }

  @Test
  public void doesNotCountStaticInstancesOnConstructorAsAType() {
    util.compile(
        createSourceFile(
            fs.getPath("foobar.js"),
            "goog.provide('foo.Bar');",
            "",
            "/** @constructor */",
            "foo.Bar = function() {};",
            "",
            "foo.Bar.getInstance = function() {",
            "  if (foo.Bar.instance) {",
            "    return foo.Bar.instance;",
            "  }",
            "  return foo.Bar.instance = new foo.Bar;",
            "};"));

    assertThat(typeRegistry.isType("foo.Bar.getInstance")).isFalse();
    assertThat(typeRegistry.isType("foo.Bar.instance")).isFalse();
    assertThat(typeRegistry.getNestedTypes(typeRegistry.getType("foo.Bar"))).isEmpty();
  }

  @Test
  public void detectsNodeExternUsage() throws IOException {
    Injector injector =
        guice
            .toBuilder()
            .setModulePrefix("modules")
            .setModules("one.js")
            .setModuleExterns("externs/two.js")
            .build()
            .createInjector();

    injector.injectMembers(this);

    // Module externs are loaded directly.
    Path path = fs.getPath("externs/two.js");
    Files.createDirectories(path.getParent());
    Files.write(
        path,
        "module.exports = function(a, b) { return a + b; };".getBytes(StandardCharsets.UTF_8));

    util.compile(
        createSourceFile(
            fs.getPath("modules/one.js"),
            "var two = require('two');",
            "exports.path = two('a', 'b');"));

    assertThat(typeRegistry.getAllTypes())
        .containsExactly(typeRegistry.getType("module$exports$module$modules$one"));

    NodeLibrary library = injector.getInstance(NodeLibrary.class);
    assertThat(library.canRequireId("two")).isTrue();
  }

  private void defineInputModules(String... modules) {
    guice
        .toBuilder()
        .setModulePrefix("modules")
        .setModules(modules)
        .setUseNodeLibrary(false)
        .build()
        .createInjector()
        .injectMembers(this);
  }

  private static void assertNamespace(NominalType type) {
    assertThat(type.getType().isObject()).isTrue();
    assertThat(type.getType().isConstructor()).isFalse();
    assertThat(type.getType().isInterface()).isFalse();
    assertThat(type.getType().isEnumType()).isFalse();
    assertThat(type.getJsDoc().isTypedef()).isFalse();
  }

  private static void assertConstructor(NominalType type) {
    assertThat(type.getType()).isConstructor();
    assertThat(type.getType().isInterface()).isFalse();
    assertThat(type.getType().isEnumType()).isFalse();
    assertThat(type.getJsDoc().isTypedef()).isFalse();
  }

  private static void assertInterface(NominalType type) {
    assertThat(type.getType()).isInterface();
    assertThat(type.getType().isConstructor()).isFalse();
    assertThat(type.getType().isInterface()).isTrue();
    assertThat(type.getType().isEnumType()).isFalse();
    assertThat(type.getJsDoc().isTypedef()).isFalse();
  }

  private static void assertEnum(NominalType type) {
    assertThat(type.getType().isConstructor()).isFalse();
    assertThat(type.getType().isInterface()).isFalse();
    assertThat(type.getType()).isEnumType();
    assertThat(type.getType().isEnumType()).isTrue();
  }

  private static void assertTypedef(NominalType type) {
    assertThat(type.getJsDoc().isTypedef()).isTrue();
  }

  private static void assertPath(NominalType type, String expected) {
    assertThat(type.getSourceFile().toString()).isEqualTo(expected);
  }

  private static void assertPosition(NominalType type, int line, int col) {
    assertThat(type.getSourcePosition()).isEqualTo(Position.of(line, col));
  }

  private static void assertNoModule(NominalType type) {
    assertThat(type.getModule().isPresent()).isFalse();
  }

  @SuppressWarnings("ConstantConditions")
  private static void assertModule(
      NominalType type, Module.Type moduleType, String id, String path) {
    Module module = type.getModule().get();
    assertThat(module.getId().getType()).isEqualTo(moduleType);
    assertThat(module.getId().getCompiledName()).isEqualTo(id);
    assertThat(module.getPath().toString()).isEqualTo(path);
  }
}
