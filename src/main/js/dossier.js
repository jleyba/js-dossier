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
goog.require('goog.style');
goog.require('goog.ui.ac');


/**
 * @typedef {{classes: !Array.<string>,
 *            enums: !Array.<string>,
 *            interfaces: !Array.<string>,
 *            namespaces: !Array.<string>}}
 * @private
 */
dossier.TypeInfo_;


/**
 * Initializes the dossier page.
 */
dossier.init = function() {
  var typeInfo = /** @type {dossier.TypeInfo_} */(goog.global['TYPES']);
  dossier.initNavList_(typeInfo);
  setTimeout(function() {
    dossier.initSearchBox_(typeInfo);
    setTimeout(dossier.polyFillDetailsElements_, 0);
  }, 0);
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
 * Builds a link path from the page hosting this script to another
 * {@code type}.
 * @param {string} prefix The link prefix.
 * @param {string} type Name of the type to link to.
 * @return {string} Path to use when linking to {@code type}.
 * @private
 */
dossier.getLinkPath_ = function(prefix, type) {
  return dossier.BASE_PATH_ + prefix + type.replace(/\./g, '_') + '.html';
};


/**
 * Initializes the auto-complete for the top navigation bar's search box.
 * @param {dossier.TypeInfo_} typeInfo The types to link to from the current
 *     page.
 * @private
 */
dossier.initSearchBox_ = function(typeInfo) {
  var allTerms = goog.array.concat(
      typeInfo['classes'], typeInfo['enums'],
      typeInfo['interfaces'], typeInfo['namespaces']);

  var searchForm = goog.dom.getElement('searchbox');
  var input = searchForm.getElementsByTagName('input')[0];
  goog.ui.ac.createSimpleAutoComplete(allTerms, input, false, true);
  goog.events.listen(searchForm, goog.events.EventType.SUBMIT, function(e) {
    e.preventDefault();
    e.stopPropagation();

    var value = input.value;
    var linkPrefix;
    if (goog.array.indexOf(typeInfo['classes'], value) >= 0) {
      linkPrefix = 'class_';
    } else if (goog.array.indexOf(typeInfo['enums'], value) >= 0) {
      linkPrefix = 'enum_';
    } else if (goog.array.indexOf(typeInfo['interfaces'], value) >= 0) {
      linkPrefix = 'interface_';
    } else if (goog.array.indexOf(typeInfo['namespaces'], value) >= 0) {
      linkPrefix = 'namespace_';
    }

    if (linkPrefix) {
      window.location.href = dossier.getLinkPath_(linkPrefix, value);
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
  var nav = goog.dom.getElement('left');
  if (!nav) {
    return;  // The current page does not have a left nav pane.
  }

  var sections = [];
  if (typeInfo['classes'].length) {
    sections.push(dossier.createNavList_(
        'Classes', 'class_', typeInfo['classes']));
  }
  if (typeInfo['enums'].length) {
    sections.push(dossier.createNavList_(
        'Enums', 'enum_', typeInfo['enums']));
  }
  if (typeInfo['interfaces'].length) {
    sections.push(dossier.createNavList_(
        'Interfaces', 'interface_', typeInfo['interfaces']));
  }
  if (typeInfo['namespaces'].length) {
    sections.push(dossier.createNavList_(
        'Namespaces', 'namespace_', typeInfo['namespaces']));
  }

  goog.dom.removeChildren(nav);
  goog.array.forEach(sections, function(section) {
    goog.dom.appendChild(nav, section);
  });
};


/**
 * Builds a list of type links for the left navigation pane.
 * @param {string} header The section header to display.
 * @param {string} linkPrefix A prefix to apply to each link.
 * @param {!Array.<string>} types The types to build links for.
 * @return {!Element} The root element for the newly created link section.
 * @private
 */
dossier.createNavList_ = function(header, linkPrefix, types) {
  var details = goog.dom.createDom(goog.dom.TagName.DETAILS, null,
      goog.dom.createDom(goog.dom.TagName.SUMMARY, null, header));
  var list = goog.dom.createElement(goog.dom.TagName.UL);
  goog.dom.appendChild(details, list);
  goog.array.forEach(types, function(type) {
    goog.dom.appendChild(list,
        goog.dom.createDom(goog.dom.TagName.LI, null,
            goog.dom.createDom(goog.dom.TagName.A, {
              'href': dossier.getLinkPath_(linkPrefix, type)
            }, type)));
  });
  return details;
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
