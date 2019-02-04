workspace(name = "dossier")

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "25f5399f18d8bf9ce435f85c6bbf671ec4820bc4396b3022cc5dc4bc66303609",
    strip_prefix = "rules_closure-0.4.2",
    url = "http://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_closure/archive/0.4.2.tar.gz",
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "0cc6607e2daa675101e9b7398a436f09167dffb8ca0489b0307ff7260498c13c",
    strip_prefix = "protobuf-3.5.0",
    urls = [
        "https://mirror.bazel.build/github.com/google/protobuf/archive/v3.5.0.tar.gz",
        "https://github.com/google/protobuf/archive/v3.5.0.tar.gz",
    ],
)

new_http_archive(
    name = "dossier_closure_library",
    build_file = "third_party/BUILD.closure_library",
    sha256 = "5320f10c53a7dc47fbb863a2d7f9344245889afe9fd4e8ff5e44bd89aabcefc7",
    strip_prefix = "closure-library-20171203",
    urls = [
        "https://mirror.bazel.build/github.com/google/closure-library/archive/v20171203.tar.gz",
        "https://github.com/google/closure-library/archive/v20171203.tar.gz",
    ],
)

new_http_archive(
    name = "dossier_closure_templates_library",
    build_file = "third_party/BUILD.closure_templates_library",
    sha256 = "06c12a8ddb5206deac1a9d323afbf4d6bca1b9ca5ed3ca1dca76bb96fb503e46",
    strip_prefix = "closure-templates-release-2017-08-08",
    urls = [
        "https://mirror.bazel.build/github.com/google/closure-templates/archive/release-2017-08-08.tar.gz",
        "https://github.com/google/closure-templates/archive/release-2017-08-08.tar.gz",
    ],
)

