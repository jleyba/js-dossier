java_library(
    name = "jscomp",
    srcs = glob(["*.java"]),
    deps = [
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jsr305",
        "//src/java/com/github/jsdossier/annotations",
    ],
    visibility = [
        "//src/java/com/github/jsdossier:__pkg__",
        "//test/java/com/github/jsdossier:__pkg__",
    ],
)