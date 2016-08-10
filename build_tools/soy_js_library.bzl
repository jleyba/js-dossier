# -*- mode: python; -*-
#
# Copyright 2013-2016 Jason Leyba
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Rule for generating JavaScript modules from soy templates."""

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")

def soy_js_library(name, srcs, deps, visibility):
  compiler = "//src/java/com/github/jsdossier/soy:SoyJsCompiler"

  outs = [src.split("/", 2)[1] + ".js" for src in srcs]

  cmd = [
    "$(location %s)" % compiler,
    "--output_dir $(@D)"
  ]
  cmd += ["-f $(location %s)" % src for src in srcs]

  native.genrule(
      name = name + "_gen",
      srcs = srcs,
      outs = outs,
      tools = [compiler],
      message = "Generating JS from Soy templates",
      cmd = " ".join(cmd),
      visibility = visibility,
  )

  closure_js_library(
      name = name,
      srcs = [":" + name + "_gen"],
      language = "ECMASCRIPT6_STRICT",
      suppress = [
          "JSC_MISSING_REQUIRE_CALL_WARNING",
      ],
      deps = deps,
      visibility = visibility,
  )
