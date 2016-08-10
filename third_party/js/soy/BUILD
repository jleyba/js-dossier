load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")

licenses(["notice"])  # Apache 2

exports_files(["LICENSE"])

closure_js_library(
    name = "soy",
    srcs = ["@soyutils_usegoog//file"],
    suppress = [
        "JSC_OPTIONAL_PARAM_NOT_MARKED_OPTIONAL",
    ],
    deps = ["//third_party/js/closure_library"],
    visibility = ["//visibility:public"],
)
