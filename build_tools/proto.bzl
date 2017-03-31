_proto_srcs_provider = provider()

def _proto_library_impl(ctx):
  transitive_deps = set()
  for dep in ctx.attr.deps:
    transitive_deps += getattr(dep.proto_library, "deps", [])
  transitive_deps += ctx.files.srcs

  return struct(
      proto_library=_proto_srcs_provider(
          srcs=ctx.files.srcs,
          deps=transitive_deps))


def _collect_inputs(deps):
  srcs = set()
  transitive_srcs = set()
  for dep in deps:
    srcs += getattr(dep.proto_library, "srcs", [])
    transitive_srcs += getattr(dep.proto_library, "deps", [])
  transitive_srcs += srcs
  return struct(srcs=srcs, transitive_srcs=transitive_srcs)


def _js_proto_library_impl(ctx):
  protoc = ctx.executable._protoc
  out = ctx.outputs.out

  inputs = _collect_inputs(ctx.attr.deps)

  cmd = [protoc.path, "--proto_path=."]
  cmd.append("--js_out=binary,error_on_name_conflict,library=%s:%s" % (ctx.label.name, out.dirname))
  cmd.extend([src.path for src in inputs.srcs])

  ctx.action(command=" ".join(cmd),
             inputs=list(inputs.transitive_srcs) + [protoc],
             outputs=[out],
             progress_message="Generating %s" % out)


def _java_proto_library_impl(ctx):
  protoc = ctx.executable._protoc
  out = ctx.outputs.out

  inputs = _collect_inputs(ctx.attr.deps)

  cmd = ["PROTO_OUT=$(mktemp -d ${TMPDIR:-/tmp}/proto.XXXXXXXXXX);"]
  cmd.extend([protoc.path, "--proto_path=.", "--java_out=$PROTO_OUT"])
  cmd.extend([src.path for src in inputs.srcs])
  cmd.extend(["&&", "jar", "-cf", out.path, "-C", "$PROTO_OUT", "."])

  ctx.action(command=" ".join(cmd),
             inputs=list(inputs.transitive_srcs) + [protoc],
             outputs=[out],
             progress_message="Generating %s" % out)


proto_library = rule(
    implementation=_proto_library_impl,
    attrs={
        "srcs": attr.label_list(allow_files=FileType([".proto"])),
        "deps": attr.label_list(providers=["proto_library"]),
    })


_java_proto_library = rule(
    implementation=_java_proto_library_impl,
    attrs={
        "deps": attr.label_list(providers=["proto_library"]),
        "_protoc": attr.label(default=Label("//third_party/proto:protoc"),
                              allow_files=True,
                              executable=True,
                              cfg="host")
    },
    output_to_genfiles = True,
    outputs = {
        "out": "%{name}.srcjar"
    }
)


js_proto_library = rule(
    implementation=_js_proto_library_impl,
    attrs={
        "deps": attr.label_list(providers=["proto_library"]),
        "_protoc": attr.label(default=Label("//third_party/proto:protoc"),
                              allow_files=True,
                              executable=True,
                              cfg="host")
    },
    output_to_genfiles = True,
    outputs = {
        "out": "%{name}.js"
    }
)

def java_proto_library(name, proto_deps, java_deps=None, visibility=None):
  _java_proto_library(
      name = name + "_src",
      deps = proto_deps,
      visibility = ["//visibility:private"])

  native.java_library(
      name = name,
      srcs = [":%s_src" % name],
      deps = (java_deps or []) + ["//lib/maven:protobuf"],
      visibility = visibility,
  )
