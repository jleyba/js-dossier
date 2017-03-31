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

const Arrays = goog.require('goog.array');
const Index = goog.require('proto.dossier.Index');
const KeyCodes = goog.require('goog.events.KeyCodes');
const NamedType = goog.require('proto.dossier.expression.NamedType');
const dom = goog.require('goog.dom');
const events = goog.require('goog.events');
const page = goog.require('dossier.page');
const soy = goog.require('goog.soy');
const soyNav = goog.require('dossier.soy.nav');


function sortEntries(/** !Array<!Index.Entry> */entries) {
  entries.forEach(entry => {
    if (!entry.getType().getQualifiedName()) {
      entry.getType().setQualifiedName(entry.getType().getName());
    }

    if (entry.getChildList().length) {
      sortEntries(entry.getChildList());
    }
  });

  entries.sort((a, b) => {
    let nameA = a.getType().getQualifiedName();
    let nameB = b.getType().getQualifiedName();
    if (nameA === nameB) {
      return 0;
    }
    return nameA < nameB ? -1 : 1;
  });
}


/**
 * Builds a tree structure from the given list of descriptors where the
 * root node represents the global scope.
 *
 * @param {!Array<!Index.Entry>} entries The index entries to build a tree from.
 * @param {boolean=} opt_modules Whether the entries are modules.
 * @return {!Array<!Index.Entry>} The constructed, possibly disconnected trees.
 */
function buildTree(entries, opt_modules) {
  sortEntries(entries);

  const delimiter = opt_modules ? '/' : '.';
  let roots = [];
  let stack = [];
  let allEntries = new Set;

  /**
   * @type {!Array<{parent: (Index.Entry|undefined),
   *                fakeEntry: !Index.Entry,
   *                childIndex: number}>}
   */
  let fakeEntries = [];
  let entriesToResort = /** !Set<!Index.Entry> */new Set;

  function recordEntry(/** !Index.Entry */entry) {
    let last = Arrays.peek(stack);
    allEntries.add(entry.getType().getQualifiedName());

    if (entry.getIsNamespace()) {
      stack.push(entry);
    }
    let list = last ? last.getChildList() : roots;

    if (opt_modules && last) {
      entry.getType().setName(
          entry.getType().getQualifiedName()
              .slice(last.getType().getQualifiedName().length));
    }

    return list.push(entry) - 1;
  }

  function recordFakeEntry(/** string */name, /** string */qualifiedName) {
    let fakeEntry = new Index.Entry();
    fakeEntry.setIsNamespace(true);
    fakeEntry.setType(new NamedType());
    fakeEntry.getType().setName(name);
    fakeEntry.getType().setQualifiedName(qualifiedName);

    let parent = Arrays.peek(stack);
    let childIndex = recordEntry(fakeEntry);
    fakeEntries.push({parent, fakeEntry, childIndex});
  }

  function processEntry(/** !Index.Entry */entry, /** string */name) {
    for (let index = name.indexOf(delimiter, 1);
         index != -1;
         index = name.indexOf(delimiter)) {
      let parent = Arrays.peek(stack);
      let fakeName = name.slice(0, index);
      let fakeQualifiedName = parent
          ? `${parent.getType().getQualifiedName()}${opt_modules ? '' : '.'}${fakeName}`
          : fakeName;

      if (allEntries.has(fakeQualifiedName)) {
        break;
      }

      recordFakeEntry(fakeName, fakeQualifiedName);
      name = opt_modules ? name.slice(index) : name.slice(index + 1);
    }

    entry.getType().setName(name);
    recordEntry(entry);
  }

  entries.forEach(entry => {
    while (stack.length) {
      let last = Arrays.peek(stack);
      let parentName = last.getType().getQualifiedName();
      let childName = entry.getType().getQualifiedName();

      if (!opt_modules && childName.startsWith(parentName + '.')) {
        childName = childName.slice(parentName.length + 1);
        processEntry(entry, childName);
        return;

      } else if (opt_modules && childName.startsWith(parentName + '/')) {
        childName = childName.slice(parentName.length);
        processEntry(entry, childName);
        return;
      }

      stack.pop();
    }

    if (opt_modules) {
      recordEntry(entry);
    } else {
      processEntry(entry, entry.getType().getName());
    }
  });

  for (let i = fakeEntries.length - 1; i >= 0; i -= 1) {
    let {parent, fakeEntry, childIndex} = fakeEntries[i];

    if (fakeEntry.getChildList().length === 1) {
      let child = fakeEntry.getChildList()[0];
      child.getType().setName(
          `${fakeEntry.getType().getName()}${opt_modules ? '' : '.'}${child.getType().getName()}`);
      // If there is a parent, simply swap out the children. Otherwise, we can
      // merge the child's data into the current record to preserve all data.
      if (parent) {
        entriesToResort.add(parent);
        entriesToResort.delete(fakeEntry);
        parent.getChildList()[childIndex] = child;
      } else {
        entriesToResort.add(fakeEntry);
        entriesToResort.delete(child);
        Index.Entry.merge(fakeEntry, child);
      }
    }
  }

  if (fakeEntries.length) {
    sortEntries(roots);
  }

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
   * @param {!events.BrowserEvent} e The browser event.
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
   * @param {!events.BrowserEvent} e .
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
 * @param {!Index} typeInfo The type information to build the list from.
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

  let modules = buildTree(typeInfo.getModuleList(), true);
  let types = buildTree(typeInfo.getTypeList());
  let links = typeInfo.getPageList();
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
