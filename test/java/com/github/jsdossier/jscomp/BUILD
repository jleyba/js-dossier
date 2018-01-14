load("//build_tools:java_test_suite.bzl", "java_test_suite")

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"]),
    deps = [
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jimfs",
        "//lib/maven:junit",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/testing",
        "//test/java/com/github/jsdossier/testing",
    ],
)