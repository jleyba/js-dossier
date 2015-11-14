# Description:
#   This BUILD file defines java_library's for maven jars with the required
#   runtime dependencies.

package(default_visibility = ["//visibility:public"])

java_library(
    name = "args4j",
    exports = ["@args4j//jar"],
)

java_library(
    name = "auto_common",
    exports = ["@auto_common//jar"],
    runtime_deps = [
        ":inject",
        ":guava"
    ],
)

java_library(
    name = "auto_factory",
    exports = ["@auto_factory//jar"],
    exported_plugins = [":auto_factory_plugin"],
)

java_plugin(
    name = "auto_factory_plugin",
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    deps = [
        ":auto_common",
        ":guava",
        "@auto_factory//jar",
        "@javawriter//jar",
    ],
    visibility = ["//visibility:private"],
)

java_library(
    name = "auto_value",
    exports = ["@auto_value//jar"],
    exported_plugins = [":auto_value_plugin"],
)

java_plugin(
    name = "auto_value_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = ["@auto_value//jar"],
    visibility = ["//visibility:private"],
)

java_library(
    name = "closure_compiler",
    # exports = ["@closure_compiler//jar"],
    exports = ["//third_party/java/closure_compiler"],
    runtime_deps = [
        # "@closure_compiler_externs//jar",
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
    exports = ["@closure_templates//jar"],
    runtime_deps = [
        "@icu4j//jar",
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
        "@commonmark//jar",
        "@commonmark_gfm_tables//jar",
    ],
)

java_library(
    name = "gson",
    exports = ["@gson//jar"],
)

java_library(
    name = "guava",
    exports = ["@guava//jar"],
)

java_library(
    name = "guice",
    exports = ["@guice//jar"],
    runtime_deps = [
        "@aopalliance//jar",
        ":inject",
    ],
)

java_library(
    name = "guice_assistedinject",
    exports = ["@guice_assistedinject//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "guice_multibindings",
    exports = ["@guice_multibindings//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "inject",
    exports = ["@inject//jar"],
)

java_library(
    name = "jimfs",
    exports = ["@jimfs//jar"],
    runtime_deps = [
        "@icu4j//jar",
        ":guava"
    ],
    testonly = 1,
)

java_library(
    name = "joda_time",
    exports = ["@joda_time//jar"],
)

java_library(
    name = "jsoup",
    exports = ["@jsoup//jar"],
    runtime_deps = [":junit"],
    testonly = 1,
)

java_library(
    name = "jsr305",
    exports = ["@jsr305//jar"],
)

java_library(
    name = "junit",
    exports = ["@junit//jar"],
    runtime_deps = ["@hamcrest_core//jar"],
    testonly = 1,
)

java_library(
    name = "mockito",
    exports = ["@mockito//jar"],
    testonly = 1,
)

java_library(
    name = "owasp_html_sanitizer",
    exports = ["@owasp_html_sanitizer//jar"],
    runtime_deps = [
        ":guava",
        ":jsr305",
    ],
)

java_library(
    name = "protobuf",
    exports = ["@protobuf//jar"],
)

java_library(
    name = "truth",
    exports = ["@truth//jar"],
    runtime_deps = [
        ":guava",
        ":jsr305",
        ":junit",
    ],
    testonly = 1,
)
