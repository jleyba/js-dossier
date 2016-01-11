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

'use strict';

goog.provide('dossier');

goog.require('dossier.keyhandler');
goog.require('dossier.nav');
goog.require('dossier.search');
goog.require('goog.array');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.events.KeyCodes');
goog.require('goog.dom');
goog.require('goog.dom.TagName');
goog.require('goog.dom.classlist');
goog.require('goog.string');
goog.require('goog.userAgent');

goog.forwardDeclare('goog.debug.ErrorHandler');
goog.forwardDeclare('goog.events.EventWrapper');



/**
 * Initializes the dossier page.
 */
dossier.init = function() {
  let typeInfo = /** @type {!TypeRegistry} */(goog.global['TYPES']);

  const keyHandler = goog.module.get('dossier.keyhandler');
  const search = goog.module.get('dossier.search');

  let input = search.init(typeInfo, dossier.BASE_PATH_);
  let drawer = dossier.initNavList_(typeInfo);

  keyHandler.init(drawer, input);

  dossier.initSourceHilite_();
  setTimeout(dossier.adjustTarget_, 0);
};
goog.exportSymbol('init', dossier.init);


/**
 * Computes the relative path used to load this script. It is assumed that
 * this script is always in a directory that is an ancestor of the current
 * file running this script.
 * @private {string}
 * @const
 */
dossier.BASE_PATH_ = (function() {
  var scripts = goog.dom.getDocument().querySelectorAll('script');
  var dirPath = '';
  var thisFile = 'dossier.js';
  goog.array.find(scripts, function(script) {
    var src = script.getAttribute('src');
    var len = src.length;
    if (src.slice(len - thisFile.length) === thisFile) {
      dirPath = src.slice(0, len - thisFile.length);
      return true;
    }
    return false;
  });

  let trimN = dirPath.split(/\.\.\//).length;
  let currentPath = window.location.pathname.split(/\//);
  currentPath.splice(currentPath.length - trimN, trimN);

  let basePath = currentPath.join('/');
  if (!basePath.endsWith('/')) {
    basePath += '/';
  }
  return basePath;
})();


/**
 * Scrolls the page to ensure the active target (if any) is not hidden by the
 * fixed position header.
 * @private
 */
dossier.adjustTarget_ = function() {
  let target = document.querySelector(':target');
  if (!target) {
    return;
  }
  let position = target.getBoundingClientRect();
  if (position.top < 56) {
    let scrollBy = 64 - position.top;
    document.querySelector('.content').scrollTop -= scrollBy;
  }
};


/**
 * Initializes a history event listener to hilite the selected row when
 * viewing a "srcfile" article.
 * @private
 */
dossier.initSourceHilite_ = function() {
  var srcTable = document.querySelector('article.srcfile table');
  if (!srcTable) {
    return;
  }

  var hash = location.hash;
  if (hash) {
    var target = document.querySelector('tr > td a' + location.hash);
    if (target) {
      highlightRow(target);
    }
  }

  goog.events.listen(window, goog.events.EventType.HASHCHANGE, function(e) {
    goog.array.forEach(srcTable.querySelectorAll('tr.hilite'), function(tr) {
      goog.dom.classlist.remove(tr, 'hilite');
    });
    var a = srcTable.querySelector('tr > td a:target');
    if (a) {
      highlightRow(a);
    }
  });

  /**
   * Applies the highlight class to the row containing the given link.
   * @param {!Element} a The element whose row to highlight.
   */
  function highlightRow(a) {
    var tr = /** @type {!Element} */(goog.dom.getAncestor(a, function(node) {
      return node.nodeName === 'TR';
    }));
    goog.dom.classlist.add(tr, 'hilite');
  }
};


/**
 * Initializes the side navigation bar from local history.
 * @param {!TypeRegistry} typeInfo The type information to build the list from.
 * @return {!dossier.nav.NavDrawer} The new nav drawer widget.
 * @private
 */
dossier.initNavList_ = function(typeInfo) {
  const currentFile = window.location.pathname.slice(dossier.BASE_PATH_.length);
  const nav = goog.module.get('dossier.nav');
  return nav.createNavDrawer(typeInfo, currentFile, dossier.BASE_PATH_);
};
