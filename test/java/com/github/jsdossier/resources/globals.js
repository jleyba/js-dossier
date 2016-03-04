/**
 * This is a global constructor.
 * @constructor
 */
var GlobalCtor = function() {

  /**
   * This is a private field and should not be documented.
   * @private {number}
   */
  this.privateX_ = 1;

  /**
   * This is a function defined inside the constructor.
   */
  this.instanceMethod = function() {};
};

/**
 * A static property.
 * @type {string}
 */
GlobalCtor.staticProp = 'hi';


/**
 * This is just an alias for an extern and should be documented as such.
 * @type {function(new: Date)}
 */
GlobalCtor.dateCtor = Date;


/**
 * This is a nested constructor and should be documented as a nested type.
 * @constructor
 */
GlobalCtor.Other = function() {};


/**
 * A static function.
 * @return Return statement; no type.
 */
GlobalCtor.staticFunc = function() {};


/**
 * This is function defined on the prototype.
 * @param {string} x description.
 * @param y description only.
 */
GlobalCtor.prototype.protoMethod = function(x, y) {
};


/**
 * Method description here.
 * @return {string} A string.
 * @deprecated This is a deprecated method.
 */
GlobalCtor.prototype.deprecatedMethod = function() {
};


/**
 * @deprecated Annotation is only detail on method.
 */
GlobalCtor.prototype.deprecatedMethod2 = function() {
};


/**
 * This is a global private constructor.
 * @constructor
 * @private
 */
var GlobalPrivateCtor = function() {
};


/**
 * Lorem ipsum blah blah blah.
 * @deprecated Do not use this deprecated class. Use something else.
 * @constructor
 * @final
 * @struct
 */
var DeprecatedFoo = function() {
};


/**
 * This is a global enumeration of strings.
 * @enum {string}
 */
var GlobalEnum = {
  FRUIT: 'apple',

  /** Color description. */
  COLOR: 'red',

  /**
   * Day of the week.
   * @deprecated Do not use this anymore.
   */
  DAY: 'Monday'
};


/**
 * This is a function defined on a global enumeration.
 */
GlobalEnum.doSomething = function() {
};


/**
 * Even though this enum is just a declared name and has is never assigned a
 * value, it should still be documented.
 * @enum {!(string|number|{age: ?number})}
 */
var UndefinedEnum;


/**
 * <table>
 * <caption>This is the table caption</caption>
 * <thead>
 * <tr><th>Fruit</th><th>Color</th></tr>
 * </thead>
 * <tbody>
 * <tr><td>Apple</td><td>Red</td></tr>
 * <tr><td>Apple</td><td>Green</td></tr>
 * <tr><td colspan="2">Orange</td></tr>
 * </tbody>
 * </table>
 * @constructor
 */
function SomeCtor() {
}


/**
 * # Heading 1
 *
 * ## Heading 2
 *
 * ### Heading 3
 *
 * #### Heading 4
 *
 * ##### Heading 5
 *
 * ###### Heading 6
 *
 * Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam vitae
 * elementum urna, id placerat risus. Suspendisse potenti. Sed vel ante ac mi
 * efficitur bibendum ut eget mauris.
 *
 * <small>The fine print</small>
 *
 * The following text is a [link](#).
 *
 * The following text should have **strong emphasis**.
 *
 * The following text should be rendered _with emphasis_.
 *
 * ---
 *
 * Here are some lists:
 *
 * 1. Lorem ipsum dolor sit amet
 * 2. Aliquam vitaie elementum urna
 * 3. Suspendisse potenti
 *
 *
 * * Lorem ipsum dolor sit amet
 * * Aliquam vitaie elementum urna
 * * Suspendisse potenti
 *
 *
 * 1. Item 1
 *    * Lorem ipsum dolor sit amet
 *    * Aliquam vitaie elementum urna
 *    * Suspendisse potenti
 * 2. Item 2
 *    * Sed vel ante ac mi efficitur
 *
 *
 * ---
 *
 * > Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium
 * > doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore
 * > veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim
 * > ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia
 * > consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque
 * > porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur,
 * > adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et
 * > dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis
 * > nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid
 * > ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea
 * > voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem
 * > eum fugiat quo voluptas nulla pariatur?
 * >
 * > <small>Cicero, Section 1.10.32, <cite>de Finibus Bonorum et Malorum</cite>,
 * > 45 BC.</small>
 *
 * ---
 *
 * | Fruit  | Color  |
 * | :----- | :----: |
 * | Apple  | Red    |
 * | Apple  | Green  |
 * | Banana | Yellow |
 * | Orange ||
 * [Table caption]
 *
 * ---
 *
 * Lorem ipsum `dolor sit amet`, consectetur adipiscing elit. Aliquam vitae
 * elementum urna, id placerat risus. Suspendisse potenti. Sed `vel` `ante` ac mi
 * efficitur bibendum ut eget mauris.
 *
 *    // This is an indented code block.
 *    function sayHello() {
 *      console.log('Hello, world!');
 *    }
 *
 * ```
 * // This a fenced code block.
 * function sayHello() {
 *   console.log('Hello, world!');
 * }
 * ```
 *
 * ---
 *
 * <dl>
 * <dt>Term 1</dt>
 * <dd>Definition 1.a</dd>
 * <dd>Definition 1.b</dd>
 * <dt>Term 2</dt>
 * <dd>Definition 2.a</dd>
 * <dd>Definition 2.b</dd>
 * </dl>
 */
