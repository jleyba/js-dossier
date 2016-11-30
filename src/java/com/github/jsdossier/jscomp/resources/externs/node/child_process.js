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
 * @fileoverview Definitions for node's child_process module. Depends on the events module.
 * @see http://nodejs.org/api/child_process.html
 * @see https://github.com/joyent/node/blob/master/lib/child_process.js
 */

var events = require('events');
var stream = require('stream');

/**
 * @const
 */
var child_process = {};

/**
 * @constructor
 * @param {...*} var_args
 * @extends events.EventEmitter
 */
child_process.ChildProcess = function(var_args) {}; // Private?

/**
 * @type {stream.ReadableStream}
 */
child_process.ChildProcess.prototype.stdin;

/**
 * @type {stream.WritableStream}
 */
child_process.ChildProcess.prototype.stdout;

/**
 * @type {stream.WritableStream}
 */
child_process.ChildProcess.prototype.stderr;

/**
 * @type {number}
 */
child_process.ChildProcess.prototype.pid;

/**
 * @param {string=} signal
 * @return {void}
 */
child_process.ChildProcess.prototype.kill;

/**
 * @param {Object.<string,*>} message
 * @param {*} sendHandle
 * @return {void}
 */
child_process.ChildProcess.prototype.send;

/**
 * @return {void}
 */
child_process.ChildProcess.prototype.disconnect;

/**
 * @return {void}
 */
child_process.ChildProcess.prototype.unref;

/**
 * @typedef {{cwd: (string|undefined),
 *            stdio: ?(Array|string|undefined),
 *            customFds: (Array|undefined),
 *            env: (Object<string,*>|undefined),
 *            detached: (boolean|undefined),
 *            uid: (number|undefined),
 *            gid: (number|undefined),
 *            encoding: (string|undefined),
 *            timeout: (number|undefined),
 *            maxBuffer: (number|undefined),
 *            killSignal: (string|undefined)}}
 */
child_process.Options;

/**
 * @param {string} command
 * @param {child_process.Options|function(Error, Buffer, Buffer)=} options
 * @param {function(Error, Buffer, Buffer)=} callback
 * @return {child_process.ChildProcess}
 */
child_process.exec;

/**
 * @param {string} file
 * @param {Array.<string>} args
 * @param {child_process.Options} options
 * @param {function(Error, Buffer, Buffer)} callback
 * @return {child_process.ChildProcess}
 */
child_process.execFile;

/**
 * @param {string} modulePath
 * @param {Array.<string>=} args
 * @param {child_process.Options=} options
 * @return {child_process.ChildProcess}
 */
child_process.fork;

/**
 * @param {string} command
 * @param {Array<string>} args
 * @param {child_process.Options=} options
 * @return {child_process.ChildProcess}
 */
child_process.spawn;

module.exports = child_process;
