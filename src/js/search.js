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

const AutoComplete = goog.require('goog.ui.ac.AutoComplete');
const Entry = goog.require('proto.dossier.Index.Entry');
const EventTarget = goog.require('goog.events.EventTarget');
const Heap = goog.require('dossier.Heap');
const Index = goog.require('proto.dossier.Index');
const InputHandler = goog.require('goog.ui.ac.InputHandler');
const Renderer = goog.require('goog.ui.ac.Renderer');
const events = goog.require('goog.events');
const googString = goog.require('goog.string');
const page = goog.require('dossier.page');
const unpackProtoToSanitizedUri = goog.require('soydata.unpackProtoToSanitizedUri');
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
  let qualifiedName = entry.getType().getQualifiedName() || entry.getType().getName();
  let baseName = getLast(qualifiedName.split(/\./));
  let typeHref = unpackProtoToSanitizedUri(entry.getType().getLink().getHref()).getContent();

  nameToHref.set(qualifiedName, typeHref);

  entry.getChildList().forEach(child => addTypes(nameToHref, child));

  entry.getStaticPropertyList().forEach(name => {
    let href = typeHref + '#' + name;
    if (!name.startsWith(qualifiedName + '.')) {
      if (name.startsWith(baseName + '.')) {
        name = name.substring(baseName.length + 1);
      }
      name = qualifiedName + '.' + name;
    }
    nameToHref.set(name, href);
  });

  entry.getPropertyList().forEach(name => {
    let href = typeHref + '#' + name;

    // Show as 'Type#property', not '.prototype.' because we don't know if
    // the property is actually defined on the prototype vs being set in the
    // constructor.
    nameToHref.set(qualifiedName + '#' + name, href);
  });
}


/**
 * Matcher used for auto-complete suggestions in the search box. This class
 * uses multiple passes for its suggestions:
 *
 * 1) A case insensitive substring match is performed on all known words.
 * 2) If the previous step matches more terms than requested, the list is
 *    further filtered using the Damerau–Levenshtein distance.
 *
 * @see https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance
 */
class AutoCompleteMatcher {
  /** @param {!Array<string>} terms */
  constructor(terms) {
    /** @private @const */
    this.terms_ = terms;
  }

  /**
   * Method used by the {@link AutoComplete} class to request matches on an
   * input token.
   * @param {string} token
   * @param {number} max
   * @param {function(string, !Array<string>)} callback
   */
  requestMatchingRows(token, max, callback) {
    callback(token, this.match(token, max));
  }

  /**
   * @param {string} token The token to match on this matcher's terms.
   * @param {number} max The maximum number of results to return.
   * @return {!Array<string>} The matched entries.
   */
  match(token, max) {
    if (!token) {
      return [];
    }

    let matcher = new RegExp(googString.regExpEscape(token), 'i');
    let heap = new Heap((a, b) => b.key - a.key);
    for (let term of this.terms_) {
      // 1) Require at least a substring match.
      if (!term.match(matcher)) {
        continue;
      }

      // 2) Select the if it is K-closest (K=max) using Damerau–Levenshtein
      // distance.
      //
      // NB: we could apply this second pass only if there are >max substring
      // matches, but this ensures suggestions are always consistently sorted
      // based on D-L distance.
      let distance = this.damerauLevenshteinDistance_(token, term);
      if (heap.size() < max) {
        heap.insert(distance, term);
      } else if (distance < heap.peekKey()) {
        heap.remove();
        heap.insert(distance, term);
      }
    }

    return heap.entries()
        .sort((a, b) => a.key - b.key)
        .map(e => e.value);
  }

  /**
   * @param {string} a
   * @param {string} b
   * @return {number}
   * @private
   */
  damerauLevenshteinDistance_(a, b) {
    let d = [];

    for (let i = 0; i <= a.length; i++) {
      d[i] = [i];
    }

    for (let j = 0; j <= b.length; j++) {
      d[0][j] = j;
    }

    for (let i = 1; i <= a.length; i++) {
      for (let j = 1; j <= b.length; j++) {
        let cost = a.charAt(i - 1) === b.charAt(j - 1) ? 0 : 1;
        d[i][j] = Math.min(
            d[i - 1][j] + 1,        // Deletion
            d[i][j - 1] + 1,        // Insertion
            d[i - 1][j - 1] + cost  // Substitution
        );

        if (i > 1 && j > 1
            && a.charAt(i - 1) == b.charAt(j - 2)
            && a.charAt(i - 2) == b.charAt(j - 1)) {
          d[i][j] = Math.min(
              d[i][j],
              d[i - 2][j - 2] + cost  // Transposition
          );
        }
      }
    }

    return d[a.length][b.length];
  }
}
exports.AutoCompleteMatcher = AutoCompleteMatcher;


/**
 * @param {!Array<string>} data The input data array.
 * @param {!Element} input The controlling input element.
 * @return {!AutoComplete} A new auto-complete object.
 */
function createAutoComplete(data, input) {
  const parent = input.ownerDocument.createElement('div');
  parent.classList.add('dossier-ac');
  parent.ownerDocument.body.appendChild(parent);

  let matcher = new AutoCompleteMatcher(data);
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
 * @private
 */
class SearchBox extends EventTarget {
  /**
   * @param {!Map<string, string>} nameToUri Map of search term to URI.
   * @param {!Element} formEl The form element containing the input element.
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
   * @param {!events.Event} e the event to respond to.
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
 * @param {!Index} typeInfo The type information to populate the search
 *     auto-complete from.
 * @return {!SearchBox} a new search box object.
 */
exports.createSearchBox = function(typeInfo) {
  let formEl = /** @type {!Element} */(document.querySelector('header form'));

  let nameToUri = /** !Map<string, string> */new Map;
  typeInfo.getTypeList().forEach(type => addTypes(nameToUri, type));
  typeInfo.getModuleList().forEach(module => addTypes(nameToUri, module));

  let input = formEl.querySelector('input');
  input.setAttribute(
      'title', 'Search (/ or ' + (userAgent.MAC ? '⌘' : 'Ctrl+') + 'E)');

  return new SearchBox(nameToUri, formEl);
};
