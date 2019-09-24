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
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_jimfs_jimfs",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:javax_inject_javax_inject",
        "@maven//:com_google_common_html_types_types",
        "@maven//:com_google_truth_extensions_truth_proto_extension",       
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
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_jimfs_jimfs",
        "@maven//:com_google_truth_truth",
        "@maven//:com_google_common_html_types_types",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:com_google_code_gson_gson",
        "@maven//:org_jsoup_jsoup",
    ],
)
