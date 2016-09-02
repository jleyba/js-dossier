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

goog.module('dossier.nav');

const TypeIndex = goog.require('dossier.TypeIndex');
const page = goog.require('dossier.page');
const soyNav = goog.require('dossier.soy.nav');
const Arrays = goog.require('goog.array');
const asserts = goog.require('goog.asserts');
const dom = goog.require('goog.dom');
const events = goog.require('goog.events');
const KeyCodes = goog.require('goog.events.KeyCodes');
const browser = goog.require('goog.labs.userAgent.browser');
const device = goog.require('goog.labs.userAgent.device');
const soy = goog.require('goog.soy');
const SanitizedHtml = goog.require('soydata.SanitizedHtml');


/**
 * @param {!Array<!dossier.TypeIndex.Entry>} entries
 */
function sortEntries(entries) {
  entries.sort((a, b) => {
    let nameA = a.type.qualifiedName || a.type.name;
    let nameB = b.type.qualifiedName || b.type.name;
    if (nameA === nameB) {
      return 0;
    }
    return nameA < nameB ? -1 : 1;
  });

  entries.forEach(entry => {
    if (entry.child.length) {
      sortEntries(entry.child);
    }
  });
}


/**
 * Builds a tree structure from the given list of descriptors where the
 * root node represents the global scope.
 *
 * @param {!Array<!dossier.TypeIndex.Entry>} entries The index entries to
 *     build a tree from.
 * @return {!Array<!dossier.TypeIndex.Entry>} The constructed, possibly
 *     disconnected trees.
 */
function buildTree(entries) {
  let roots = [];
  let stack = [];

  entries.forEach(entry => {
    if (!entry.type.qualifiedName) {
      entry.type.qualifiedName = entry.type.name;
    }

    while (stack.length) {
      let last = Arrays.peek(stack);
      let parentName = last.type.qualifiedName;
      let childName = entry.type.qualifiedName;
      let isChild = false;

      if (childName.startsWith(parentName + '.')) {
        entry.type.name = childName.slice(parentName.length + 1);
        isChild = true;
      } else if (childName.startsWith(parentName + '/')) {
        entry.type.name = childName.slice(parentName.length);
        isChild = true;
      }

      if (isChild) {
        last.child.push(entry);
        if (entry.isNamespace) {
          stack.push(entry);
        }
        return;
      }

      stack.pop();
    }

    if (!entry.type.qualifiedName) {
      entry.type.qualifiedName = entry.type.name;
    }
    roots.push(entry);
    if (entry.isNamespace) {
      stack.push(entry);
    }
  });

  sortEntries(roots);
  return roots;
}
exports.buildTree = buildTree;  // For testing.


/**
 * Creates the element used to mask page content when the navigation menu is
 * open.
 * @return {!Element} The new mask element.
 */
function createMask() {
  let mask = document.createElement('div');
  mask.classList.add('dossier-nav-mask');
  return mask;
}


/**
 * @param {!Element} el .
 * @param {boolean=} opt_value .
 * @private
 */
function updateControl(el, opt_value) {
  if (goog.isBoolean(opt_value)) {
    if (opt_value) {
      el.classList.add('open');
    } else {
      el.classList.remove('open');
    }
  } else {
    el.classList.toggle('open');
  }
}


/**
 * Widget for the side navigation drawer.
 */
class NavDrawer {
  /**
   * @param {!Element} navButton The button that opens the nav drawer.
   * @param {!Element} navEl The main element for the nav drawer.
   */
  constructor(navButton, navEl) {
    /** @private {!Element} */
    this.navButton_ = navButton;

    /** @private {!Element} */
    this.navEl_ = navEl;

    /**
     * Special element that receives focus whenever the nav drawer is opened.
     * This ensures tab stop will select the first item in the navigation tree
     * instead of going back to the input box (which would cause the drawer to
     * close).
     * @private {!Element}
     */
    this.focusSink_ = navEl.ownerDocument.createElement('div');
    this.focusSink_.tabIndex = -1;

    events.listen(this.focusSink_, 'blur', () => this.focusSink_.tabIndex = -1);
    events.listen(this.focusSink_, 'focus', () => this.focusSink_.tabIndex = 1);
    navEl.insertBefore(this.focusSink_, navEl.firstChild);
  }

  /** @return {!Element} The main element for this nav drawer. */
  get element() {
    return this.navEl_;
  }

  /** @return {boolean} Whether the nav drawer is currently open. */
  get isOpen() {
    return this.navEl_.classList.contains('visible');
  }

  /**
   * Updates the highlighted element in the nav drawer based on the current
   * page.
   */
  updateCurrent() {
    let currentPage = page.getBasePath() + page.getCurrentFile();
    let current = this.navEl_.querySelector('a.current');
    if (current) {
      let href = current.getAttribute('href');
      if (href === currentPage) {
        return;
      }
      current.classList.remove('current');
    }

    let link = this.navEl_.querySelector(`a[href="${currentPage}"]`);
    if (link) {
      link.classList.add('current');
    }
  }

