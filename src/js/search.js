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

const events = goog.require('goog.events');
const EventType = goog.require('goog.events.EventType');
const KeyCodes = goog.require('goog.events.KeyCodes');
const Strings = goog.require('goog.string');
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


/**
 * @param {!Array<string>} terms .
 * @param {!Map<string, string>} nameToHref .
 * @param {!Descriptor} descriptor .
 */
function addTypes(terms, nameToHref, descriptor) {
  let baseName = getLast(descriptor.qualifiedName.split(/\./));

  nameToHref.set(descriptor.qualifiedName, descriptor.href);
  terms.push(descriptor.qualifiedName);

  if (descriptor.types) {
    descriptor.types.forEach(type => addTypes(terms, nameToHref, type));
  }

  if (descriptor.statics) {
    descriptor.statics.forEach(function(name) {
      let href = descriptor.href + '#' + name;

      if (Strings.startsWith(name, baseName + '.')) {
        name = name.substring(baseName.length + 1);
      }

      name = descriptor.qualifiedName + '.' + name;
      nameToHref.set(name, href);
      terms.push(name);
    });
  }

  if (descriptor.members) {
    descriptor.members.forEach(function(name) {
      let href = descriptor.href + '#' + name;
      nameToHref.set(descriptor.qualifiedName + '#' + name, href);
      terms.push(descriptor.qualifiedName + '#' + name);
    });
  }
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
 * Initializes the auto-complete for the top navigation bar's search box.
 * @param {!TypeRegistry} typeInfo The types to link to from the current
 *     page.
 * @param {string} basePath The base path for the main page.
 * @return {!HTMLInputElement} The search box input element.
 */
exports.init = function(typeInfo, basePath) {
  let nameToHref = /** !Map<string, string> */new Map;
  let allTerms = /** !Array<string> */[];
  if (typeInfo.types) {
    typeInfo.types.forEach(
        descriptor => addTypes(allTerms, nameToHref, descriptor));
  }

  if (typeInfo.modules) {
    typeInfo.modules.forEach(module => addTypes(allTerms, nameToHref, module));
  }

  let searchForm = document.querySelector('header form');
  events.listen(searchForm, EventType.SUBMIT, function(e) {
    e.preventDefault();
    e.stopPropagation();
    navigatePage();
    return false;
  });

  let input =
      /** @type {!HTMLInputElement} */(searchForm.querySelector('input'));
  input.setAttribute(
      'title', 'Search (/ or ' + (goog.userAgent.MAC ? '⌘' : 'Ctrl+') + 'E)');

  let icon = searchForm.querySelector('.material-icons');
  if (icon) {
    events.listen(icon, EventType.CLICK, () => input.focus());
  }

  let ac = createAutoComplete(allTerms, input);
  events.listen(ac, AutoComplete.EventType.UPDATE, navigatePage);

  return input;

  function navigatePage() {
    let href = nameToHref.get(input.value);
    if (href) {
      window.location.href = basePath + href;
    }
  }
};
