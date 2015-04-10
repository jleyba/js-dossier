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

goog.provide('dossier.nav.test');
goog.setTestOnly('dossier.nav.test');

goog.require('dossier.nav');
goog.require('goog.testing.dom');
goog.require('goog.testing.jsunit');


var nav = goog.module.get('dossier.nav');


function assertNode(node, key, value) {
  assertEquals(key, node.getKey());
  assertEquals(value, node.getValue());
}


function testBuildTree_singleNode() {
  var input = {name: 'foo'};
  var root = nav.buildTree([input]);

  assertEquals(1, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo', input);
}


function testBuildTree_singleNodeIsNestedNamespace() {
  var input = {name: 'foo.bar'};
  var root = nav.buildTree([input]);

  assertEquals(1, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo.bar', input);
}


function testBuildTree_multipleNamespaces() {
  var foo = {name: 'foo'};
  var bar = {name: 'bar'};
  var root = nav.buildTree([foo, bar]);

  assertEquals(2, root.getChildCount());
  assertNode(root.getChildAt(0), 'bar', bar);
  assertNode(root.getChildAt(1), 'foo', foo);
}


function testBuildTree_nestedNamespaces() {
  var input = [
    {name: 'foo', namespace: true},
    {name: 'foo.bar', namespace: true},
    {name: 'foo.baz', namespace: true},
    {name: 'foo.quot', namespace: true},
    {name: 'foo.quot.quux', namespace: true},
    {name: 'foo.one.two'}
  ];
  var root = nav.buildTree(input);

  assertEquals(1, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo', input[0]);

  var foo = root.getChildAt(0);
  assertEquals(4, foo.getChildCount());
  assertNode(foo.getChildAt(0), 'bar', input[1]);
  assertNode(foo.getChildAt(1), 'baz', input[2]);
  assertNode(foo.getChildAt(2), 'one.two', input[5]);
  assertNode(foo.getChildAt(3), 'quot', input[3]);

  var quot = foo.getChildAt(3);
  assertEquals(1, quot.getChildCount());
  assertNode(quot.getChildAt(0), 'quux', input[4]);
}


function testBuildTree_multiRooted() {
  var input = [
    {name: 'foo', namespace: true},
    {name: 'foo.bar', namespace: true},
    {name: 'foo.baz', namespace: true},
    {name: 'quot.quux', namespace: true},
    {name: 'quot.quux.one.two'}
  ];

  var root = nav.buildTree(input);

  assertEquals(2, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo', input[0]);
  assertNode(root.getChildAt(1), 'quot.quux', input[3]);

  var foo = root.getChildAt(0);
  assertEquals(2, foo.getChildCount());
  assertNode(foo.getChildAt(0), 'bar', input[1]);
  assertNode(foo.getChildAt(1), 'baz', input[2]);

  var quot = root.getChildAt(1);
  assertEquals(1, quot.getChildCount());
  assertNode(quot.getChildAt(0), 'one.two', input[4]);
}


function testBuildTree_collapsesNamespacesWithNoDataAndOneChild() {
  var input = [
    {name: 'foo.bar.one'},
    {name: 'foo.bar.two'},
    {name: 'foo.baz.quot'},
    {name: 'foo.baz.quux'}
  ];

  var root = nav.buildTree(input);
  assertEquals(1, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo', null);

  var foo = root.getChildAt(0);
  assertEquals(2, foo.getChildCount());
  assertNode(foo.getChildAt(0), 'bar', null);
  assertNode(foo.getChildAt(1), 'baz', null);

  var bar = foo.getChildAt(0);
  assertEquals(2, bar.getChildCount());
  assertNode(bar.getChildAt(0), 'one', input[0]);
  assertNode(bar.getChildAt(1), 'two', input[1]);

  var baz = foo.getChildAt(1);
  assertEquals(2, baz.getChildCount());
  assertNode(baz.getChildAt(0), 'quot', input[2]);
  assertNode(baz.getChildAt(1), 'quux', input[3]);
}


function testBuildTree_attachesNestedClassesToParentNamespace() {
  var input = [
    {name: 'foo', namespace: true},
    {name: 'foo.Bar'},
    {name: 'foo.Bar.Baz'},
    {name: 'foo.Bar.Quot'},
    {name: 'foo.Bar.Quot.Quux'}
  ];

  var root = nav.buildTree(input);
  assertEquals(1, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo', input[0]);

  var foo = root.getChildAt(0);
  assertEquals(4, foo.getChildCount());
  assertNode(foo.getChildAt(0), 'Bar', input[1]);
  assertNode(foo.getChildAt(1), 'Bar.Baz', input[2]);
  assertNode(foo.getChildAt(2), 'Bar.Quot', input[3]);
  assertNode(foo.getChildAt(3), 'Bar.Quot.Quux', input[4]);
}


function testBuildTree_modulesAreAlwaysUnderRoot() {
  var input = [
    {name: 'foo', types: [{name: 'Clazz'}]},
    {name: 'foo.bar'}
  ];

  var root = nav.buildTree(input, true);
  assertEquals(2, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo', input[0]);
  assertNode(root.getChildAt(1), 'foo.bar', input[1]);

  var foo = root.getChildAt(0);
  assertEquals(1, foo.getChildCount());
  assertNode(foo.getChildAt(0), 'Clazz', input[0].types[0]);
}


function testBuildTree_sortsNodesByKey() {
  var input = [
    {name: 'zz.bb'},
    {name: 'zz.aa'},
    {name: 'zz', namespace: true},
    {name: 'aa'}
  ];

  var root = nav.buildTree(input);
  assertEquals(2, root.getChildCount());
  assertNode(root.getChildAt(0), 'aa', input[3]);
  assertNode(root.getChildAt(1), 'zz', input[2]);

  var zz = root.getChildAt(1);
  assertEquals(2, zz.getChildCount());
  assertNode(zz.getChildAt(0), 'aa', input[1]);
  assertNode(zz.getChildAt(1), 'bb', input[0]);
}


function testBuildList() {
  var input = [
    {name: 'GlobalCtor', href: 'class_GlobalCtor.html'},
    {name: 'GlobalCtor.Other', href: 'class_GlobalCtor_Other.html'},
    {name: 'closure.module',
     href: 'namespace_closure_module.html',
     namespace: true},
    {name: 'closure.module.Clazz',
     href: 'class_closure_module_Clazz.html'},
    {name: 'closure.module.PubClass',
     href: 'class_closure_module_PubClass.html'},
    {name: 'foo',
     href: 'namespace_foo.html',
     namespace: true},
    {name: 'foo.One', href: 'class_foo_One.html'},
    {name: 'foo.quot',
     href: 'namespace_foo_quot.html',
     namespace: true},
    {name: 'foo.quot.OneBarAlias', href: 'class_foo_quot_OneBarAlias.html'}
  ];

  var root = nav.buildList(input, '');
  document.body.appendChild(root);
  goog.testing.dom.assertHtmlContentsMatch(
      [
        '<li><a href="class_GlobalCtor.html">GlobalCtor</a></li>',
        '<li><a href="class_GlobalCtor_Other.html">GlobalCtor.Other</a></li>',
        '<li>',
        '<input type="checkbox" id=".nav:closure.module">',
        '<label for=".nav:closure.module">',
        '<a href="namespace_closure_module.html">closure.module</a>',
        '</label>',
        '<ul class="nav-tree">',
        '<li><a href="class_closure_module_Clazz.html">Clazz</a></li>',
        '<li><a href="class_closure_module_PubClass.html">PubClass</a></li>',
        '</ul>',
        '</li>',
        '<li>',
        '<input type="checkbox" id=".nav:foo">',
        '<label for=".nav:foo">',
        '<a href="namespace_foo.html">foo</a>',
        '</label>',
        '<ul class="nav-tree">',
        '<li><a href="class_foo_One.html">One</a></li>',
        '<li>',
        '<input type="checkbox" id=".nav:foo.quot">',
        '<label for=".nav:foo.quot">',
        '<a href="namespace_foo_quot.html">quot</a>',
        '</label>',
        '<ul class="nav-tree">',
        '<li><a href="class_foo_quot_OneBarAlias.html">OneBarAlias</a></li>',
        '</ul>',
        '</li>',
        '</ul>',
        '</li>'
      ].join(''),
      root,
      true);
}


function testBuildList_forModules() {
  var input = [
    {name: 'example', href: 'module_example.html',
     types: [{name: 'Greeter', href: 'type_Greeter.html'},
             {name: 'foo', namespace: true, href: 'type_foo.html'},
             {name: 'foo.bar', href: 'type_foo.bar.html'}]},
    {name: 'example/nested', href: 'module_example_nested.html',
     types: [{name: 'IdGenerator', href: 'type_IdGenerator.html'},
             {name: 'IdGenerator.Impl', href: 'type_IdGenerator.Impl.html'}]}
  ];

  var root = nav.buildList(input, '', '', true);
  document.body.appendChild(root);
  goog.testing.dom.assertHtmlContentsMatch(
      [
        '<li>',
        '<input type="checkbox" id=".nav-module:example">',
        '<label for=".nav-module:example">',
        '<a href="module_example.html">example</a>',
        '</label>',
        '<ul class="nav-tree">',
        '<li><a href="type_Greeter.html">Greeter</a></li>',
        '<li>',
        '<input type="checkbox" id=".nav-module:example.foo">',
        '<label for=".nav-module:example.foo">',
        '<a href="type_foo.html">foo</a>',
        '</label>',
        '<ul class="nav-tree">',
        '<li><a href="type_foo.bar.html">bar</a></li>',
        '</ul>',
        '</li>',
        '</ul>',
        '</li>',
        '<li>',
        '<input type="checkbox" id=".nav-module:example/nested">',
        '<label for=".nav-module:example/nested">',
        '<a href="module_example_nested.html">example/nested</a>',
        '</label>',
        '<ul class="nav-tree">',
        '<li><a href="type_IdGenerator.html">IdGenerator</a></li>',
        '<li><a href="type_IdGenerator.Impl.html">IdGenerator.Impl</a></li>',
        '</ul>',
        '</li>'
      ].join(''),
      root,
      true);
}


function testBuildList_prependsBasePathToLinks() {
  var input = [{name: 'GlobalCtor', href: 'class_GlobalCtor.html'}];

  var root = nav.buildList(input, '../..');
  var el = root.querySelector('a[href]');
  assertEquals('../../class_GlobalCtor.html', el.getAttribute('href'));
}


function testBuildList_marksTheNodeForTheCurrentFile() {
  var input = [{name: 'GlobalCtor', href: 'class_GlobalCtor.html'}];

  var root = nav.buildList(input, '../..', 'class_GlobalCtor.html');
  var el = root.querySelector('.current');
  assertEquals('A', el.tagName);
  assertEquals('GlobalCtor', el.textContent);
}


function testBuildList_marksTheNodeForTheCurrentFile_namespace() {
  var input = [
    {name: 'foo', namespace: true, href: 'class_GlobalCtor.html'},
    {name: 'foo.bar', href: ''}
  ];

  var root = nav.buildList(input, '../..', 'class_GlobalCtor.html');
  var el = root.querySelector('.current');
  assertEquals('LABEL', el.tagName);
  assertEquals('.nav:foo', el.getAttribute('for'));
}


function testBuildList_handlesDatalessNodes() {
  var input = [
    {name: 'foo.bar.one', href: 'one.html'},
    {name: 'foo.bar.two', href: 'two.html'},
    {name: 'foo.baz.quot', href: 'quot.html'},
    {name: 'foo.baz.quux', href: 'quux.html'}
  ];

  var root = nav.buildList(input, '', '', false);
  document.body.appendChild(root);

  assertNotNull(root.querySelector('input#\\.nav\\:foo'));
  var label = root.querySelector('label[for=".nav:foo"]');
  assertNotNull(label);
  assertEquals('foo', label.querySelector('a').textContent);
  assertNull(label.querySelector('a').getAttribute('href'));

  assertNotNull(root.querySelector('input#\\.nav\\:foo\\.bar'));
  label = root.querySelector('label[for=".nav:foo\\.bar"]');
  assertNotNull(label);
  assertEquals('bar', label.querySelector('a').textContent);
  assertNull(label.querySelector('a').getAttribute('href'));

  var leaves = root.querySelectorAll(
      'label[for=".nav:foo\\.bar"] + .nav-tree a[href]');
  assertEquals(2, leaves.length);
  assertEquals('one', leaves[0].textContent);
  assertEquals('one.html', leaves[0].getAttribute('href'));
  assertEquals('two', leaves[1].textContent);
  assertEquals('two.html', leaves[1].getAttribute('href'));

  assertNotNull(root.querySelector('input#\\.nav\\:foo\\.baz'));
  label = root.querySelector('label[for=".nav:foo\\.baz"]');
  assertNotNull(label);
  assertEquals('baz', label.querySelector('a').textContent);
  assertNull(label.querySelector('a').getAttribute('href'));

  leaves = root.querySelectorAll(
      'label[for=".nav:foo\\.baz"] + .nav-tree a[href]');
  assertEquals(2, leaves.length);
  assertEquals('quot', leaves[0].textContent);
  assertEquals('quot.html', leaves[0].getAttribute('href'));
  assertEquals('quux', leaves[1].textContent);
  assertEquals('quux.html', leaves[1].getAttribute('href'));
}
