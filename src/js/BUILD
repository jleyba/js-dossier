load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library")
load("//build_tools:closure.bzl", "closure_js_binary_set")

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
    name = "soy",
    srcs = [
        "soyplugins.js",
        ":gen_soy_js",
    ],
    suppress = [
        "JSC_IMPLICITLY_NULLABLE_JSDOC",
        "JSC_UNKNOWN_EXPR_TYPE",
        "JSC_UNUSED_LOCAL_ASSIGNMENT",
    ],
    visibility = ["//visibility:private"],
    deps = [
        "//src/proto:jslib",
        "@dossier_closure_library//:closure",
        "@dossier_closure_templates_library//:soy",
    ],
)

closure_js_library(
    name = "app_lib",
    srcs = [
        "app.js",
        "heap.js",
        "main.js",
        "nav.js",
        "page.js",
        "search.js",
        "xhr.js",
    ],
    visibility = ["//visibility:private"],
    deps = [
        ":soy",
        "//src/proto:jslib",
        "@dossier_closure_library//:closure",
        "@dossier_closure_templates_library//:soy",
        "@dossier_jspb_library//:jspb",
    ],
)

closure_js_binary_set(
    name = "dossier",
    defs = [
        "--jscomp_error=accessControls",
        "--jscomp_error=ambiguousFunctionDecl",
        "--jscomp_error=checkRegExp",
        "--jscomp_error=checkTypes",
        "--jscomp_error=checkVars",
        "--jscomp_error=constantProperty",
        # Can't seem to suppress this in @dossier_closure_library//:closure
        "--jscomp_warning=deprecated",
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
        # closure_js_binary always sets some defines that we don't actually use the source for and
        # the errors cannot be suppressed since the define is on the binary, not library.
        "--jscomp_warning=unknownDefines",
        "--jscomp_error=uselessCode",
        "--jscomp_error=visibility",
        "--rewrite_polyfills=False",
    ],
    dependency_mode = "STRICT",
    entry_points = ["goog:dossier.main"],
    language = "ECMASCRIPT5_STRICT",
    output_wrapper = "(function(){%output%;}).call(this);",
    visibility = ["//src/java/com/github/jsdossier:__pkg__"],
    deps = [":app_lib"],
)

closure_js_library(
    name = "serviceworker_lib",
    srcs = ["serviceworker/main.js"],
    visibility = ["//visibility:private"],
)

closure_js_binary_set(
    name = "serviceworker",
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
        # closure_js_binary always sets some defines that we don't actually use the source for and
        # the errors cannot be suppressed since the define is on the binary, not library.
        "--jscomp_warning=unknownDefines",
        "--jscomp_error=uselessCode",
        "--jscomp_error=visibility",
        "--rewrite_polyfills=False",
    ],
    dependency_mode = "STRICT",
    entry_points = ["goog:dossier.serviceworker.main"],
    language = "ECMASCRIPT5_STRICT",
    visibility = ["//src/java/com/github/jsdossier:__pkg__"],
    deps = [":serviceworker_lib"],
)
