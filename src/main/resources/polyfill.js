(function() {
  setUpLeftNav(window['TYPES']);
  setUpDetailsPolyfill();

  function setUpLeftNav(typeInfo) {
    if (!typeInfo) {
      throw Error('Type information not found!');
    }

    var sections = [];
    if (typeInfo['classes'].length) {
      sections.push(createNavList(
          'Classes', 'class_', typeInfo['classes']));
    }
    if (typeInfo['enums'].length) {
      sections.push(createNavList(
          'Enums', 'enum_', typeInfo['enums']));
    }
    if (typeInfo['interfaces'].length) {
      sections.push(createNavList(
          'Interfaces', 'interface_', typeInfo['interfaces']));
    }
    if (typeInfo['namespaces'].length) {
      sections.push(createNavList(
          'Namespaces', 'namespace_', typeInfo['namespaces']));
    }

    var nav = document.getElementById('left');
    nav.removeChild(nav.firstChild);
    var section;
    while (section = sections.shift()) {
      nav.appendChild(section);
    }
  }

  function createNavList(header, linkPrefix, types) {
    var details = document.createElement('details');
    var summary = document.createElement('summary');
    summary.appendChild(document.createTextNode(header));
    details.appendChild(summary);

    var list = document.createElement('ul');
    details.appendChild(list);
    for (var i = 0, n = types.length; i < n; ++i) {
      var item = document.createElement('li');
      list.appendChild(item);

      var link = document.createElement('a');
      item.appendChild(link);

      link.href = linkPrefix + types[i].replace(/\./g, '_') + '.html';
      link.appendChild(document.createTextNode(types[i]));
    }

    return details;
  }

  function setUpDetailsPolyfill() {
    if (document.createElement('details').hasOwnProperty('open')) {
      return;  // polyfill not needed.
    }

    var details = document.querySelectorAll('details');
    for (var i = 0, n = details.length; i < n; ++i) {
      (function(el) {
        var open = true;
        el.setAttribute('open', '');
        el.onclick = function() {
          open = !open;
          if (!open) {
            el.removeAttribute('open');
          } else {
            el.setAttribute('open', '');
          }

          var children = el.childNodes;
          for (var i = 0, n = children.length; i < n; ++i) {
            (function(child) {
              if (child.nodeType !== 1 ||
                  child.tagName.toLowerCase() === 'summary') {
                return;
              }
              child.style.display = open ? '' : 'none';
            })(children[i]);
          }
        };

        el.onclick();
      })(details[i]);
    }
  }
})();
