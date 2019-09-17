workspace(name = "dossier")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "2.7"

RULES_JVM_EXTERNAL_SHA = "f04b1466a00a2845106801e0c5cec96841f49ea4e7d1df88dc8e4bf31523df74"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

http_archive(
    name = "io_bazel_rules_closure",
    sha256 = "f2badc609a80a234bb51d1855281dd46cac90eadc57545880a3b5c38be0960e7",
    strip_prefix = "rules_closure-b2a6fb762a2a655d9970d88a9218b7a1cf098ffa",
    urls = [
        "https://github.com/bazelbuild/rules_closure/archive/b2a6fb762a2a655d9970d88a9218b7a1cf098ffa.tar.gz",  # 2019-08-05
    ],
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_repositories")
load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "args4j:args4j:jar:2.0.26",
        "com.atlassian.commonmark:commonmark:0.12.1",
        "com.google.auto.factory:auto-factory:1.0-beta6",
        "com.google.auto.service:auto-service:1.0-rc4",
        "com.google.auto.value:auto-value:1.6.3",
        "com.google.auto.value:auto-value-annotations:1.6.3",
        "com.google.code.findbugs:jsr305:1.3.9",
        "com.google.guava:guava:27.0.1-jre",
        "com.google.inject:guice:4.1.0",
        "com.google.javascript:closure-compiler-externs:v20190819",
        "com.google.jimfs:jimfs:1.0",
        "com.google.template:soy:2019-08-22",
        "com.google.truth:truth:0.42",
        "com.google.truth.extensions:truth-proto-extension:0.42",
        "com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:r239",
        "org.jsoup:jsoup:1.8.3",
    ],
    repositories = [
        "https://jcenter.bintray.com/",
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

maven_jar(
    name = "com_google_javascript_closure_compiler_unshaded",
    artifact = "com.google.javascript:closure-compiler-unshaded:v20190819",
    sha1 = "49ffac557a908252a37e8806f5897a274dbbc198",
)

closure_repositories(
    omit_com_google_template_soy = True,
)
