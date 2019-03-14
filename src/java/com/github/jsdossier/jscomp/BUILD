java_library(
    name = "jscomp",
    srcs = glob(["*.java"]),
    resources = glob(["resources/externs/node/*.js"]),
    visibility = [
        "//src/java/com/github/jsdossier:__subpackages__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
    deps = [
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/google/javascript/jscomp",
        "//third_party/java/auto:factory",
        "//third_party/java/auto:value",
        "//third_party/java/closure_compiler",
        "//third_party/java/guava",
        "//third_party/java/guice",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
    ],
)
