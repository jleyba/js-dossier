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
 * @fileoverview Defines the centralized keyboard input handler for the
 * main dossier widgets.
 */

goog.module('dossier.keyhandler');

const nav = goog.require('dossier.nav');
const page = goog.require('dossier.page');
const events = goog.require('goog.events');
const EventType = goog.require('goog.events.EventType');
const KeyCodes = goog.require('goog.events.KeyCodes');
const userAgent = goog.require('goog.userAgent');


/**
 * @param {!nav.NavDrawer} navDrawer The nav drawer widget.
 * @param {!HTMLInputElement} inputEl The search input box element.
 */
exports.init = function(navDrawer, inputEl) {
  // This isn't *really* a key event, but the user could tab to the
  // search box while the nav drawer is open.
  events.listen(inputEl, EventType.FOCUS, function() {
    if (!page.useGutterNav()) {
      navDrawer.hide();
    }
  });

  events.listen(document.documentElement, EventType.KEYDOWN, function(e) {
    switch (e.keyCode) {
      case KeyCodes.E:
        if (document.activeElement !== inputEl
            && (userAgent.MAC ? e.metaKey : e.ctrlKey)) {
          navDrawer.hide();
          inputEl.focus();
          e.preventDefault();
          e.stopPropagation();
          return false;
        }
        break;

      case KeyCodes.N:
        if (document.activeElement !== inputEl) {
          navDrawer.toggleVisibility();
        }
        break;

      case KeyCodes.SLASH:
        if (document.activeElement !== inputEl) {
          navDrawer.hide();
          inputEl.focus();
          e.preventDefault();
          e.stopPropagation();
          return false;
        }
        break;

      case KeyCodes.ESC:
        if (document.activeElement === inputEl) {
          inputEl.blur();
        }
        navDrawer.hide();
        break;

      default:
        if (navDrawer.isOpen) {
          navDrawer.handleKeyEvent(/** @type {!goog.events.BrowserEvent} */(e));
        }
        break;
    }
  });
};
