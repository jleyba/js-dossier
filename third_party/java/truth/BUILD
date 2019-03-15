package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "truth",
    testonly = 1,
    exports = [
        "@com_google_truth_extensions_truth_liteproto_extension//jar",
        "@com_google_truth_extensions_truth_proto_extension//jar",
        "@com_google_truth_truth//jar",
    ],
    runtime_deps = [
        "//third_party/java/guava",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/junit",
        "@com_googlecode_java_diff_utils_diffutils//jar",
    ],
)
