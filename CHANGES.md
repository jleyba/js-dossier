# v0.11.0-dev

## Bug Fixes

- Typedefs are now included in the search index
- Render tags (visibility, deprecation, etc) for typedefs

## Changes

- Treat the compiler's IArrayLike, IObject, and IThenable like other built-in
  externs and automatically link to external documentation.
- Render properties and functions as expandable cards.
- Improved handling of goog.module.declareLegacyNamespace.
- Upgraded to com.google.javascript:closure-compiler-unshaded:v20160517
- Upgraded to com.google.code.gson:gson:2.6.2
- Upgraded to com.google.guava:guava:20.0-SNAPSHOT (c6387ede215926b377e812c35906f5bd0eec84f0)
- Upgraded to com.google.template:soy:2016-01-12
- Upgraded to com.atlassian.commonmark:commonmark:0.5.0
- Removed dependency com.atlassian.commonmark:commonmark-ext-gfm-tables
- Upgraded closure library to f73e58b8faf3cf8f51c7829c3696c85d37c52fb7

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
