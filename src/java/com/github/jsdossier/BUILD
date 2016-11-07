java_library(
    name = "jsdossier",
    srcs = glob(["*.java"]),
    resources = [
        "//src/js:dossier",
        "//src/js:serviceworker",
    ] + glob(["resources/*.css"]),
    deps = [
        "//lib/maven:args4j",
        "//lib/maven:auto_factory",
        "//lib/maven:auto_value",
        "//lib/maven:closure_compiler",
        "//lib/maven:commonmark",
        "//lib/maven:gson",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:joda_time",
        "//lib/maven:jsr305",
        "//lib/maven:owasp_html_sanitizer",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/markdown",
        "//src/java/com/github/jsdossier/proto",
        "//src/java/com/github/jsdossier/soy",
    ],
    visibility = [
        "//src/java/com/github/jsdossier/testing:__pkg__",
        "//test/java/com/github/jsdossier:__subpackages__"
    ],
)


# Generates a single jar with Dossier's class files, _excluding_ third party
# dependencies.
genrule(
  name = "lib_no_deps",
  srcs = [
    "//src/java/com/github/jsdossier",
    "//src/java/com/github/jsdossier/annotations",
    "//src/java/com/github/jsdossier/jscomp",
    "//src/java/com/github/jsdossier/markdown",
    "//src/java/com/github/jsdossier/proto",
    "//src/java/com/github/jsdossier/soy",
  ],
  outs = ["dossier-no-deps.jar"],
  cmd = 'readonly GENFILE="$$(pwd)/$@"; ' +
        'readonly OUT="$$(mktemp -d $${TMPDIR:-/tmp}/dossier.XXXXXX)/js-dossier"; ' +
        'readonly CWD="$$(pwd)"; ' +
        'mkdir -p $$OUT && ' +
        'cd $$OUT && ' +
        'jar -xf $$CWD/$(location //src/java/com/github/jsdossier) && ' +
        'jar -xf $$CWD/$(location //src/java/com/github/jsdossier/annotations) && ' +
        'jar -xf $$CWD/$(location //src/java/com/github/jsdossier/jscomp) && ' +
        'jar -xf $$CWD/$(location //src/java/com/github/jsdossier/markdown) && ' +
        'jar -xf $$CWD/$(location //src/java/com/github/jsdossier/proto) && ' +
        'jar -xf $$CWD/$(location //src/java/com/github/jsdossier/soy) && ' +
        'jar -cf $$GENFILE -C $$OUT . && ' +
        'cd $$(dirname $$GENFILE) && rm -rf $$OUT/',
)

java_binary(
    name = "dossier",
    runtime_deps = [":jsdossier"],
    main_class = "com.github.jsdossier.Main",
    visibility = ["//:__pkg__"],
)

java_binary(
    name = "GenerateReadme",
    resources = glob(["resources/*.md"]),
    runtime_deps = [":jsdossier"],
    main_class = "com.github.jsdossier.Config",
    visibility = ["//:__pkg__"],
)
