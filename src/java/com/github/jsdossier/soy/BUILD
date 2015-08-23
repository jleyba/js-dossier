java_library(
    name = "soy",
    srcs = glob(["*.java"]),
    resources = glob(["resources/*"]),
    deps = [
        "//lib/maven:closure_templates",
        "//lib/maven:guava",
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