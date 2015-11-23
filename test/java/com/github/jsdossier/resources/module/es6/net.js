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

/**
 * @fileoverview Module provides network related functionality.
 */


/**
 * Fetches a JSON resource from the given URL.
 *
 * @param {string} url The URL To fetch the resource from.
 * @return {!Promise<!Object>} A promise that will be fulfilled with the parsed
 *     JSON response.
 */
export function fetchJson(url) {
  return fetch(url)
      .then(response => response.text())
      .then(text => JSON.parse(text));
}


/**
 * Performs a HTTP GET request using a XMLHttpRequest.
 *
 * @param {string} path The resource to retrieve.
 * @param {!Map<string, string>=} opt_headers Any extra headers to include with
 *     the request.
 * @return {!Promise<string>} A promise that will be fulfilled with the server's
 *     response.
 */
function xhrGet(resource, opt_headers) {
  return new Promise(function(fulfill, reject) {
    let req = new XMLHttpRequest;
    req.onload = () => fulfill(req.responseText);
    req.onerror = () => reject(Error('request failed'));
    req.open('GET', resource);
    req.send();
  });
}

export { xhrGet as get }


/**
 * A simple HTTP client.
 */
export class HttpClient {
  constructor() {}

  /**
   * Retrieves a {@linkplain fetchJson() JSON resource}.
   *
   * @param {string} url The URL To fetch the resource from.
   * @return {!Promise<!Object>} A promise that will be fulfilled with the parsed
   *     JSON response.
   */
  fetchJson(url) {
    return fetchJson(url);
  }

  /**
   * Executes a basic {@linkplain xhrGet() HTTP get request}.
   *
   * @param {string} resource The resource to retrieve.
   * @return {!Promise<string>} A promise that will be fulfilled with the server's
   *     response.
   */
  get(resource) {
    return xhrGet(resource);
  }
}
