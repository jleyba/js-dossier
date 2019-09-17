java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["*.java"]),
    visibility = [
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
    deps = [
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/soy",
        "//third_party/java/auto:value",
        "//third_party/java/closure_compiler",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_jimfs_jimfs",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)
