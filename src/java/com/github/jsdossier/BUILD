java_library(
    name = "jsdossier",
    srcs = glob(["*.java"]),
    resources = [
        "//src/js:dossier_bin",
    ] + glob(["resources/*"]),
    deps = [
        "//lib/maven:args4j",
        "//lib/maven:closure_compiler",
        "//lib/maven:commonmark",
        "//lib/maven:gson",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:joda_time",
        "//lib/maven:jsr305",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/proto",
        "//src/java/com/github/jsdossier/soy",
    ],
    visibility = [
        "//src/java/com/github/jsdossier/testing:__pkg__",
        "//test/java/com/github/jsdossier:__subpackages__"
    ],
)

java_binary(
    name = "dossier",
    runtime_deps = [":jsdossier"],
    main_class = "com.github.jsdossier.Main",
)

java_binary(
    name = "Config",
    runtime_deps = [":jsdossier"],
    main_class = "com.github.jsdossier.Config",
)