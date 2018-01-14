load("//build_tools:java_test_suite.bzl", "java_test_suite")

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"]),
    deps = [
        "//lib/maven:commonmark",
        "//lib/maven:guava",
        "//lib/maven:junit",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier/markdown",
    ],
)
