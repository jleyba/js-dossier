load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_proto_library")
load("//build_tools:proto.bzl", "java_proto_library", "js_proto_library", "proto_library")

proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    deps = ["//third_party/webutil/html/types/proto"],
)

js_proto_library(
    name = "js_proto",
    visibility = ["//src/js:__pkg__"],
    deps = [":proto"],
)

java_proto_library(
    name = "java_proto",
    java_deps = ["//third_party/webutil/html/types/proto:java_proto"],
    proto_deps = [":proto"],
    visibility = [
        "//src/java/com/github/jsdossier:__subpackages__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)
