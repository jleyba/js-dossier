genrule(
  name = "readme",
  outs = ["README.md"],
  tools = ["//src/java/com/github/jsdossier:GenerateReadme"],
  executable = 0,
  cmd = "$(location //src/java/com/github/jsdossier:GenerateReadme) $@",
)

genrule(
  name = "release",
  srcs = [
      "LICENSE",
      "CHANGES.md",
      "package.json",
      ":readme",
      "//src/java/com/github/jsdossier:dossier_deploy.jar",
  ],
  outs = [
      "js-dossier.tar.gz",
  ],
  cmd = 'readonly GENFILE="$$(pwd)/$@"; ' +
        'readonly OUT="$$(mktemp -d $${TMPDIR:-/tmp}/dossier.XXXXXX)/js-dossier"; ' +
        'mkdir -p $$OUT && ' +
        'cp $(location :readme) $$OUT/ && ' +
        'cp $(location package.json) $$OUT/ && ' +
        'cp $(location //:LICENSE) $$OUT/ && ' +
        'cp $(location //:CHANGES.md) $$OUT/ && ' +
        'cp $(location //src/java/com/github/jsdossier:dossier_deploy.jar) $$OUT/dossier.jar && ' +
        'chmod a-x $$OUT/* && ' +
        'cd $$(dirname $$OUT) && ' +
        'tar -zcvf $$GENFILE js-dossier/ && ' +
        'cd $$(dirname $$GENFILE) && rm -rf $$OUT/',
)
