/**
 * @fileoverview Used to test type filtering.
 */

goog.provide('foo');
goog.provide('foo.bar');
goog.provide('foo.bar.baz');
goog.provide('foo.quot');


/**
 * This link to {@link foo.bar.Two} should not resolve since foo.bar is
 * filtered out of generated documentation.
 * @constructor
 */
foo.One = function() {};


/**
 * @return {!foo.FilteredClass} A new filtered class.
 */
foo.newFilteredClass = function() {
  return new foo.FilteredClass;
};


/**
 * This class should be excluded from generated documentation based on
 * on the e2e configuration.
 * @constructor
 */
foo.FilteredClass = function() {};


/**
 * @constructor
 */
foo.bar.Two = function() {};


/**
 * A function.
 */
foo.bar.someFunc = function() {};


/**
 * @constructor
 */
foo.bar.baz.Three = function() {};


/**
 * This class should be filtered out, but its alias below should be included
 * (with this comment).
 * @constructor
 */
foo.One_bar = function() {};


/**
 * @param {!foo.One_bar} other The other instance to compare.
 */
foo.One_bar.prototype.equals = function(other) {};


/**
 * Run, forest, run!
 */
foo.One_bar.prototype.run = function() {};


/**
 * @const
 */
foo.quot.OneBarAlias = foo.One_bar;
