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
const assert = goog.require('goog.asserts').assert;
const dom = goog.require('goog.dom');
const events = goog.require('goog.events');
const KeyCodes = goog.require('goog.events.KeyCodes');
const Strings = goog.require('goog.string');


/**
 * A generic tree node with an associated key and value.
 * @final
 */
var TreeNode = goog.defineClass(null, {
  /**
   * @param {string} key .
   * @param {?Descriptor} value .
   */
  constructor: function(key, value) {
    /** @private {string} */
    this.key_ = key;

    /** @private {?Descriptor} */
    this.value_ = value;

    /** @private {?TreeNode} */
    this.parent_ = null;

    /** @private {?Array<!TreeNode>} */
    this.children_ = null;
  },

  /** @return {string} The node key. */
  getKey: function() {
    return this.key_;
  },

  /** @param {string} key The new key. */
  setKey: function(key) {
    this.key_ = key;
  },

  /** @return {?Descriptor} The node value. */
  getValue: function() {
    return this.value_;
  },

  /** @param {?Descriptor} v The new value. */
  setValue: function(v) {
    this.value_ = v;
  },

  /** @return {TreeNode} This node's parent. */
  getParent: function() {
    return this.parent_;
  },

  /** @param {!TreeNode} node The new child to add. */
  addChild: function(node) {
    assert(!node.parent_);
    if (!this.children_) {
      this.children_ = [];
    }
    node.parent_ = this;
    this.children_.push(node);
  },

  /** @return {number} The number of children attached to this node. */
  getChildCount: function() {
    return this.children_ ? this.children_.length : 0;
  },

  /** @return {Array<!TreeNode>} This node's children, if any. */
  getChildren: function() {
    return this.children_;
  },

  /**
   * @param {number} index The index to retrieve.
   * @return {TreeNode} The child at the given index, if any.
   */
  getChildAt: function(index) {
    return this.children_ ? this.children_[index] : null;
  },

  /** @param {!TreeNode} node The node to remove. */
  removeChild: function(node) {
    assert(node.parent_ === this);
    node.parent_ = null;
    goog.array.remove(this.children_, node);
  },

  /** @return {Array<!TreeNode>} The removed children. */
  removeChildren: function() {
    var ret = this.children_;
    if (this.children_) {
      this.children_.forEach(function(child) {
        child.parent_ = null;
      });
      this.children_ = null;
    }
    return ret;
  },

  /**
   * @param {string} key The key to search for.
   * @return {TreeNode} The node with the matching key.
   */
  findChild: function(key) {
    if (!this.children_) {
      return null;
    }
    return goog.array.find(this.children_, function(child) {
      return child.key_ === key;
    });
  }
});


/**
 * Collapses all children nodes such that every node has a value and only
 * namespaces have children.
 *
 * @param {!TreeNode} node The node to collapse.
 */
function collapseNodes(node) {
  var children = node.getChildren();
  if (!children) {
    assert(node.getValue());
    return;
  }

  children.forEach(collapseNodes);
  children.filter(function(child) {
    return child.getValue() === null && child.getChildCount() == 1;
  }).forEach(function(child) {
    node.removeChild(child);
    var grandChildren = child.removeChildren();
    if (grandChildren) {
      grandChildren.forEach(function(grandchild) {
        grandchild.setKey(child.getKey() + '.' + grandchild.getKey());
        node.addChild(grandchild);
      });
    }
  });

  children.filter(function(child) {
    return child.getValue()
        && !child.getValue().namespace
        && !child.getValue().types
        && child.getChildCount();
  }).forEach(function(child) {
    var grandChildren = child.removeChildren();
    if (grandChildren) {
      grandChildren.forEach(function(grandchild) {
        grandchild.setKey(child.getKey() + '.' + grandchild.getKey());
        node.addChild(grandchild);
      });
    }
  });
}


/**
 * Sorts the tree so each node's children are sorted by key.
 */
function sortTree(root) {
  var children = root.getChildren();
  if (children) {
    children.sort(function(a, b) {
      if (a.getKey() < b.getKey()) return -1;
      if (a.getKey() > b.getKey()) return 1;
      return 0;
    });
    children.forEach(sortTree);
  }
}

/**
 * Builds a tree structure from the given list of descriptors where the
 * root node represents the global scope.
 *
 * @param {!Array<!Descriptor>} descriptors The descriptors.
 * @param {boolean} isModule Whether the descriptors describe CommonJS modules.
 * @param {TreeNode=} opt_root The root node. If not provided, one will be
 *     created.
 * @return {!TreeNode} The root of the tree node.
 */
