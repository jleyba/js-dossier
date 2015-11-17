load("/build_tools/js_library", "js_library")

licenses(["notice"])  # Apache 2

exports_files(["LICENSE"])

filegroup(
    name = "closure_library",
    srcs = glob(
        [
            "closure/goog/**/*.js",
            "third_party/closure/goog/**/*.js",
        ],
        exclude = [
            "closure/goog/**/*_test.js",
            "closure/goog/demos/**/*.js",
            "third_party/closure/goog/**/*_test.js",
        ],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "base",
    srcs = ["closure/goog/base.js"],
    visibility = ["//visibility:public"],
)