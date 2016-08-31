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
 * @fileoverview Defines the control for the search widget.
 */

goog.module('dossier.search');

const TypeIndex = goog.require('dossier.TypeIndex');
const Entry = goog.require('dossier.TypeIndex.Entry');
const page = goog.require('dossier.page');
const events = goog.require('goog.events');
const EventTarget = goog.require('goog.events.EventTarget');
const EventType = goog.require('goog.events.EventType');
const ArrayMatcher = goog.require('goog.ui.ac.ArrayMatcher');
const AutoComplete = goog.require('goog.ui.ac.AutoComplete');
const InputHandler = goog.require('goog.ui.ac.InputHandler');
const Renderer = goog.require('goog.ui.ac.Renderer');
const userAgent = goog.require('goog.userAgent');

goog.forwardDeclare('goog.ui.ac.RenderOptions');



/**
 * @param {!Array<string>} arr an array of strings.
 * @return {string} the last string.
 */
function getLast(arr) {
  return arr[arr.length - 1];
}


function addTypes(/** !Map<string, string> */nameToHref, /** !Entry */entry) {
  let qualifiedName = entry.type.qualifiedName || entry.type.name;
  let baseName = getLast(qualifiedName.split(/\./));
  let typeHref = entry.type.link.href.getContent();

  nameToHref.set(qualifiedName, typeHref);

  entry.child.forEach(child => addTypes(nameToHref, child));

  entry.staticProperty.forEach(name => {
    let href = typeHref + '#' + name;
    if (!name.startsWith(qualifiedName + '.')) {
      if (name.startsWith(baseName + '.')) {
        name = name.substring(baseName.length + 1);
      }
      name = qualifiedName + '.' + name;
    }
    nameToHref.set(name, href);
  });

  entry.property.forEach(name => {
    let href = typeHref + '#' + name;

    // Show as 'Type#property', not '.prototype.' because we don't know if
    // the property is actually defined on the prototype vs being set in the
    // constructor.
    nameToHref.set(qualifiedName + '#' + name, href);
  });
}


/**
 * @param {!Array<?>} data The input data array.
 * @param {!Element} input The controlling input element.
 * @return {!AutoComplete} A new auto-complete object.
 */
function createAutoComplete(data, input) {
  const parent = input.ownerDocument.createElement('div');
  parent.classList.add('dossier-ac');
  parent.ownerDocument.body.appendChild(parent);

  let matcher = new ArrayMatcher(data, true);
  let renderer = new Renderer(parent);
  let inputHandler = new InputHandler(null, null, false);

  let ac = new AutoComplete(matcher, renderer, inputHandler);
  ac.setMaxMatches(10);

  inputHandler.attachAutoComplete(ac);
  inputHandler.attachInputs(input);

  renderer.setAutoPosition(false);
  renderer.setShowScrollbarsIfTooLarge(true);
  renderer.setUseStandardHighlighting(true);

  return ac;
}


/**
 * Describes a selection made in the search box.
 */
exports.SelectionEvent = class {
  /**
   * @param {string} uri The URI for the selected item.
   */
  constructor(uri) {
    /** @const */ this.type = this.constructor.TYPE;
    /** @const */ this.uri = uri;
  }

  /** @return {string} The name of this event type. */
  static get TYPE() {
    return 'select';
  }
};


/**
 * Widget for controlling the top navigation bar's search box.
 */
class SearchBox extends EventTarget {
  /**
   * @param {!Map<string, string>} nameToUri Map of search term to URI.
   * @param {!Element} formEl The form element containing the input element.
   * @private
   */
  constructor(nameToUri, formEl) {
    super();

    let inputEl = /** @type {!Element} */(formEl.querySelector('input'));

    /** @private {!Map<string, string>} */
    this.nameToUri_ = nameToUri;

    /** @private {!Element} */
    this.inputEl_ = inputEl;

    events.listen(formEl, 'submit', this.onUpdate_, false, this);
    events.listen(inputEl, 'focus', () => this.dispatchEvent('focus'));

    let icon = formEl.querySelector('.material-icons');
    if (icon) {
      events.listen(icon, 'click', () => this.inputEl_.focus());
    }

    /** @private {!AutoComplete} */
    this.ac_ = createAutoComplete(Array.from(nameToUri.keys()), inputEl);
    this.ac_.listen(AutoComplete.EventType.UPDATE, this.onUpdate_, false, this);
  }

  /** @override */
  disposeInternal() {
    this.ac_.dispose();
    super.disposeInternal();
  }

  /**
   * @param {!goog.events.Event} e the event to respond to.
   * @private
   */
  onUpdate_(e) {
    e.preventDefault();
    e.stopPropagation();
    let uri = this.nameToUri_.get(this.inputEl_.value);
    if (uri) {
      uri = page.getBasePath() + uri;
      this.dispatchEvent(new exports.SelectionEvent(uri));
    }
  }

  /**
   * Focuses the search box.
   */
  focus() {
    this.inputEl_.focus();
  }

  /**
   * @return {boolean} Whether the search box is currently focused.
   */
  get isActive() {
    return this.inputEl_.ownerDocument.activeElement === this.inputEl_;
  }
}
exports.SearchBox = SearchBox;


/**
 * @param {!TypeIndex} typeInfo The type information to populate the search
 *     auto-complete from.
 * @return {!SearchBox} a new search box object.
 */
exports.createSearchBox = function(typeInfo) {
  let formEl = /** @type {!Element} */(document.querySelector('header form'));

  let nameToUri = /** !Map<string, string> */new Map;
  typeInfo.type.forEach(type => addTypes(nameToUri, type));
  typeInfo.module.forEach(module => addTypes(nameToUri, module));

  let input = formEl.querySelector('input');
  input.setAttribute(
      'title', 'Search (/ or ' + (userAgent.MAC ? '⌘' : 'Ctrl+') + 'E)');

  return new SearchBox(nameToUri, formEl);
};
