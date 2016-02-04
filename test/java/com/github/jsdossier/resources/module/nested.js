/**
 * @fileoverview A nested package.
 */


/**
 * Basic description of a person.
 */
class Person {
  /**
   * @param {string} name the person's name.
   * @param {number} age the person's age.
   */
  constructor(name, age) {
    this.name = name;
    this.age = age;
  }
}


/**
 * A greeter.
 * @constructor
 */
function Greeter() {}


/**
 * Greet the person.
 * @param {!Person} person The person to greet.
 * @return {string} A greeting.
 */
Greeter.prototype.greet = function(person) {
  return 'Hello, ' + person.name;
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


/**
 * @interface
 */
var IncreasingIdGenerator = function() {};


/**
 * @return {string} A new ID that will always be greater than the last.
 */
IncreasingIdGenerator.prototype.getNext = function() {};


// Public API

exports.Greeter = Greeter;
exports.IdGenerator = IdGenerator;
exports.IncreasingIdGenerator = IncreasingIdGenerator;
exports.Person = Person;


/**
 * An {@link IdGenerator} that generates incrementing IDs.
 * @constructor
 * @implements {IdGenerator}
 * @implements {IncreasingIdGenerator}
 */
exports.IncrementingIdGenerator = function() {
  /** @private {number} */
  this.next_ = 0;
};


/** @override */
exports.IncrementingIdGenerator.prototype.getNext = function() {
  return 'id' + (this.next_++);
};
