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

goog.module('dossier.app');

const PageData = goog.require('dossier.PageData');
const page = goog.require('dossier.page');
const search = goog.require('dossier.search');
const {mainPageContent, pageTitle} = goog.require('dossier.soy');
const Promise = goog.require('goog.Promise');
const array = goog.require('goog.array');
const dom = goog.require('goog.dom');
const events = goog.require('goog.events');
const KeyCodes = goog.require('goog.events.KeyCodes');
const xhr = goog.require('goog.labs.net.xhr');
const soy = goog.require('goog.soy');
const style = goog.require('goog.style');
const userAgent = goog.require('goog.userAgent');

let app = null;


/** @record */
function PageState() {}
/** @type {string} */ PageState.prototype['version'] = '';
/** @type {number} */ PageState.prototype['id'] = 0;
/** @type {string} */ PageState.prototype['title'] = '';
/** @type {number} */ PageState.prototype['scroll'] = 0;


/**
 * Responds to click events on a property card.
 *
 * @param {!Event} e The click event.
 */
function onCardHeaderClick(e) {
  if (e.target.nodeName == 'A') {
    return;
  }

  let prop = e.currentTarget.parentNode;
  if (prop && prop.classList && prop.classList.contains('property')) {
    prop.classList.toggle('open');
  }
}


let /** !HTMLAnchorElement */linkResolver;


/**
 * @param {string} uri The URI to resolve.
 * @return {string} The resolved URI.
 */
function resolveUri(uri) {
  if (!linkResolver) {
    linkResolver =
        /** @type {!HTMLAnchorElement} */(document.createElement('A'));
    linkResolver.style.display = 'none';
    document.documentElement.appendChild(linkResolver);
  }
  linkResolver.href = uri;
  return linkResolver.href;
}


/**
 * @param {string} data
 * @return {!PageData}
 * @throws {!TypeError}
 */
function parsePageData(data) {
  let parsed = JSON.parse(data);
  if (!goog.isObject(parsed)) {
    throw TypeError('did parse to a JSON object');
  }
  return new PageData(parsed);
}


/** @final */
class DataService {
  /**
   * @param {!Map<string, string>} uriDataMap Maps URIs for HTML pages to their
   *     corresponding JSON data file.
   */
  constructor(uriDataMap) {
    /** @private @const {!Map<string, string>} */
    this.uriDataMap_ = uriDataMap;

    /** @private {Promise<string>} */
    this.pendingXhr_ = null;
  }

  /**
   * @param {string} uri The URI to load.
   * @return {!Promise<!PageData>} The loaded page data.
   */
  load(uri) {
    let dataUri = this.resolveDataUri_(uri);
    if (!dataUri) {
      return Promise.reject(Error('failed to resolve URL'));
    }

    if (this.pendingXhr_) {
      this.pendingXhr_.cancel();
    }

    return this.pendingXhr_ = Promise.resolve().then(() => {
      // Force the compiler to recognize this value as non-null.
      let jsonUrl = /** @type {string} */(dataUri);
      let json = window.sessionStorage.getItem(jsonUrl);
      if (json) {
        return parsePageData(json);
      }
      return xhr.get(jsonUrl).then(responseText => {
        let data = parsePageData(responseText);
        window.sessionStorage.setItem(jsonUrl, responseText);
        return data;
      });
    });
  }

  /**
   * @param {string} uri
   * @return {?string}
   * @private
   */
  resolveDataUri_(uri) {
    let path = uri;
    if (!path.startsWith('/')) {
      let currentPath = location.pathname;
      let index = currentPath.lastIndexOf('/');
      path = currentPath.slice(0, index + 1) + path;
    }
    let resolved = resolveUri(path);
    let index = resolved.indexOf('?');
    if (index != -1) {
      resolved = resolved.slice(0, index);
    } else if ((index = resolved.indexOf('#')) != -1) {
      resolved = resolved.slice(0, index);
    }
    resolved = this.uriDataMap_.get(resolved) || null;
    return resolved;
  }
}


/**
 * Maintains global application state.
 */
