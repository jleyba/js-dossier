java_library(
    name = "jsdossier",
    srcs = glob(["*.java"]),
    resources = [
        "//src/js:dossier",
        "//src/js:serviceworker",
    ] + glob(["resources/*.css"]),
    visibility = [
        "//src/java/com/github/jsdossier/testing:__pkg__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
    deps = [
        "//lib/maven:safe_types",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/markdown",
        "//src/java/com/github/jsdossier/soy",
        "//src/proto:java_proto",
        "//third_party/java/args4j",
        "//third_party/java/auto:factory",
        "//third_party/java/auto:value",
        "//third_party/java/closure_compiler",
        "//third_party/java/commonmark",
        "//third_party/java/gson",
        "//third_party/java/guava",
        "//third_party/java/guice",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/owasp_html_sanitizer",
    ],
)

java_binary(
    name = "dossier",
    main_class = "com.github.jsdossier.Main",
    visibility = ["//:__pkg__"],
    runtime_deps = [":jsdossier"],
)

java_binary(
    name = "GenerateReadme",
    main_class = "com.github.jsdossier.Config",
    resources = glob(["resources/*.md"]),
    visibility = ["//:__pkg__"],
    runtime_deps = [":jsdossier"],
)
