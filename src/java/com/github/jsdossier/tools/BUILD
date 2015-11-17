java_library(
    name = "tools",
    srcs = glob(["*.java"]),
    deps = [
        "//lib/maven:args4j",
        "//lib/maven:guava",
        "//lib/maven:closure_compiler",
    ],
)

java_binary(
    name = "WriteDeps",
    main_class = "com.github.jsdossier.tools.WriteDeps",
    runtime_deps = [":tools"],
)

java_binary(
    name = "Compile",
    main_class = "com.github.jsdossier.tools.Compile",
    runtime_deps = [":tools"],
    visibility = ["//visibility:public"],
)