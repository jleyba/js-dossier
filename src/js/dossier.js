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

/**
 * @fileoverview Main script for a generated page of documentation. This
 * script will initliaze the auto-complete search box and load the initial
 * state of the side nav list.
 */

'use strict';

goog.provide('dossier');

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

  const search = goog.module.get('dossier.search');
  search.init(typeInfo, dossier.BASE_PATH_);

  dossier.initNavList_(typeInfo);
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
 * @private
 */
dossier.initNavList_ = function(typeInfo) {
  const currentFile = window.location.pathname.slice(dossier.BASE_PATH_.length);

  const nav = goog.module.get('dossier.nav');

  const navButton = document.querySelector('button.dossier-menu');
  const navEl = document.querySelector('nav');

  const mask = nav.createMask();
  navEl.parentNode.appendChild(mask);

  const toggleVisibility = () => navEl.classList.toggle('visible');
  goog.events.listen(mask, goog.events.EventType.CLICK, toggleVisibility);
  goog.events.listen(navButton, goog.events.EventType.CLICK, toggleVisibility);

  let typeSection = navEl.querySelector('.types');
  if (typeSection && typeInfo.types) {
    buildSectionList(typeSection, typeInfo.types, false);
  }

  let moduleSection = navEl.querySelector('.modules');
  if (moduleSection && typeInfo.modules) {
    buildSectionList(moduleSection, typeInfo.modules, true);
  }

  goog.events.listen(navEl, goog.events.EventType.CLICK, function(e) {
    let el = goog.dom.getAncestor(e.target, function(node) {
      return node
          && node.classList
          && node.classList.contains('toggle');
    }, true, 4);

    if (el && el.classList.contains('toggle')) {
      updateControl(/** @type {!Element} */(el));
    }
  });

  let trees = navEl.querySelectorAll('.tree');
  for (let i = 0; i < trees.length; i++) {
    let tree = trees[i];
    let numItems = tree.querySelectorAll('li').length;
    tree.dataset.maxHeight = numItems * 48;  // TODO: magic number!
    tree.style.maxHeight = 0;
  }

  if (window.localStorage) {
    let toggles = navEl.querySelectorAll('.toggle[data-id]');
    goog.array.forEach(toggles, function(el) {
      var state = window.localStorage.getItem(el.dataset.id);
      updateControl(el, state === 'open');
    });
  }

  let current = navEl.querySelector('.current');
  if (current) {
    revealElement(current);
  }

  /** @param {!Element} el . */
  function revealElement(el) {
    for (let current = el;
        current && current != navEl;
        current = goog.dom.getParentElement(current)) {
      if (current.classList.contains('tree')) {
        let control = current.previousElementSibling;
        if (control && control.classList.contains('toggle')) {
          updateControl(control, true, true);
        }
      }
    }
    navEl.scrollTop = el.offsetTop - (window.innerHeight / 2);
  }

  /**
   * @param {!Element} section .
   * @param {!Array<!Descriptor>} descriptors .
   * @param {boolean} isModule .
   */
  function buildSectionList(section, descriptors, isModule) {
    let list =
        nav.buildList(descriptors, dossier.BASE_PATH_, currentFile, isModule);
    section.appendChild(list);

    let toggle = section.querySelector('.toggle');
    toggle.dataset.id = nav.getIdPrefix(isModule);
  }

  /**
   * @param {!Element} el .
   * @param {boolean=} opt_value .
   * @param {boolean=} opt_skipPersist .
   */
  function updateControl(el, opt_value, opt_skipPersist) {
    if (goog.isBoolean(opt_value)) {
      if (opt_value) {
        el.classList.add('open');
      } else {
        el.classList.remove('open');
      }
    } else {
      el.classList.toggle('open');
    }

    if (!opt_skipPersist) {
      updateStorage(el);
    }

    let tree = el.nextSibling;
    if (tree && tree.classList.contains('tree')) {
      if (el.classList.contains('open')) {
        tree.style.maxHeight = tree.dataset.maxHeight + 'px';
      } else {
        tree.style.maxHeight = '0';
      }
    }
  }

  /** @param {!Element} el . */
  function updateStorage(el) {
    if (window.localStorage && el.dataset.id) {
      window.localStorage.setItem(
          el.dataset.id,
          el.classList.contains('open') ? 'open' : 'closed');
    }
  }
};
