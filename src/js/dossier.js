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

goog.provide('dossier');

goog.require('dossier.nav');
goog.require('goog.array');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.dom');
goog.require('goog.dom.classlist');
goog.require('goog.string');
goog.require('goog.ui.ac');
goog.require('goog.ui.ac.AutoComplete.EventType');

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
  var ac = goog.ui.ac.createSimpleAutoComplete(allTerms, input, false, true);
  ac.setMaxMatches(20);
  goog.events.listen(ac,
      goog.ui.ac.AutoComplete.EventType.UPDATE, navigatePage);

  function navigatePage() {
    var href = nameToHref[input.value];
    if (href) {
      window.location.href = dossier.BASE_PATH_ + href;
    }
  }
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

  if (opt_isModule) {
    descriptorName = '(' + descriptorName + ')';
  }

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
  var currentFile = '';
  if (dossier.BASE_PATH_) {
    currentFile = window.location.pathname
      .split('/')
      .slice(dossier.BASE_PATH_.split('/').length)
      .join('/');
  } else if (window.location.pathname && window.location.pathname !== '/') {
    currentFile = window.location.pathname.slice(
        window.location.pathname.lastIndexOf('/') + 1);
  }

  var nav = goog.module.get('dossier.nav');
  var view = goog.dom.getElement('nav-types-view');
  if (view && typeInfo.types) {
    view.appendChild(nav.buildList(
        typeInfo.types, dossier.BASE_PATH_, currentFile, false));
  }

  view = goog.dom.getElement('nav-modules-view');
  if (view && typeInfo.modules) {
    view.appendChild(nav.buildList(
        typeInfo.modules, dossier.BASE_PATH_, currentFile, true));
  }

  if (!window.localStorage) {
    return;
  }
  var nav = document.querySelector('nav');
  var inputs = nav.querySelectorAll('input[type="checkbox"][id]');
  goog.array.forEach(inputs, function(el) {
    var state = window.localStorage.getItem(el.id);
    if (goog.isString(state)) {
      el.checked = state == 'closed';
    } else {
      // Default to opened.
      el.checked = false;
    }
  });
  goog.events.listen(nav, goog.events.EventType.CHANGE, function(e) {
    window.localStorage.setItem(e.target.id,
        e.target.checked ? 'closed' : 'open');
  });
};
