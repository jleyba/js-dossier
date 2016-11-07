load("//build_tools:proto.bzl", "java_proto_library", "proto_library")

java_proto_library(
    name = "java_proto",
    deps = [":lib"],
    visibility = [
        "//src/java/com/github/jsdossier:__subpackages__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)

proto_library(
    name = "lib",
    srcs = glob(["*.proto"]),
)