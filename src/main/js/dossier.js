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
 * @fileoverview Main script for a generated page of documentation. This page
 * will build the list of links for the left navigation column and initialize
 * the auto-complete search box for the top navigation bar.
 */

goog.provide('dossier');

goog.require('goog.array');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.dom');
goog.require('goog.dom.NodeType');
goog.require('goog.dom.TagName');
goog.require('goog.dom.classes');
goog.require('goog.style');
goog.require('goog.ui.ac');


/**
 * @typedef {({name: string, href: string, isInterface: boolean}|
 *            {name: string, href: string, isTypedef: boolean}|
 *            {name: string, href: string})}
 * @private
 */
dossier.Descriptor_;


/**
 * @typedef {{types: !Array.<dossier.Descriptor_>,
 *            files: !Array.<dossier.Descriptor_>}}
 * @private
 */
dossier.TypeInfo_;


/**
 * Initializes the dossier page.
 */
dossier.init = function() {
  var typeInfo = /** @type {dossier.TypeInfo_} */(goog.global['TYPES']);
  dossier.initNavList_(typeInfo);
  setTimeout(goog.partial(dossier.initSearchBox_, typeInfo), 0);
  setTimeout(dossier.polyFillDetailsElements_, 0);
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
 * Initializes the auto-complete for the top navigation bar's search box.
 * @param {dossier.TypeInfo_} typeInfo The types to link to from the current
 *     page.
 * @private
 */
dossier.initSearchBox_ = function(typeInfo) {
  var nameToHref = {};
  var allTerms = goog.array.concat(
      goog.array.map(typeInfo['files'], function(descriptor) {
        var name = 'file://' + descriptor['name'];
        nameToHref[name] = descriptor['href'];
        return name;
      }),
      goog.array.map(typeInfo['types'], function(descriptor) {
        nameToHref[descriptor['name']] = descriptor['href'];
        return descriptor['name'];
      }));

  var searchForm = goog.dom.getElement('searchbox');
  var input = searchForm.getElementsByTagName('input')[0];
  var ac = goog.ui.ac.createSimpleAutoComplete(allTerms, input, false, true);
  ac.setMaxMatches(100);
  goog.events.listen(searchForm, goog.events.EventType.SUBMIT, function(e) {
    e.preventDefault();
    e.stopPropagation();

    var href = nameToHref[input.value];
    if (href) {
      window.location.href = dossier.BASE_PATH_ + href;
    }

    return false;
  });
};


/**
 * Builds the navigation side bar.
 * @param {dossier.TypeInfo_} typeInfo The types to link to from the current
 *     page.
 * @private
 */
dossier.initNavList_ = function(typeInfo) {
  if (!goog.dom.getElement('sidenav')) {
    return;  // The current page does not have a side nav pane.
  }

  // Now that everything is built, configure the controls to expand and
  // collapse the very portions of the navigation menu. We can't do this
  // through CSS because we effectively want to toggle between
  // height: 0 and height: auto, but height: auto cannot be animated.
  var typesControl = goog.dom.getElement('sidenav-types-ctrl');
  var typesList = dossier.createNavList_('sidenav-types', typeInfo['types']);

  var filesControl = goog.dom.getElement('sidenav-files-ctrl');
  var filesList = dossier.createFileNavList_(
      'sidenav-files', typeInfo['files']);

  // Compute sizes in terms of the root font-size, so things scale properly.
  var rootFontSize = goog.style.getFontSize(document.documentElement);
  var typesHeight =
      (goog.style.getSize(typesList).height / rootFontSize) + 'rem';
  var filesHeight =
      (goog.style.getSize(filesList).height / rootFontSize) + 'rem';

  // Initialize heights to 0.
  goog.style.setHeight(typesList, 0);
  goog.style.setHeight(filesList, 0);

  goog.events.listen(typesControl, goog.events.EventType.CHANGE, function() {
    goog.style.setHeight(typesList, typesControl.checked ? typesHeight : 0);
  });
  goog.events.listen(filesControl, goog.events.EventType.CHANGE, function() {
    goog.style.setHeight(filesList, filesControl.checked ? filesHeight : 0);
  });
};


/**
 * Abstract representation of a documented file.
 * @param {string} name The file name.
 * @param {string=} opt_href Link to the file; may be omitted if this instance
 *     represents a directory.
 * @constructor
 * @private
 */
dossier.File_ = function(name, opt_href) {

  /** @type {string} */
  this.name = name;

  /** @type {string} */
  this.href = opt_href || '';

  /** @type {!Array.<!dossier.File_>} */
  this.children = [];
};


/**
 * @param {!Array.<dossier.Descriptor_>} files The file descriptors to
 *     build a tree structure from.
 * @returns {dossier.File_} The root file.
 * @private
 */
dossier.buildFileTree_ = function(files) {
  var root = new dossier.File_('');

  goog.array.forEach(files, function(file) {
    var parts = file['name'].split(/[\/\\]/);

    var current = root;
    goog.array.forEach(parts, function(part, index) {
      if (!part) return;

      if (index === parts.length - 1) {
        current.children.push(new dossier.File_(part, file['href']));
      } else {
        var next = goog.array.find(current.children, function(dir) {
          return dir.name === part;
        });
        if (!next) {
          next = new dossier.File_(part);
          current.children.push(next);
        }
        current = next;
      }
    });
  });

  collapseDirs(root);
  return root;

  function collapseDirs(file) {
    goog.array.forEach(file.children, collapseDirs);

    if (!file.href && file.children.length === 1) {
      var name = file.name ? file.name + '/' : '';
      file.name = name + file.children[0].name;
      file.href = file.children[0].href;
      file.children = file.children[0].children;
    }
  }
};


/**
 * Builds a list of file links for the side navigation pane.
 * @param {string} id ID for the section to attach the created list to.
 * @param {!Array.<dossier.Descriptor_>} files The descriptors to build
 *     links for.
 * @return {!Element} The configured nav list display element.
 * @private
 */
dossier.createFileNavList_ = function(id, files) {
  var container = goog.dom.getElement(id);
  var noDataPlaceholder =
      /** @type {!Element} */ (container.querySelector('i'));

  if (!files.length) {
    return noDataPlaceholder;
  }

  goog.dom.removeNode(noDataPlaceholder);
  var rootEl = goog.dom.createElement(goog.dom.TagName.UL);
  goog.dom.appendChild(container, rootEl);

  var rootFile = dossier.buildFileTree_(files);
  processTreeNode(rootFile, rootEl);
  return rootEl;

  /**
   * @param {dossier.File_} file .
   * @param {!Element} parentEl .
   */
  function processTreeNode(file, parentEl) {
    var isDirectory = !!file.children.length;
    if (!isDirectory) {
      goog.dom.appendChild(
          parentEl,
          goog.dom.createDom(
              goog.dom.TagName.LI, 'link',
              goog.dom.createDom(goog.dom.TagName.A, {
                'href': dossier.BASE_PATH_ + file.href
              }, file.name)));
      return;
    }

    var name = file.name ? file.name + '/' : '';
    var list = goog.dom.createElement(goog.dom.TagName.UL);
    goog.dom.appendChild(
        parentEl,
        goog.dom.createDom(goog.dom.TagName.LI, null, name, list));
    goog.array.forEach(file.children, function(child) {
      processTreeNode(child, list);
    });
  }
};


/**
 * Builds a list of type links for the side navigation pane.
 * @param {string} id ID for the section to attach the created list to.
 * @param {!Array.<dossier.Descriptor_>} descriptors The descriptors to build
 *     links for.
 * @return {!Element} The configured nav list display element.
 * @private
 */
dossier.createNavList_ = function(id, descriptors) {
  var container = goog.dom.getElement(id);
  var noDataPlaceholder =
      /** @type {!Element} */ (container.querySelector('i'));

  if (!descriptors.length) {
    return noDataPlaceholder;
  }

  goog.dom.removeNode(noDataPlaceholder);

  var list = goog.dom.createElement(goog.dom.TagName.UL);
  goog.array.forEach(descriptors, function(descriptor) {
    if (descriptor['isTypedef']) {
      return;  // Do not include typedefs in the side index.
    }

    var listItem = descriptor['name'];
    if (descriptor['isInterface']) {
      listItem = goog.dom.createDom(goog.dom.TagName.I, null, listItem);
    }

    goog.dom.appendChild(list,
        goog.dom.createDom(goog.dom.TagName.LI, 'link',
            goog.dom.createDom(goog.dom.TagName.A, {
              'href': dossier.BASE_PATH_ + descriptor['href']
            }, listItem)));
  });
  goog.dom.appendChild(container, list);

  return list;
};


/**
 * Simulates the behavior of HTML5 details and summary tags for user-agents
 * that do not support them.
 * @private
 */
dossier.polyFillDetailsElements_ = function() {
  var doc = goog.dom.getDocument();
  var details = doc.getElementsByTagName(goog.dom.TagName.DETAILS);
  if (!details.length || details[0].hasOwnProperty('open')) {
    return;  // polyfill not needed.
  }

  goog.array.forEach(details, function(el) {
    var open = true;
    el.setAttribute('open', '');

    var onclick = function() {
      open = !open;
      if (!open) {
        el.removeAttribute('open');
      } else {
        el.setAttribute('open', '');
      }

      goog.array.forEach(el.childNodes, function(child) {
        if (child.nodeType === goog.dom.NodeType.ELEMENT &&
            child.tagName.toUpperCase() !== goog.dom.TagName.SUMMARY) {
          goog.style.setElementShown(child, open);
        }
      });
    };

    goog.events.listen(el, goog.events.EventType.CLICK, onclick);
    onclick();  // Start in a cloesd state.
  });
};
