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
        "//lib/maven:safe_types",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/markdown",
        "//src/java/com/github/jsdossier/soy",
        "//src/proto:java_proto",
    ],
    visibility = [
        "//src/java/com/github/jsdossier/testing:__pkg__",
        "//test/java/com/github/jsdossier:__subpackages__"
    ],
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
