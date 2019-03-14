package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "guice",
    exports = ["@com_google_inject_guice//jar"],
    runtime_deps = [
        "//third_party/java/aopalliance",
        "//third_party/java/jsr330_inject",
    ],
)

java_library(
    name = "assistedinject",
    exports = ["@com_google_inject_extensions_guice_assistedinject//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "multibindings",
    exports = ["@com_google_inject_extensions_guice_multibindings//jar"],
    runtime_deps = [":guice"],
)
