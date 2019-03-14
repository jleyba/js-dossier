# Description:
#   This BUILD file defines java_library's for maven jars with the required
#   runtime dependencies.

package(default_visibility = ["//visibility:public"])

java_library(
    name = "closure_templates",
    exports = ["@dossier_closure_templates//jar"],
    runtime_deps = [
        "//third_party/java/args4j",
        "//third_party/java/guava",
        "//third_party/java/guice",
        "//third_party/java/guice:assistedinject",
        "//third_party/java/guice:multibindings",
        "//third_party/java/icu4j",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "@com_google_common_html_types_types//jar",
    ],
)

java_library(
    name = "safe_types",
    exports = ["@com_google_common_html_types_types//jar"],
)
