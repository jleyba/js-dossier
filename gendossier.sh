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
  -r       Build a release
  -s       Build sample documentation for dossier.js
  -t       Run all tests
EOF
}


run_jsc() {
  bazel build //src/js:all
  write_deps
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

run_tests() {
  bazel test //test/...
  write_deps
}

write_deps() {
  bazel build //src/java/com/github/jsdossier/tools:WriteDeps
  bazel-bin/src/java/com/github/jsdossier/tools/WriteDeps \
      -c "${ROOT}/third_party/js/closure_library/closure/goog/" \
      -i "${ROOT}/bazel-genfiles/src/js/proto.dossier.js" \
      -i "${ROOT}/bazel-genfiles/src/js/dossier.soy.js" \
      -i "${ROOT}/bazel-genfiles/src/js/nav.soy.js" \
      -i "${ROOT}/bazel-genfiles/src/js/types.soy.js" \
      -i "${ROOT}/src/js/app.js" \
      -i "${ROOT}/src/js/heap.js" \
      -i "${ROOT}/src/js/main.js" \
      -i "${ROOT}/src/js/nav.js" \
      -i "${ROOT}/src/js/page.js" \
      -i "${ROOT}/src/js/search.js" \
      -i "${ROOT}/src/js/soyplugins.js" \
      -i "${ROOT}/src/js/serviceworker/main.js" \
      -i "${ROOT}/test/js/heap_test.js" \
      -i "${ROOT}/test/js/nav_test.js" \
      -i "${ROOT}/test/js/search_test.js" \
      -i "${ROOT}/third_party/js/soy/soyutils_usegoog.js" \
      -o "${ROOT}/test/js/deps.js"
}

build_release() {
  bazel clean && \
      bazel test //test/... && \
      bazel build :release && \
      echo "Release built: bazel-genfiles/js-dossier.tar.gz"
}

build_sample() {
  bazel build //src/java/com/github/jsdossier:dossier_deploy.jar
  java -agentlib:jdwp=transport=dt_socket,server=y,suspend="${DEBUG:-n}",address=5005 \
      -jar bazel-bin/src/java/com/github/jsdossier/dossier_deploy.jar \
      --config sample_config.json
}

update_readme() {
  bazel build :readme && cp bazel-genfiles/README.md README.md
}

main() {
  local no_options=1
  local js=0
  local less=0
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
