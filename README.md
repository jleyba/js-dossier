# Dossier

Dossier is a [JSDoc](http://en.wikipedia.org/wiki/JSDoc) parsing tool built on
top of the [Closure Compiler](https://developers.google.com/closure/compiler/?csw=1).

## Usage

    java -jar dossier.jar [options]

**Options**

 * `--output PATH`, `-o PATH` Path to the directory to write all generated
   documentation to.<p/>

 * `--src PATH`, `-s PATH`  A .js file to generate API documentation for. If
   this path refers to a directory, all .js files under the directory will be
   included as sources. This option may be specified multiple times.<p/>

 * `--exclude PATH`, `-x PATH`  Path to a .js file to exclude from processing.
   If a directory is specified, all of its descendants will be excluded. This
   option may be specified multiple times.<p/>

 * `--exclude_filter REGEX`, `-f REGEX`  Defines a regular expression to apply
    to all of the input sources; those sources matching this expression will be
    excluded from processing. This option may be specified multiple times.<p/>

 * `--extern PATH`, `-e PATH`  Path to a .js file to include as an extern file
   for the Closure compiler. These files are used to define references to
   external types, but are excluded when generating API documentation. This
   option may be specified multiple times.<p/>

 * `--closure_library PATH`  Path to the base directory of the Closure library
   (which must contain base.js and deps.js). When this option is specified,
   Closure's deps.js and all of the files specified by `--closure_deps` will be
   parsed for calls to `goog.addDependency`.  The resulting map will be used to
   automatically expand the set of `--src` input files any time a symbol is
   goog.require'd with the file that goog.provides that symbol along with its
   transitive dependencies.

    For example, suppose you have one source file, `foo.js`:

        goog.require('goog.array');
        // ...

    and you invoke Dossier with:

        java -jar dossier.jar -o docs/ -s foo.js --closure_library closure/goog

    due to the dependencies of goog.array declared in closure/goog/deps.js, this
    is equivalent to invoking Dossier with:

        java -jar dossier.jar -o docs \
            -s closure/goog/base.js \
            -s closure/goog/debug/error.js \
            -s closure/goog/string/string.js \
            -s closure/goog/asserts/asserts.js \
            -s closure/goog/array/array.js \
            -s foo.js

    Notice specifying `--closure_library` instructs Dossier to sort the input
    files so a file that goog.provides symbol X comes before any file that
    goog.requires X.

 * `--closure_deps PATH`  Path to a file to parse for calls to
   `goog.addDependency`. This option requires also setting `--closure_library`.
   <p/>

 * `--license PATH`  Path to a license file to include with the generated
   documentation.

 * `--readme PATH`  Path to a README file to include in the generated
   documentation. This file, which should use markdown syntax, will be included
   as the content of the main index page.

## LICENSE

Copyright 2013 Jason Leyba

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.