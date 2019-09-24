load("//build_tools:java_test_suite.bzl", "java_test_suite")

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"]),
    deps = [
        "//src/java/com/github/jsdossier/markdown",
        "@maven//:com_atlassian_commonmark_commonmark",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)
