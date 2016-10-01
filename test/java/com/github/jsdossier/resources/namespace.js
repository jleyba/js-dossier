goog.provide('namespace');
goog.provide('namespace.nested.internal');
goog.provide('namespace.nested.internal.implicit.Foo');
goog.provide('namespace.nested.logging.Console');


namespace.Widget = class {};
namespace.nested.internal.Component = class {};
// namespace.nested.internal.implicit should not be included in generated output
namespace.nested.internal.implicit.Foo = class {};
namespace.nested.logging.log = function(/** string */msg) {};
namespace.nested.logging.Console = class {};
