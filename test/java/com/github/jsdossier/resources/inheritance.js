/**
 * @fileoverview Defines a sample namespace with various classes to we
 * properly document inheritance.
 */

goog.provide('sample.inheritance');



/**
 * @interface
 */
sample.inheritance.BaseInterface = function() {};


/**
 * @interface
 * @extends {sample.inheritance.BaseInterface}
 */
sample.inheritance.SecondInterface = function() {};


/**
 * @interface
 * @template T
 */
sample.inheritance.OtherInterface = function() {};


/**
 * @interface
 * @extends {sample.inheritance.SecondInterface}
 * @extends {sample.inheritance.OtherInterface<number>}
 */
sample.inheritance.LeafInterface = function() {};


/**
 * Runs stuff.
 * @interface
 */
sample.inheritance.Runnable = function() {};


/**
 * Run this instance.
 */
sample.inheritance.Runnable.prototype.run = function() {};


/**
 * @constructor
 */
sample.inheritance.BaseClass = function() {};


/**
 * The base method.
 */
sample.inheritance.BaseClass.prototype.run = function() {};


/**
 * @constructor
 * @extends {sample.inheritance.BaseClass}
 * @implements {sample.inheritance.LeafInterface}
 */
sample.inheritance.SecondClass = function() {};


/** @override */
sample.inheritance.SecondClass.prototype.run = function() {};


/**
 * @constructor
 * @extends {sample.inheritance.SecondClass}
 * @implements {sample.inheritance.Runnable}
 * @final
 */
sample.inheritance.FinalClass = function() {};


/** @override */
sample.inheritance.FinalClass.prototype.run = function() {};


/**
 * @param {T} value The initial value.
 * @param {string=} opt_name Class name.
 * @constructor
 * @template T
 * @struct
 */
sample.inheritance.TemplateClass = function(value, opt_name) {
  this.value = value;
  this.name = opt_name || '';
};


/** @return {T} . */
sample.inheritance.TemplateClass.prototype.getValue = function() {
  return this.value;
};


/**
 * Adds a value to this instance's {@link #getValue() value}.
 * @param {T} value The value to add.
 */
sample.inheritance.TemplateClass.prototype.add = function(value) {
  this.value += value;
};


/**
 * @constructor
 * @extends {sample.inheritance.TemplateClass<number>}
 * @struct
 */
sample.inheritance.NumberClass = function() {
  sample.inheritance.TemplateClass.call(this, 1234);
};


/**
 * Type definition for an addition result.
 * @typedef {number}
 */
sample.inheritance.NumberClass.Result;


/**
 * Similar to {@link #add}, but handles many values.
 * @param {number} n The first number to add.
 * @param {...number} var_args The remaining numbers to add.
 * @return {number} The result.
 */
sample.inheritance.NumberClass.prototype.addMany = function(n, var_args) {
  var result = this.getValue();
  for (var i = 0; i < arguments.length; i++) {
    result += arguments[i];
  }
  return result;
};


/**
 * A runnable error.
 * @param {string} msg The error message.
 * @constructor
 * @extends {Error}
 * @implements {sample.inheritance.Runnable}
 */
sample.inheritance.RunnableError = function(msg) {
  Error.call(this, msg);

  /** @override */
  this.name = 'RunnableError';

  /** @override */
  this.run = function() {};
};


/**
 * This class extends another using Closure's goog.defineClass. This comment
 * should be used for the class description, but be merged with the constructor
 * documentation below.
 * @extends {sample.inheritance.TemplateClass<string>}
 * @implements {sample.inheritance.Runnable}
 */
sample.inheritance.StringClass = goog.defineClass(sample.inheritance.TemplateClass, {
  /**
   * @param {string} value The initial value.
   */
  constructor: function(value) {
    sample.inheritance.TemplateClass.call(this, value, 'StringClass');
  },

  statics: {
    /**
     * Tests if a value is a string.
     * @param {*} value The value to test.
     * @return {boolean} Whether the value is a string.
     */
    isString: function(value) {
      return typeof value === 'string';
    }
  },

  /** @override */
  run: function() {
  },

  /**
   * @param {number} fromIndex The starting index.
   * @param {number=} opt_toIndex The ending index.
   * @return {string} A substring of this instance's current value.
   */
  substring: function(fromIndex, opt_toIndex) {
    return this.value.substring(fromIndex, opt_toIndex);
  }
});
