/**
 * @fileoverview Main package module.
 */

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
