load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")

licenses(["notice"])  # Apache 2

exports_files(["LICENSE"])

closure_js_library(
    name = "closure_library",
    srcs = ["@closure_library//:js_library_files"],
    suppress = [
        "JSC_DUPLICATE_ENUM_VALUE",
        "JSC_EXTRA_REQUIRE_WARNING",
        "JSC_ILLEGAL_PROTOTYPE_MEMBER",
        "JSC_INVALID_SUPPRESS",
        "JSC_MISSING_JSDOC",
        "JSC_MISSING_RETURN_JSDOC",
        "JSC_MISSING_SEMICOLON",
        "JSC_MUST_BE_PRIVATE",
        "JSC_PROVIDES_NOT_SORTED",
    ],
    visibility = ["//visibility:public"],
)
