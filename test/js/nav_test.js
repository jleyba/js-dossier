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

'use strict';

goog.provide('dossier.nav.test');
goog.setTestOnly('dossier.nav.test');

goog.require('dossier.nav');
goog.require('goog.testing.jsunit');
goog.require('proto.dossier.Index');
goog.require('proto.dossier.expression.NamedType');


var nav = goog.module.get('dossier.nav');


function assertEntry(entry, name, qualifiedName) {
  assertEquals(name, entry.getType().getName());
  assertEquals(qualifiedName, entry.getType().getQualifiedName());
}


function createEntry(name, opt_qualifiedName) {
  let type = new proto.dossier.expression.NamedType;
  type.setName(name);
  if (opt_qualifiedName) {
    type.setQualifiedName(opt_qualifiedName);
  }

  let entry = new proto.dossier.Index.Entry;
  entry.setType(type);
  return entry;
}


function createNamespaceEntry(name, opt_qualifiedName) {
  let entry = createEntry(name, opt_qualifiedName);
  entry.setIsNamespace(true);
  return entry;
}


function testBuildTree_singleNode() {
  let input = createEntry('foo');
  let result = nav.buildTree([input]);
  assertEquals(1, result.length);
  assertEntry(result[0], 'foo', 'foo');
}


function testBuildTree_singleNodeIsNestedNamespace() {
  let input = createEntry('foo.bar');
  let result = nav.buildTree([input]);
  assertEquals(1, result.length);
  assertEntry(result[0], 'foo.bar', 'foo.bar');
}


function testBuildTree_multipleNamespaces() {
  let foo = createEntry('foo');
  let bar = createEntry('bar');
  let result = nav.buildTree([foo, bar]);
  assertEquals(2, result.length);
  assertEntry(result[0], 'bar', 'bar');
  assertEntry(result[1], 'foo', 'foo');
}


function testBuildTree_nestedNamespaces() {
  var input = [
      createNamespaceEntry('foo'),
      createNamespaceEntry('foo.bar'),
      createNamespaceEntry('foo.baz'),
      createNamespaceEntry('foo.quot'),
      createNamespaceEntry('foo.quot.quux'),
      createEntry('foo.one.two')
  ];
  let result = nav.buildTree(input);

  assertEquals(1, result.length);
  assertEntry(result[0], 'foo', 'foo');

  let foo = result[0];
  assertEquals(4, foo.getChildList().length);
  assertEntry(foo.getChildList()[0], 'bar', 'foo.bar');
  assertEntry(foo.getChildList()[1], 'baz', 'foo.baz');
  assertEntry(foo.getChildList()[2], 'one.two', 'foo.one.two');
  assertEntry(foo.getChildList()[3], 'quot', 'foo.quot');

  let quot = foo.getChildList()[3];
  assertEquals(1, quot.getChildList().length);
  assertEntry(quot.getChildList()[0], 'quux', 'foo.quot.quux');
}


function testBuildTree_collapsesEmptyNamespaces_oneEntry() {
  let result = nav.buildTree([createNamespaceEntry('foo.bar.baz')]);
  assertEquals(1, result.length);
  assertEntry(result[0], 'foo.bar.baz', 'foo.bar.baz');
}


function testBuildTree_collapsesEmptyNamespaces_emptyIsCommonAncestor() {
  let result = nav.buildTree([
    createNamespaceEntry('foo.bar.baz.quot'),
    createNamespaceEntry('foo.bar.baz.quux')
  ]);

  assertEquals(1, result.length);
  assertEntry(result[0], 'foo.bar.baz', 'foo.bar.baz');

  assertEquals(2, result[0].getChildList().length);
  assertEntry(result[0].getChildList()[0], 'quot', 'foo.bar.baz.quot');
  assertEntry(result[0].getChildList()[1], 'quux', 'foo.bar.baz.quux');
}


function testBuildTree_twoRoots() {
  var input = [
    createNamespaceEntry('foo'),
    createNamespaceEntry('bar')
  ];

  let result = nav.buildTree(input);
  assertEquals(2, result.length);
  assertEntry(result[0], 'bar', 'bar');
  assertEntry(result[1], 'foo', 'foo');
}


function testBuildTree_multiRooted() {
  var input = [
      createNamespaceEntry('foo'),
      createNamespaceEntry('foo.bar'),
      createNamespaceEntry('foo.baz'),
      createNamespaceEntry('quot.quux'),
      createNamespaceEntry('quot.quux.one.two')
  ];

  let result = nav.buildTree(input);
  assertEquals(2, result.length);
  assertEntry(result[0], 'foo', 'foo');
  assertEntry(result[1], 'quot.quux', 'quot.quux');

  let foo = result[0];
  assertEquals(2, foo.getChildList().length);
  assertEntry(foo.getChildList()[0], 'bar', 'foo.bar');
  assertEntry(foo.getChildList()[1], 'baz', 'foo.baz');

  let quot = result[1];
  assertEquals(1, quot.getChildList().length);

  let one = quot.getChildList()[0];
  assertEntry(one, 'one.two', 'quot.quux.one.two');
  assertEquals(0, one.getChildList().length);
}


