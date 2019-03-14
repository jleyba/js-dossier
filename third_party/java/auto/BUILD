package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_library(
    name = "common",
    visibility = ["//visibility:private"],
    exports = ["@com_google_auto_auto_common//jar"],
    runtime_deps = [
        "//third_party/java/guava",
        "//third_party/java/jsr330_inject",
    ],
)

java_plugin(
    name = "value_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    visibility = ["//visibility:private"],
    deps = ["@com_google_auto_value_auto_value//jar"],
)

java_library(
    name = "value",
    exported_plugins = [":value_plugin"],
    exports = ["@com_google_auto_value_auto_value_annotations//jar"],
)

java_plugin(
    name = "service_plugin",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    visibility = ["//visibility:private"],
    deps = [
        ":common",
        "//third_party/java/guava",
        "@com_google_auto_service_auto_service//jar",
    ],
)

java_library(
    name = "service",
    exported_plugins = [":service_plugin"],
    exports = ["@com_google_auto_service_auto_service//jar"],
)

java_plugin(
    name = "factory_plugin",
    generates_api = 1,
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    visibility = ["//visibility:private"],
    deps = [
        ":common",
        ":service",
        "//third_party/java/google_java_format",
        "//third_party/java/guava",
        "//third_party/java/javapoet",
        "@com_google_auto_factory_auto_factory//jar",
    ],
)

java_library(
    name = "factory",
    exported_plugins = [":factory_plugin"],
    exports = ["@com_google_auto_factory_auto_factory//jar"],
)
