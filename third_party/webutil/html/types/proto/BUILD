licenses(["notice"])

genrule(
    name = "_proto",
    # From @io_bazel_rules_closure; used for consistency, but we still need
    # to transform it into a usable form for java.
    srcs = ["@com_google_common_html_types_html_proto//file"],
    outs = ["html.proto"],
    cmd = "cp $< $@",
)

proto_library(
    name = "proto",
    srcs = [":html.proto"],
    strip_import_prefix = "/third_party",
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "java_proto",
    visibility = ["//visibility:public"],
    deps = [":proto"],
)
