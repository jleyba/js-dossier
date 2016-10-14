/*
 Copyright 2013-2016 Jason Leyba

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

goog.module('dossier.Heap');


/** @typedef {{key: number, value: string}} */
var Node;


/**
 * A generic heap (min/max properties determined by the creator).
 * @final
 */
class Heap {
  /**
   * @param {function(!Node, !Node): number} comparatorFn The function to use
   *     when comparing two nodes in the heap. The function should return -1, 0,
   *     or 1 if the first node is less than, equal to, or greater than the
   *     second.
   */
  constructor(comparatorFn) {
    /** @private @const {!Array<!Node>} */
    this.nodes_ = [];

    /** @private @const */
    this.comparatorFn_ = comparatorFn;
  }

  /** @return {number} */
  size() {
    return this.nodes_.length;
  }

  /**
   * @return {!Array<!Node>} Returns a copy of the raw nodes in this heap.
   */
  entries() {
    return this.nodes_.map(node => Object.assign({}, node));
  }

  /** @return {!Array<number>} */
  keys() {
    return this.nodes_.map(node => node.key);
  }

  /** @return {!Array<string>} */
  values() {
    return this.nodes_.map(node => node.value);
  }

  /** @return {(string|undefined)} */
  peek() {
    return this.nodes_[0] && this.nodes_[0].value;
  }

  /** @return {(number|undefined)} */
  peekKey() {
    return this.nodes_[0] && this.nodes_[0].key;
  }

  /**
   * @return {(string|undefined)} The removed value.
   */
  remove() {
    let n = this.nodes_.length;
    if (!n) {
      return undefined;
    }

    let root = this.nodes_[0];
    if (n === 1) {
      delete this.nodes_[0];
      this.nodes_.length = 0;
    } else {
      this.nodes_[0] = this.nodes_.pop();
      this.moveDown_(0);
    }
    return root.value;
  }

  /**
   * @param {number} key
   * @param {string} value
   */
  insert(key, value) {
    this.nodes_.push({key, value});
    this.moveUp_(this.nodes_.length - 1);
  }

  /**
   * @param {number} index
   * @private
   */
  moveUp_(index) {
    const node = this.nodes_[index];
    while (index > 0) {
      let parentIndex = (index - 1) >> 1;
      let diff = this.comparatorFn_(this.nodes_[parentIndex], node);
      if (diff > 0) {
        this.nodes_[index] = this.nodes_[parentIndex];
        index = parentIndex;
      } else {
        break;
      }
    }
    this.nodes_[index] = node;
  }

  /**
   * @param {number} index
   * @private
   */
  moveDown_(index) {
    const node = this.nodes_[index];
    const size = this.nodes_.length;
    while (index < (size >> 1)) {
      let leftIndex = (index << 1) + 1;
      let rightIndex = (index << 1) + 2;

      let minIndex;
      if (rightIndex < size) {
        let diff =
            this.comparatorFn_(this.nodes_[leftIndex], this.nodes_[rightIndex]);
        minIndex = diff > 0 ? rightIndex : leftIndex;
      } else {
        minIndex = leftIndex;
      }

      let diff = this.comparatorFn_(this.nodes_[minIndex], node);
      if (diff > 0) {
        break;
      }

      this.nodes_[index] = this.nodes_[minIndex];
      index = minIndex;
    }
    this.nodes_[index] = node;
  }
}


// PUBLIC API


exports = Heap;
