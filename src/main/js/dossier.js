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

goog.require('dossier.soy');
goog.require('goog.array');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.dom');
goog.require('goog.dom.NodeType');
goog.require('goog.dom.TagName');
goog.require('goog.dom.classlist');
goog.require('goog.soy');
goog.require('goog.style');
goog.require('goog.ui.ac');
goog.require('goog.ui.ac.AutoComplete.EventType');


/**
 * @typedef {{name: string,
 *            href: string,
 *            isInterface: boolean,
 *            isTypedef: boolean,
 *            types: Array.<dossier.Descriptor_>}}
 * @private
 */
dossier.Descriptor_;


/**
 * @typedef {{types: !Array.<dossier.Descriptor_>,
 *            modules: !Array.<dossier.Descriptor_>,
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
  dossier.initIndex_(typeInfo);
  dossier.initVisibilityControls_();
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

  goog.array.forEach(typeInfo['modules'], function(module) {
    nameToHref[module['name']] = module['href'];
    allTerms = goog.array.concat(
        allTerms,
        module['name'],
        goog.array.map(module['types'] || [], function(type) {
          var displayName = module['name'] + '.' + type['name'];
          nameToHref[displayName] = type['href'];
          return displayName;
        }));
  });

  var searchForm = goog.dom.getElement('searchbox');
  goog.events.listen(searchForm, goog.events.EventType.SUBMIT, function(e) {
    e.preventDefault();
    e.stopPropagation();
    navigatePage();
    return false;
  });

  var input = searchForm.getElementsByTagName('input')[0];
  var ac = goog.ui.ac.createSimpleAutoComplete(allTerms, input, false, true);
  ac.setMaxMatches(15);
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
 * Builds the list of links for the main index page.
 * @param {dossier.TypeInfo_} typeInfo The types to link to.
 * @private
 */
dossier.initIndex_ = function(typeInfo) {
  if (!goog.dom.getElement('type-index') ||
      !goog.dom.getElement('file-index') ||
      !goog.dom.getElement('module-index')) {
    return;  // The current page is not the main index.
  }

  dossier.createFileNavList_('file-index', typeInfo['files']);
  dossier.createNavList_('type-index', typeInfo['types']);
  dossier.createNavList_('module-index', typeInfo['modules'], true);
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

  var overview = goog.dom.getElement('sidenav-overview');
  overview.href = dossier.BASE_PATH_ + 'index.html';

  // Now that everything is built, configure the controls to expand and
  // collapse the very portions of the navigation menu. We can't do this
  // through CSS because we effectively want to toggle between
  // height: 0 and height: auto, but height: auto cannot be animated.
  var typesControl = goog.dom.getElement('sidenav-types-ctrl');
  var typesList = dossier.createNavList_('sidenav-types', typeInfo['types']);

  var modulesControl = goog.dom.getElement('sidenav-modules-ctrl');
  var modulesList = dossier.createNavList_(
      'sidenav-modules', typeInfo['modules']);

  var filesControl = goog.dom.getElement('sidenav-files-ctrl');
  var filesList = dossier.createFileNavList_(
      'sidenav-files', typeInfo['files']);

  // Compute sizes in terms of the root font-size, so things scale properly.
  var rootFontSize = goog.style.getFontSize(document.documentElement);
  var typesHeight =
      (goog.style.getSize(typesList).height / rootFontSize) + 'rem';
  var filesHeight =
      (goog.style.getSize(filesList).height / rootFontSize) + 'rem';
  var modulesHeight =
      (goog.style.getSize(modulesList).height / rootFontSize) + 'rem';

  // Initialize heights.
  typesControl.checked = dossier.loadCheckedState_('dossier.typesList');
  filesControl.checked = dossier.loadCheckedState_('dossier.filesList');
  modulesControl.checked = dossier.loadCheckedState_('dossier.modulesList');
  goog.style.setHeight(typesList, typesControl.checked ? typesHeight : 0);
  goog.style.setHeight(filesList, filesControl.checked ? filesHeight : 0);
  goog.style.setHeight(modulesList, modulesControl.checked ? modulesHeight : 0);

  initChangeHandler(typesControl, typesList, typesHeight, 'dossier.typesList');
  initChangeHandler(filesControl, filesList, filesHeight, 'dossier.filesList');
  initChangeHandler(modulesControl, modulesList, modulesHeight, 'dossier.modulesList');

  /**
   * @param {!Element} controlEl .
   * @param {!Element} listEl .
   * @param {string} expandedHeight .
   * @param {string} storageKey .
   */
  function initChangeHandler(controlEl, listEl, expandedHeight, storageKey) {
    goog.events.listen(controlEl, goog.events.EventType.CHANGE, function() {
      goog.style.setHeight(listEl, controlEl.checked ? expandedHeight : 0);
      dossier.storeCheckedState_(storageKey, controlEl);
    });
  }
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

    // Collapse files if the file is a directory whose only child is
    // also a directory.
    if (!file.href
        && file.children.length === 1
        && file.children[0].children.length) {
      var name = file.name ? file.name + '/' : '';
      file.name = name + file.children[0].name;
      file.href = file.children[0].href;
      file.children = file.children[0].children;
    }
  }
};


