http_file(
  name = "dossier_protoc_linux_x86_64",
  url = "http://bazel-mirror.storage.googleapis.com/github.com/google/protobuf/releases/download/v3.1.0/protoc-3.1.0-linux-x86_64.zip",
  sha256 = "7c98f9e8a3d77e49a072861b7a9b18ffb22c98e37d2a80650264661bfaad5b3a",
)

http_file(
  name = "dossier_protoc_macosx",
  url = "http://bazel-mirror.storage.googleapis.com/github.com/google/protobuf/releases/download/v3.1.0/protoc-3.1.0-osx-x86_64.zip",
  sha256 = "2cea7b1acb86671362f7aa554a21b907d18de70b15ad1f68e72ad2b50502920e",
)

http_archive(
    name = "io_bazel_rules_closure",
    strip_prefix = "rules_closure-0.4.1",
    sha256 = "ba5e2e10cdc4027702f96e9bdc536c6595decafa94847d08ae28c6cb48225124",
    url = "http://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_closure/archive/0.4.1.tar.gz",
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
    artifact = "com.google.auto:auto-common:0.5",
    sha1 = "27185563ca9551183fa5379807c3034c0012c8c4",
)

maven_jar(
    name = "dossier_auto_factory",
    artifact = "com.google.auto.factory:auto-factory:1.0-beta3",
    sha1 = "99b2ffe0e41abbd4cc42bf3836276e7174c4929d",
)

maven_jar(
    name = "dossier_auto_value",
    artifact = "com.google.auto.value:auto-value:1.1",
    sha1 = "f6951c141ea3e89c0f8b01da16834880a1ebf162",
)

maven_jar(
    name = "dossier_closure_compiler",
    artifact = "com.google.javascript:closure-compiler-unshaded:v20160911",
    sha1 = "96ac7a8c32377690555ac93310498bebea3e26ef",
)

maven_jar(
    name = "dossier_closure_compiler_externs",
    artifact = "com.google.javascript:closure-compiler-externs:v20160911",
    sha1 = "3186e2c9a2018bb49547947d2919b0e6998a1fe2",
)

maven_jar(
    name = "dossier_closure_templates",
    artifact = "com.google.template:soy:2016-08-25",
    sha1 = "bb2a8a8b08f0668abc80e5b25eaffca000cecf57",
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
    artifact = "com.google.guava:guava:20.0",
    sha1 = "89507701249388e1ed5ddcf8c41f4ce1be7831ef",
)

maven_jar(
    name = "dossier_guice",
    artifact = "com.google.inject:guice:3.0",
    sha1 = "9d84f15fe35e2c716a02979fb62f50a29f38aefa",
)

maven_jar(
    name = "dossier_guice_assistedinject",
    artifact = "com.google.inject.extensions:guice-assistedinject:3.0",
    sha1 = "544449ddb19f088dcde44f055d30a08835a954a7",
)

maven_jar(
    name = "dossier_guice_multibindings",
    artifact = "com.google.inject.extensions:guice-multibindings:3.0",
    sha1 = "5e670615a927571234df68a8b1fe1a16272be555",
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
    artifact = "com.google.protobuf:protobuf-java:3.1.0",
    sha1 = "e13484d9da178399d32d2d27ee21a77cfb4b7873",
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
