load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary")
load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")

closure_js_library(
    name = "all_js",
    srcs = [
        "app.js",
        "main.js",
        "nav.js",
        "page.js",
        "search.js",
    ],
    externs = ["externs.js"],
    language = "ECMASCRIPT6_STRICT",
    deps = [
        "//src/java/com/github/jsdossier/soy:js",
        "//third_party/js/closure_library",
        "//third_party/js/soy",
    ],
)

closure_js_binary(
    name = "dossier",
    deps = [":all_js"],
    language = "ECMASCRIPT6_STRICT",
    compilation_level = "ADVANCED",
    dependency_mode = "STRICT",
    entry_points = ["goog:dossier.main"],
    defs = [
        # Setting pedantic=1 on closure_js_binary triggers --jscomp_error=*
        # This includes an undocumented error group that complains about
        # missing require statements in goog.module code. These errors are
        # a) incorrect, b) cannot be suppressed since we do not know the
        # reporting error group. So instead of jscomp_error=*, we explicitly
        # enable all of the documented error groups.

        "--jscomp_error=accessControls",
        "--jscomp_error=ambiguousFunctionDecl",
        "--jscomp_error=checkEventfulObjectDisposal",
        "--jscomp_error=checkRegExp",
        "--jscomp_error=checkTypes",
        "--jscomp_error=checkVars",
        "--jscomp_error=conformanceViolations",
        "--jscomp_error=const",
        "--jscomp_error=constantProperty",
        "--jscomp_error=deprecated",
        "--jscomp_error=deprecatedAnnotations",
        "--jscomp_error=duplicateMessage",
        "--jscomp_error=es3",
        "--jscomp_error=es5Strict",
        "--jscomp_error=externsValidation",
        "--jscomp_error=fileoverviewTags",
        "--jscomp_error=globalThis",
        "--jscomp_error=internetExplorerChecks",
        "--jscomp_error=invalidCasts",
        "--jscomp_error=misplacedTypeAnnotation",
        "--jscomp_error=missingGetCssName",
        "--jscomp_error=missingProperties",
        "--jscomp_error=missingProvide",
        "--jscomp_error=missingRequire",
        "--jscomp_error=missingReturn",
        "--jscomp_error=msgDescriptions",
        "--jscomp_error=newCheckTypes",
        "--jscomp_error=nonStandardJsDocs",
        "--jscomp_error=suspiciousCode",
        "--jscomp_error=strictModuleDepCheck",
        "--jscomp_error=typeInvalidation",
        "--jscomp_error=typeInvalidation",
        "--jscomp_error=undefinedNames",
        "--jscomp_error=undefinedVars",
        "--jscomp_error=unknownDefines",
        "--jscomp_error=unusedLocalVariables",
        "--jscomp_error=unusedPrivateMembers",
        "--jscomp_error=uselessCode",
        "--jscomp_error=useOfGoogBase",
        "--jscomp_error=underscore",
        "--jscomp_error=visibility",

        # This is the only error group we disable, because arrow function
        # expressions (especially with Array.forEach) trigger the error.
        "--jscomp_off=reportUnknownTypes",

        "--language_out=ECMASCRIPT5_STRICT",
    ],
    output_wrapper = "(function(){%output%;}).call(this);",
    visibility = ["//src/java/com/github/jsdossier:__pkg__"],
)
