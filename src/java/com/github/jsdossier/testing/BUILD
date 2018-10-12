java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    testonly = 1,
    deps = [
        "//lib/maven:auto_value",
        "//lib/maven:closure_compiler",
        "//lib/maven:closure_templates",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jimfs",
        "//lib/maven:jsr305",
        "//lib/maven:junit",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/soy",
    ],
    visibility = [
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)
