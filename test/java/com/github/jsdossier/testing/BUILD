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
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_javax_inject",
        "@maven//:junit_junit",
    ],
)
