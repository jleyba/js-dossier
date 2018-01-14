load("//build_tools:javascript.bzl", "js_proto_library")
load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")

licenses(["notice"])

proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "java_proto",
    deps = [":proto"],
    visibility = ["//visibility:public"],
)

js_proto_library(
    name = "js_proto",
    proto = ":proto",
)

closure_js_library(
    name = "jslib",
    srcs = [":js_proto"],
    suppress = ["JSC_IMPLICITLY_NULLABLE_JSDOC"],
    visibility = ["//visibility:public"],
    deps = ["@dossier_jspb_library//:jspb"],
)
