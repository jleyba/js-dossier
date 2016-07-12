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
goog.require('goog.testing.dom');
goog.require('goog.testing.jsunit');


var nav = goog.module.get('dossier.nav');


function assertNode(node, key, value) {
  assertEquals(key, node.getKey());
  assertEquals(value, node.getValue());
}


function testBuildTree_singleNode() {
  var input = {name: 'foo', qualifiedName: 'foo'};
  var root = nav.buildTree([input]);

  assertEquals(1, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo', input);
}


function testBuildTree_singleNodeIsNestedNamespace() {
  var input = {name: 'foo.bar', qualifiedName: 'foo.bar'};
  var root = nav.buildTree([input]);

  assertEquals(1, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo.bar', input);
}


function testBuildTree_multipleNamespaces() {
  var foo = {name: 'foo', qualifiedName: 'foo'};
  var bar = {name: 'bar', qualifiedName: 'bar'};
  var root = nav.buildTree([foo, bar]);

  assertEquals(2, root.getChildCount());
  assertNode(root.getChildAt(0), 'bar', bar);
  assertNode(root.getChildAt(1), 'foo', foo);
}


function testBuildTree_nestedNamespaces() {
  var input = [
    {name: 'foo', qualifiedName: 'foo', namespace: true},
    {name: 'foo.bar', qualifiedName: 'foo.bar', namespace: true},
    {name: 'foo.baz', qualifiedName: 'foo.baz', namespace: true},
    {name: 'foo.quot', qualifiedName: 'foo.quot', namespace: true},
    {name: 'foo.quot.quux', qualifiedName: 'foo.quot.quux', namespace: true},
    {name: 'foo.one.two', qualifiedName: 'foo.one.two'}
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
    {name: 'foo', qualifiedName: 'foo', namespace: true},
    {name: 'foo.bar', qualifiedName: 'foo.bar', namespace: true},
    {name: 'foo.baz', qualifiedName: 'foo.baz', namespace: true},
    {name: 'quot.quux', qualifiedName: 'quot.quux', namespace: true},
    {name: 'quot.quux.one.two', qualifiedName: 'quot.quux.one.two'}
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
    {name: 'foo.bar.one', qualifiedName: 'foo.bar.one'},
    {name: 'foo.bar.two', qualifiedName: 'foo.bar.two'},
    {name: 'foo.baz.quot', qualifiedName: 'foo.baz.quot'},
    {name: 'foo.baz.quux', qualifiedName: 'foo.baz.quux'}
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
    {name: 'foo', qualifiedName: 'foo', namespace: true},
    {name: 'Bar', qualifiedName: 'foo.Bar'},
    {name: 'Baz', qualifiedName: 'foo.Bar.Baz'},
    {name: 'Quot', qualifiedName: 'foo.Bar.Quot'},
    {name: 'Quux', qualifiedName: 'foo.Bar.Quot.Quux'}
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
    {name: 'foo', qualifiedName: 'foo', types: [{
      name: 'Clazz', qualifiedName: 'foo.Clazz'
    }]},
    {name: 'foo.bar', qualifiedName: 'foo.bar'}
  ];

  var root = nav.buildTree(input, true);
  assertEquals(2, root.getChildCount());
  assertNode(root.getChildAt(0), 'foo', input[0]);
  assertNode(root.getChildAt(1), 'foo.bar', input[1]);

  var foo = root.getChildAt(0);
  assertEquals(0, foo.getChildCount());

  var bar = root.getChildAt(1);
  assertEquals(0, bar.getChildCount());
}


function testBuildTree_sortsNodesByKey() {
  var input = [
    {name: 'zz.bb', qualifiedName: 'zz.bb'},
    {name: 'zz.aa', qualifiedName: 'zz.aa'},
    {name: 'zz', qualifiedName: 'zz', namespace: true},
    {name: 'aa', qualifiedName: 'aa'}
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
    {name: 'GlobalCtor',
     qualifiedName: 'GlobalCtor',
     href: 'class_GlobalCtor.html'},
    {name: 'Other',
     qualifiedName: 'GlobalCtor.Other',
     href: 'class_GlobalCtor_Other.html'},
    {name: 'closure.module',
     qualifiedName: 'closure.module',
     href: 'namespace_closure_module.html',
     namespace: true},
    {name: 'Clazz',
     qualifiedName: 'closure.module.Clazz',
     href: 'class_closure_module_Clazz.html'},
    {name: 'PubClass',
     qualifiedName: 'closure.module.PubClass',
     href: 'class_closure_module_PubClass.html'},
    {name: 'foo',
     qualifiedName: 'foo',
     href: 'namespace_foo.html',
     namespace: true},
    {name: 'One',
     qualifiedName: 'foo.One',
     href: 'class_foo_One.html'},
    {name: 'foo.quot',
     qualifiedName: 'foo.quot',
     href: 'namespace_foo_quot.html',
     namespace: true},
    {name: 'OneBarAlias',
     qualifiedName: 'foo.quot.OneBarAlias',
     href: 'class_foo_quot_OneBarAlias.html'}
  ];

  var root = nav.buildList(input, '');
  document.body.appendChild(root);
  goog.testing.dom.assertHtmlContentsMatch(
      [
        '<li><a class="item" tabindex="1" href="class_GlobalCtor.html">GlobalCtor</a></li>',
        '<li><a class="item" tabindex="1" href="class_GlobalCtor_Other.html">GlobalCtor.Other</a></li>',
        '<li>',
        '<div class="toggle" data-id=".nav:closure.module">',
        '<a class="item" tabindex="1" href="namespace_closure_module.html">closure.module</a>',
        '<i class="material-icons">expand_more</i>',
        '</div>',
        '<ul class="tree">',
        '<li><a class="item" tabindex="1" href="class_closure_module_Clazz.html">Clazz</a></li>',
        '<li><a class="item" tabindex="1" href="class_closure_module_PubClass.html">PubClass</a></li>',
        '</ul>',
        '</li>',
        '<li>',
        '<div class="toggle" data-id=".nav:foo">',
        '<a class="item" tabindex="1" href="namespace_foo.html">foo</a>',
        '<i class="material-icons">expand_more</i>',
        '</div>',
        '<ul class="tree">',
        '<li><a class="item" tabindex="1" href="class_foo_One.html">One</a></li>',
        '<li>',
        '<div class="toggle" data-id=".nav:foo.quot">',
        '<a class="item" tabindex="1" href="namespace_foo_quot.html">quot</a>',
        '<i class="material-icons">expand_more</i>',
        '</div>',
        '<ul class="tree">',
        '<li><a class="item" tabindex="1" href="class_foo_quot_OneBarAlias.html">OneBarAlias</a></li>',
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
    {name: 'example',
     qualifiedName: 'example',
     href: 'module_example.html',
     types: [{name: 'Greeter',
              qualifiedName: 'example.Greeter',
              href: 'type_Greeter.html'},
             {name: 'foo',
              qualifiedName: 'example.foo',
              namespace: true,
              href: 'type_foo.html'},
             {name: 'foo.bar',
              qualifiedName: 'example.foo.bar',
              href: 'type_foo.bar.html'}]},
    {name: 'example/nested',
     qualifiedName: 'example/nested',
     href: 'module_example_nested.html',
     types: [{name: 'IdGenerator',
              qualifiedName: 'example/nested.IdGenerator',
              href: 'type_IdGenerator.html'},
             {name: 'IdGenerator.Impl',
              qualifiedName: 'example/nested.IdGenerator.Impl',
              href: 'type_IdGenerator.Impl.html'}]}
  ];

  var root = nav.buildList(input, '', '', true);
  document.body.appendChild(root);
  goog.testing.dom.assertHtmlContentsMatch(
      [
        '<li>',
        '<div class="toggle" data-id=".nav-module:example">',
        '<a class="item" tabindex="1" href="module_example.html">example</a>',
        '<i class="material-icons">expand_more</i>',
        '</div>',
        '<ul class="tree">',
        '<li>',
        '<a class="item" tabindex="1" href="module_example_nested.html">nested</a>',
        '</li>',
        '</ul>',
        '</ul>',
        '</li>'
      ].join(''),
      root,
      true);
}


function testBuildList_prependsBasePathToLinks() {
  var input = [{name: 'GlobalCtor', qualifiedName: 'GlobalCtor',
                href: 'class_GlobalCtor.html'}];

  var root = nav.buildList(input, '../..');
  var el = root.querySelector('a[href]');
  assertEquals('../../class_GlobalCtor.html', el.getAttribute('href'));
}


function testBuildList_marksTheNodeForTheCurrentFile() {
  var input = [{name: 'GlobalCtor', qualifiedName: 'GlobalCtor',
                href: 'class_GlobalCtor.html'}];

  var root = nav.buildList(input, '../..', 'class_GlobalCtor.html');
  var el = root.querySelector('.current');
  assertEquals('A', el.tagName);
  assertEquals('GlobalCtor', el.textContent);
}


function testBuildList_marksTheNodeForTheCurrentFile_namespace() {
  var input = [
    {name: 'foo',
     qualifiedName: 'foo',
     namespace: true,
     href: 'class_GlobalCtor.html'},
    {name: 'foo.bar', qualifiedName: 'foo.bar', href: ''}
  ];

  var root = nav.buildList(input, '../..', 'class_GlobalCtor.html');
  var el = root.querySelector('.current');
  assertEquals('A', el.tagName);
  assertEquals('DIV', el.parentNode.tagName);
  assertEquals('.nav:foo', el.parentNode.dataset.id);
}
