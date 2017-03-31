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

goog.module('dossier.xhr');

const HttpStatus = goog.require('goog.net.HttpStatus');
const XmlHttp = goog.require('goog.net.XmlHttp');


/**
 * @param {string} url The URL to request.
 * @return {!Promise<string>} A promise that will be resolved with the response
 *     text.
 */
exports.get = function(url) {
  return new Promise((resolve, reject) => {
    let request = XmlHttp();
    try {
      request.open('GET', url, true);
    } catch (ex) {
      reject(Error('Error opening XHR: ' + ex.message));
    }

    request.onerror = function() {
      reject(Error('Network error: ' + url));
    };

    request.onreadystatechange = function() {
      if (request.readyState === XmlHttp.ReadyState.COMPLETE) {
        if (HttpStatus.isSuccess(request.status)) {
          resolve(request.responseText);
        } else {
          reject(Error(`HTTP status=${request.status}`));
        }
      }
    };

    try {
      request.send();
    } catch (ex) {
      request.onreadystatechange = goog.nullFunction;
      reject(Error('Error sending XHR: ' + ex.message));
    }
  });
};
