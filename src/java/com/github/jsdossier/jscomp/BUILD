java_library(
    name = "jscomp",
    srcs = glob(["*.java"]),
    resources = glob(["resources/externs/node/*.js"]),
    visibility = [
        "//src/java/com/github/jsdossier:__subpackages__",
        "//test/java/com/github/jsdossier:__subpackages__",
    ],
    deps = [
        "//src/java/com/github/jsdossier/annotations",
        "//third_party/java/auto:factory",
        "//third_party/java/auto:value",
        "//third_party/java/closure_compiler",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_javax_inject",
    ],
)
