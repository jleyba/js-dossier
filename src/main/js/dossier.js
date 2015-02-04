/**
 * @license Copyright 2013 Jason Leyba
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * @fileoverview Main script for a generated page of documentation. This
 * script will initliaze the auto-complete search box and load the initial
 * state of the side nav list.
 */

goog.provide('dossier');

goog.require('goog.array');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.dom');
goog.require('goog.dom.classlist');
goog.require('goog.string');
goog.require('goog.ui.ac');
goog.require('goog.ui.ac.AutoComplete.EventType');


/**
 * @typedef {{name: string,
 *            href: string,
 *            statics: Array.<dossier.Descriptor_>,
 *            members: Array.<dossier.Descriptor_>,
 *            types: Array.<dossier.Descriptor_>}}
 * @private
 */
dossier.Descriptor_;


/**
 * @typedef {{types: !Array.<dossier.Descriptor_>,
 *            modules: !Array.<dossier.Descriptor_>}}
 * @private
 */
dossier.TypeInfo_;


/**
 * Initializes the dossier page.
 */
dossier.init = function() {
  var typeInfo = /** @type {dossier.TypeInfo_} */(goog.global['TYPES']);
  dossier.initSearchBox_(typeInfo);
  dossier.initNavList_();
  dossier.initSourceHilite_();
};
goog.exportSymbol('init', dossier.init);


/**
 * Computes the relative path used to load this script.
 * @private {string}
 * @const
 */
dossier.BASE_PATH_ = (function() {
  var scripts = goog.dom.getDocument().getElementsByTagName('script');
  var dirPath = './';
  var thisFile = 'dossier.js';
  goog.array.find(scripts, function(script) {
    var src = script.src;
    var len = src.length;
    if (src.substr(len - thisFile.length) === thisFile) {
      dirPath = src.substr(0, len - thisFile.length);
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
 * @param {dossier.TypeInfo_} typeInfo The types to link to from the current
 *     page.
 * @private
 */
dossier.initSearchBox_ = function(typeInfo) {
  var nameToHref = {};
  var allTerms = [];
  goog.array.forEach(typeInfo['types'], function(descriptor) {
    dossier.addTypes_(allTerms, nameToHref, descriptor);
  });

  goog.array.forEach(typeInfo['modules'], function(module) {
    dossier.addTypes_(allTerms, nameToHref, module, true);
  });

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
 * @param {dossier.Descriptor_} descriptor .
 * @param {boolean=} opt_isModule .
 * @param {string=} opt_parent .
 * @private
 */
dossier.addTypes_ = function(terms, nameToHref, descriptor, opt_isModule, opt_parent) {
  var descriptorName = descriptor['name'];
  if (opt_parent) {
    descriptorName = opt_parent +
        (goog.string.endsWith(opt_parent, ')') ? ' ' : '.') +
        descriptorName;
  }
  nameToHref[descriptorName] = descriptor['href'];
  terms.push(descriptorName);

  if (opt_isModule) {
    descriptorName = '(' + descriptorName + ')';
  }

  if (opt_isModule && descriptor['types']) {
    goog.array.forEach(descriptor['types'], function(type) {
      dossier.addTypes_(terms, nameToHref, type, false, descriptorName);
    });
  }

  if (descriptor['statics']) {
    goog.array.forEach(descriptor['statics'], function(name) {
      var href = descriptor['href'] + '#' + name;
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

  if (descriptor['members']) {
    goog.array.forEach(descriptor['members'], function(name) {
      var href = descriptor['href'] + '#' + name;
      nameToHref[descriptorName + '#' + name] = href;
      terms.push(descriptorName + '#' + name);
    });
  }
};


/**
 * Initializes the side navigation bar from local history.
 * @private
 */
dossier.initNavList_ = function() {
  initChangeHandler('nav-types', 'dossier.typesList');
  initChangeHandler('nav-modules', 'dossier.modulesList');

  /**
   * @param {string} id .
   * @param {string} storageKey .
   */
  function initChangeHandler(id, storageKey) {
    var controlEl = goog.dom.getElement(id);
    if (!controlEl) {
      return;
    }

    if (window.localStorage) {
      var state = window.localStorage.getItem(storageKey);
      controlEl.checked = !goog.isString(state) || state === 'closed';

      goog.events.listen(controlEl, goog.events.EventType.CHANGE, function() {
        window.localStorage.setItem(
            storageKey, controlEl.checked ? 'closed' : 'open');
      });
    }
  }
};
