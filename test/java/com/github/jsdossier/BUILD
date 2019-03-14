load("//build_tools:java_test_suite.bzl", "java_test_suite")

LIB_SRCS = [
    "AbstractTypeInspectorTest.java",
]

java_library(
    name = "lib",
    testonly = 1,
    srcs = LIB_SRCS,
    deps = [
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/testing",
        "//src/proto:java_proto",
        "//test/java/com/github/jsdossier/testing",
        "//third_party/java/closure_compiler",
        "//third_party/java/guava",
        "//third_party/java/guice",
        "//third_party/java/jimfs",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/protobuf",
        "//third_party/java/safe_types",
        "//third_party/java/truth",
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
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/testing",
        "//src/proto:java_proto",
        "//test/java/com/github/jsdossier/testing",
        "//third_party/java/closure_compiler",
        "//third_party/java/gson",
        "//third_party/java/guava",
        "//third_party/java/guice",
        "//third_party/java/jimfs",
        "//third_party/java/jsoup",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/safe_types",
        "//third_party/java/truth",
    ],
)
