def _js_binary_impl(ctx):
    externs = set(order="compile")
    srcs = set(order="compile")
    for dep in ctx.attr.deps:
        externs += dep.transitive_js_externs
        srcs += dep.transitive_js_srcs

    args = (ctx.attr.flags +
        ["--js_output_file=\"%s\"" % ctx.outputs.out.path] +
        ["--js=%s" % src.path for src in srcs] +
        ["--externs=%s" % extern.path for extern in externs])

    ctx.action(
        inputs=list(srcs),
        outputs=[ctx.outputs.out],
        arguments=args,
        executable=ctx.executable.compiler)

    return struct(files=set([ctx.outputs.out]))

js_binary = rule(
    implementation=_js_binary_impl,
    attrs={
        "compiler": attr.label(
            default=Label("//lib/maven:ClosureCompiler"),
            executable=True),
        "deps": attr.label_list(
            allow_files=False,
            providers=["transitive_js_externs", "transitive_js_srcs"]),
        "flags": attr.string_list(),
    },
    outputs={"out": "%{name}.js"})
