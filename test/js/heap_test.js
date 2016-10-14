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

goog.provide('dossier.Heap.test');
goog.setTestOnly('dossier.Heap.test');

goog.require('dossier.Heap');
goog.require('goog.testing.jsunit');


const Heap = goog.module.get('dossier.Heap');


function makeHeap(...args) {
  let heap = new Heap((a, b) => b.key - a.key);
  for (let arg of args) {
    heap.insert(arg[0], arg[1]);
  }
  return heap;
}


function testSize() {
  let h = makeHeap([0, 'a'], [1, 'b'], [2, 'c'], [3, 'd']);
  assertEquals(4, h.size());

  h.remove();
  assertEquals(3, h.size());

  h.remove();
  h.remove();
  h.remove();
  assertEquals(0, h.size());
}


function testKeys() {
  let h = makeHeap([0, 'a'], [1, 'b'], [2, 'c'], [3, 'd']);

  let keys = h.keys();
  assertEquals(keys.toString(), 4, keys.length);

  for (let i = 0; i < 4; i++) {
    assertTrue(`keys contains ${i}: ${keys}`, keys.indexOf(i) != -1);
  }
}


function testValues() {
  let h = makeHeap([0, 'a'], [1, 'b'], [2, 'c'], [3, 'd']);

  let values = h.values();
  assertEquals(values.toString(), 4, values.length);

  for (let i of ['a', 'b', 'c', 'd']) {
    assertTrue(`values contains ${i}: ${values}`, values.indexOf(i) != -1);
  }
}

function assertRoot(heap, key, value) {
  assertEquals(value, heap.peek());
  assertEquals(key, heap.peekKey());
}


function testPeek_emptyHeap() {
  let h = makeHeap();
  assertUndefined(h.peek());
  assertUndefined(h.peekKey());
}


function testPeek() {
  let h = makeHeap([0, 'a'], [1, 'b'], [2, 'c'], [3, 'd']);
  assertRoot(h, 3, 'd');

  h = makeHeap([1, 'b'], [3, 'd'], [0, 'a'], [2, 'c']);
  assertRoot(h, 3, 'd');

  h = makeHeap([3, 'd'], [2, 'c'], [1, 'b'], [0, 'a']);
  assertRoot(h, 3, 'd');
}


function testRemove() {
  let h = makeHeap([0, 'a'], [1, 'b'], [2, 'c'], [3, 'd']);
  assertEquals('d', h.remove());
  assertEquals('c', h.remove());
  assertEquals('b', h.remove());
  assertEquals('a', h.remove());
  assertUndefined(h.remove());
}


function testInsertPeek_max() {
  let h = makeHeap();
  h.insert(0, 'a');
  assertRoot(h, 0, 'a');
  h.insert(0, 'aa');
  assertRoot(h, 0, 'a');
  h.insert(1, 'b');
  assertRoot(h, 1, 'b');
  h.insert(2, 'c');
  assertRoot(h, 2, 'c');
  h.insert(3, 'd');
  assertRoot(h, 3, 'd');
  h.insert(3, 'dd');
  assertRoot(h, 3, 'd');
}


function testEntries() {
  let h = makeHeap([3, 'd'], [2, 'c'], [1, 'b'], [0, 'a']);
  let entries = h.entries().map(e => e.value).sort();
  assertArrayEquals(['a', 'b', 'c', 'd'], entries);
}
