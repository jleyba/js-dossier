load("/build_tools/java_test_suite", "java_test_suite")

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"]),
    deps = [
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:jimfs",
        "//lib/maven:junit",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier/jscomp",
        "//test/java/com/github/jsdossier/testing",
    ],
)