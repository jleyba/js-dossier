load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library")

genrule(
    name = "gen_soy_js",
    outs = [
        "dossier.soy.js",
        "nav.soy.js",
        "types.soy.js",
    ],
    cmd = "$(location //src/java/com/github/jsdossier/soy:GenerateSoyTemplates) $(@D)",
    tools = ["//src/java/com/github/jsdossier/soy:GenerateSoyTemplates"],
)

closure_js_library(
    name = "app_lib",
    srcs = glob(["*js"]) + [
        ":gen_soy_js",
        "//src/proto:js_proto",
        "//third_party/webutil/html/types/proto:js_proto",
    ],
    language = "ECMASCRIPT6_STRICT",
    visibility = ["//visibility:private"],
    deps = [
        "@io_bazel_rules_closure//closure/library",
        "@io_bazel_rules_closure//closure/protobuf:jspb",
        "@io_bazel_rules_closure//closure/templates:soy_jssrc",
    ],
)

closure_js_binary(
    name = "dossier",
    compilation_level = "ADVANCED",
    defs = [
        "--jscomp_error=accessControls",
        "--jscomp_error=ambiguousFunctionDecl",
        "--jscomp_error=checkRegExp",
        "--jscomp_error=checkTypes",
        "--jscomp_error=checkVars",
        "--jscomp_error=constantProperty",
        "--jscomp_error=deprecated",
        "--jscomp_error=duplicateMessage",
        "--jscomp_error=es5Strict",
        "--jscomp_error=externsValidation",
        "--jscomp_error=fileoverviewTags",
        "--jscomp_error=globalThis",
        "--jscomp_error=invalidCasts",
        "--jscomp_error=missingProperties",
        "--jscomp_error=nonStandardJsDocs",
        "--jscomp_error=strictModuleDepCheck",
        "--jscomp_error=typeInvalidation",
        "--jscomp_error=undefinedVars",
        "--jscomp_error=unknownDefines",
        "--jscomp_error=uselessCode",
        "--jscomp_error=visibility",
        "--rewrite_polyfills=False",
    ],
    dependency_mode = "STRICT",
    entry_points = ["goog:dossier.main"],
    language = "ECMASCRIPT5_STRICT",
    output_wrapper = "(function(){%output%;}).call(this);",
    pedantic = False,  # TODO: enable when es6 polyfills pass checks.
    visibility = ["//src/java/com/github/jsdossier:__pkg__"],
    deps = [":app_lib"],
)

closure_js_library(
    name = "serviceworker_lib",
    srcs = ["serviceworker/main.js"],
    language = "ECMASCRIPT6_STRICT",
    visibility = ["//visibility:private"],
    deps = ["@io_bazel_rules_closure//closure/library"],
)

closure_js_binary(
    name = "serviceworker",
    compilation_level = "ADVANCED",
    defs = [
        "--jscomp_error=accessControls",
        "--jscomp_error=ambiguousFunctionDecl",
        "--jscomp_error=checkRegExp",
        "--jscomp_error=checkTypes",
        "--jscomp_error=checkVars",
        "--jscomp_error=constantProperty",
        "--jscomp_error=deprecated",
        "--jscomp_error=duplicateMessage",
        "--jscomp_error=es5Strict",
        "--jscomp_error=externsValidation",
        "--jscomp_error=fileoverviewTags",
        "--jscomp_error=globalThis",
        "--jscomp_error=invalidCasts",
        "--jscomp_error=missingProperties",
        "--jscomp_error=nonStandardJsDocs",
        "--jscomp_error=strictModuleDepCheck",
        "--jscomp_error=typeInvalidation",
        "--jscomp_error=undefinedVars",
        "--jscomp_error=unknownDefines",
        "--jscomp_error=uselessCode",
        "--jscomp_error=visibility",
        "--rewrite_polyfills=False",
    ],
    dependency_mode = "STRICT",
    entry_points = ["goog:dossier.serviceworker.main"],
    language = "ECMASCRIPT5_STRICT",
    pedantic = False,  # TODO: enable when es6 polyfills pass checks.
    visibility = ["//src/java/com/github/jsdossier:__pkg__"],
    deps = [":serviceworker_lib"],
)
