/**
 * @fileoverview This is a sample JSON namespace definition used to test that
 * we properly document goog.provided namespaces that only contain functions.
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON
 * @see http://www.json.org
 * @package
 */

goog.provide('sample.json');



/**
 * @define {boolean} Whether to enable debug mode.
 */
sample.json.DEBUG = false;


/**
 * Converts a value to its JSON representation.
 * @param {*} value The value to convert.
 * @return {string} The value's JSON representation.
 * @see sample.json.parse
 * @see sample.json.DEBUG
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON
 */
sample.json.stringify = function(value) {
};


/**
 * Parses a JSON string.
 * @param {string} str The string to parse.
 * @return {*} The parsed value.
 * @see sample.json.stringify
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON
 */
sample.json.parse = function(str) {
};


/**
 * A private function; should be excluded from generated documentation.
 * @return {boolean} Whether the current environment has native JSON support.
 * @private
 */
sample.json.hasNativeSupport_ = function() {
  return typeof JSON === 'object';
};
