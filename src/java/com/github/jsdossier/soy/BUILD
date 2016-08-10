load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")
load("/build_tools/soy_js_library", "soy_js_library")


java_library(
    name = "soy",
    srcs = glob(["*.java"],
                exclude = [
                    "ProtoDescriptorsToJs.java",
                    "SoyJsCompiler.java"]),
    resources = glob(["resources/*"]),
    deps = [
        "//lib/maven:closure_templates",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:guice_multibindings",
        "//lib/maven:inject",
        "//lib/maven:jsr305",
        "//lib/maven:owasp_html_sanitizer",
        "//lib/maven:protobuf",
        "//src/java/com/github/jsdossier/proto",
    ],
    visibility = [
        "//src/java/com/github/jsdossier:__pkg__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)

genrule(
    name = "gen_proto_js",
    outs = ["proto.dossier.js"],
    tools = [":ProtoDescriptorsToJs"],
    cmd = "$(location :ProtoDescriptorsToJs) $@",
)

closure_js_library(
    name = "proto_js",
    srcs = [":gen_proto_js"],
    language = "ECMASCRIPT6_STRICT",
    deps = [
        "//third_party/js/closure_library",
        "//third_party/js/soy",
    ],
)

soy_js_library(
    name = "js",
    srcs = glob(["resources/*.soy"], exclude = ["resources/proto.soy"]),
    deps = [
        ":proto_js",
        "//third_party/js/closure_library",
        "//third_party/js/soy",
    ],
    visibility = [
        "//src/js:__pkg__",
    ],
)

java_binary(
    name = "SoyJsCompiler",
    srcs = ["SoyJsCompiler.java"],
    main_class = "com.github.jsdossier.soy.SoyJsCompiler",
    deps = [
        ":soy",
        "//lib/maven:args4j",
        "//lib/maven:closure_templates",
        "//lib/maven:guava",
        "//lib/maven:guice",
    ],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "ProtoDescriptorsToJs",
    srcs = ["ProtoDescriptorsToJs.java"],
    main_class = "com.github.jsdossier.soy.ProtoDescriptorsToJs",
    deps = [
        ":soy",
        "//lib/maven:closure_templates",
        "//lib/maven:inject",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:protobuf",
        "//src/java/com/github/jsdossier/proto",
    ],
)