/**
 * Builds a heirarchical list of file links.
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

  var rootFile = dossier.buildFileTree_(files);
  var list = goog.soy.renderAsFragment(dossier.soy.fileNavlist, {
    file: rootFile,
    basePath: dossier.BASE_PATH_
  });
  goog.dom.removeNode(noDataPlaceholder);
  goog.dom.appendChild(container, /** @type {!Element} */(list));
  return /** @type {!Element} */(list);
};


/**
 * Builds a list of type links for the side navigation pane.
 * @param {string} id ID for the section to attach the created list to.
 * @param {!Array.<dossier.Descriptor_>} descriptors The descriptors to build
 *     links for.
 * @param {boolean=} opt_includeSubTypes Whether to include links to subtypes.
 * @return {!Element} The configured nav list display element.
 * @private
 */
dossier.createNavList_ = function(id, descriptors, opt_includeSubTypes) {
  var container = goog.dom.getElement(id);
  var placeHolder =
      /** @type {!Element} */ (container.querySelector('i'));

  var list = goog.soy.renderAsFragment(dossier.soy.navlist, {
    types: descriptors,
    basePath: dossier.BASE_PATH_,
    includeSubTypes: !!opt_includeSubTypes
  });

  if (list && list.childNodes.length) {
    goog.dom.removeNode(placeHolder);
    goog.dom.appendChild(container, list);
    return /** @type {!Element} */(list);
  } else {
    goog.style.setElementShown(container, false);
    return placeHolder;
  }
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


/**
 * Stores whether an element is currently checked.
 * @param {string} key The local storage item key to use.
 * @param {!Element} el The element whose checked state to store.
 * @private
 */
dossier.storeCheckedState_ = function(key, el) {
  if (window.localStorage) {
    if (el.checked) {
      window.localStorage.setItem(key, '1');
    } else {
      window.localStorage.removeItem(key);
    }
  }
};


/**
 * Loads whether a key is set in local storage.
 * @param {string} key The key to check.
 * @return {boolean} Whether the key is set.
 * @private
 */
dossier.loadCheckedState_ = function(key) {
  return window.localStorage ? !!window.localStorage.getItem(key) : false;
};


/**
 * @enum {string}
 */
dossier.Visibility = {
  PUBLIC: 'public',
  PROTECTED: 'protected',
  PRIVATE: 'private'
};


/**
 * @private {string}
 * @const
 */
dossier.VISIBILITY_STORAGE_NAMESPACE_ = 'dossier.visibility';


/**
 * Computes the key used for a visibility key in local storage.
 * @param {dossier.Visibility} visibility .
 * @return {string} .
 * @private
 */
dossier.getVisibilityKey_ = function(visibility) {
  return dossier.VISIBILITY_STORAGE_NAMESPACE_ + '.' + visibility;
};


/**
 * @private
 */
dossier.initVisibilityControls_ = function() {
  if (!document.getElementById('show-public')) return;

  var main = document.getElementsByTagName('main')[0];
  var total = {public: 0, protected: 0, private: 0};

  initControlStates();
  initControl('show-public', dossier.Visibility.PUBLIC);
  initControl('show-protected', dossier.Visibility.PROTECTED);
  initControl('show-private', dossier.Visibility.PRIVATE);

  insertPlaceholders(document.getElementById('typedefs'));
  insertPlaceholders(document.getElementById('instance-methods'));
  insertPlaceholders(document.getElementById('instance-properties'));
  insertPlaceholders(document.getElementById('static-functions'));
  insertPlaceholders(document.getElementById('static-properties'));
  insertPlaceholders(document.getElementById('compiler-constants'));

  goog.style.setElementShown(
      document.getElementById('visibility-controls'),
      total.public || total.protected || total.private);

  function initControlStates() {
    var ls = window.localStorage;
    if (ls && !ls.getItem(dossier.VISIBILITY_STORAGE_NAMESPACE_)) {
      ls.setItem(dossier.VISIBILITY_STORAGE_NAMESPACE_, '1');
      ls.setItem(dossier.getVisibilityKey_(dossier.Visibility.PUBLIC), '1');
      ls.removeItem(dossier.getVisibilityKey_(dossier.Visibility.PROTECTED));
      ls.removeItem(dossier.getVisibilityKey_(dossier.Visibility.PRIVATE));
    }
  }

  /**
   * @param {string} id Element id.
   * @param {dossier.Visibility} visibility The visibility the element controls.
   */
  function initControl(id, visibility) {
    var control = document.getElementById(id);
    if (control) {
      var key = dossier.getVisibilityKey_(visibility);
      control.checked = dossier.loadCheckedState_(key);
      onChange(/** @type {!Element} */(control), visibility);
      goog.events.listen(control, goog.events.EventType.CHANGE, function() {
        dossier.storeCheckedState_(key, /** @type {!Element} */ (control));
        onChange(/** @type {!Element} */(control), visibility);
      });
    }
  }

  /**
   * @param {!Element} control The element.
   * @param {dossier.Visibility} className The visibility class name to add or
   *     remove based on whether the control element is checked.
   */
  function onChange(control, className) {
    if (control.checked) {
      goog.dom.classlist.add(main, className);
    } else {
      goog.dom.classlist.remove(main, className);
    }
  }

  function insertPlaceholders(section) {
    if (!section) {
      return;
    }
    var details = goog.array.toArray(
        section.querySelectorAll('.wrap-details, h3'));
    var numPublic = 0;
    var numProtected = 0;
    var numPrivate = 0;

    do {
      goog.array.forEach(resetCount(), function(el) {
        if (goog.dom.classlist.contains(el, 'public') && numPublic) {
          goog.dom.insertSiblingAfter(
              createPlaceholder('public', numPublic), el);
          numPublic = 0;
        } else if (
            goog.dom.classlist.contains(el, 'protected') && numProtected) {
          goog.dom.insertSiblingAfter(
              createPlaceholder('protected', numProtected), el);
          numProtected = 0;
        } else if (
            goog.dom.classlist.contains(el, 'private') && numPrivate) {
          goog.dom.insertSiblingAfter(
              createPlaceholder('private', numPrivate), el);
          numPrivate = 0;
        }
      });
    } while (details.length);

    function resetCount() {
      var tmp = [];
      numPublic = numProtected = numPrivate = 0;
      for (var i = 0, n = details.length; i < n; ++i) {
        var el = details.shift();
        if (el.tagName === goog.dom.TagName.H3) {
          break;
        }
        tmp.push(el);
        if (goog.dom.classlist.contains(el, 'public')) {
          numPublic++;
          total.public++;
        } else if (goog.dom.classlist.contains(el, 'protected')) {
          numProtected++;
          total.protected++;
        } else if (goog.dom.classlist.contains(el, 'private')) {
          numPrivate++;
          total.private++;
        }
      }
      return tmp;
    }
  }

  function createPlaceholder(className, count) {
    return goog.soy.renderAsFragment(dossier.soy.hiddenVisibility, {
      visibility: className,
      count: count
    });
  }
};
