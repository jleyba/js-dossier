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
 * It is an extension of the internally defined {@link PublicClass}.
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


/**
 * This is an internal function exposed below.
 * @param {!PublicClass} a Reference to internal class as parameter.
 * @param {(string|PublicClass)} b Reference to internal class in union.
 */
function internal(a, b) {
}
exports.publicMethod = internal;


/**
 * A private function; should be excluded from generated documentation.
 * @private
 */
function secret() {
}
exports.getSecret = secret;
