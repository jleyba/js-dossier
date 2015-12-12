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
goog.require('goog.array');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.events.KeyCodes');
goog.require('goog.dom');
goog.require('goog.dom.TagName');
goog.require('goog.dom.classlist');
goog.require('goog.string');
goog.require('goog.ui.ac.ArrayMatcher');
goog.require('goog.ui.ac.AutoComplete');
goog.require('goog.ui.ac.InputHandler');
goog.require('goog.ui.ac.Renderer');
goog.require('goog.userAgent');

goog.forwardDeclare('goog.debug.ErrorHandler');
goog.forwardDeclare('goog.events.EventWrapper');
goog.forwardDeclare('goog.ui.ac.RenderOptions');



/**
 * Initializes the dossier page.
 */
dossier.init = function() {
  var typeInfo = /** @type {!TypeRegistry} */(goog.global['TYPES']);
  dossier.initSearchBox_(typeInfo);
  dossier.initNavList_(typeInfo);
  dossier.initSourceHilite_();
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
  return dirPath;
})();


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
 * Initializes the auto-complete for the top navigation bar's search box.
 * @param {!TypeRegistry} typeInfo The types to link to from the current
 *     page.
 * @private
 */
dossier.initSearchBox_ = function(typeInfo) {
  var nameToHref = {};
  var allTerms = [];
  if (typeInfo.types) {
    goog.array.forEach(typeInfo.types, function(descriptor) {
      dossier.addTypes_(allTerms, nameToHref, descriptor);
    });
  }

  if (typeInfo.modules) {
    goog.array.forEach(typeInfo.modules, function(module) {
      dossier.addTypes_(allTerms, nameToHref, module, true);
    });
  }

  var searchForm = document.querySelector('header form');
  goog.events.listen(searchForm, goog.events.EventType.SUBMIT, function(e) {
    e.preventDefault();
    e.stopPropagation();
    navigatePage();
    return false;
  });

  var input = searchForm.querySelector('input');
  input.setAttribute(
      'title', 'Search (' + (goog.userAgent.MAC ? '⌘' : 'Ctrl+') + 'E)');

  var ac = dossier.createAutoComplete_(allTerms, input);

  goog.events.listen(ac,
      goog.ui.ac.AutoComplete.EventType.UPDATE, navigatePage);

  goog.events.listen(
      document.documentElement,
      goog.events.EventType.KEYDOWN,
      function(e) {
        if (document.activeElement !== input
            && e.keyCode === goog.events.KeyCodes.E
            && (goog.userAgent.MAC ? e.metaKey : e.ctrlKey)) {
          input.focus();
          e.preventDefault();
          e.stopPropagation();
          return false;
        } else if (document.activeElement === input
            && e.keyCode === goog.events.KeyCodes.ESC) {
          input.blur();
        }
      });

  function navigatePage() {
    var href = nameToHref[input.value];
    if (href) {
      window.location.href = dossier.BASE_PATH_ + href;
    }
  }
};


/**
 * @param {!Array<?>} data The input data array.
 * @param {!Element} input The controlling input element.
 * @return {!goog.ui.ac.AutoComplete} A new autocomplete object.
 * @private
 */
dossier.createAutoComplete_ = function(data, input) {
  const parent = goog.dom.createDom('div', 'dossier-ac');
  parent.ownerDocument.body.appendChild(parent);

  let matcher = new goog.ui.ac.ArrayMatcher(data, true);
  let renderer = new goog.ui.ac.Renderer(parent);
  let inputHandler = new goog.ui.ac.InputHandler(null, null, false);

  let ac = new goog.ui.ac.AutoComplete(matcher, renderer, inputHandler);
  ac.setMaxMatches(6);

  inputHandler.attachAutoComplete(ac);
  inputHandler.attachInputs(input);

  renderer.setAutoPosition(false);
  renderer.setShowScrollbarsIfTooLarge(true);
  renderer.setUseStandardHighlighting(true);

  return ac;
};


/**
 * @param {!Array<string>} terms .
 * @param {!Object<string, string>} nameToHref .
 * @param {!Descriptor} descriptor .
 * @param {boolean=} opt_isModule .
 * @param {string=} opt_parent .
 * @private
 */
dossier.addTypes_ = function(terms, nameToHref, descriptor, opt_isModule, opt_parent) {
  var descriptorName = descriptor.name;
  if (opt_parent) {
    descriptorName = opt_parent +
        (goog.string.endsWith(opt_parent, ')') ? ' ' : '.') +
        descriptorName;
  }
  nameToHref[descriptorName] = descriptor.href;
  terms.push(descriptorName);

  if (opt_isModule && descriptor.types) {
    goog.array.forEach(descriptor.types, function(type) {
      dossier.addTypes_(terms, nameToHref, type, false, descriptorName);
    });
  }

  if (descriptor.statics) {
    goog.array.forEach(descriptor.statics, function(name) {
      var href = descriptor.href + '#' + name;
      if (goog.string.endsWith(descriptorName, ')')) {
        name = descriptorName + ' ' + name;
      } else if (name.indexOf('.') === -1) {
        name = descriptorName + '.' + name;
      } else {
        name = descriptorName + name.slice(name.lastIndexOf('.'));
      }
      nameToHref[name] = href;
      terms.push(name);
    });
  }

  if (descriptor.members) {
    goog.array.forEach(descriptor.members, function(name) {
      var href = descriptor.href + '#' + name;
      nameToHref[descriptorName + '#' + name] = href;
      terms.push(descriptorName + '#' + name);
    });
  }
};


/**
 * Initializes the side navigation bar from local history.
 * @param {!TypeRegistry} typeInfo The type information to build the list from.
 * @private
 */
dossier.initNavList_ = function(typeInfo) {
  const currentFile = window.location.pathname.slice(1);

  const nav = goog.module.get('dossier.nav');

  const navButton = document.querySelector('button.dossier-menu');
  const navEl = document.querySelector('nav');

  const mask = nav.createMask();
  navEl.parentNode.insertBefore(mask, navEl.nextSibling);

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
