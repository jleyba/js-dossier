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

goog.addDependency('../../../../../src/js/nav.js', ['dossier.nav'], ['goog.array', 'goog.asserts', 'goog.string'], true);
goog.addDependency('../../../../../test/js/nav_test.js', ['dossier.nav.test'], ['dossier.nav', 'goog.testing.dom', 'goog.testing.jsunit']);
goog.addDependency('../../../../../src/js/dossier.js', ['dossier'], ['dossier.nav', 'goog.array', 'goog.events', 'goog.events.EventType', 'goog.dom', 'goog.dom.classlist', 'goog.string', 'goog.ui.ac', 'goog.ui.ac.AutoComplete.EventType']);
