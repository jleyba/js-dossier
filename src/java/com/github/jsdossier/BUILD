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
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/markdown",
        "//src/java/com/github/jsdossier/soy",
        "//src/proto:java_proto",
        "//third_party/java/auto:factory",
        "//third_party/java/auto:value",
        "//third_party/java/closure_compiler",
        "@maven//:args4j_args4j",
        "@maven//:com_atlassian_commonmark_commonmark",
        "@maven//:com_google_auto_factory_auto_factory",
        "@maven//:com_google_auto_value_auto_value_annotations",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_common_html_types_types",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:com_googlecode_owasp_java_html_sanitizer_owasp_java_html_sanitizer",
        "@maven//:javax_inject_javax_inject",
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
