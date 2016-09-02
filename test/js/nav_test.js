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

goog.require('dossier.Index');
goog.require('dossier.expression.NamedType');
goog.require('dossier.nav');
goog.require('goog.testing.jsunit');


var nav = goog.module.get('dossier.nav');


function assertEntry(entry, name, qualifiedName) {
  assertEquals(name, entry.type.name);
  assertEquals(qualifiedName, entry.type.qualifiedName);
}


function createEntry(name, opt_qualifiedName) {
  let type = new dossier.expression.NamedType;
  type.name = name;
  if (opt_qualifiedName) {
    type.qualifiedName = opt_qualifiedName;
  }

  let entry = new dossier.Index.Entry;
  entry.type = type;
  return entry;
}


function createNamespaceEntry(name, opt_qualifiedName) {
  let entry = createEntry(name, opt_qualifiedName);
  entry.isNamespace = true;
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
  assertEquals(4, foo.child.length);
  assertEntry(foo.child[0], 'bar', 'foo.bar');
  assertEntry(foo.child[1], 'baz', 'foo.baz');
  assertEntry(foo.child[2], 'one.two', 'foo.one.two');
  assertEntry(foo.child[3], 'quot', 'foo.quot');

  let quot = foo.child[3];
  assertEquals(1, quot.child.length);
  assertEntry(quot.child[0], 'quux', 'foo.quot.quux');
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
  assertEquals(2, foo.child.length);
  assertEntry(foo.child[0], 'bar', 'foo.bar');
  assertEntry(foo.child[1], 'baz', 'foo.baz');

  let quot = result[1];
  assertEquals(1, quot.child.length);
  assertEntry(quot.child[0], 'one.two', 'quot.quux.one.two');
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

  let foo = root[0].child;
  assertEquals(4, foo.length);
  assertEntry(foo[0], 'Bar', 'foo.Bar');
  assertEntry(foo[1], 'Bar.Baz', 'foo.Bar.Baz');
  assertEntry(foo[2], 'Bar.Quot', 'foo.Bar.Quot');
  assertEntry(foo[3], 'Bar.Quot.Quux', 'foo.Bar.Quot.Quux');
}
