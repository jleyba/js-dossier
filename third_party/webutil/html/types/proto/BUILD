load("//build_tools:proto.bzl", "java_proto_library", "js_proto_library", "proto_library")

licenses(["notice"])

proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "java_proto",
    proto_deps = [":proto"],
    visibility = ["//visibility:public"],
)

js_proto_library(
    name = "js_proto",
    visibility = ["//visibility:public"],
    deps = [":proto"],
)