function testBuildTree_attachesNestedClassesToParentNamespace() {
  var input = [
      createNamespaceEntry('foo'),
      createEntry('Bar', 'foo.Bar'),
      createEntry('Baz', 'foo.Bar.Baz'),
      createEntry('Quot', 'foo.Bar.Quot'),
      createEntry('Quux', 'foo.Bar.Quot.Quux')
  ];

  let root = nav.buildTree(input);
  assertEquals(1, root.length);
  assertEntry(root[0], 'foo', 'foo');

  let foo = root[0].getChildList();
  assertEquals(4, foo.length);
  assertEntry(foo[0], 'Bar', 'foo.Bar');
  assertEntry(foo[1], 'Bar.Baz', 'foo.Bar.Baz');
  assertEntry(foo[2], 'Bar.Quot', 'foo.Bar.Quot');
  assertEntry(foo[3], 'Bar.Quot.Quux', 'foo.Bar.Quot.Quux');
}


function testBuildTree_insertsSyntheticNamespaces() {
  var input = [
    createNamespaceEntry('foo'),
    createNamespaceEntry('foo.a.b.c.red.green.blue'),
    createNamespaceEntry('foo.a.b.c.one.two.three'),
    createNamespaceEntry('foo.a.apple')
  ];

  let result = nav.buildTree(input);
  assertEquals(1, result.length);
  assertEntry(result[0], 'foo', 'foo');
  assertEquals(1, result[0].getChildList().length);

  let a = result[0].getChildList()[0];
  assertEntry(a, 'a', 'foo.a');
  assertEquals(2, a.getChildList().length);
  assertEntry(a.getChildList()[0], 'apple', 'foo.a.apple');

  let bc = a.getChildList()[1];
  assertEntry(bc, 'b.c', 'foo.a.b.c');
  assertEquals(2, bc.getChildList().length);
  assertEntry(bc.getChildList()[0], 'one.two.three', 'foo.a.b.c.one.two.three');
  assertEntry(bc.getChildList()[1], 'red.green.blue', 'foo.a.b.c.red.green.blue');
}


function testBuildTree_collapsesSyntheticNamespcaesWithOneChild() {
  let result = nav.buildTree([
      createNamespaceEntry('foo'),
      createEntry('foo.Bar'),
      createNamespaceEntry('foo.bar.baz'),
      createEntry('foo.bar.baz.quot.Quux'),
      createEntry('foo.bar.other.One')
  ]);

  assertEquals(1, result.length);
  assertEntry(result[0], 'foo', 'foo');

  let foo = result[0];
  assertEquals(2, foo.getChildList().length);

  assertEntry(foo.getChildList()[0], 'Bar', 'foo.Bar');
  assertEquals(0, foo.getChildList()[0].getChildList().length);

  let bar = foo.getChildList()[1];
  assertEntry(bar, 'bar', 'foo.bar');
  assertEquals(2, bar.getChildList().length);
  assertEntry(bar.getChildList()[0], 'baz', 'foo.bar.baz');
  assertEntry(bar.getChildList()[1], 'other.One', 'foo.bar.other.One');

  let baz = bar.getChildList()[0];
  assertEquals(1, baz.getChildList().length);
  assertEntry(baz.getChildList()[0], 'quot.Quux', 'foo.bar.baz.quot.Quux');
}


function testBuildTree_modules() {
  var input = [
    createNamespaceEntry('foo'),
    createNamespaceEntry('foo/a/b'),
    createNamespaceEntry('foo/a/b/c'),
    createNamespaceEntry('foo/a/red.fruit'),
    createNamespaceEntry('foo/a/red.fruit/apple'),
    createNamespaceEntry('foo/a/b/x/y/z'),
    createNamespaceEntry('bar/baz')
  ];

  let result = nav.buildTree(input, true);
  assertEquals(2, result.length);
  assertEntry(result[0], 'bar/baz', 'bar/baz');
  assertEquals(0, result[0].getChildList().length);

  let foo = result[1];
  assertEntry(foo, 'foo', 'foo');
  assertEquals(1, foo.getChildList().length);

  let a = foo.getChildList()[0];
  assertEntry(a, '/a', 'foo/a');
  assertEquals(2, a.getChildList().length);

  let ab = a.getChildList()[0];
  assertEntry(ab, '/b', 'foo/a/b');
  assertEquals(2, ab.getChildList().length);
  assertEntry(ab.getChildList()[0], '/c', 'foo/a/b/c');
  assertEntry(ab.getChildList()[1], '/x/y/z', 'foo/a/b/x/y/z');

  let redFruit = a.getChildList()[1];
  assertEntry(redFruit, '/red.fruit', 'foo/a/red.fruit');
  assertEquals(1, redFruit.getChildList().length);
  assertEntry(redFruit.getChildList()[0], '/apple', 'foo/a/red.fruit/apple');
}


function testBuildTree_collapsesModules() {
  let result = nav.buildTree([
    createNamespaceEntry('foo'),
    createNamespaceEntry('foo/bar/a'),
    createNamespaceEntry('foo/bar/b'),
    createNamespaceEntry('foo/bar/c')
  ], true);

  assertEquals(1, result.length);
  assertEntry(result[0], 'foo', 'foo');

  let foo = result[0];
  assertEquals(1, foo.getChildList().length);
  assertEntry(foo.getChildList()[0], '/bar', 'foo/bar');
}
