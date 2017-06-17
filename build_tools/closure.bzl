load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library")

def closure_js_binary_set(name, **kwargs):
  """Defines to closure_js_binary targets for name.
  
  The first, :name, builds with ADVANCED optimizations.
  The second, :name_simple, builds with SIMPLE optimizations and pretty printed code (for easier
  debugging).
  """
  closure_js_binary(
      name = name,
      compilation_level = "ADVANCED",
      **kwargs)

  defs = kwargs.get("defs", [])
  defs.append("--formatting=PRETTY_PRINT")

  closure_js_binary(
      name = name + "_simple",
      compilation_level = "SIMPLE",
      **kwargs)
