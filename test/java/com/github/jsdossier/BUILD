LIB_SRCS = [
    "CompilerUtil.java",
    "GuiceRule.java",
    "soy/TestProto.java",
]

java_library(
    name = "lib",
    srcs = LIB_SRCS,
    deps = [
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:junit",
        "//lib/maven:protobuf",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/proto",
    ],
    testonly = 1,
)

java_test(
    name = "AllTests",
    srcs = glob(["**/*.java"], exclude = LIB_SRCS + glob(["resources/*"])),
    resources = glob(["resources/**"]),
    deps = [
        ":lib",
        "//lib/maven:closure_compiler",
        "//lib/maven:closure_templates",
        "//lib/maven:gson",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jimfs",
        "//lib/maven:jsoup",
        "//lib/maven:jsr305",
        "//lib/maven:junit",
        "//lib/maven:mockito",
        "//lib/maven:protobuf",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/proto",
        "//src/java/com/github/jsdossier/soy",
    ],
)