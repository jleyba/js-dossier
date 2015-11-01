load("/build_tools/java_test_suite", "java_test_suite")

LIB_SRCS = [
    "AbstractTypeInspectorTest.java",
]

java_library(
    name = "lib",
    srcs = LIB_SRCS,
    deps = [
        "//lib/maven:closure_compiler",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jimfs",
        "//lib/maven:junit",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/proto",
        "//src/java/com/github/jsdossier/testing",
        "//test/java/com/github/jsdossier/testing",
    ],
)

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"], exclude = LIB_SRCS),
    resources = glob(["resources/**"]),
    deps = [
        ":lib",
        "//lib/maven:closure_compiler",
        "//lib/maven:gson",
        "//lib/maven:guava",
        "//lib/maven:guice",
        "//lib/maven:inject",
        "//lib/maven:jimfs",
        "//lib/maven:jsoup",
        "//lib/maven:junit",
        "//lib/maven:mockito",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/annotations",
        "//src/java/com/github/jsdossier/jscomp",
        "//src/java/com/github/jsdossier/proto",
        "//src/java/com/github/jsdossier/testing",
        "//test/java/com/github/jsdossier/testing",
    ],
)