class Application {
  /**
   * @param {!DataService} dataService Service used to load JSON data files.
   * @param {!dossier.search.SearchBox} searchBox The search box widget to use.
   * @param {!dossier.nav.NavDrawer} navDrawer The nav drawer widget to use.
   * @param {!Element} mainEl The main content element.
   */
  constructor(dataService, searchBox, navDrawer, mainEl) {
    /** @private @const {!DataService} */
    this.dataService_ = dataService;

    /** @private {number} */
    this.stateId_ = 0;

    /**
     * @type {!dossier.search.SearchBox}
     * @const
     */
    this.searchBox = searchBox;

    /**
     * @type {!dossier.nav.NavDrawer}
     * @const
     */
    this.navDrawer = navDrawer;

    /** @type {!Element} */
    this.mainEl = mainEl;

    /** @private {!Element} */
    this.progressBar_ = document.createElement('progress');
    document.body.appendChild(this.progressBar_);
    this.hideProgressBar();
  }

  hideProgressBar() {
    this.progressBar_.style.display = 'none';
  }

  /**
   * Hides the navigation drawer if the page is too small to show it along with
   * the main content.
   */
  maybeHideNavDrawer() {
    if (!page.useGutterNav()) {
      this.navDrawer.hide();
    }
  }

  /**
   * Focuses the search box.
   *
   * @param {!goog.events.BrowserEvent=} opt_e The browser event this action is
   *     in response to. If provided, the event's propagation will be cancelled.
   */
  focusSearchBox(opt_e) {
    this.maybeHideNavDrawer();
    this.searchBox.focus();
    if (opt_e) {
      opt_e.preventDefault();
      opt_e.stopPropagation();
    }
  }

  /**
   * Ensures the correct row is highlighted when viewing a "srcfile" article.
   */
  updateSourceHighlight() {
    let srcTable = this.mainEl.querySelector('article.srcfile table');
    if (!srcTable) {
      return;
    }

    let current = srcTable.querySelector('tr.target');
    if (current) {
      current.classList.remove('target');
    }

    if (location.hash) {
      // We we change the current hash via history.pushState, the browser will
      // not always properly apply the :target pseudo class, so we manually
      // track this with the .target class.
      let target = srcTable.querySelector('tr' + location.hash);
      if (target) {
        target.classList.add('target');
        this.scrollTo(target);
      }
    }
  }

  /**
   * Ensures the main content's container is scrolled so the given target is
   * in view. This method compensates for the fixed position header at the top
   * of the page.
   *
   * @param {!Element} target the target to scroll to.
   */
  scrollTo(target) {
    let position = target.getBoundingClientRect();
    let offset = 64 - position.top;
    if (offset != 0) {
      this.mainEl.parentElement.scrollTop -= offset;
    }
  }

  onKeyDown(/** !goog.events.BrowserEvent */e) {
    if (this.searchBox.isActive) {
      return;
    }

    switch (e.keyCode) {
      case KeyCodes.N:
        this.navDrawer.toggleVisibility();
        break;

      case KeyCodes.E:
        if (userAgent.MAC ? e.metaKey : e.ctrlKey) {
          this.focusSearchBox(e);
        }
        break;

      case KeyCodes.SLASH:
        this.focusSearchBox(e);
        break;

      default:
        if (this.navDrawer.isOpen) {
          this.navDrawer.handleKeyEvent(e);
        }
        break;
    }
  }

  load(/** string */uri) {
    if (this.navDrawer.isOpen && !page.useGutterNav()) {
      this.navDrawer.hide();
    }

    if (!location.protocol.startsWith('http')) {
      location.href = uri;
      return;
    }

    let index = uri.indexOf('#');
    if (index == 0) {
      location.hash = uri;
      return;
    } else if (index > 0) {
      let targetPage = uri.substring(0, index);
      let currentPage = location.pathname.substring(1);
      if (targetPage === currentPage) {
        location.hash = uri.substring(index + 1);
        return;
      }
    }

    this.dataService_.load(uri)
        .then(
            data => this.onload_(uri, data),
            err => {
              if (err instanceof Promise.CancellationError) {
                return;
              }
              console.error(err);
              location.href = uri;
            });
  }

