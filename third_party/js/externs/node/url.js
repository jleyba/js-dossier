/*
 * Copyright 2012 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @fileoverview Definitions for node's url module.
 * @see http://nodejs.org/api/url.html
 * @see https://github.com/joyent/node/blob/master/lib/url.js
 */

/**
 * @const
 */
var url = {};

/**
 * @typedef {{href: (?string|undefined),
 *            protocol: (?string|undefined),
 *            host: (?string|undefined),
 *            auth: (?string|undefined),
 *            hostname: (?string|undefined),
 *            port: (?string|undefined),
 *            pathname: (?string|undefined),
 *            search: (?string|undefined),
 *            path: (?string|undefined),
 *            query: (?string|undefined),
 *            hash: (?string|undefined)}}
 */
var URL;

/**
 * @param {string} urlStr
 * @param {boolean=} parseQueryString
 * @param {boolean=} slashesDenoteHost
 * @return {URL}
 * @nosideeffects
 */
url.parse;

/**
 * @param {URL} urlObj
 * @return {string}
 * @nosideeffects
 */
url.format;

/**
 * @param {string} from
 * @param {string} to
 * @return {string}
 * @nosideeffects
 */
url.resolve;

module.exports = url;
