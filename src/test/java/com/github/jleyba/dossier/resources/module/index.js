/**
 * @fileoverview Main package module.
 */

/**
 * The main function.
 * @param {string} a First arg.
 * @param {boolean} b Second arg.
 * @param {?} c Last arg; any type will do.
 */
function main(a, b, c) {
}
module.exports = main;

/**
 * @param {number} a The first number.
 * @param {number} b The second number.
 * @return {number} The smaller of the two numbers.
 */
function min(a, b) {
  return Math.min(a, b);
}
exports.min = min;

/**
 * @param {number} a The first number.
 * @param {number} b The second number.
 * @return {number} The larger of the two numbers.
 */
exports.max = function(a, b) {
  return Math.max(a, b);
};



/** @type {function(new: GlobalCtor)} */
exports.GlobalCtor = require('GlobalCtor');


exports.Greeter = require('./nested').Greeter;
