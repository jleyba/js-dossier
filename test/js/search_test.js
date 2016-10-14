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

goog.provide('dossier.search.test');
goog.setTestOnly('dossier.search.test');

goog.require('dossier.search');
goog.require('goog.testing.jsunit');

const search = goog.module.get('dossier.search');


function createMatcher(...terms) {
  return new search.AutoCompleteMatcher(terms);
}


function testBasicMatch() {
  let matcher = createMatcher('a', 'Ab', 'abc', 'ba', 'ca');
  assertArrayEquals(
      ['a', 'ba', 'ca', 'Ab', 'abc'],
      matcher.match('a', 10));
}


function testReturnsClosestIfMorePrefixMatchesThanMax() {
  let matches = createMatcher('a', 'Ab', 'abc', 'ba', 'ca').match('a', 3);
  matches.sort();
  assertArrayEquals(['a', 'ba', 'ca'], matches);
}


function testCanMatchOnMidwordString() {
  let matcher = createMatcher('foo.bar.One', 'foo.bar.Two');
  let matches = matcher.match('o.bar', 2);
  assertArrayEquals(['foo.bar.One', 'foo.bar.Two'], matches);
}


function testClosenessMatch() {
  let matcher = createMatcher(
    'Display', 'DisplayManager', 'DisplayGraph', 'DisplayWorker',
    'DisplayItem', 'DisplayEventListener', 'DisplayEventTarget',
    'WorkingDisplay', 'BrokenDisplay');

  assertArrayEquals([
    'Display',
    'DisplayItem',
    'DisplayGraph'
  ], matcher.match('display', 3));

  assertArrayEquals([
    'Display',
    'DisplayItem',
    'DisplayGraph',
    'DisplayWorker'
  ], matcher.match('display', 4));

  assertArrayEquals([
    'Display',
    'DisplayItem',
    'DisplayGraph',
    'DisplayWorker',
    'BrokenDisplay'
  ], matcher.match('display', 5));
}
