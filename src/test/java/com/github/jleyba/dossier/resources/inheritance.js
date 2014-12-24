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
 * @param {T} value .
 * @constructor
 * @template T
 */
sample.inheritance.TemplateClass = function(value) {
  this.value = value;
};


/** @return {T} . */
sample.inheritance.TemplateClass.prototype.getValue = function() {
  return this.value;
};


/**
 * @constructor
 * @extends {sample.inheritance.TemplateClass<number>}
 */
sample.inheritance.NumberClass = function() {
  sample.inheritance.TemplateClass.call(this, 1234);
};
