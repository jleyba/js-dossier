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

/**
 * @fileoverview Main script for a generated page of documentation. This
 * script will initliaze the auto-complete search box and load the initial
 * state of the side nav list.
 */

goog.module('dossier.main');

const Index = goog.require('dossier.Index');
const app = goog.require('dossier.app');
const nav = goog.require('dossier.nav');
const page = goog.require('dossier.page');
const search = goog.require('dossier.search');
const browser = goog.require('goog.labs.userAgent.browser');
const engine = goog.require('goog.labs.userAgent.engine');

if (engine.isWebKit() && !browser.isChrome() && !browser.isOpera()) {
  // Tag the browser as webkit (not blink) so we can avoid some ugly
  // transitions in CSS
  document.documentElement.classList.add('webkit');
}

const typeIndex = new Index(/** @type {!Object} */(goog.global['TYPES']));
app.run(
    typeIndex,
    search.createSearchBox(typeIndex),
    nav.createNavDrawer(typeIndex, page.getCurrentFile(), page.getBasePath()));