  /**
   * @param {string} uri
   * @param {!PageData} data
   * @private
   */
  onload_(uri, data) {
    this.replacePageState();

    let newTitle = soy.renderAsFragment(pageTitle, {data});
    let newMain = soy.renderAsFragment(mainPageContent, {data});

    this.updatePageContent(newTitle.textContent, newMain, 0, uri);
    this.hideProgressBar();
    this.resolveAmbiguity();

    // Scroll to the new page's target and re-save page state to capture that
    // scroll position.
    this.openCurrentTarget(true);
    this.replacePageState();
  }

  /**
   * @param {string} title the new title.
   * @param {(number|!Node)} htmlOrId the new content node, or an ID for HTML
   *     content saved in session storage.
   * @param {number} scroll the new content scroll position.
   * @param {string=} opt_path the page path to save in page history. If
   *     omitted, history will not be updated.
   */
  updatePageContent(title, htmlOrId, scroll, opt_path) {
    if (typeof htmlOrId === 'number') {
      this.stateId_ = htmlOrId;
      let html = window.sessionStorage.getItem(this.getDomKey_());
      this.mainEl.innerHTML = html;
    } else {
      this.mainEl.innerHTML = '';
      this.mainEl.appendChild(/** @type {!Node} */(htmlOrId));
    }
    document.title = title;
    this.mainEl.parentElement.scrollTop = scroll;

    if (opt_path) {
      this.savePageState(opt_path);
    }

    this.initProperties();
    this.updateSourceHighlight();
    this.navDrawer.updateCurrent();
  }

  /**
   * Opens the current target in the main content element.
   *
   * @param {boolean} opt_scroll Whether to also scroll the element into view.
   */
  openCurrentTarget(opt_scroll) {
    let targetId = location.hash ? location.hash.substring(1) : null;
    if (!targetId) {
      return;
    }

    // Search for the target directly instead of using :target pseudo element as
    // the browser may not have updated that index yet.
    let prefix = '.property.expandable';
    let selector = `${prefix}[id="${targetId}"],${prefix}[name="${targetId}"]`;

    let target = this.mainEl.querySelector(selector);
    if (!target) {
      return;
    }

    target.classList.add('open');
    if (opt_scroll) {
      this.scrollTo(target);
    }
  }

