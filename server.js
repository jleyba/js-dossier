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
 * @fileoverview Simple static file server used for testing.
 *
 * Usage: node server.js [dir]
 *
 * where [dir] defaults to "out".
 */

'use strict';

var fs = require('fs');
var http = require('http');
var https = require('https');
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

try {
  var options = {
    key: fs.readFileSync(path.join(__dirname, 'key.pem')),
    cert: fs.readFileSync(path.join(__dirname, 'cert.pem'))
  };
} catch (ex) {
  console.log('Error reading self-signed certificate');
  console.log('Did you run gencert.sh?');
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

var httpServer = http.createServer(app).listen(3000, function() {
  var host = httpServer.address().address;
  var port = httpServer.address().port;
  console.log('Example app listening at http://%s:%s', host, port);
});

var httpsServer = https.createServer(options, app).listen(3001, function() {
  var host = httpsServer.address().address;
  var port = httpsServer.address().port;
  console.log('Example app listening at https://%s:%s', host, port);
});
