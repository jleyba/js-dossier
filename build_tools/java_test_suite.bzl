def java_test_suite(name, srcs, resources=None, deps=None):
  tests = []

  for src in srcs:
    if src.endswith('Test.java'):
      test_name = src[:-len('.java')]
      tests += [test_name]
      native.java_test(
          name = test_name,
          srcs = [src],
          resources = resources,
          deps = deps)

  native.test_suite(
      name = name,
      tests = tests,
      tags = ["manual"])
