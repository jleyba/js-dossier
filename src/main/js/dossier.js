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
      !goog.dom.getElement('file-index')) {
    return;  // The current page is not the main index.
  }

  dossier.createFileNavList_('file-index', typeInfo['files']);
  dossier.createNamespaceNavList_('type-index', typeInfo['types']);
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

  var filesControl = goog.dom.getElement('sidenav-files-ctrl');
  var filesList = dossier.createFileNavList_(
      'sidenav-files', typeInfo['files']);

  // Compute sizes in terms of the root font-size, so things scale properly.
  var rootFontSize = goog.style.getFontSize(document.documentElement);
  var typesHeight =
      (goog.style.getSize(typesList).height / rootFontSize) + 'rem';
  var filesHeight =
      (goog.style.getSize(filesList).height / rootFontSize) + 'rem';

  // Initialize heights.
  typesControl.checked = loadCheckedState('dossier.typesList');
  filesControl.checked = loadCheckedState('dossier.filesList');
  goog.style.setHeight(typesList, typesControl.checked ? typesHeight : 0);
  goog.style.setHeight(filesList, filesControl.checked ? filesHeight : 0);

  goog.events.listen(typesControl, goog.events.EventType.CHANGE, function() {
    goog.style.setHeight(typesList, typesControl.checked ? typesHeight : 0);
    storeCheckedState('dossier.typesList', typesControl);
  });
  goog.events.listen(filesControl, goog.events.EventType.CHANGE, function() {
    goog.style.setHeight(filesList, filesControl.checked ? filesHeight : 0);
    storeCheckedState('dossier.filesList', filesControl);
  });

  function storeCheckedState(key, el) {
    if (window.localStorage) {
      if (el.checked) {
        window.localStorage.setItem(key, '1');
      } else {
        window.localStorage.removeItem(key);
      }
    }
  }

  function loadCheckedState(key) {
    return window.localStorage ? !!window.localStorage.getItem(key) : false;
  }
};


/**
 * Abstract representation of a documented namespace like object.
 * @param {string} name The namespace name.
 * @param {string=} opt_href The namespace href.
 * @constructor
 * @private
 */
dossier.Namespace_ = function(name, opt_href) {
  /** @type {string} */
  this.name = name;

  /** @type {string} */
  this.href = opt_href || '';

  /** @type {!Array.<!dossier.Namespace_>} */
  this.children = [];

  /** @type {boolean} */
  this.isInterface = false;
};


/**
 * @param {!Array.<dossier.Descriptor_>} descriptors The descriptors to
 *     build a tree structure from.
 * @returns {dossier.Namespace_} The root namespace.
 * @private
 */
