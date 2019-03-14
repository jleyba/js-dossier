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
        "//lib/maven:closure_templates",
        "//lib/maven:safe_types",
        "//src/proto:java_proto",
        "//third_party/java/gson",
        "//third_party/java/guava",
        "//third_party/java/guice",
        "//third_party/java/guice:multibindings",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/protobuf",
    ],
)

java_binary(
    name = "GenerateSoyTemplates",
    main_class = "com.github.jsdossier.soy.Renderer",
    visibility = ["//src/js:__pkg__"],
    runtime_deps = [":soy"],
)
