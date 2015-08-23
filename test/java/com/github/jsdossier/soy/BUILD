load("/build_tools/java_test_suite", "java_test_suite")

java_library(
    name = "lib",
    srcs = ["TestProto.java"],
    deps = [
        "//lib/maven:protobuf",
        "//src/java/com/github/jsdossier/proto",
    ],
)

java_test_suite(
    name = "AllTests",
    srcs = glob(["*.java"]),
    deps = [
        ":lib",
        "//lib/maven:closure_templates",
        "//lib/maven:guava",
        "//lib/maven:jsoup",
        "//lib/maven:jsr305",
        "//lib/maven:junit",
        "//lib/maven:mockito",
        "//lib/maven:protobuf",
        "//lib/maven:truth",
        "//src/java/com/github/jsdossier",
        "//src/java/com/github/jsdossier/proto",
        "//src/java/com/github/jsdossier/soy",
        "//test/java/com/github/jsdossier/testing",
    ],
)