/**
 * @fileoverview Simple static file server used for testing.
 *
 * Usage: node server.js [dir]
 *
 * where [dir] defaults to "out".
 */

'use strict';

var path = require('path');

try {
  var express = require('express');
  var serveIndex = require('serve-index');
} catch (ex) {
  console.log('Dependencies not found');
  console.log('Try one of the following: ');
  console.log('  npm install express serve-index');
  console.log('  npm install -g express serve-index');
  throw ex;
}

var app = express();

var staticDir = path.join(__dirname, process.argv[2] || 'out');
console.log('Serving static content from: ' + staticDir);
app.use('/', serveIndex(staticDir));
app.use('/', express.static(staticDir, {
 dotfiles: 'ignore',
 index: false,
 redirect: true
}));

var server = app.listen(3000, function () {
  var host = server.address().address;
  var port = server.address().port;
  console.log('Example app listening at http://%s:%s', host, port);
});