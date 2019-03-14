java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["*.java"]),
    visibility = [
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
    deps = [
        "//lib/maven:closure_templates",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/soy",
        "//third_party/java/auto:value",
        "//third_party/java/closure_compiler",
        "//third_party/java/guava",
        "//third_party/java/guice",
        "//third_party/java/jimfs",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