exports.buildTree = function(descriptors, isModule, opt_root) {
  let root = opt_root || new TreeNode('', null);
  descriptors.forEach(function(descriptor) {
    if (isModule) {
      let moduleRoot = new TreeNode(descriptor.name, descriptor);
      root.addChild(moduleRoot);
      if (descriptor.types) {
        let ret = exports.buildTree(descriptor.types, false, moduleRoot);
        assert(ret === moduleRoot);
      }
      return;
    }

    var currentNode = root;
    descriptor.qualifiedName.split(/\./).forEach(function(part) {
      if (currentNode === root && currentNode.getKey() === part) {
        return;
      }
      var found = currentNode.findChild(part);
      if (!found) {
        found = new TreeNode(part, null);
        currentNode.addChild(found);
      }
      currentNode = found;
    });
    assert(currentNode.getValue() === null);
    currentNode.setValue(descriptor);
  });

  collapseNodes(root);
  sortTree(root);

  return root;
};


/**
 * @param {!Descriptor} descriptor .
 * @return {string} .
 */
function getId(descriptor) {
  var prefix = descriptor['module'] ? '.nav.module:' : '.nav:';
  return prefix + descriptor.name;
}


/**
 * @param {!TreeNode} node The node.
 * @param {string} basePath Base path to prepend to any links.
 * @param {string} currentPath Path for the file running this script.
 * @param {string} idPrefix Prefix to prepend to node key for the DOM ID.
 * @return {!Element} The list item.
 */
function buildListItem(node, basePath, currentPath, idPrefix) {
  assert(node.getValue() || node.getChildCount());

  var li = document.createElement('li');

  var a = document.createElement('a');
  a.classList.add('item');
  a.textContent = node.getKey();
  a.tabIndex = 2;
  if (node.getValue()) {
    a.href = basePath + node.getValue().href;
    if (node.getValue().interface) {
      a.classList.add('interface');
    }
  }

  if (node.getValue() && node.getValue().href === currentPath) {
    a.classList.add('current');
  }

  var children = node.getChildren();
  if (children) {
    let i = document.createElement('i');
    i.classList.add('material-icons');
    i.textContent = 'expand_more';

    let div = document.createElement('div');
    div.dataset.id = idPrefix + node.getKey();
    div.classList.add('toggle');
    div.appendChild(a);
    div.appendChild(i);

    li.appendChild(div);

    var ul = document.createElement('ul');
    ul.classList.add('tree');
    li.appendChild(ul);

    children.forEach(function(child) {
      ul.appendChild(
          buildListItem(
              child, basePath, currentPath, idPrefix + node.getKey() + '.'));
    });
  } else {
    li.appendChild(a);
  }

  return li;
}


const TYPE_ID_PREFIX = '.nav:';
const MODULE_TYPE_ID_PREFIX = '.nav-module:';


/**
 * Returns the ID prefix used for data types.
 *
 * @param {boolean} modules whether to return the ID for module types.
 * @return {string} the ID prefix.
 */
function getIdPrefix(modules) {
  return modules ? MODULE_TYPE_ID_PREFIX : TYPE_ID_PREFIX;
}


/**
 * Builds a nested list for the given types.
 * @param {!Array<!Descriptor>} descriptors The descriptors.
 * @param {string} basePath Base path to prepend to any links.
 * @param {string} currentPath Path for the file running this script.
 * @param {boolean} isModule Whether the descriptors describe CommonJS modules.
 * @return {!Element} The root element for the tree.
 */
