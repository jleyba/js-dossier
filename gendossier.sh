#!/bin/bash
#
# Generates various resources for Dossier.

set -e

usage() {
  cat <<EOF
usage $0 [...options]

Generate various resources for Dossier. If no options are specified,
all steps will be executed.

OPTIONS:
  -h       Print this help message and exit
  -j       Run the Closure Compiler on dossier.js
  -l       Run lessc on dossier.less
  -p       Run protoc on dossier.proto
  -r       Build a release
  -s       Build sample documentation for dossier.js
EOF
}


run_jsc() {
  python ./closure_library/closure/bin/calcdeps.py \
      -i ./src/main/js/dossier.js \
      -i ./src/main/js/deps.js \
      -d ./closure_library/closure/goog/deps.js \
      -o compiled \
      -c ./closure_compiler/compiler.jar \
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
      -f "--third_party=false" \
      -f "--output_wrapper=\"(function(){%output%;init();})();\"" \
      --output_file=./src/main/resources/dossier.js
}

run_lessc() {
  lessc --yui-compress \
      src/main/js/dossier.less \
      src/main/resources/dossier.css
}

run_protoc() {
  protoc --java_out=src/main/java \
      src/main/proto/dossier.proto
}

build_release() {
  mvn clean test assembly:single
}

build_sample() {
  java -jar target/dossier-0.1.1-jar-with-dependencies.jar \
      --src src/main/js/dossier.js \
      --closure_library closure_library/closure/goog \
      --license LICENSE \
      --readme README.md \
      --output target/docs
}

main() {
  local all=1
  local js=0
  local less=0
  local proto=0
  local release=0
  local sample=0

  while getopts "hjlpr" option
  do
    case $option in
      h)
        usage
        exit 0
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
    esac
  done

  if (( $all )); then
    js=1
    less=1
    proto=1
    release=1
    sample=1
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

  if (( $release )); then
    build_release
  fi

  if (( $sample )); then
    build_sample
  fi
}

main $@
