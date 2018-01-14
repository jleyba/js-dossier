load("//build_tools:java_test_suite.bzl", "java_test_suite")

LIB_SRCS = [
    "AbstractTypeInspectorTest.java",
]

java_library(
    name = "lib",
    testonly = 1,
    srcs = LIB_SRCS,
    deps = [
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jimfs",
        "//lib/maven:jsr305",
        "//lib/maven:junit",
        "//lib/maven:protobuf",
        "//lib/maven:safe_types",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/testing",
        "//src/proto:java_proto",
        "//test/java/com/github/jsdossier/testing",
    ],
)

java_test_suite(
    name = "AllTests",
    srcs = glob(
        ["*.java"],
        exclude = LIB_SRCS,
    ),
    resources = glob(["resources/**"]),
    deps = [
        ":lib",
        "//lib/maven:closure_compiler",
        "//lib/maven:gson",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jimfs",
        "//lib/maven:jsoup",
        "//lib/maven:jsr305",
        "//lib/maven:junit",
        "//lib/maven:mockito",
        "//lib/maven:safe_types",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/testing",
        "//src/proto:java_proto",
        "//test/java/com/github/jsdossier/testing",
    ],
)
