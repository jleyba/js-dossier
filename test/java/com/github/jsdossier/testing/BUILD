java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["*.java"]),
    visibility = [
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
    deps = [
        "//src/java/com/github/jsdossier/jscomp",
        "//third_party/java/closure_compiler",
        "//third_party/java/guava",
        "//third_party/java/guice",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
    ],
)
