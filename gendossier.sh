#!/bin/bash
#
# Generates various resources for Dossier.

set -e

readonly ROOT="$(cd $(dirname $0) && pwd)"
readonly RESOURCES="${ROOT}/src/java/com/github/jsdossier/resources"

usage() {
  cat <<EOF
usage $0 [...options]

Generate various resources for Dossier. If no options are specified,
builds a new release (-r).

OPTIONS:
  -h       Print this help message and exit
  -d       Refresh the project's readme documentation
  -j       Run the Closure Compiler on dossier.js
  -l       Run lessc on dossier.less
  -p       Run protoc on dossier.proto
  -r       Build a release
  -s       Build sample documentation for dossier.js
  -t       Run all tests
EOF
}


run_jsc() {
  bazel build //src/js:dossier_bin
}

run_lessc() {
  if type -P lessc >/dev/null; then
    lessc --clean-css="--s0 --advanced" --autoprefix="last 2 versions, edge > 12" \
        src/js/dossier.less \
        $RESOURCES/dossier.css
  else
    echo >&2 "[ERROR] lessc not found: install node from https://nodejs.org, then run:"
    echo >&2 "  $ npm install -g less less-plugin-clean-css less-plugin-autoprefix"
    exit 2
  fi
}

run_protoc() {
  if type -P protoc >/dev/null; then
    protoc --java_out=src/java \
        --proto_path=src/proto \
        --proto_path=third_party/proto \
        src/proto/dossier.proto
    protoc --java_out=test/java \
        --proto_path=src/proto \
        --proto_path=test/java/com/github/jsdossier/soy \
        --proto_path=third_party/proto \
        test/java/com/github/jsdossier/soy/test_proto.proto
  else
    echo >&2 "[ERROR] protoc not found: download v2.6.1 from:"
    echo >&2 "  https://developers.google.com/protocol-buffers/docs/downloads"
    exit 2
  fi
}

run_tests() {
  bazel test //test/...

  bazel run //src/java/com/github/jsdossier/tools:WriteDeps -- \
      -c "${ROOT}/third_party/js/closure_library/closure/goog/" \
      -i "${ROOT}/src/js/dossier.js" \
      -i "${ROOT}/src/js/nav.js" \
      -i "${ROOT}/test/js/nav_test.js" \
      -o "${ROOT}/test/js/deps.js"
}

build_release() {
  bazel clean
  bazel test //test/... && \
      bazel build //src/java/com/github/jsdossier:dossier_deploy.jar && \
      echo "Release built: bazel-bin/src/java/com/github/jsdossier/dossier_deploy.jar"
}

build_sample() {
  bazel build //src/java/com/github/jsdossier:dossier_deploy.jar
  java -agentlib:jdwp=transport=dt_socket,server=y,suspend="${DEBUG:-n}",address=5005 \
      -jar bazel-bin/src/java/com/github/jsdossier/dossier_deploy.jar \
      --config sample_config.json
}

