#!/bin/bash
#
# Generates various resources for Dossier.

set -e

readonly RESOURCES="src/java/com/github/jsdossier/resources"

usage() {
  cat <<EOF
usage $0 [...options]

Generate various resources for Dossier. If no options are specified,
all steps will be executed.

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
  buck build //src/java/com/github/jsdossier/tools:Compile

  java -jar ./buck-out/gen/src/java/com/github/jsdossier/tools/Compile.jar \
      -c ./third_party/js/closure_library/closure/goog/ \
      -i ./src/js/dossier.js \
      -i ./src/js/nav.js \
      -f "--charset=UTF-8" \
      -f "--compilation_level=ADVANCED_OPTIMIZATIONS" \
      -f "--define=goog.DEBUG=false" \
      -f "--externs=./src/js/externs.js" \
      -f "--manage_closure_dependencies" \
      -f "--closure_entry_point=dossier" \
      -f "--jscomp_error=accessControls" \
      -f "--jscomp_error=ambiguousFunctionDecl" \
      -f "--jscomp_error=checkRegExp" \
      -f "--jscomp_error=checkTypes" \
      -f "--jscomp_error=checkVars" \
      -f "--jscomp_error=constantProperty" \
      -f "--jscomp_error=deprecated" \
      -f "--jscomp_error=duplicateMessage" \
      -f "--jscomp_error=es5Strict" \
      -f "--jscomp_error=externsValidation" \
      -f "--jscomp_error=fileoverviewTags" \
      -f "--jscomp_error=globalThis" \
      -f "--jscomp_error=invalidCasts" \
      -f "--jscomp_error=missingProperties" \
      -f "--jscomp_error=nonStandardJsDocs" \
      -f "--jscomp_error=strictModuleDepCheck" \
      -f "--jscomp_error=typeInvalidation" \
      -f "--jscomp_error=undefinedVars" \
      -f "--jscomp_error=unknownDefines" \
      -f "--jscomp_error=uselessCode" \
      -f "--jscomp_error=visibility" \
      -f "--language_in=ES5" \
      -f "--third_party=false" \
      -f "--output_wrapper=\"(function(){%output%;init();})();\"" \
      -f "--js_output_file=$RESOURCES/dossier.js"
}

run_lessc() {
  lessc --compress \
      src/js/dossier.less \
      $RESOURCES/dossier.css
}

run_protoc() {
  protoc --java_out=src/java \
      --proto_path=src/proto \
      --proto_path=third_party/java \
      src/proto/dossier.proto
  protoc --java_out=test/java \
      --proto_path=src/proto \
      --proto_path=test/java/com/github/jsdossier/soy \
      --proto_path=third_party/java \
      test/java/com/github/jsdossier/soy/test_proto.proto
}

run_tests() {
  buck build //src/java/com/github/jsdossier/tools:WriteDeps
  buck test app_tests

  java -jar ./buck-out/gen/src/java/com/github/jsdossier/tools/WriteDeps.jar \
      -c ./third_party/js/closure_library/closure/goog/ \
      -i ./src/js/dossier.js \
      -i ./src/js/nav.js \
      -i ./test/js/nav_test.js \
      -o ./test/js/deps.js
}

build_release() {
  buck clean
  buck test app_tests && buck build app && \
      echo "Release built: buck-out/gen/src/java/com/github/jsdossier/dossier.jar"
}

build_sample() {
  buck build app
  java -Xmx2048M \
      -jar buck-out/gen/src/java/com/github/jsdossier/dossier.jar \
      --config sample_config.json
}

update_readme() {
  buck build //src/java/com/github/jsdossier:Config
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
  java -jar buck-out/gen/src/java/com/github/jsdossier/Config.jar 2>> README.md

  cat >> README.md <<EOF
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

Markdown support can be disabled by setting \`useMarkdown=false\` in your
configuration.

### The \`@code\` and \`@literal\` Taglets

The \`@code\` and \`@literal\` taglets may be used to specify text that
should be HTML escaped for rendering; the \`@code\` taglet will wrap its
output in \`<code>\` tags. For example, the following

<pre><code>{@&shy;code 1 &lt; 2 &amp;&amp; 3 &lt; 4;}</code></pre>

will produce

    <code>1 &lt; 2 &amp;&amp; 3 &lt; 4;</code>

### Type Linking

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
you can use the \`gendossier.sh\` script to complete various actions:

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
EOF
}

main() {
  local all=1
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
        all=0; readme=1
        ;;
      j)
        all=0; js=1
        ;;
      l)
        all=0; less=1
        ;;
      p)
        all=0; proto=1
        ;;
      r)
        all=0; release=1
        ;;
      s)
        all=0; sample=1
        ;;
      t)
        all=0; test=1
        ;;
    esac
  done

  if (( $all )); then
    js=1
    less=1
    proto=1
    release=1
    sample=1
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
