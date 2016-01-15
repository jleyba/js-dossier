/*
 Copyright 2013-2015 Jason Leyba

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

'use strict';

/**
 * A simple arithmetic calculator.
 */
class Calculator {
  /**
   * @param {number} radix the radix to use.
   */
  constructor(radix) {
    /** @private {number} */
    this.radix_ = radix;
  }

  /**
   * @return {!Calculator} A new base-2 calculator.
   */
  static base2() {
    return new Calculator(2);
  }

  /**
   * @return {!Calculator} A new decimal calculator.
   */
  static decimal() {
    return new Calculator(10);
  }

  /**
   * Returns an iterable object that will compute `n` values in the fibonacci
   * sequence.
   *
   * @param {number} n the number of places to compute.
   */
  static fibonacci(n) {
    return {
      [Symbol.iterator]: function *() {
        let times = 0;
        let prev = 0;
        let current = 1;
        return {
          next() {
            [prev, current] = [current, prev + current];
            return {value: current, done: (++times >= n)};
          }
        };
      }
    };
  }

  /**
   * @return {number} The radix for this calculator.
   */
  get radix() {
    return this.radix_;
  }

  /**
   * @param {number} r The new radix for this calculator.
   */
  set radix(r) {
    this.radix_ = r;
  }

  /**
   * @param {number} a the first number to add.
   * @param {number} b the second number to add.
   * @return {number} the result of adding the two numbers.
   */
  add(a, b) {
    return a + b;
  }
}
