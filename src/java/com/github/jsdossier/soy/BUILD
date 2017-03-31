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
        "//lib/maven:gson",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:guice_multibindings",
        "//lib/maven:inject",
        "//lib/maven:jsr305",
        "//lib/maven:protobuf",
        "//lib/maven:safe_types",
        "//src/proto:java_proto",
    ],
)

java_binary(
    name = "GenerateSoyTemplates",
    main_class = "com.github.jsdossier.soy.Renderer",
    visibility = ["//src/js:__pkg__"],
    runtime_deps = [":soy"],
)
