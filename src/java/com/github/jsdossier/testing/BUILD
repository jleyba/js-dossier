java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    testonly = 1,
    deps = [
        "//lib/maven:auto_value_lib",
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jimfs",
        "//lib/maven:junit",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
    ],
    plugins = ["//lib/maven:auto_value_plugin"],
    visibility = [
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)