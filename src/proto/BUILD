load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")
load("//build_tools:javascript.bzl", "js_proto_library")

proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    deps = ["//third_party/webutil/html/types/proto"],
)

js_proto_library(
    name = "js_proto",
    proto = ":proto",
)

closure_js_library(
    name = "jslib",
    srcs = [":js_proto"],
    suppress = ["JSC_IMPLICITLY_NULLABLE_JSDOC"],
    visibility = ["//src/js:__pkg__"],
    deps = [
        "@dossier//third_party/webutil/html/types/proto:jslib",
        "@dossier_jspb_library//:jspb",
    ],
)

java_proto_library(
    name = "java_proto",
    deps = [":proto"],
    visibility = [
        "//src/java/com/github/jsdossier:__subpackages__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)
