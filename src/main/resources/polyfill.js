(function() {
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
})();
