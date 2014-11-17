#!/bin/bash
#
# Generates various resources for Dossier.

set -e

readonly RESOURCES="src/main/java/com/github/jleyba/dossier/resources"

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
EOF
}


run_jsc() {
  buck build \
      //third_party/java/soy:SoyToJsSrcCompiler \
      //third_party/java/closure_compiler:compiler

  java -jar buck-out/gen/third_party/java/soy/SoyToJsSrcCompiler.jar \
      --shouldGenerateJsdoc \
      --shouldProvideRequireSoyNamespaces \
      --outputPathFormat src/main/js/dossier_soy.js \
      --srcs $RESOURCES/dossier.soy

  python ./third_party/js/closure_library/closure/bin/calcdeps.py \
      -i ./src/main/js/soyutils_usegoog.js \
      -i ./src/main/js/dossier_soy.js \
      -i ./src/main/js/dossier.js \
      -i ./src/main/js/deps.js \
      -d ./third_party/js/closure_library/closure/goog/deps.js \
      -o compiled \
      -c ./buck-out/gen/third_party/java/closure_compiler/compiler.jar \
      -f "--compilation_level=ADVANCED_OPTIMIZATIONS" \
      -f "--define=goog.DEBUG=false" \
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
      --output_file=$RESOURCES/dossier.js
}

run_lessc() {
  lessc --compress \
      src/main/js/dossier.less \
      $RESOURCES/dossier.css
}

run_protoc() {
  protoc --java_out=src/main/java \
      src/main/proto/dossier.proto
  protoc --java_out=src/test/java \
      src/test/java/com/github/jleyba/dossier/soy/test_proto.proto
}

build_release() {
  buck clean
  buck build app || \
      echo "Release built: buck-out/gen/src/main/java/com/github/jleyba/dossier/dossier.jar"
}

build_sample() {
  buck build app
  java -jar buck-out/gen/src/main/java/com/github/jleyba/dossier/dossier.jar \
      --config sample_config.json
}

update_readme() {
  buck build //src/main/java/com/github/jleyba/dossier:Config
  cat > README.md <<EOF
# Dossier

Dossier is a [JSDoc](http://en.wikipedia.org/wiki/JSDoc) parsing tool built on
top of the [Closure Compiler](https://developers.google.com/closure/compiler/?csw=1).

## Usage

    java -jar dossier.jar -c config.json

Where \`config.json\` is a configuration file with the options listed below.

EOF
  java -jar buck-out/gen/src/main/java/com/github/jleyba/dossier/Config.jar 2>> README.md

  cat >> README.md <<EOF
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
    buck test
  fi

  if (( $release )); then
    build_release
  fi

  if (( $sample )); then
    build_sample
  fi
}

main $@
