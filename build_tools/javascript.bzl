def _js_proto_library_impl(ctx):
  protoc = ctx.executable._protoc
  out = ctx.outputs.out
  
  cmd = [protoc.path, "--proto_path=."]
  cmd.append("--js_out=binary,error_on_name_conflict,library=%s:%s" % (ctx.label.name, out.dirname))
  cmd.extend([src.path for src in ctx.attr.proto.proto.direct_sources])

  ctx.action(command=" ".join(cmd),
             inputs=ctx.attr.proto.proto.transitive_sources + [protoc],
             outputs=[out],
             progress_message="Generating %s" % out)


js_proto_library = rule(
    implementation = _js_proto_library_impl,
    attrs = {
        "proto": attr.label(
            mandatory = True,
            providers = ["proto"],
        ),
        "_protoc": attr.label(
            default=Label("@com_google_protobuf//:protoc"),
            allow_files=True,
            executable=True,
            cfg="host",
        ),
    },
    output_to_genfiles = True,
    outputs = {
        "out": "%{name}.js"
    }
)
