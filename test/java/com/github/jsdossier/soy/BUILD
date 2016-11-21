load("//build_tools:java_test_suite.bzl", "java_test_suite")
load("//build_tools:proto.bzl", "java_proto_library", "proto_library")

proto_library(
    name = "proto",
    srcs = ["test_proto.proto"],
)

java_proto_library(
    name = "proto_lib",
    deps = [":proto"],
)

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"]),
    deps = [
        ":proto_lib",
        "//lib/maven:closure_templates",
        "//lib/maven:gson",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jsoup",
        "//lib/maven:jsr305",
        "//lib/maven:junit",
        "//lib/maven:mockito",
        "//lib/maven:protobuf",
        "//lib/maven:safe_types",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/soy",
        "//src/proto:java_proto",
        "//test/java/com/github/jsdossier/testing",
    ],
)
