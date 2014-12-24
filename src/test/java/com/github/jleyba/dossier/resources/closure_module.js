/**
 * @fileoverview This comment should be the used for the module
 * summary in the generated documentation.
 */

goog.module('closure.module');


/**
 * This is an internal class and should not be documented.
 * @constructor
 */
var InternalClass = function() {
};


/**
 * This class is defined internally, but assigned to exports and thus
 * should be documented.
 * @constructor
 */
var PublicClass = function() {
};


/**
 * This is a method.
 */
PublicClass.prototype.aMethod = function() {
};


exports.PubClass = PublicClass;


/**
 * This class is defined on exports and should be documented.
 * @constructor
 * @extends {PublicClass}
 */
exports.Clazz = function() {
  PublicClass.call(this);
};
goog.inherits(exports.Clazz, PublicClass);


/** Lorem ipsum. */
exports.Clazz.prototype.method = function() {
};