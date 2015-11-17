_JS_FILE_TYPE = FileType([".js"])

def _js_binary_impl(ctx):
    externs = set(order="compile")
    srcs = set(order="compile")
    for dep in ctx.attr.deps:
        externs += dep.transitive_js_externs
        srcs += dep.transitive_js_srcs

    externs += _JS_FILE_TYPE.filter(ctx.files.externs)

    args = (ctx.attr.flags +
        ["--js_output_file=\"%s\"" % ctx.outputs.out.path] +
        ["--js=%s" % src.path for src in srcs] +
        ["--externs=$(pwd)/%s" % extern.path for extern in externs])

    ctx.action(
        inputs=list(srcs),
        outputs=[ctx.outputs.out],
        arguments=args,
        executable=ctx.executable.compiler)

    return struct(files=set([ctx.outputs.out]))


# TODO: figure out why this fails on Linux so we can replace the macro below.
_js_binary = rule(
    implementation=_js_binary_impl,
    attrs={
        "compiler": attr.label(
            default=Label("//lib/maven:ClosureCompiler"),
            executable=True),
        "deps": attr.label_list(
            allow_files=False,
            providers=["transitive_js_externs", "transitive_js_srcs"]),
        "externs": attr.label_list(allow_files=FileType([".js"])),
        "flags": attr.string_list(),
    },
    outputs={"out": "%{name}.js"})


def js_binary(name, srcs, externs, flags, visibility):
    cmd = [
        "$(location //src/java/com/github/jsdossier/tools:Compile)",
        '-c "$$(dirname $(location //third_party/js/closure_library:base))"',
        ' -f "--js_output_file=\\"$@\\""'
    ]
    cmd += [' -i "$(location %s)"' % src for src in srcs]
    cmd += [' -f "--externs=\\"$(location %s)\\""' % e for e in externs]
    cmd += [' -f "%s"' % f for f in flags]

    native.genrule(
        name=name,
        srcs=srcs + externs + [
            "//third_party/js/closure_library",
            "//third_party/js/closure_library:base"
        ],
        outs=["%s.js" % name],
        tools=["//src/java/com/github/jsdossier/tools:Compile"],
        cmd=' '.join(cmd),
        visibility=visibility,
    )
