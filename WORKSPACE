workspace(name = "dossier")

http_archive(
    name = "io_bazel_rules_closure",
    strip_prefix = "rules_closure-0.4.2",
    sha256 = "25f5399f18d8bf9ce435f85c6bbf671ec4820bc4396b3022cc5dc4bc66303609",
    url = "http://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_closure/archive/0.4.2.tar.gz",
)

http_archive(
  name = "com_google_protobuf",
  strip_prefix = "protobuf-3.5.0",
  sha256 = "0cc6607e2daa675101e9b7398a436f09167dffb8ca0489b0307ff7260498c13c",
  urls = [
      "https://mirror.bazel.build/github.com/google/protobuf/archive/v3.5.0.tar.gz",
      "https://github.com/google/protobuf/archive/v3.5.0.tar.gz",
  ],
)

new_http_archive(
  name = "dossier_closure_library",
  urls = [
      "https://mirror.bazel.build/github.com/google/closure-library/archive/v20171203.tar.gz",
      "https://github.com/google/closure-library/archive/v20171203.tar.gz",
  ],
  sha256 = "5320f10c53a7dc47fbb863a2d7f9344245889afe9fd4e8ff5e44bd89aabcefc7",
  strip_prefix = "closure-library-20171203",
  build_file = "third_party/BUILD.closure_library",
)

new_http_archive(
  name = "dossier_closure_templates_library",
  urls = [
      "https://mirror.bazel.build/github.com/google/closure-templates/archive/release-2017-08-08.tar.gz",
      "https://github.com/google/closure-templates/archive/release-2017-08-08.tar.gz",
  ],
  sha256 = "06c12a8ddb5206deac1a9d323afbf4d6bca1b9ca5ed3ca1dca76bb96fb503e46",
  strip_prefix = "closure-templates-release-2017-08-08",
  build_file = "third_party/BUILD.closure_templates_library",
)


new_http_archive(
  name = "dossier_jspb_library",
  urls = [
      "https://mirror.bazel.build/github.com/google/protobuf/archive/v3.5.0.tar.gz",
      "https://github.com/google/protobuf/archive/v3.5.0.tar.gz",
  ],
  sha256 = "0cc6607e2daa675101e9b7398a436f09167dffb8ca0489b0307ff7260498c13c",
  strip_prefix = "protobuf-3.5.0/js",
  build_file = "third_party/BUILD.jspb_library",
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
    artifact = "com.google.auto:auto-common:0.8",
    sha1 = "c6f7af0e57b9d69d81b05434ef9f3c5610d498c4",
)

maven_jar(
    name = "dossier_auto_factory",
    artifact = "com.google.auto.factory:auto-factory:1.0-beta3",
    sha1 = "99b2ffe0e41abbd4cc42bf3836276e7174c4929d",
)

maven_jar(
    name = "dossier_auto_value",
    artifact = "com.google.auto.value:auto-value:1.4.1",
    sha1 = "8172ebbd7970188aff304c8a420b9f17168f6f48",
)

maven_jar(
    name = "dossier_closure_compiler",
    artifact = "com.google.javascript:closure-compiler-unshaded:v20180716",
    sha1 = "b870c25dab5e90a17b02fb0b0a3641c7963139ea",
)

maven_jar(
    name = "dossier_closure_compiler_externs",
    artifact = "com.google.javascript:closure-compiler-externs:v20180716",
    sha1 = "b0b20609e31d2ee56554ffd154b22d1533eeb209",
)

maven_jar(
    name = "dossier_closure_templates",
    artifact = "com.google.template:soy:2018-01-03",
    sha1 = "62089a55675f338bdfb41fba1b29fe610f654b4d",
)

maven_jar(
    name = "dossier_commonmark",
    artifact = "com.atlassian.commonmark:commonmark:0.5.1",
    sha1 = "b35ae2353871955674bbfa1a92394272b1dada45",
)

maven_jar(
    name = "dossier_gson",
    artifact = "com.google.code.gson:gson:2.6.2",
    sha1 = "f1bc476cc167b18e66c297df599b2377131a8947",
)

maven_jar(
    name = "dossier_guava",
    artifact = "com.google.guava:guava:22.0",
    sha1 = "3564ef3803de51fb0530a8377ec6100b33b0d073",
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
    name = "dossier_joda_time",
    artifact = "joda-time:joda-time:2.3",
    sha1 = "56498efd17752898cfcc3868c1b6211a07b12b8f",
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
    artifact = "com.google.common.html.types:types:1.0.5",
    sha1 = "cbf72feac4a1599add33222a876e24ab31a3f387",
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
