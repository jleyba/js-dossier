# Description:
#   This BUILD file defines java_library's for maven jars with the required
#   runtime dependencies.

package(default_visibility = ["//visibility:public"])

java_library(
    name = "args4j",
    exports = ["@dossier_args4j//jar"],
)

java_library(
    name = "auto_common",
    exports = ["@dossier_auto_common//jar"],
    runtime_deps = [
        ":inject",
        ":guava"
    ],
)

java_library(
    name = "auto_factory",
    exports = ["@dossier_auto_factory//jar"],
    exported_plugins = [":auto_factory_plugin"],
)

java_plugin(
    name = "auto_factory_plugin",
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    deps = [
        ":auto_common",
        ":guava",
        "@dossier_auto_factory//jar",
        "@dossier_javawriter//jar",
    ],
    visibility = ["//visibility:private"],
)

java_library(
    name = "auto_value",
    exports = ["@dossier_auto_value//jar"],
    exported_plugins = [":auto_value_plugin"],
)

java_plugin(
    name = "auto_value_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = ["@dossier_auto_value//jar"],
    visibility = ["//visibility:private"],
)

java_library(
    name = "closure_compiler",
    exports = ["@dossier_closure_compiler//jar"],
    # exports = ["//third_party/java/closure_compiler"],
    runtime_deps = [
        "@dossier_closure_compiler_externs//jar",
        ":args4j",
        ":gson",
        ":guava",
        ":jsr305",
        ":protobuf",
    ],
)

java_binary(
    name = "ClosureCompiler",
    main_class = "com.google.javascript.jscomp.CommandLineRunner",
    runtime_deps = [":closure_compiler"],
)

java_library(
    name = "closure_templates",
    exports = ["@dossier_closure_templates//jar"],
    runtime_deps = [
        "@dossier_icu4j//jar",
        ":args4j",
        ":guava",
        ":guice",
        ":guice_assistedinject",
        ":guice_multibindings",
        ":inject",
        ":jsr305",
    ],
)

java_library(
    name = "commonmark",
    exports = [
        "@dossier_commonmark//jar",
    ],
)

java_library(
    name = "gson",
    exports = ["@dossier_gson//jar"],
)

java_library(
    name = "guava",
    exports = ["@dossier_guava//jar"],
)

java_library(
    name = "guice",
    exports = ["@dossier_guice//jar"],
    runtime_deps = [
        "@dossier_aopalliance//jar",
        ":inject",
    ],
)

java_library(
    name = "guice_assistedinject",
    exports = ["@dossier_guice_assistedinject//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "guice_multibindings",
    exports = ["@dossier_guice_multibindings//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "inject",
    exports = ["@dossier_inject//jar"],
)

java_library(
    name = "jimfs",
    exports = ["@dossier_jimfs//jar"],
    runtime_deps = [
        "@dossier_icu4j//jar",
        ":guava"
    ],
    testonly = 1,
)

java_library(
    name = "joda_time",
    exports = ["@dossier_joda_time//jar"],
)

java_library(
    name = "jsoup",
    exports = ["@dossier_jsoup//jar"],
    runtime_deps = [":junit"],
    testonly = 1,
)

java_library(
    name = "jsr305",
    exports = ["@dossier_jsr305//jar"],
)

java_library(
    name = "junit",
    exports = ["@dossier_junit//jar"],
    runtime_deps = ["@dossier_hamcrest_core//jar"],
    testonly = 1,
)

java_library(
    name = "mockito",
    exports = ["@dossier_mockito//jar"],
    testonly = 1,
)

java_library(
    name = "owasp_html_sanitizer",
    exports = ["@dossier_owasp_html_sanitizer//jar"],
    runtime_deps = [
        ":guava",
        ":jsr305",
    ],
)

java_library(
    name = "protobuf",
    exports = ["@dossier_protobuf//jar"],
)

java_library(
    name = "truth",
    exports = ["@dossier_truth//jar"],
    runtime_deps = [
        ":guava",
        ":jsr305",
        ":junit",
    ],
    testonly = 1,
)
