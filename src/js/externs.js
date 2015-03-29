/**
 * @constructor
 * @final
 */
var Descriptor = function() {};

/** @type {string} */
Descriptor.prototype.name = '';

/** @type {boolean} */
Descriptor.prototype.namespace = false;

/** @type {boolean} */
Descriptor.prototype.interface = false;

/** @type {string} */
Descriptor.prototype.href = '';

/** @type {Array<!Descriptor>} */
Descriptor.prototype.statics = null;

/** @type {Array<!Descriptor>} */
Descriptor.prototype.members = null;

/** @type {Array<!Descriptor>} */
Descriptor.prototype.types = null;


/**
 * @constructor
 * @final
 */
var TypeRegistry = function() {};

/** @type {Array<!Descriptor>} */
TypeRegistry.prototype.types = null;

/** @type {Array<!Descriptor>} */
TypeRegistry.prototype.modules = null;