class Formatter {
  /**
   * # Heading 1
   *
   * ## Heading 2
   *
   * ### Heading 3
   *
   * #### Heading 4
   *
   * ##### Heading 5
   *
   * ###### Heading 6
   *
   * Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam vitae
   * elementum urna, id placerat risus. Suspendisse potenti. Sed vel ante ac mi
   * efficitur bibendum ut eget mauris.
   *
   * <small>The fine print</small>
   *
   * The following text is a [link](#).
   *
   * The following text should have **strong emphasis**.
   *
   * The following text should be rendered _with emphasis_.
   *
   * ---
   *
   * Here are some lists:
   *
   * 1. Lorem ipsum dolor sit amet
   * 2. Aliquam vitaie elementum urna
   * 3. Suspendisse potenti
   *
   *
   * * Lorem ipsum dolor sit amet
   * * Aliquam vitaie elementum urna
   * * Suspendisse potenti
   *
   *
   * 1. Item 1
   *    * Lorem ipsum dolor sit amet
   *    * Aliquam vitaie elementum urna
   *    * Suspendisse potenti
   * 2. Item 2
   *    * Sed vel ante ac mi efficitur
   *
   *
   * ---
   *
   * > Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium
   * > doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore
   * > veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim
   * > ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia
   * > consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque
   * > porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur,
   * > adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et
   * > dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis
   * > nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid
   * > ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea
   * > voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem
   * > eum fugiat quo voluptas nulla pariatur?
   * >
   * > <small>Cicero, Section 1.10.32, <cite>de Finibus Bonorum et Malorum</cite>,
   * > 45 BC.</small>
   *
   * ---
   *
   * | Fruit  | Color  | Quantity |
   * | :----- | :----: | -------: |
   * | Apple  | Red    |        1 |
   * | Apple  | Green  |        2 |
   * | Banana | Yellow |        3 |
   * | Orange ||                4 |
   * [Table caption]
   *
   * ---
   *
   * Lorem ipsum `dolor sit amet`, consectetur adipiscing elit. Aliquam vitae
   * elementum urna, id placerat risus. Suspendisse potenti. Sed `vel` `ante` ac mi
   * efficitur bibendum ut eget mauris.
   *
   *    // This is an indented code block.
   *    function sayHello() {
   *      console.log('Hello, world!');
   *    }
   *
   * ```
   * // This a fenced code block.
   * function sayHello() {
   *   console.log('Hello, world!');
   * }
   * ```
   *
   * ---
   *
   * <dl>
   * <dt>Term 1</dt>
   * <dd>Definition 1.a</dd>
   * <dd>Definition 1.b</dd>
   * <dt>Term 2</dt>
   * <dd>Definition 2.a</dd>
   * <dd>Definition 2.b</dd>
   * </dl>
   */
  format() {
  }
}


/**
 * Color names.
 * @enum {string}
 * @deprecated
 */
const Color = {
  RED: 'red',
  GREEN: 'green',
  BLUE: 'blue'
};


/**
 * Private color names.
 * @enum {string}
 * @private
 */
const PrivateColor_ = {
  PINK: 'pink',
  TEAL: 'teal'
};
