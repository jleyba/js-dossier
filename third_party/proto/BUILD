package(default_visibility = ["//visibility:public"])

licenses(["notice"])

genrule(
    name = "protoc_bin",
    srcs = select({
        ":darwin": ["@dossier_protoc_macosx//file"],
        "//conditions:default": ["@dossier_protoc_linux_x86_64//file"],
    }),
    outs = ["protoc"],
    cmd = " && ".join([
        "IN=$$(pwd)/$(SRCS)",
        "OUT=$$(pwd)/$@",
        "TMP=$$(mktemp -d $${TMPDIR:-/tmp}/genrule.XXXXXXXXXX)",
        "cd $$TMP",
        "unzip -q $$IN bin/protoc",
        "mv bin/protoc $$OUT",
        "rm -rf $$TMP",
    ]),
    executable = True,
)

config_setting(
    name = "darwin",
    values = {"cpu": "darwin"},
    visibility = ["//visibility:private"],
)
