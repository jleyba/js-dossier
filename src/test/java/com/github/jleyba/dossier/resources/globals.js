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
 * This is just an alias for an extern and should be documented as such.
 * @type {function(new: Date)}
 */
GlobalCtor.dateCtor = Date;


/**
 * This is a nested constructor and should be documented as a nested type.
 * @constructor
 */
GlobalCtor.Other = function() {};


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
 * Method description here.
 * @return {string} A string.
 * @deprecated This is a deprecated method.
 */
GlobalCtor.prototype.deprecatedMethod = function() {
};


/**
 * @deprecated Annotation is only detail on method.
 */
GlobalCtor.prototype.deprecatedMethod2 = function() {
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


/**
 * This is a global enumeration of strings.
 * @enum {string}
 */
var GlobalEnum = {
  FRUIT: 'apple',
  COLOR: 'red'
};


/**
 * This is a function defined on a global enumeration.
 */
GlobalEnum.doSomething = function() {
};


/** @enum {string} */
var EmptyEnum = {};


/** @enum {string} */
var UndefinedEnum;