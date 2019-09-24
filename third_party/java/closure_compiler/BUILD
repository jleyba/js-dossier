package(default_visibility = ["//visibility:public"])

licenses(["notice"])  # Apache 2

exports_files(["LICENSE"])

java_import(
    name = "closure_compiler_snapshot",
    jars = ["closure-compiler-unshaded-1.0-SNAPSHOT.jar"],
    srcjar = "closure-compiler-unshaded-1.0-SNAPSHOT-sources.jar",
    visibility = ["//visibility:private"],
)

java_library(
    name = "closure_compiler",
    exports = ["@com_google_javascript_closure_compiler_unshaded//jar"],
    # exports = [":closure_compiler_snapshot"],
    runtime_deps = [
        "@maven//:com_google_javascript_closure_compiler_externs",
    ],
)

java_binary(
    name = "ClosureCompiler",
    main_class = "com.google.javascript.jscomp.CommandLineRunner",
    runtime_deps = [":closure_compiler"],
)
