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

goog.module('dossier.page');

const array = goog.require('goog.array');


/** @return {string} */
function computeBasePath() {
  const scripts = /** @type {!NodeList<!HTMLElement>} */(
      document.querySelectorAll('script'));
  /** @type {string} */let dirPath = '';
  const thisFile = 'dossier.js';
  array.find(scripts, function(script) {
    var src = script.getAttribute('src');
    if (!src) {
      return false;
    }
    var len = src.length;
    if (src.slice(len - thisFile.length) === thisFile) {
      dirPath = src.slice(0, len - thisFile.length);
      return true;
    }
    return false;
  });

  let trimN = dirPath.split(/\.\.\//).length;
  let currentPath = window.location.pathname.split(/\//);
  currentPath.splice(currentPath.length - trimN, trimN);

  let basePath = currentPath.join('/');
  if (!basePath.endsWith('/')) {
    basePath += '/';
  }
  return basePath;
}


/**
 * The base path on the server or the directory containing the main dossier
 * script. It is assumed that all resources are served from this directory or
 * one of its descendants.
 *
 * @const {string}
 */
const BASE_PATH = computeBasePath();


// PUBLIC API


/**
 * @return {string} the path for the root directory on the server under which
 *     all resources are served.
 */
exports.getBasePath = function() {
  return BASE_PATH;
};


/**
 * Returns whether the page is large enough to display the side nav in the
 * gutter next to the main content
 *
 * @return {boolean} Whether the page is large enough to use the gutter nav.
 */
exports.useGutterNav = function() {
  return window.innerWidth >= 1112;  // Keep in sync with nav.less
};


/**
 * @return {string} path to the current file, relative to the base path.
 */
exports.getCurrentFile = function() {
  return location.pathname.slice(BASE_PATH.length);
};
