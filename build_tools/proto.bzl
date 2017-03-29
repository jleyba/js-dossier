def proto_library(name, srcs):
  native.filegroup(name = name, srcs = srcs)

def java_proto_library(name, deps, visibility=None):
  protoc = "//third_party/proto:protoc"

  cmd = ["PROTO_OUT=$$(mktemp -d $${TMPDIR:-/tmp}/genrule.XXXXXXXXXX);"]
  cmd += ["$(location %s)" % protoc]
  cmd += ["--proto_path=."]
  cmd += ["--proto_path=./third_party"]
  cmd += ["--java_out=$$PROTO_OUT"]
  cmd += ["$(locations " + dep + ")" for dep in deps]
  cmd += ["&& jar -cf $(@) -C $$PROTO_OUT ."]

  native.genrule(
      name = name + "_gen",
      srcs = deps + ["//third_party/webutil/html/types/proto"],
      message = "Compiling Protocol Buffers",
      tools = [protoc],
      outs = [name + "_gen.srcjar"],
      cmd = " ".join(cmd),
  )

  native.java_library(
      name = name,
      srcs = [name + "_gen.srcjar"],
      deps = [
          "//lib/maven:protobuf",
          "//lib/maven:safe_types",
      ],
      visibility = visibility,
  )