dossier.Namespace_.fromRawTypeInfo = function(descriptors) {
  var root = new dossier.Namespace_('');
  goog.array.forEach(descriptors, function(descriptor) {
    var parts = descriptor['name'].split('.');
    var current = root;
    goog.array.forEach(parts, function(part, index) {
      if (!part) return;

      var next = goog.array.find(current.children, function(ns) {
        return ns.name === part;
      });

      if (!next) {
        next = new dossier.Namespace_(part);
        current.children.push(next);
      }
      current = next;

      if (index === parts.length - 1) {
        current.href = descriptor['href'];
        current.isInterface = descriptor['isInterface'];
      }
    });
  });

  collapse(root);
  return root;

  function collapse(namespace) {
    goog.array.forEach(namespace.children, collapse);
    if (!namespace.href && namespace.children.length === 1) {
      var name = namespace.name ? namespace.name + '.' : '';
      namespace.name = name + namespace.children[0].name;
      namespace.href = namespace.children[0].href;
      namespace.children = namespace.children[0].children;
      namespace.isInterface = namespace.children[0].isInterface;
    }
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
 * Builds a heirarchical list of namespace links.
 * @param {string} id ID for the section to attach the created list to.
 * @param {!Array.<dossier.Descriptor_>} namespaces The descriptors to build
 *     links for.
 * @return {!Element} The configured list display element.
 * @private
 */
dossier.createNamespaceNavList_ = function(id, namespaces) {
  var container = goog.dom.getElement(id);
  var noDataPlaceholder = /** @type {!Element} */ (
      container.querySelector('i'));

  if (!namespaces.length) {
    return noDataPlaceholder;
  }

  goog.dom.removeNode(noDataPlaceholder);
  var rootEl = /** @type {!HTMLUListElement} */(
      goog.dom.createElement(goog.dom.TagName.UL));
  goog.dom.appendChild(container, rootEl);

  var rootNamespace = dossier.Namespace_.fromRawTypeInfo(namespaces);
  processTreeNode(rootNamespace, rootEl);
  return rootEl;

  /**
   * @param {dossier.Namespace_} ns .
   * @param {!HTMLUListElement} parentEl .
   */
  function processTreeNode(ns, parentEl) {
    // If the name is empty, then we are at the root and
    // can add children directly to the parent element without adding
    // a new list.
    if (!ns.name) {
      goog.array.forEach(ns.children, function(child) {
        processTreeNode(child, parentEl);
      });
      return;
    }

    var nameNode = goog.dom.createTextNode(ns.name);
    if (ns.isInterface) {
      nameNode = goog.dom.createDom(goog.dom.TagName.I, null, nameNode);
    }

    var li;
    if (ns.href) {
      li = goog.dom.createDom(
          goog.dom.TagName.LI, 'link',
          goog.dom.createDom(goog.dom.TagName.A, {
            'href': dossier.BASE_PATH_ + ns.href
          }, nameNode));
    } else {
      li = goog.dom.createDom(goog.dom.TagName.LI, null, nameNode);
    }
    goog.dom.appendChild(parentEl, li);

    if (ns.children.length) {
      var list = /** @type {!HTMLUListElement} */(
          goog.dom.createElement(goog.dom.TagName.UL));
      goog.dom.appendChild(li, list);
      goog.array.forEach(ns.children, function(child) {
        processTreeNode(child, list);
      });
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

  goog.dom.removeNode(noDataPlaceholder);
  var rootEl = /** @type {!HTMLUListElement} */(
      goog.dom.createElement(goog.dom.TagName.UL));
  goog.dom.appendChild(container, rootEl);

  var rootFile = dossier.buildFileTree_(files);
  processTreeNode(rootFile, rootEl);
  return rootEl;

  /**
   * @param {dossier.File_} file .
   * @param {!HTMLUListElement} parentEl .
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

    // If the name is empty, then we are at the root and can add
    // children directly to the parent element without adding a
    // new list.
    if (!file.name) {
      goog.array.forEach(file.children, function(child) {
        processTreeNode(child, parentEl);
      });
      return;
    }

    var list = /** @type {!HTMLUListElement} */(
        goog.dom.createElement(goog.dom.TagName.UL));
    goog.dom.appendChild(
        parentEl,
        goog.dom.createDom(goog.dom.TagName.LI, null, file.name + '/', list));
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


/**
 * @private
 */
dossier.initVisibilityControls_ = function() {
  if (!document.getElementById('show-public')) return;

  var main = document.getElementsByTagName('main')[0];
  var total = {public: 0, protected: 0, private: 0};

  initControl('show-public', 'public');
  initControl('show-protected', 'protected');
  initControl('show-private', 'private');
  insertPlaceholders(document.getElementById('typedefs'));
  insertPlaceholders(document.getElementById('instance-methods'));
  insertPlaceholders(document.getElementById('instance-properties'));
  insertPlaceholders(document.getElementById('static-functions'));
  insertPlaceholders(document.getElementById('static-properties'));
  insertPlaceholders(document.getElementById('compiler-constants'));
  goog.style.setElementShown(
      document.getElementById('visibility-controls'),
      total.public || total.protected || total.private);

  function initControl(id, className) {
    var control = document.getElementById(id);
    if (control) {
      onChange(control, className);
      goog.events.listen(control, goog.events.EventType.CHANGE, function() {
        onChange(control, className);
      });
    }
  }

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
