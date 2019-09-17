#!/bin/bash
#
# Generates various resources for Dossier.

set -e

readonly ROOT="$(cd $(dirname $0) && pwd)"
readonly RESOURCES="${ROOT}/src/java/com/github/jsdossier/resources"
readonly GOOGLE_JAVA_FORMAT="${ROOT}/third_party/java/google_java_format/google-java-format-1.3-all-deps.jar"
readonly BAZEL="$(which bazelisk)"

usage() {
  cat <<EOF
usage $0 [...options]

Generate various resources for Dossier. If no options are specified,
builds a new release (-r).

OPTIONS:
  -h       Print this help message and exit
  -d       Refresh the project's readme documentation
  -f       Format all java code
  -j       Run the Closure Compiler on dossier.js
  -l       Run lessc on dossier.less
  -r       Build a release
  -s       Build sample documentation for dossier.js
  -t       Run all tests
EOF
}


run_jsc() {
  "${BAZEL}" build //src/js:all
}

run_lessc() {
  if type -P lessc >/dev/null; then
    lessc --clean-css="--s0 --advanced" --autoprefix="last 2 versions, edge > 12" \
        src/css/dossier.less \
        $RESOURCES/dossier.css
  else
    echo >&2 "[ERROR] lessc not found: install node from https://nodejs.org, then run:"
    echo >&2 "  $ npm install -g less less-plugin-clean-css less-plugin-autoprefix"
    exit 2
  fi
}

run_tests() {
  "${BAZEL}" test //test/...
}

build_release() {
  "${BAZEL}" clean && \
      "${BAZEL}" test //test/... && \
      "${BAZEL}" build :release && \
      echo "Release built: bazel-genfiles/js-dossier.tar.gz"
}

build_sample() {
  "${BAZEL}" build //src/java/com/github/jsdossier:dossier_deploy.jar
  java -agentlib:jdwp=transport=dt_socket,server=y,suspend="${DEBUG:-n}",address=5005 \
      -jar bazel-bin/src/java/com/github/jsdossier/dossier_deploy.jar \
      --config sample_config.json
}

update_readme() {
  "${BAZEL}" build :readme && cp bazel-genfiles/README.md README.md
}

format_java() {
  cd "${ROOT}"
  java -jar "${GOOGLE_JAVA_FORMAT}" -i $(find src test -name *.java)
}

main() {
  if [[ -z "${BAZEL}" ]]; then
    echo "bazelisk not installed: https://github.com/bazelbuild/bazelisk"
    exit 1
  fi

  local no_options=1
  local js=0
  local less=0
  local readme=0
  local release=0
  local sample=0
  local test=0

  while getopts "dfhjlprst" option
  do
    case $option in
      h)
        usage
        exit 0
        ;;
      d)
        no_options=0; readme=1
        ;;
      f)
        no_options=0; format=1
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

  if (( $format )); then
    format_java
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
