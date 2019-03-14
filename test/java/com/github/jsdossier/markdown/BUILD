load("//build_tools:java_test_suite.bzl", "java_test_suite")

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"]),
    deps = [
        "//src/java/com/github/jsdossier/markdown",
        "//third_party/java/commonmark",
        "//third_party/java/guava",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