new_http_archive(
    name = "dossier_jspb_library",
    build_file = "third_party/BUILD.jspb_library",
    sha256 = "0cc6607e2daa675101e9b7398a436f09167dffb8ca0489b0307ff7260498c13c",
    strip_prefix = "protobuf-3.5.0/js",
    urls = [
        "https://mirror.bazel.build/github.com/google/protobuf/archive/v3.5.0.tar.gz",
        "https://github.com/google/protobuf/archive/v3.5.0.tar.gz",
    ],
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")

closure_repositories()

maven_jar(
    name = "dossier_aopalliance",
    artifact = "aopalliance:aopalliance:1.0",
    sha1 = "0235ba8b489512805ac13a8f9ea77a1ca5ebe3e8",
)

maven_jar(
    name = "dossier_args4j",
    artifact = "args4j:args4j:2.0.26",
    sha1 = "01ebb18ebb3b379a74207d5af4ea7c8338ebd78b",
)

maven_jar(
    name = "dossier_auto_common",
    artifact = "com.google.auto:auto-common:0.10",
    sha1 = "c8f153ebe04a17183480ab4016098055fb474364",
)

maven_jar(
    name = "dossier_auto_factory",
    artifact = "com.google.auto.factory:auto-factory:1.0-beta3",
    sha1 = "99b2ffe0e41abbd4cc42bf3836276e7174c4929d",
)

maven_jar(
    name = "dossier_auto_value",
    artifact = "com.google.auto.value:auto-value:1.6.3",
    sha1 = "8edb6675b9c09ffdcc19937428e7ef1e3d066e12",
)

maven_jar(
    name = "com_google_auto_value_auto_value_annotations",
    artifact = "com.google.auto.value:auto-value-annotations:1.6.3",
    sha1 = "b88c1bb7f149f6d2cc03898359283e57b08f39cc",
)

maven_jar(
    name = "dossier_closure_compiler",
    artifact = "com.google.javascript:closure-compiler-unshaded:v20190121",
    sha1 = "8b2b86d73d102b28fd14b6311704e8b9294d370a",
)

maven_jar(
    name = "dossier_closure_compiler_externs",
    artifact = "com.google.javascript:closure-compiler-externs:v20190121",
    sha1 = "1558f377bed236fa9ffabe72aec4809d1434645f",
)

maven_jar(
    name = "dossier_closure_templates",
    artifact = "com.google.template:soy:2018-03-14",
    sha1 = "76a1322705ba5a6d6329ee26e7387417725ce4b3",
)

maven_jar(
    name = "dossier_commonmark",
    artifact = "com.atlassian.commonmark:commonmark:0.12.1",
    sha1 = "9e0657f89ab2731f8a7235d926fdae7febf104cb",
)

maven_jar(
    name = "dossier_gson",
    artifact = "com.google.code.gson:gson:2.8.5",
    sha1 = "f645ed69d595b24d4cf8b3fbb64cc505bede8829",
)

maven_jar(
    name = "dossier_guava",
    artifact = "com.google.guava:guava:27.0.1-jre",
    sha1 = "bd41a290787b5301e63929676d792c507bbc00ae",
)

maven_jar(
    name = "dossier_com_google_guava_failureaccess",
    artifact = "com.google.guava:failureaccess:jar:1.0.1",
    sha1 = "1dcf1de382a0bf95a3d8b0849546c88bac1292c9",
)

maven_jar(
    name = "dossier_guice",
    artifact = "com.google.inject:guice:4.1.0",
    sha1 = "eeb69005da379a10071aa4948c48d89250febb07",
)

maven_jar(
    name = "dossier_guice_assistedinject",
    artifact = "com.google.inject.extensions:guice-assistedinject:4.1.0",
    sha1 = "af799dd7e23e6fe8c988da12314582072b07edcb",
)

maven_jar(
    name = "dossier_guice_multibindings",
    artifact = "com.google.inject.extensions:guice-multibindings:4.1.0",
    sha1 = "3b27257997ac51b0f8d19676f1ea170427e86d51",
)

maven_jar(
    name = "dossier_icu4j",
    artifact = "com.ibm.icu:icu4j:51.1",
    sha1 = "8ce396c4aed83c0c3de9158dc72c834fd283d5a4",
)

maven_jar(
    name = "dossier_hamcrest_core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

maven_jar(
    name = "dossier_inject",
    artifact = "javax.inject:javax.inject:1",
    sha1 = "6975da39a7040257bd51d21a231b76c915872d38",
)

maven_jar(
    name = "dossier_javawriter",
    artifact = "com.squareup:javawriter:2.5.1",
    sha1 = "54c87b3d91238e5b58e1a436d4916eee680ec959",
)

maven_jar(
    name = "dossier_jimfs",
    artifact = "com.google.jimfs:jimfs:1.0",
    sha1 = "edd65a2b792755f58f11134e76485a928aab4c97",
)

maven_jar(
    name = "dossier_jsoup",
    artifact = "org.jsoup:jsoup:1.8.3",
    sha1 = "65fd012581ded67bc20945d85c32b4598c3a9cf1",
)

maven_jar(
    name = "dossier_jsr305",
    artifact = "com.google.code.findbugs:jsr305:1.3.9",
    sha1 = "40719ea6961c0cb6afaeb6a921eaa1f6afd4cfdf",
)

maven_jar(
    name = "dossier_junit",
    artifact = "junit:junit:4.12",
    sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
)

maven_jar(
    name = "dossier_mockito",
    artifact = "org.mockito:mockito-all:1.10.19",
    sha1 = "539df70269cc254a58cccc5d8e43286b4a73bf30",
)

maven_jar(
    name = "dossier_owasp_html_sanitizer",
    artifact = "com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:r239",
    sha1 = "ea8dd89a9e8fcf90c1b666ac0585e7769224da5e",
)

maven_jar(
    name = "dossier_protobuf",
    artifact = "com.google.protobuf:protobuf-java:3.5.0",
    sha1 = "200fb936907fbab5e521d148026f6033d4aa539e",
)

maven_jar(
    name = "dossier_safe_types",
    artifact = "com.google.common.html.types:types:1.0.8",
    sha1 = "9e9cf7bc4b2a60efeb5f5581fe46d17c068e0777",
)

maven_jar(
    name = "dossier_truth",
    artifact = "com.google.truth:truth:0.32",
    sha1 = "e996fb4b41dad04365112786796c945f909cfdf7",
)

maven_jar(
    name = "dossier_truth_proto",
    artifact = "com.google.truth.extensions:truth-proto-extension:0.32",
    sha1 = "b3d8f4a713af63029511917bc8e2775d6256df18",
)

maven_jar(
    name = "dossier_truth_liteproto",
    artifact = "com.google.truth.extensions:truth-liteproto-extension:0.32",
    sha1 = "f8bd960b1c5a8ff8a2d621fe20fff488beafd0ee",
)
