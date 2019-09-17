load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")
load("//build_tools:javascript.bzl", "js_proto_library")

proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    deps = ["//third_party/webutil/html/types/proto"],
)

js_proto_library(
    name = "js_proto",
    deps = [":proto"],
)

closure_js_library(
    name = "jslib",
    srcs = [":js_proto"],
    suppress = [
        "JSC_IMPLICITLY_NULLABLE_JSDOC",
        "JSC_PROTOTYPAL_HIDDEN_SUPERCLASS_PROPERTY",
    ],
    visibility = ["//src/js:__pkg__"],
    deps = [
        "@io_bazel_rules_closure//closure/protobuf:jspb",
        "@io_bazel_rules_closure//closure/templates:soy_html_proto",
    ],
)

java_proto_library(
    name = "java_proto_plus_deps",
    deps = [":proto"],
)

# The java_proto_library in "java_proto_plus_deps" outputs
#  bazel-bin/third_party/webutil/html/types/proto/libproto-speed.jar
#  bazel-bin/src/proto/libproto-speed.jar
#  bazel-bin/src/proto/proto-speed-src.jar
# But the first one we don't want because it redefines classes from
# @maven//:com_google_common_html_types_types
# and that will cause Java runtime sealing violations when the classloader finds both.
# So we make labels for the .jar and -src.jar we want
genrule(
    name = "cp",
    srcs = ["java_proto_plus_deps"],
    outs = ["libproto.jar", "proto-src.jar"],
    cmd = "cp $(BINDIR)/src/proto/libproto-speed.jar $(location libproto.jar);" +
        "cp $(BINDIR)/src/proto/proto-speed-src.jar $(location proto-src.jar)",
)

# Re-import the two jar files from the genrule so it can appear in java_library#deps
java_import(
    name = "java_proto",
    jars = ["libproto.jar"],
    srcjar = "proto-src.jar",
    visibility = [
        "//src/java/com/github/jsdossier:__subpackages__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
)
