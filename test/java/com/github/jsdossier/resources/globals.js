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

  /** Color description. */
  COLOR: 'red',

  /**
   * Day of the week.
   * @deprecated Do not use this anymore.
   */
  DAY: 'Monday'
};


/**
 * This is a function defined on a global enumeration.
 */
GlobalEnum.doSomething = function() {
};


/**
 * Even though this enum is just a declared name and has is never assigned a
 * value, it should still be documented.
 * @enum {!(string|number|{age: ?number})}
 */
var UndefinedEnum;


/**
 * <table>
 * <caption>This is the table caption</caption>
 * <thead>
 * <tr><th>Fruit</th><th>Color</th></tr>
 * </thead>
 * <tbody>
 * <tr><td>Apple</td><td>Red</td></tr>
 * <tr><td>Apple</td><td>Green</td></tr>
 * <tr><td colspan="2">Orange</td></tr>
 * </tbody>
 * </table>
 * @constructor
 */
function SomeCtor() {
}