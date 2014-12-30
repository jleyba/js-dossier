/**
 * @fileoverview A nested package.
 */

/**
 * A greeter.
 * @constructor
 */
function Greeter() {}


/**
 * Greeter the person.
 * @param {string} name The person to greet.
 * @return {string} A greeting.
 */
Greeter.prototype.greet = function(name) {
  return 'Hello, ' + name;
};


/**
 * Generates IDs.
 * @interface
 */
var IdGenerator = function() {};


/**
 * @return {string} A new ID.
 * @throws {Error} If an ID could not be generated.
 */
IdGenerator.prototype.getNext = function() {};


// Public API

exports.Greeter = Greeter;
exports.IdGenerator = IdGenerator;


/**
 * An {@link IdGenerator} that generates incrementing IDs.
 * @constructor
 * @implements {IdGenerator}
 */
exports.IncrementingIdGenerator = function() {
  /** @private {number} */
  this.next_ = 0;
};


/** @override */
exports.IncrementingIdGenerator.prototype.getNext = function() {
  return 'id' + (this.next_++);
};