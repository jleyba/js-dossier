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

package com.github.jsdossier;

/** The supported module naming conventions. */
public enum ModuleNamingConvention {
  /**
   * Standard ES6 module naming. Each module's display name will be the path to the source file,
   * sans file extension (e.g. "foo/bar/baz.js" -> "foo/bar/baz").
   */
  ES6,

  /**
   * Module naming as supported by Node JS. If a module file's base name is "index.js", the display
   * name will the path for the parent directory (e.g. "foo/bar/index.js" -> "foo/bar").
   */
  NODE
}
