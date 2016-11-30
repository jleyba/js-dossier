java_library(
    name = "jscomp",
    srcs = glob(["*.java"]),
    resources = glob(["resources/externs/node/*.js"]),
    visibility = [
        "//src/java/com/github/jsdossier:__subpackages__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
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
)
