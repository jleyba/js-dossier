/**
 * This is a global constructor.
 * @constructor
 */
var GlobalCtor = function() {

  /**
   * This is a private field and should not be documented.
   * @private {number}
   */
  this.privateX_ = 1;

  /**
   * This is a function defined inside the constructor.
   */
  this.instanceMethod = function() {};
};

/**
 * A static property.
 * @type {string}
 */
GlobalCtor.staticProp = 'hi';


/**
 * A static function.
 * @return Return statement; no type.
 */
GlobalCtor.staticFunc = function() {};


/**
 * This is function defined on the prototype.
 * @param {string} x description.
 * @param y description only.
 */
GlobalCtor.prototype.protoMethod = function(x, y) {
};


/**
 * This is a global private constructor.
 * @constructor
 * @private
 */
var GlobalPrivateCtor = function() {
};


/**
 * Lorem ipsum blah blah blah.
 * @deprecated Do not use this deprecated class. Use something else.
 * @constructor
 * @final
 * @struct
 */
var DeprecatedFoo = function() {
};