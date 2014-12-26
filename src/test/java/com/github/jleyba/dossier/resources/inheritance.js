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
 */
sample.inheritance.OtherInterface = function() {};


/**
 * @interface
 * @extends {sample.inheritance.SecondInterface}
 * @extends {sample.inheritance.OtherInterface}
 */
sample.inheritance.LeafInterface = function() {};


/**
 * @constructor
 */
sample.inheritance.BaseClass = function() {};


/**
 * @constructor
 * @extends {sample.inheritance.BaseClass}
 * @implements {sample.inheritance.LeafInterface}
 */
sample.inheritance.SecondClass = function() {};


/**
 * @constructor
 * @extends {sample.inheritance.SecondClass}
 * @final
 */
sample.inheritance.FinalClass = function() {};


/**
 * @param {T} value The initial value.
 * @param {string=} opt_name Class name.
 * @constructor
 * @template T
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
 * @param {T} value The value to add.
 */
sample.inheritance.TemplateClass.prototype.add = function(value) {
  this.value = value;
};


/**
 * @constructor
 * @extends {sample.inheritance.TemplateClass<number>}
 */
sample.inheritance.NumberClass = function() {
  sample.inheritance.TemplateClass.call(this, 1234);
};


/**
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