update_readme() {
  bazel build //src/java/com/github/jsdossier:Config
  cat > README.md <<EOF
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

Where \`config.json\` is a configuration file with the options listed below.

EOF
  bazel-bin/src/java/com/github/jsdossier/Config 2>> README.md

  cat >> README.md <<EOF
## ES6 Support

Dossier supports ES6 code insofar as the [Closure Compiler](https://github.com/google/closure-compiler/wiki/ECMAScript6)
does. Since the compiler transpiles ES6 to ES5 for analysis, there is some
information loss with Dossier. Most notably, type information is lost for
\`Symbol\` types and generator functions. To use Dossier with ES6 code, in your
configuration file, simply set the input \`language\` to \`ES6\` or
\`ES6_STRICT\` (which is the default).

### Module Support

Dossier currently recognizes three types of JavaScript modules:

1. Closure modules identified by \`goog.module(id)\` declaration
2. ES6 modules identified by the use of an \`export\` or \`import\` declaration
3. Node-style CommonJS modules

Node modules must be explicitly declared as \`modules\` inputs in your
configuration file so Dossier knows to look for \`require()\` and
\`exports\` expressions.

For Node and ES6 modules, you may import other modules by their _relative_
path:

    import {Foo as Bar} from './lib';  // ES6
    const Baz = require('./dir/lib');  // Node

Refer to the section on [type linking](#type-linking) below for information
on how to refer to imported types within a JSDoc comment.

## Formatting

Before generating the final HTML output, Dossier runs all comments through a
[CommonMark](http://commonmark.org/) parser. Since markdown is sensitive to the
leading whitespace on each line, care must be taken with comment formatting.
Comments are extracted from the source according to the follow rules:

1. The \`/**\` on the opening line is removed; all subsequent content is
   considered part of the comment.
2. On each subsequent line, all whitespace up to the first non-space character
   is removed.
3. If the first character on a line after removing whitespace is a \`*\`, it
   is removed from the line. All subsequent content is considered part of the
   comment.
4. On the final line, the closing \`*/\` is removed.

For example, the JSDoc comment (.'s inserted to highlight whitespace)

    /**
    .*.Line one.
    .*.Line two.
    .*
    .*.* list item one
    .*.* line item two
    .*
    .*.....code block
    .*/

is passed to the parser as

    .Line one.
    .Line two.

    .* list item one
    .* list item two

    .....code block

When applied to comments attached to annotations, the same rules apply, except
the comment text starts after the annotation, type, or name (as applicable for
the annotation). For instance,

    /**
     * @param {string} x This is the comment for
     *     parameter x.
     */

the comment string parsed for parameter \`x\` is (again, .'s inserted to denote
leading whitespace):

    .This is the comment for
    .....parameter x.

### The \`@code\` and \`@literal\` Taglets

The \`@code\` and \`@literal\` taglets may be used to specify text that
should be HTML escaped for rendering; the \`@code\` taglet will wrap its
output in \`<code>\` tags. For example, the following

<pre><code>{@&shy;code 1 &lt; 2 &amp;&amp; 3 &lt; 4;}</code></pre>

will produce

    <code>1 &lt; 2 &amp;&amp; 3 &lt; 4;</code>

### Type Linking <a name="type-linking"></a>

Dossier uses the \`@link\` and \`@linkplain\` taglets to generate links to
named types (\`@link\` will generate \`<code>\` formatted links).  The taglet
contents up to the first space are parsed as the type name and everything after
the space is the link text. If there is no text within the taglet, the type
name will be used. For example, suppose there is a type named
\`example.Widget\`, then

<pre><code>An {@&shy;link example.Widget} link.
A {@&shy;link example.Widget widget link}.
</code></pre>

would produce

    An <a href="path/to/example.Widget.html"><code>example.Widget</code></a> link.
    A <a href="path/to/example.Widget.html"><code>widget link</code></a>.

You may use a hash tag (#) to reference a type's property inside a link:
<code>{&shy;@link example.Widget#build()}</code>. You may omit the type's name
as a qualifier when linking to one of its own properties:
<code>{&shy;@link #build()}</code>. Dossier will favor instance over static
properties when de-referencing a hash link.

Dossier tracks type aliases so your documentation may reflect the actual source.
For instance, if you import a type from a module, you may refer to that type
by its imported alias:

<pre><code>import {Widget as Whatsit} from './lib';

/** A {@&shy;link Whatsit} object. */
export const w = new Whatsit;
</code></pre>

Here, the comment on the exported \`w\` property produces

    <p>A <a href="module/lib_exports_Widget"><code>Whatsit</code></a> object.</p>

When using the [revealing module pattern](https://carldanley.com/js-revealing-module-pattern/),
your module's documentation can refer to a type by its internal name and
Dossier will generate a link to the exported type.

<pre><code>class Widget {}

/** A factory that generates {@&shy;link Widget} objects. */
class WidgetFactory {}

export {Widget as WhatsIt, WidgetFactory}
</code></pre>

In the above, since \`Widget\`'s public name is \`WhatsIt\`, the generate
documentation would be (extra newlines inserted for readability)

    <p>A factory that generates
    <a href="module/lib_exports_WhatsIt.html"><code>Widget</code></a> objects.
    </p>

Within an ES6 or Node module, you may refer to another module without importing
using the module's _relative_ path as your type symbol. To refer to an exported
type from another module, simply qualify it with the module's relative path.

<pre><code>/** A link to module {@&shy;link ./foo/bar} */
/** A link to type {@&shy;link ./foo/bar.Baz} */
</code></pre>

### The \`@see\` Annotation

Use the \`@see\` annotation in your JSDoc to add a reference to another
type or an external resource. The text context following the annotation is
processed in the following order:

1. The annotation contents are processed as a type link using the rules defined
   in the previous section. If the contents define a valid reference to another
   type or property, a link will be included in the HTML output.
2. If the annotation is a well-formed http or https URL, it will be rendered as
   a link.
3. Otherwise, the contents of the annotation are processed as markdown like a
   comment's main body.


__Example__

    class Greeter {
      /** @param {!Person} person . */
      greet(person) {}
    }

    /**
     * @see Greeter
     * @see #name
     * @see http://www.example.com
     */
    class Person {
      /** @return {string} . */
      name() { return ''; }
    }

In this example, the \`@see\` annotations on the \`Person\` class would
generate the following links:

    <a href="Greeter.html"><code>Greeter</code></a>
    <a href="Person.html#name"><code>#name</code></a>
    <a href="http://www.example.com">http://www.example.com</a>

## HTML Sanitization

All HTML output is sanitized using the [owasp HTML sanitizer](https://github.com/owasp/java-html-sanitizer).
Refer to the [source](https://github.com/jleyba/js-dossier/blob/master/src/java/com/github/jsdossier/soy/HtmlSanitizer.java)
for an up-to-date list of the supported HTML tags and attributes.

## Building

Dossier is built using [Bazel](http://bazel.io/). Once
you have [installed](http://bazel.io/docs/install.html) Bazel,
you can use the \`gendossier.sh\` script to complete various actions:

    ./gendossier.sh -h

## LICENSE

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
EOF
}

main() {
  local no_options=1
  local js=0
  local less=0
  local proto=0
  local readme=0
  local release=0
  local sample=0
  local test=0

  while getopts "dhjlprst" option
  do
    case $option in
      h)
        usage
        exit 0
        ;;
      d)
        no_options=0; readme=1
        ;;
      j)
        no_options=0; js=1
        ;;
      l)
        no_options=0; less=1
        ;;
      p)
        no_options=0; proto=1
        ;;
      r)
        no_options=0; release=1
        ;;
      s)
        no_options=0; sample=1
        ;;
      t)
        no_options=0; test=1
        ;;
    esac
  done

  if (( $no_options )); then
    release=1
  fi

  if (( $readme )); then
    update_readme
  fi

  if (( $js )); then
    run_jsc
  fi

  if (( $less )); then
    run_lessc
  fi

  if (( $proto )); then
    run_protoc
  fi

  if (( $test )); then
    run_tests
  fi

  if (( $release )); then
    build_release
  fi

  if (( $sample )); then
    build_sample
  fi
}

main $@
