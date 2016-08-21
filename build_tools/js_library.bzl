_JS_FILE_TYPE = FileType([".js"])

def _js_library_impl(ctx):
    externs = set(order="compile")
    srcs = set(order="compile")
    for dep in ctx.attr.deps:
        externs += dep.transitive_js_externs
        srcs += dep.transitive_js_srcs
    
    externs += _JS_FILE_TYPE.filter(ctx.files.externs)
    srcs += _JS_FILE_TYPE.filter(ctx.files.srcs)
    
    return struct(
        files=set(),
        transitive_js_externs=externs,
        transitive_js_srcs=srcs)

js_library = rule(
    implementation=_js_library_impl,
    attrs={
        "externs": attr.label_list(allow_files=_JS_FILE_TYPE),
        "srcs": attr.label_list(allow_files=_JS_FILE_TYPE),
        "deps": attr.label_list(
            providers=[
               "transitive_js_externs",
               "transitive_js_srcs"])
    })
