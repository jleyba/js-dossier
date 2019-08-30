def _js_proto_library_impl(ctx):
    if len(ctx.attr.deps) > 1:
        fail("%s must have exactly 1 dep" % ctx.label.name)

    lib = ctx.attr.deps[0]
    protoc = ctx.executable._protoc
    out = ctx.outputs.out

    args = ctx.actions.args()
    args.add("--js_out=binary,error_on_name_conflict,library=%s:%s" % (ctx.label.name, out.dirname))
    args.add_joined(
        "--descriptor_set_in",
        lib[ProtoInfo].transitive_descriptor_sets,
        join_with = ":",
    )
    args.add_all([src.short_path for src in lib[ProtoInfo].direct_sources])

    ctx.actions.run(
        outputs = [out],
        inputs = lib[ProtoInfo].transitive_descriptor_sets,
        executable = ctx.executable._protoc,
        arguments = [args],
        progress_message = "Generating %s" % out,
    )

js_proto_library = rule(
    implementation = _js_proto_library_impl,
    attrs = {
        "deps": attr.label_list(
            allow_empty = False,
            mandatory = True,
            providers = [ProtoInfo],
        ),
        "_protoc": attr.label(
            default = Label("@com_google_protobuf//:protoc"),
            allow_files = True,
            executable = True,
            cfg = "host",
        ),
    },
    output_to_genfiles = True,
    outputs = {
        "out": "%{name}.js",
    },
)
