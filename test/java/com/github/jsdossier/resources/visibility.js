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

/**
 * @fileoverview This file demonstrates various visibility settings. By
 * default everything in the file should have package visibility.
 *
 * @package
 */

goog.provide('vis');

/**
 * This class is explicitly public.
 * @public
 */
vis.Public = class {
  /** @param {number} x A number. */
  constructor(x) {}
};


/**
 * This class is explicitly private.
 * @private
 */
vis.Private = class {
  /** @param {number} x A number. */
  constructor(x) {}
};


/**
 * This class inherits its visibility from the file.
 */
 vis.InheritsVis = class {
  /** @param {number} x A number. */
  constructor(x) {}
};

/**
 * A package-private function.
 */
vis.one = function() {};

/**
 * A deprecated, package-private function.
 * @deprecated
 */
vis.deprecated = function() {};

/**
 * A public function.
 */
vis.public = function() {};

/**
 * A private function.
 * @private
 */
vis.private_ = function() {};
