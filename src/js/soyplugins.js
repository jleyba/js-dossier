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

goog.module('dossier.soyplugins');
goog.module.declareLegacyNamespace();

const {ordainSanitizedHtml} = goog.require('soydata.VERY_UNSAFE');


const MDN_URL = `https://developer.mozilla.org/en-US/docs/Web/JavaScript/`;
const MDN_REFERENCE_URL = MDN_URL + 'Reference/';
const CLOSURE_URL =
    'https://github.com/google/closure-compiler/wiki/'
        + 'Special-types-in-the-Closure-Type-System#';

/** @const {!Map<string, string>} */
const TYPE_MAP = new Map([
  ['Arguments', MDN_REFERENCE_URL + 'Functions/arguments'],
  ['Array', MDN_REFERENCE_URL + 'Global_Objects/Array'],
  ['Boolean', MDN_REFERENCE_URL + 'Global_Objects/Boolean'],
  ['Date', MDN_REFERENCE_URL + 'Global_Objects/Date'],
  ['Error', MDN_REFERENCE_URL + 'Global_Objects/Error'],
  ['Function', MDN_REFERENCE_URL + 'Global_Objects/Function'],
  ['Generator', MDN_REFERENCE_URL + 'Global_Objects/Generaor'],
  ['IArrayLike', CLOSURE_URL + 'iarraylike'],
  ['IObject', CLOSURE_URL + 'iobject'],
  ['IThenable', CLOSURE_URL + 'ithenable'],
  ['Infinity', MDN_REFERENCE_URL + 'Global_Objects/Infinity'],
  ['Iterable', MDN_REFERENCE_URL + 'Global_Objects/Symbol/iterator'],
  ['Iterator', MDN_URL + 'Guide/The_Iterator_protocol'],
  ['Map', MDN_REFERENCE_URL + 'Global_Objects/Map'],
  ['Math', MDN_REFERENCE_URL + 'Global_Objects/Math'],
  ['NaN', MDN_REFERENCE_URL + 'Global_Objects/NaN'],
  ['Number', MDN_REFERENCE_URL + 'Global_Objects/Number'],
  ['Object', MDN_REFERENCE_URL + 'Global_Objects/Object'],
  ['Promise', MDN_REFERENCE_URL + 'Global_Objects/Promise'],
  ['RangeError', MDN_REFERENCE_URL + 'Global_Objects/RangeError'],
  ['ReferenceError', MDN_REFERENCE_URL + 'Global_Objects/ReferenceError'],
  ['RegExp', MDN_REFERENCE_URL + 'Global_Objects/RegExp'],
  ['Set', MDN_REFERENCE_URL + 'Global_Objects/Set'],
  ['Symbol', MDN_REFERENCE_URL + 'Global_Objects/Symbol'],
  ['String', MDN_REFERENCE_URL + 'Global_Objects/String'],
  ['SyntaxError', MDN_REFERENCE_URL + 'Global_Objects/SyntaxError'],
  ['TypeError', MDN_REFERENCE_URL + 'Global_Objects/TypeError'],
  ['URIError', MDN_REFERENCE_URL + 'Global_Objects/URIError'],
  ['arguments', MDN_REFERENCE_URL + 'Functions/arguments'],
  ['boolean', MDN_REFERENCE_URL + 'Global_Objects/Boolean'],
  ['null', MDN_REFERENCE_URL + 'Global_Objects/Null'],
  ['number', MDN_REFERENCE_URL + 'Global_Objects/Number'],
  ['string', MDN_REFERENCE_URL + 'Global_Objects/String'],
  ['undefined', MDN_REFERENCE_URL + 'Global_Objects/Undefined'],
  ['WeakMap', MDN_REFERENCE_URL + 'Global_Objects/WeakMap'],
  ['WeakSet', MDN_REFERENCE_URL + 'Global_Objects/WeakSet']
]);


/**
 * @param {!goog.soy.data.SanitizedContent} type The type whose URL to retrieve,
 *     wrapped in a sanitized content object.
 * @return {(string|undefined)} The URL or null.
 */
exports.getExternLink = function(type) {
  return TYPE_MAP.get(type.toString());
};


/**
 * @param {!goog.soy.data.SanitizedContent} arg The arg to wrap in sanitized content.
 * @return {!goog.soy.data.SanitizedHtml} The sanitized content.
 */
exports.sanitizeHtml = function(arg) {
  return ordainSanitizedHtml(arg.toString());
};
