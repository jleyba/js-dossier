# Dossier

Dossier is a [JSDoc](http://en.wikipedia.org/wiki/JSDoc) parsing tool built on
top of the [Closure Compiler](https://developers.google.com/closure/compiler/).
Dossier uses the compiler to parse your code and build a type graph. It then
traverses the graph to find types to generate documentation for. Proper use of
Closure's [annotations](https://developers.google.com/closure/compiler/docs/js-for-compiler)
will not only improve the type-checking and optimizations of the Closure
Compiler, but will also improve Dossier's ability to generate meaningful documentation.

## Usage

    java -jar dossier.jar -c config.json

Where `config.json` is a configuration file with the options listed below.

__Configuration Options__

 * `output` Path to the directory to write all generated documentation to. This
    field is required.

 * `closureLibraryDir` Path to the base directory of the Closure library (which
    must contain base.js and depsjs). When this option is specified, Closure's
    deps.js and all of the files specified by `closureDepsFile` will be parsed
    for calls to `goog.addDependency`. The resulting map will be used to
    automatically expand the set of `sources` any time a symbol is
    goog.require'd with the ile that goog.provides that symbol, along with all
    of its transitive dependencies.
   
    For example, suppose you have one source file, `foo.js`:
   
        goog.require('goog.array');
        // ...
   
    and your configuration includes:
   
        "sources": ["foo.js"],
        "closureLibraryDir": "closure/goog"
   
    due to the dependencies of goog.array declared in closure/goog/deps.js,
    this is equivalent to the following configuration:
   
        "sources": [
            "closure/goog/base.js",
            "closure/goog/debug/error.js",
            "closure/goog/string/string.js",
            "closure/goog/asserts/asserts.js",
            "closure/goog/array/array.js",
            "foo.js"
        ]
   
    Notice specifying `closureLibraryDir` instructs Dossier to sort the input
    files so a a file that goog.provides symbol X comes before any file that
    goog.requires X.

 * `closureDepsFile` Path to a file to parse for calls to `goog.addDependency`.
    This option requires also setting `closureLibraryDir`.

 * `sources` A list of .js files to extract API documentation from. If a glob
    pattern is specified, every .js file under the current working directory
    matching that pattern will be included. Specifying the path to a directory,
    `foo`, is the same as using the glob pattern `foo/**.js`. The set of paths
    specified by this option *must* be disjoint from those specified by
    `modules`.

 * `modules` A list of .js files to extract API documentation from. Each file
    will be processed as a CommonJS module, with only its exported API included
    in the generated output. If a glob pattern is specified, every .js file
    under the current directory matching that pattern will be included.
    Specifying the path to a directory, `foo`, is the same as the glob pattern
    `foo/**.js`. The set of paths specified by this option *mut* be disjoint
    from those specified by `sources`.

 * `stripModulePrefix` A prefix to strip from every module's path when
    generating documentation. The specified path must be a directory that is an
    ancestor of every file specified in `modules`. Note: if this option is
    omitted, the closest common ancestor for all module files will be selected
    as the default.

 * `excludes` A list of .js files to exclude from processing. If a directory is
    specified, all of the .js files under that directory will be excluded. A
    glob pattern may also be specified to exclude all of the paths under the
    current working directory that match  the provided pattern.

 * `externs` A list of .js files to include as an extern file for the Closure
    compiler. These  files are used to satisfy references to external types,
    but are excluded when generating  API documentation.

 * `typeFilters` List of regular expressions for types that should be excluded
    from generated documentation, even if found in the type graph.

 * `readme` Path to a README file to include as the main landing page for the
    generated documentation. This file should use markdown syntax.

 * `customPages` List of additional files to include in the generated
    documentation. Each page is defined as a {name: string, path: string}
    object, where the name is what's displayed in the navigation menu, and
    `path` is the path to the markdown file to use. Files will be included in
    the order listed, after the standard navigation items.

 * `strict` Whether to run with all type checking flags enabled.

 * `useMarkdown` Whether to parse all comments as markdown. The `readme` and
    `customPages` will always be parsed as markdown.

 * `language` Specifies which version of ECMAScript the input sources conform
    to. Defaults to ES5. Must be one of {ES3, ES5, ES5_STRICT}


## Formatting

Before generating the final HTML output, Dossier runs all comments through the
[*pegdown*](https://github.com/sirthias/pegdown) markdown processor. Since
markdown is sensitive to the leading whitespace on each line, Dossier will trim
each line up to the first space *after* the leading \* in the comment.

For example, the JSDoc comment (.'s inserted to highlight whitespace)

    /**
    .*.Line one.
    .*.Line two.
    .*
    .*.....code block
    .*/

is passed to *pegdown* as

    Line one.
    Line two.

    ....code block

Markdown support can be disabled by setting `useMarkdown=false` in your
configuration.

### The `@code` and `@literal` Taglets

The `@code` and `@literal` taglets may be used to specify text that
should be HTML escaped for rendering; the `@code` taglet will wrap its
output in `<code>` tags. For example, the following

<pre><code>{@&shy;code 1 &lt; 2 &amp;&amp; 3 &lt; 4;}</code></pre>

will produce

    <code>1 &lt; 2 &amp;&amp; 3 &lt; 4;</code>

### Type Linking

Dossier uses the `@link` and `@linkplain` taglets to generate links to
named types (`@link` will generate `<code>` formatted links).  The taglet
contents up to the first space are parsed as the type name and everything after
the space is the link text. If there is no text within the taglet, the type
name will be used. For example, suppose there is a type named
`example.Widget`, then

<pre><code>An {@&shy;link example.Widget} link.
A {@&shy;link example.Widget widget link}.
</code></pre>

would produce

    An <a href="path/to/example_Widget.html"><code>example.Widget</code></a> link.
    A <a href="path/to/example_Widget.html"><code>widget link</code></a>.

You may use a hash tag (#) to reference a type's property inside a link:
<code>{&shy;@link example.Widget#build()}</code>. You may omit the type's name
as a qualifier when linking to one of its own properties:
<code>{&shy;@link #build()}</code>. Dossier will favor instance over static
properties when de-referencing a hash link.

## HTML Sanitization

All HTML output is sanitized using the [owasp HTML sanitizer](https://code.google.com/p/owasp-java-html-sanitizer/).
Refer to the [source](https://github.com/jleyba/js-dossier/blob/master/src/java/com/github/jsdossier/soy/HtmlSanitizer.java)
for an up-to-date list of the supported HTML tags and attributes.

## Building

Dossier is built using [Facebook's Buck](http://facebook.github.io/buck/). Once
you have [installed Buck](http://facebook.github.io/buck/setup/quick_start.html),
you can use the `gendossier.sh` script to complete various actions:

    ./gendossier.sh -h

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
