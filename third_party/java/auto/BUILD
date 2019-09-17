package(default_visibility = ["//visibility:public"])

licenses(["notice"])

java_plugin(
    name = "value_plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    visibility = ["//visibility:private"],
    deps = ["@maven//:com_google_auto_value_auto_value"],
)

java_library(
    name = "value",
    exported_plugins = [":value_plugin"],
    exports = ["@maven//:com_google_auto_value_auto_value_annotations"],
)

java_plugin(
    name = "service_plugin",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    visibility = ["//visibility:private"],
    deps = [
        "@maven//:com_google_auto_auto_common",
        "@maven//:com_google_auto_service_auto_service",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "service",
    exported_plugins = [":service_plugin"],
    exports = ["@maven//:com_google_auto_service_auto_service"],
)

java_plugin(
    name = "factory_plugin",
    generates_api = 1,
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    visibility = ["//visibility:private"],
    deps = [
        ":service",
        "@maven//:com_google_auto_auto_common",
        "@maven//:com_google_auto_factory_auto_factory",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "factory",
    exported_plugins = [":factory_plugin"],
    exports = ["@maven//:com_google_auto_factory_auto_factory"],
)
