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

goog.module('dossier.nav');

var Arrays = goog.require('goog.array');
var assert = goog.require('goog.asserts').assert;
var Strings = goog.require('goog.string');


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
 * @return {!TreeNode} The root of the tree node.
 */
exports.buildTree = function(descriptors, isModule) {
  /** @type {!TreeNode} */
  var root = new TreeNode('', null);
  descriptors.forEach(function(descriptor) {
    if (isModule) {
      var moduleRoot;
      if (descriptor.types) {
        moduleRoot = exports.buildTree(descriptor.types, false);
        moduleRoot.setKey(descriptor.name);
        moduleRoot.setValue(descriptor);
      } else {
        moduleRoot = new TreeNode(descriptor.name, descriptor);
      }
      root.addChild(moduleRoot);
      return;
    }

    var currentNode = root;
    descriptor.name.split(/\./).forEach(function(part) {
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
  a.textContent = node.getKey();
  if (node.getValue()) {
    a.href = basePath + node.getValue().href;
    if (node.getValue().interface) {
      a.classList.add('interface');
    }
  }

  var children = node.getChildren();
  if (children) {
    var input = document.createElement('input');
    input.type = 'checkbox';
    input.id = idPrefix + node.getKey();
    li.appendChild(input);

    var label = document.createElement('label');
    label.setAttribute('for', input.id);
    label.appendChild(a);
    if (node.getValue() && node.getValue().href === currentPath) {
      label.classList.add('current');
    }
    li.appendChild(label);

    var ul = document.createElement('ul');
    ul.classList.add('nav-tree');
    li.appendChild(ul);

    children.forEach(function(child) {
      ul.appendChild(buildListItem(child, basePath, currentPath,
          input.id + '.'));
    });

  } else {
    if (node.getValue() && node.getValue().href === currentPath) {
      a.classList.add('current');
    }
    li.appendChild(a);
  }

  return li;
}


var TYPE_ID_PREFIX = '.nav:';
var MODULE_TYPE_ID_PREFIX = '.nav-module:';


/**
 * Builds a nested list for the given types.
 * @param {!Array<!Descriptor>} descriptors The descriptors.
 * @param {string} basePath Base path to prepend to any links.
 * @param {string} currentPath Path for the file running this script.
 * @param {boolean} isModule Whether the descriptors describe CommonJS modules.
 * @return {!Element} The root element for the tree.
 */
exports.buildList = function(descriptors, basePath, currentPath, isModule) {
  if (basePath && !Strings.endsWith(basePath, '/')) {
    basePath += '/';
  }
  var root = exports.buildTree(descriptors, isModule);
  var rootEl = document.createElement('ul');
  if (root.getChildren()) {
    root.getChildren().forEach(function(child) {
      rootEl.appendChild(buildListItem(child, basePath, currentPath,
          isModule ? MODULE_TYPE_ID_PREFIX : TYPE_ID_PREFIX));
    });
  }
  return rootEl;
};
