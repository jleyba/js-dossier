java_library(
    name = "soy",
    srcs = glob(["*.java"]),
    resources = glob(["resources/*"]),
    visibility = [
        "//src/java/com/github/jsdossier:__pkg__",
        "//src/java/com/github/jsdossier/testing:__pkg__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
    deps = [
        "//src/proto:java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_common_html_types_types",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_multibindings",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_template_soy",
        "@maven//:javax_inject_javax_inject",
    ],
)

java_binary(
    name = "GenerateSoyTemplates",
    main_class = "com.github.jsdossier.soy.Renderer",
    visibility = ["//src/js:__pkg__"],
    runtime_deps = [":soy"],
)
