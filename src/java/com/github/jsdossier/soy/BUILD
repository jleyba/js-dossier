java_library(
    name = "soy",
    srcs = glob(["*.java"]),
    resources = glob(["resources/*"]),
    deps = [
        "//lib/maven:closure_templates",
        "//lib/maven:gson",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:guice_multibindings",
        "//lib/maven:inject",
        "//lib/maven:jsr305",
        "//lib/maven:protobuf",
        "//src/proto:java_proto",
    ],
    visibility = [
        "//src/java/com/github/jsdossier:__pkg__",
        "//src/java/com/github/jsdossier/testing:__pkg__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)

java_binary(
    name = "GenerateSoyTemplates",
    main_class = "com.github.jsdossier.soy.Renderer",
    runtime_deps = [":soy"],
    visibility = ["//src/js:__pkg__"],
)

java_binary(
    name = "ProtoDescriptorsToJs",
    main_class = "com.github.jsdossier.soy.ProtoDescriptorsToJs",
    runtime_deps = [":soy"],
    visibility = ["//src/js:__pkg__"],
)