function buildList(descriptors, basePath, currentPath, isModule) {
  if (basePath && !Strings.endsWith(basePath, '/')) {
    basePath += '/';
  }
  var root = exports.buildTree(descriptors, isModule);
  var rootEl = document.createElement('ul');
  rootEl.classList.add('tree');
  if (root.getChildren()) {
    root.getChildren().forEach(function(child) {
      rootEl.appendChild(buildListItem(child, basePath, currentPath,
          isModule ? MODULE_TYPE_ID_PREFIX : TYPE_ID_PREFIX));
    });
  }
  return rootEl;
}
exports.buildList = buildList;  // For testing.


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
    navEl.insertBefore(this.focusSink_, navEl.firstChild);
  }

  /** @return {boolean} Whether the nav drawer is currently open. */
  get isOpen() {
    return this.navEl_.classList.contains('visible');
  }

  /**
   * Hides the nav drawer.
   */
  hide() {
    this.navEl_.classList.remove('visible');
    this.updateTabIndices_();
  }

  /**
   * Toggle the visibility of the nav drawer.
   */
  toggleVisibility() {
    this.navEl_.classList.toggle('visible');
    this.updateTabIndices_();
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
        this.updateControl_(
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

  /** @private */
  updateTabIndices_() {
    this.navButton_.disabled = this.navEl_.classList.contains('visible');

    if (!this.isOpen) {
      let allControls = this.navEl_.querySelectorAll('span.item, a');
      Arrays.forEach(allControls, control => control.tabIndex = -1);
      return;
    }

    // Make the top level controls tab-able.
    let topControls = this.navEl_.querySelectorAll('a.title, span.item');
    Arrays.forEach(topControls, control => control.tabIndex = 1);

    // Get our trees.
    let trees = this.navEl_.querySelectorAll('section > .toggle.open ~ .tree');
    Arrays.forEach(trees, tree => this.enableTree_(tree, true));

    this.focusSink_.focus();
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
      this.updateControl_(/** @type {!Element} */(el));
    }
  }

  /**
   * @param {!Element} el .
   * @param {boolean=} opt_value .
   * @param {boolean=} opt_skipPersist .
   * @private
   */
  updateControl_(el, opt_value, opt_skipPersist) {
    if (goog.isBoolean(opt_value)) {
      if (opt_value) {
        el.classList.add('open');
      } else {
        el.classList.remove('open');
      }
    } else {
      el.classList.toggle('open');
    }

    if (!opt_skipPersist) {
      this.updateStorage_(el);
    }

    let tree = el.nextElementSibling;
    if (tree && tree.classList.contains('tree')) {
      if (el.classList.contains('open')) {
        tree.style.maxHeight = tree.dataset.maxHeight + 'px';
        this.enableTree_(tree, this.isOpen);
      } else {
        tree.style.maxHeight = '0';
        this.enableTree_(tree, false);
      }
    }
  }

  /**
   * @param {!Element} tree .
   * @param {boolean} enable .
   * @private
   */
  enableTree_(tree, enable) {
    if (enable) {
      Arrays.forEach(tree.childNodes, function(child) {
        let firstChild = child.firstChild;
        if (!firstChild || !firstChild.classList) {
          return;
        }

        if (firstChild.classList.contains('item')) {
          firstChild.tabIndex = 1;
        } else if (firstChild.classList.contains('toggle')) {
          let item = firstChild.querySelector('.item');
          if (item) {
            item.tabIndex = 1;
          }

          if (firstChild.classList.contains('open')
              && firstChild.nextElementSibling
              && firstChild.nextElementSibling.classList.contains('tree')) {
            this.enableTree_(firstChild.nextElementSibling, true);
          }
        }
      }, this);

    } else {
      Arrays.forEach(
          tree.querySelectorAll('.item'), item => item.tabIndex = -1);
    }
  }

  /**
   * @param {!Element} el .
   * @private
   */
  updateStorage_(el) {
    if (window.localStorage && el.dataset.id) {
      window.localStorage.setItem(
          el.dataset.id,
          el.classList.contains('open') ? 'open' : 'closed');
    }
  }
}
exports.NavDrawer = NavDrawer;  // For better documentation.


const NAV_ITEM_HEIGHT = 48;


/**
 * Creates the side nav drawer widget.
 *
 * @param {!TypeRegistry} typeInfo The type information to build the list from.
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
  events.listen(navButton, 'click', drawer.toggleVisibility, false, drawer);
  events.listen(navEl, 'click', drawer.onClick_, false, drawer);

  const typeSection = navEl.querySelector('.types');
  if (typeSection && typeInfo.types) {
    buildSectionList(typeSection, typeInfo.types, false);
  }

  const moduleSection = navEl.querySelector('.modules');
  if (moduleSection && typeInfo.modules) {
    buildSectionList(moduleSection, typeInfo.modules, true);
  }

  let trees = navEl.querySelectorAll('.tree');
  for (let i = 0, n = trees.length; i < n; i++) {
    let tree = trees[i];
    let numItems = tree.querySelectorAll('li').length;
    tree.dataset.maxHeight = numItems * NAV_ITEM_HEIGHT;
    tree.style.maxHeight = 0;
  }

  if (window.localStorage) {
    let toggles = navEl.querySelectorAll('.toggle[data-id]');
    Arrays.forEach(toggles, function(el) {
      var state = window.localStorage.getItem(el.dataset.id);
      drawer.updateControl_(el, state === 'open');
    });
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
    revealElement(current);
  }

  drawer.hide();  // Start hidden.
  return drawer;

  /**
   * @param {!Element} section .
   * @param {!Array<!Descriptor>} descriptors .
   * @param {boolean} isModule .
   */
  function buildSectionList(section, descriptors, isModule) {
    let list = buildList(descriptors, basePath, currentFile, isModule);
    section.appendChild(list);

    let toggle = section.querySelector('.toggle');
    toggle.dataset.id = getIdPrefix(isModule);
  }

  /** @param {!Element} el . */
  function revealElement(el) {
    for (let current = el;
         current && current != navEl;
         current = dom.getParentElement(current)) {
      if (current.classList.contains('tree')) {
        let control = current.previousElementSibling;
        if (control && control.classList.contains('toggle')) {
          drawer.updateControl_(control, true, true);
        }
      }
    }
    navEl.scrollTop = el.offsetTop - (window.innerHeight / 2);
  }
};