  /**
   * @param {!Event} e The event to respond to.
   * @private
   */
  captureLinkClick_(e) {
    let target = e.target;
    if (target.nodeName === 'CODE' && target.parentNode.nodeName === 'A') {
      target = target.parentNode;
    }

    if (target.nodeName !== 'A') {
      return;
    }

    let link = target;
    if (link.target) {
      return;
    }

    let href = link.getAttribute('href');
    if (!href || /^(https?:|#)/.test(href)) {
      return;  // Skip qualified URLs and anchors.
    }

    e.preventDefault();
    e.stopPropagation();
    this.load(href);
    return false;
  }

  /**
   * Initializes the click handlers that toggle whether a property box is open.
   */
  initProperties() {
    let headers =
        this.mainEl.querySelectorAll('.property.expandable > .header');
    array.forEach(headers, header => {
      header.addEventListener('click', onCardHeaderClick, false);
    });
  }

  /**
   * Types with case insensitve name collisions are rendered as multiple
   * articles in one page. This compensates for OSX, whose file system is
   * case preserving but insensitive, meaning only one file can be generated for
   * those names. A specific example of this are Closure's "goog.promise"
   * namespace and "goog.Promise" class.
   *
   * This method looks at the current content to determine which type the user
   * has actually requested by looking at the actual URL. If there is no case
   * sensitive match then the default view is left intact.
   */
  resolveAmbiguity() {
    let articles = this.mainEl.parentElement.querySelectorAll('main > article');
    if (articles.length < 2) {
      return;
    }

    const currentFile = array.peek(location.pathname.split(/\//));
    const article = array.find(articles, function(a) {
      return a.dataset && a.dataset.filename === currentFile;
    });

    if (article) {
      array.forEach(articles, function(a) {
        "use strict";
        if (a !== article) {
          dom.removeNode(a);
        }
      });
      style.setElementShown(article, true);
      document.title = article.dataset.name;
    }
  }

  /**
   * @param {boolean=} opt_increment Whether to increment page state.
   * @return {PageState} a description of the current page state.
   */
  getPageState(opt_increment) {
    if (opt_increment) {
      this.stateId_ += 1;
    }
    return {
      id: this.stateId_,
      title: document.title,
      scroll: this.mainEl.parentElement.scrollTop
    };
  }

  /**
   * Replaces the current page state.
   */
  replacePageState() {
    let state = this.getPageState();
    this.saveDom_();
    history.replaceState(
        state,
        document.title,
        (location.pathname + location.search + location.hash));
  }

  /**
   * Generates a new history entry for the page.
   *
   * @param {string=} opt_newLocation The desired location. If omitted, the
   *     current location will be preserved and the entry will simply capture
   *     the state of the DOM.
   */
  savePageState(opt_newLocation) {
    let state = this.getPageState(true);
    this.saveDom_();
    history.pushState(
        state,
        document.title,
        opt_newLocation
            || (location.pathname + location.search + location.hash));
  }

  /** @private */
  getDomKey_() {
    return this.stateId_;
  }

  /** @private */
  saveDom_() {
    window.sessionStorage.setItem(this.getDomKey_(), this.mainEl.innerHTML);
  }
}


/**
 * Runs the main application.
 *
 * @param {!dossier.Index} typeIndex The main type index.
 * @param {!dossier.search.SearchBox} searchBox The search box widget to use.
 * @param {!dossier.nav.NavDrawer} navDrawer The nav drawer widget to use.
 * @throws {Error} if the application has already been started.
 */
exports.run = function(typeIndex, searchBox, navDrawer) {
  if (app) {
    throw Error('application is already running');
  }

  let uriMap = /** !Map<string, string> */new Map;

  function processLink(/** !(dossier.Link|dossier.expression.TypeLink) */link) {
    if (link.href && !link.href.toString().startsWith('http') && link.json) {
      let href = resolveUri(page.getBasePath() + link.href);
      let json = resolveUri(page.getBasePath() + link.json);
      uriMap.set(href, json);
    }
  }

  function processEntry(/** !dossier.Index.Entry */entry) {
    if (entry.type && entry.type.link) {
      processLink(entry.type.link);
    }
    entry.child.forEach(processEntry);
  }

  typeIndex.module.forEach(processEntry);
  typeIndex.type.forEach(processEntry);
  typeIndex.page.forEach(processLink);
  typeIndex.sourceFile.forEach(processLink);

  let mainEl = /** @type {!Element} */(document.querySelector('main'));
  let dataService = new DataService(uriMap);

  app = new Application(dataService, searchBox, navDrawer, mainEl);
  events.listen(searchBox, 'focus', () => app.maybeHideNavDrawer());
  events.listen(
      searchBox, search.SelectionEvent.TYPE,
      e => app.load(e.uri));
  events.listen(document.documentElement, 'keydown', app.onKeyDown, false, app);
  events.listen(window, 'hashchange', onhashchange);

  app.resolveAmbiguity();
  app.initProperties();
  onhashchange();
  navDrawer.updateCurrent();

  if (location.protocol.startsWith('http') && window.sessionStorage) {
    let captureClick = (/** Event */e) => app.captureLinkClick_(e);
    navDrawer.element.addEventListener('click', captureClick, true);
    mainEl.addEventListener('click', captureClick, true);

    window.onpopstate = function(/** Event */e) {
      let state = e ? e.state : null;
      if (state) {
        app.updatePageContent(state['title'], state['id'], state['scroll']);
      }
    };
  }

  document.documentElement.classList.remove('loading');

  function onhashchange() {
    app.updateSourceHighlight();
    app.openCurrentTarget(true);
  }
};