  /**
   * Shows the nav drawer.
   */
  show() {
    this.navEl_.classList.add('visible');
    this.navButton_.disabled = !page.useGutterNav();
    setTimeout(() => {
      this.focusSink_.focus();
    }, 200);
  }

  /**
   * Hides the nav drawer.
   */
  hide() {
    this.navEl_.classList.remove('visible');
    this.navButton_.disabled = false;
  }

  /**
   * Toggle the visibility of the nav drawer.
   */
  toggleVisibility() {
    if (this.isOpen) {
      this.hide();
    } else {
      this.show();
    }
  }

  /**
   * Handles a keyboard event on the side nav pane.
   *
   * @param {!goog.events.BrowserEvent} e The browser event.
   */
  handleKeyEvent(e) {
    if (!this.isOpen || e.type !== 'keydown') {
      return;
    }

    if ((e.keyCode === KeyCodes.LEFT || e.keyCode === KeyCodes.RIGHT)
        && !e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey
        && e.target
        && e.target.classList
        && e.target.classList.contains('item')) {
      let parent = e.target.parentNode;
      if (!isToggle(parent) && parent) {
        parent = parent.parentNode;
      }

      if (isToggle(parent)) {
        updateControl(
            /** @type {!Element} */(parent),
            e.keyCode === KeyCodes.RIGHT);
      }
    }

    if (e.keyCode === KeyCodes.TAB && !e.shiftKey) {
      let allItems = this.navEl_.querySelectorAll('span.item, a');
      if (e.target === Arrays.peek(allItems)) {
        this.hide();
      }
    }

    function isToggle(n) {
      return n && n.classList && n.classList.contains('toggle');
    }
  }

  /**
   * Handles click events on the nav drawer.
   *
   * @param {!goog.events.BrowserEvent} e .
   * @private
   */
  onClick_(e) {
    let el = dom.getAncestor(e.target, function(node) {
      return node
          && node.classList
          && node.classList.contains('toggle');
    }, true, /*maxSteps=*/4);

    if (el && el.classList.contains('toggle')) {
      updateControl(/** @type {!Element} */(el));
    }
  }
}
exports.NavDrawer = NavDrawer;  // For better documentation.


const NAV_ITEM_HEIGHT = 45;


/**
 * Creates the side nav drawer widget.
 *
 * @param {!dossier.TypeIndex} typeInfo The type information to build the list
 *     from.
 * @param {string} currentFile The path to the file that loaded this script.
 * @param {string} basePath The path to the main index file.
 * @return {!NavDrawer} The created nav drawer widget.
 */
exports.createNavDrawer = function(typeInfo, currentFile, basePath) {
  const navButton = document.querySelector('button.dossier-menu');
  navButton.setAttribute('title', 'Show Navigation (n)');

  const navEl =
      /** @type {!Element} */(document.querySelector('nav.dossier-nav'));

  const mask = createMask();
  navEl.parentNode.appendChild(mask);

  const drawer = new NavDrawer(navButton, navEl);
  events.listen(mask, 'click', drawer.toggleVisibility, false, drawer);
  events.listen(mask, 'touchmove', e => e.preventDefault());
  events.listen(navButton, 'click', drawer.toggleVisibility, false, drawer);
  events.listen(navEl, 'click', drawer.onClick_, false, drawer);

  let modules = buildTree(typeInfo.module);
  let types = buildTree(typeInfo.type);
  let links = typeInfo.page;
  let fragment =
      soy.renderAsFragment(soyNav.drawerContents, {modules, types, links});
  navEl.appendChild(fragment);

  // Normalize all links to be relative to dossier's root. This ensures links
  // work consistently as we change the URL during content swaps.
  Arrays.forEach(navEl.querySelectorAll('a[href]'), function(link) {
    let href = link.getAttribute('href');
    if (href.startsWith('../')) {
      let index = href.lastIndexOf('../');
      href = href.substring(index + 3);
    }
    href = basePath + href;
    link.setAttribute('href', href);
  });

  let trees = navEl.querySelectorAll('.tree');
  for (let i = 0, n = trees.length; i < n; i++) {
    let tree = trees[i];
    let numItems = tree.querySelectorAll('li').length;
    tree.style.maxHeight = numItems * NAV_ITEM_HEIGHT + 'px';
  }

  let current = navEl.querySelector('.current');
  if (!current) {
    let titles = navEl.querySelectorAll('a.title[href]');
    current = Arrays.find(titles, function(el) {
      // Use the actual attribute, not the resolved href property.
      return el.getAttribute('href') === currentFile;
    });
  }

  if (current) {
    current.classList.add('current');
  }

  if (page.useGutterNav()) {
    drawer.show();
  } else {
    drawer.hide();
  }
  return drawer;
};
