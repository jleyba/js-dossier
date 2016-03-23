java_library(
    name = "jscomp",
    srcs = glob(["*.java"]),
    deps = [
        "//lib/maven:auto_factory",
        "//lib/maven:auto_value",
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jsr305",
        "//src/java/com/github/jsdossier/annotations",
    ],
    resources = [
        "//third_party/js/externs/node",
    ],
    visibility = [
        "//src/java/com/github/jsdossier:__subpackages__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)
