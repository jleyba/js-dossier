load("//build_tools:java_test_suite.bzl", "java_test_suite")

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"]),
    deps = [
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/testing",
        "//test/java/com/github/jsdossier/testing",
        "//third_party/java/closure_compiler",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_jimfs_jimfs",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)
