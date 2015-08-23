java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    testonly = 1,
    deps = [
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:junit",
        "//src/java/com/github/jsdossier/jscomp",
    ],
    visibility = [
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)