# v.next

## Changes

-  Added the `environment` configuration option to allow specifying the target
   script environment (`BROWSER` or `NODE`). Previously, the `NODE` environment
   was automatically selected by specifying any CommonJS input files.
-  Upgraded dependencies:
   +  com.google.javascript:closure-compiler-unshaded:v20180506
   +  com.google.javascript:closure-compiler-externs:v20180506
   +  com.google.template:soy:2018-01-03
   +  com.google.protobuf:protobuf-java:3.5.0


# v0.12.1

## Bug Fixes

- Fixed initialization when using CommonJS modules so the extern definitions
  for Node's core library are properly loaded.


# v0.12.0

## Changes

-  Removed the `language` input option; Dossier now always sets the input
   language to ECMASCRIPT_2017.
-  Upgraded dependencies:
   +  com.google.auto:auto-common:0.8
   +  com.google.auto.value:auto-value:1.4.1
   +  com.google.guava:guava:22.0
   +  com.google.inject:guice:4.1.0
   +  com.google.inject.extensions:guice-assistedinject:4.1.0
   +  com.google.inject.extensions:guice-multibindings:4.1.0
   +  com.google.javascript:closure-compiler-externs:v20170910
   +  com.google.javascript:closure-compiler-unshaded:v20170910

# v0.11.1

## Changes

- Upgraded to com.google.guava:guava:20.0

# v0.11.0

## Bug Fixes

- Typedefs are now included in the search index
- Render tags (visibility, deprecation, etc) for typedefs
- Instance types defined on a constructor are no longer registered as distinct
  types. This most often manifested with the private `instance_` property added
  to a constructor by Closure's `goog.addSingletonGetter` function.
- Fixed a ConcurrentModificationException that can occur when evaluating
  type expressions in multiple threads with unresolvable properties.
- Fixed navigation from the search bar when files are loaded directly from disk
  (e.g. using a `file://` scheme).
- Refine search box auto-complete suggestions using Damerau–Levenshtein the
  distance between terms.
- In CommonJS modules, fixed type checking for destructuring assignments where
  the RHS is a requirement statement.
  Example: `let {Foo, Bar} = require('./module');`

## Changes

- Dossier now requires Java 8
- Dossier will no longer generate documentation for implicit namespaces.
  An implicit namespace only exists through the definition of another symbol
  using `goog.module` or `goog.provide`. For example, the statement
  `goog.provide('foo.bar.baz');`, implicitly creates the namespaces
  `foo` and `foo.bar`.
- Treat the compiler's IArrayLike, IObject, and IThenable like other built-in
  externs and automatically link to external documentation.
- Render properties and functions as expandable cards.
- Improved handling of goog.module.declareLegacyNamespace.
- Distinguish between the different categories of nested types (e.g. class vs interface)
- Use client-side rendering when navigating between Dossier generated pages.
- Upgraded to com.google.javascript:closure-compiler-unshaded:v20160911
- Upgraded to com.google.code.gson:gson:2.6.2
- Upgraded to com.google.guava:guava:20.0-SNAPSHOT
- Upgraded to com.google.template:soy:2016-08-25
- Upgraded to com.atlassian.commonmark:commonmark:0.5.1
- Removed dependency com.atlassian.commonmark:commonmark-ext-gfm-tables
- Upgraded closure library to 727733c022dcf60d1561b27ae6ea4d1cb011370c

# v0.10.0

## Bug Fixes

- Include visibility indicators for enums
- Support node extern modules with a hyphen in the file name
- Updated HTML sanitizer policies to permit safe HTML generated from markdown
  comments
  * Fenced code block info string (`<code class="language-javascript">`)
  * Ordered lists that do not start at (`<ol start="3">`)

## Changes

- Renamed configuration option `closureDepsFile` to `closureDepFiles`
- Renamed configuration option `stripModulePrefix` to `modulePrefix`
- Dossier may now be configured entirely through command line flags
- API documentation will now differentiate between nominal interfaces (declared
  with `@interface`) and structural interfaces (declared with `@record`)
- Changed the `sourceUrlTemplate` configuration option to use `%path%` and
  `%line%` as replacement tokens instead of `${path}` and `${line}`

# v0.9.1

## Bug Fixes

- Properly set the nav drawer's initial scroll position to include the current
   page in view.
- In order to improve rendering performance, removed use of max-height
   transitions on Safari and mobile

# v0.9.0

## Bug Fixes

- Properly record documentation for types exported from Closure and Node
   modules (ES6 modules were not affected)
- Stop using ES6 additions to String.prototype; these are not available
   on MSIE

## Changes

- Added new configuration options: `externModules` and `sourceUrlTemplate`
- Added visibility tags for package-private, protected, and protected
   types/properties
- Adjusted page font-weight based on screen dpi
- Adjusted nav tree view so its (hopefully) easier to navigate when there
   is a lot of nesting
- Compiler constants (`@define` and `goog.define(name, value)`) will now
   render using their fully qualified names, exactly as they must be used
   with the compiler's --define flag.
- Dossier will now automatically include extern definitions for Node's core
   modules when the input configuration includes `modules` file list.
- Updates the HTML sanitizer to permit the `cite` attribute on `q` elements.
- Set styles on `kbd` elements
- Started user CHANGES log. For older releases, refer to git commit history
