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

goog.module('dossier.serviceworker.main');

const CACHE_NAME = 'dossier-cache-v1';


/**
 * Returns a properly typed reference to the global scope.
 * @return {!ServiceWorkerGlobalScope}
 */
function globalScope() {
  return /** @type {!ServiceWorkerGlobalScope} */(self);
}


self.addEventListener('install', function(/** !ExtendableEvent */event) {
  event.waitUntil(globalScope().skipWaiting());
});

self.addEventListener('activate', function(/** !ExtendableEvent */event) {
  event.waitUntil(
      self.caches.keys()
          .then(keys => {
            return Promise.all(keys.map(key => self.caches.delete(key)));
          })
          .then(() => globalScope().clients.claim()));
});

self.addEventListener('fetch', function(/** !FetchEvent */event) {
  event.respondWith(
      self.caches.match(event.request).then(cacheHit => {
        let fetchResponse = fetch(event.request).then(response => {
          if (!response || response.status !== 200) {
            return response;
          }

          let responseToCache = response.clone();
          self.caches.open(CACHE_NAME)
              .then(cache => cache.put(event.request, responseToCache))
              .catch(e => console.error('failed to cache response: ' + e));
          return response;
        });

        // If there was a cache hit, swallow any errors from our attempts to
        // update the cache.
        if (cacheHit) {
          fetchResponse = fetchResponse.catch(goog.nullFunction);
        }

        return cacheHit || fetchResponse;
      }));
});
