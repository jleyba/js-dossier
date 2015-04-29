/*
 Copyright 2013-2015 Jason Leyba

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package com.github.jsdossier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.filter;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.NoType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.PrototypeObjectType;
import com.google.javascript.rhino.jstype.ProxyObjectType;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;

import com.github.jsdossier.jscomp.DossierModule;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.SourceLink;
import com.github.jsdossier.proto.TypeLink;
import com.github.jsdossier.proto.TypeLinkOrBuilder;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Stack;

import javax.annotation.Nullable;

public class Linker {

  private final Path outputRoot;
  private final Path modulePrefix;
  private final Path sourcePrefix;
  private final Predicate<NominalType> typeFilter;
  private final TypeRegistry typeRegistry;

  private final Stack<NominalType> context = new Stack<>();

  /**
   * @param typeRegistry The type registry.
   */
  public Linker(
      Path outputRoot,
      Path sourcePrefix,
      Path modulePrefix,
      Predicate<NominalType> typeFilter,
      TypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    this.outputRoot = outputRoot;
    this.modulePrefix = modulePrefix;
    this.sourcePrefix = sourcePrefix;
    this.typeFilter = typeFilter;
  }

  public void pushContext(NominalType type) {
    context.push(type);
  }

  public void popContext() {
    context.pop();
  }

  private static String getTypePrefix(JSType type) {
    if (type.isInterface()) {
      return "interface_";
    } else if (type.isConstructor()) {
      return "class_";
    } else if (type.isEnumType()) {
      return "enum_";
    } else {
      return "namespace_";
    }
  }

  /**
   * Returns the display name for the given type.
   */
  public String getDisplayName(NominalType type) {
    if (!type.isModuleExports() || !type.isCommonJsModule()) {
      return type.getQualifiedName();
    }
    String displayName = getDisplayName(type.getModule());
    type.setAttribute("displayName", displayName);
    return displayName;
  }

  /**
   * Returns the display name for the given module.
   */
  public String getDisplayName(ModuleDescriptor module) {
    Path modulePath = stripExtension(module.getPath());

    Path displayPath = modulePrefix.relativize(modulePath);
    if (displayPath.getFileName().toString().equals("index")
        && displayPath.getParent() != null) {
      displayPath = displayPath.getParent();
    }
    return displayPath.toString()
        .replace(modulePath.getFileSystem().getSeparator(), "/");  // Oh windows...
  }

  private static Path stripExtension(Path path) {
    String name = path.getFileName().toString();
    return path.resolveSibling(Files.getNameWithoutExtension(name));
  }

  /**
   * Returns the path of the generated document file for the given type.
   */
  public Path getFilePath(NominalType type) {
    String name = "";
    if (type.isCommonJsModule()) {
      name = "module_" + getDisplayName(type.getModule()).replace('/', '_');
    }
    if (!type.isCommonJsModule() || !type.isModuleExports()) {
      if (!name.isEmpty()) {
        name += "_";
      }
      name += getTypePrefix(type.getJsType()) + getDisplayName(type).replace('.', '_');
    }
    return outputRoot.resolve(name + ".html");
  }

  /**
   * Returns the path of the generated documentation for the given source file.
   */
  public Path getFilePath(Path sourceFile) {
    Path path = sourcePrefix
        .relativize(sourceFile.toAbsolutePath().normalize())
        .resolveSibling(sourceFile.getFileName() + ".src.html");
    Path ret = outputRoot.resolve("source");
    for (Path part : path) {
      ret = ret.resolve(part.toString());
    }
    return ret;
  }

  /**
   * @see #getFilePath(Path)
   */
  public Path getFilePath(String sourceFile) {
    return getFilePath(sourcePrefix.getFileSystem().getPath(sourceFile));
  }

  /**
   * Returns the path to the rendered source file for the given node.
   */
  public SourceLink getSourceLink(@Nullable Node node) {
    if (node == null || node.isFromExterns()) {
      return SourceLink.newBuilder().setPath("").build();
    }
    Iterator<Path> parts = outputRoot
        .relativize(getFilePath(node.getSourceFileName()))
        .iterator();
    return SourceLink.newBuilder()
        .setPath(Joiner.on('/').join(parts))
        .setLine(node.getLineno())
        .build();
  }

  /**
   * Computes the URL path from one descriptor to the definition of another type. The referenced
   * type may be specified as:
   * <ul>
   *   <li>A fully qualified type: {@code foo.bar.Baz}
   *   <li>A fully qualified type with instance property qualifier: {@code foo.Bar#baz}. This is
   *       treated the same as {@code foo.Bar.prototype.baz}.
   * </ul>
   *
   * <p>If the referenced type is recognized, the returned path will be relative to the output
   * directory, otherwise {@code null} is returned.
   */
  @Nullable
  public TypeLink getLink(String symbol) {
    // Trim down the target symbol to something that would be indexable.
    int index = symbol.indexOf("(");
    if (index != -1) {
      symbol = symbol.substring(0, index);
    }

    if (symbol.startsWith("#")) {
      TypeLink link = getContextLink(symbol);
      if (link != null) {
        return link;
      }
    }

    String typeName = symbol;
    String propertyName = "";

    if (symbol.endsWith("#")) {
      typeName = symbol.substring(0, symbol.length() - 1);

    } else if (symbol.endsWith(".prototype")) {
      typeName = symbol.substring(0, symbol.length() - ".prototype".length());

    } else if (symbol.contains("#")) {
      String[] parts = symbol.split("#");
      typeName = parts[0];
      propertyName = parts[1];

    } else if (symbol.contains(".prototype.")) {
      String[] parts = symbol.split(".prototype.");
      typeName = parts[0];
      propertyName = parts[1];
    }

    NominalType type = getType(typeName, true);

    // Link might be a qualified path to a property.
    if (type == null && propertyName.isEmpty()) {
      index = typeName.lastIndexOf(".");
      if (index != -1) {
        propertyName = typeName.substring(index + 1);
        typeName = typeName.substring(0, index);
        type = getType(typeName, true);
      }
    }

    // Link might be qualified path to property on a module's exported type.
    if (type == null && !propertyName.isEmpty()) {
      index = typeName.lastIndexOf(".");
      if (index != -1) {
        String exportedName = typeName.substring(index + 1);
        String moduleName = typeName.substring(0, index);
        type = getType(moduleName, true);
        if (type != null && type.isModuleExports()) {
          return buildModuleLink(type, exportedName, propertyName);
        }
      }
    }

    if (type == null) {
      // If we get here, make one last attempt to resolve the referenced path
      // by checking for an extern type.
      return getExternLink(typeName);
    }

    if (typeFilter.apply(type)) {
      type = getUnfilteredAlias(type);
      if (type == null) {
        return null;
      }
    }

    if (propertyName.isEmpty()) {
      return checkNotNull(getLink(type),
          "Failed to build link for %s", type.getQualifiedName());
    }

    if (type.isModuleExports()) {
      TypeLink link = buildModuleLink(type, propertyName, null);
      if (link != null) {
        return link;
      }
    }

    try {
      pushContext(type);
      return getContextLink("#" + propertyName);
    } finally {
      popContext();
    }
  }

  @Nullable
  private TypeLink buildModuleLink(NominalType module, String typeName, String propertyName) {
    for (NominalType type : module.getTypes()) {
      if (typeName.equals(type.getName())) {
        TypeLink link;
        if (isNullOrEmpty(propertyName)) {
          link = getLink(type);
        } else {
          try {
            pushContext(type);
            link = getContextLink("#" + propertyName);
          } finally {
            popContext();
          }
        }
        checkNotNull(link, "Failed to build link for %s", type.getQualifiedName());
        return link.toBuilder()
            .setText(getDisplayName(module) + "." + link.getText())
            .build();
      }
    }
    return null;
  }

  @Nullable
  private TypeLink getContextLink(String symbol) {
    if (context.isEmpty()) {
      return null;
    }

    checkArgument(symbol.startsWith("#"));
    symbol = symbol.substring(1);
    NominalType type = context.peek();

    TypeLink link = checkNotNull(getLink(type), "Failed to build link for %s",
        type.getQualifiedName());

    String id = symbol;

    // If we have a class/interface, check for a prototype first.
    if (type.getJsType().isConstructor() || type.getJsType().isInterface()) {
      ObjectType instanceType = ((FunctionType) type.getJsType()).getInstanceType();
      if (instanceType.getPropertyNames().contains(symbol)) {
        return link.toBuilder()
            .setText(link.getText() + "#" + symbol)
            .setHref(link.getHref() + "#" + id)
            .build();
      }
    }

    if (type.getJsType().toObjectType().getPropertyType(symbol).isEnumElementType()) {
      return link.toBuilder()
          .setText(link.getText() + "." + symbol)
          .setHref(link.getHref() + "#" + id)
          .build();
    }

    if (!type.getJsType().toObjectType().getPropertyNames().contains(symbol)) {
      return link.toBuilder()
          .setText(link.getText() + "." + symbol)
          .build();
    }

    if (!type.isNamespace()) {
      id = type.getName() + "." + symbol;
    }
    // Otherwise, we check for static properties.
    return link.toBuilder()
        .setText(link.getText() + "." + symbol)
        .setHref(link.getHref() + "#" + id)
        .build();
  }

  @Nullable
  private NominalType resolve(JSType type) {
    NominalType nominalType = typeRegistry.resolve(type);
    if (nominalType != null && typeFilter.apply(nominalType)) {
      return getUnfilteredAlias(nominalType);
    }
    return nominalType;
  }

  @Nullable
  private NominalType getUnfilteredAlias(NominalType type) {
    return Iterables.getFirst(FluentIterable.from(type.getTypeDescriptor().getAliases())
        .filter(new Predicate<NominalType>() {
          @Override
          public boolean apply(NominalType input) {
            return !typeFilter.apply(input);
          }
        }), null);
  }

  @Nullable
  public TypeLink getLink(NominalType type) {
    if (typeFilter.apply(type)) {
      type = getUnfilteredAlias(type);
      if (type == null) {
        return null;
      }
    }
    TypeLink.Builder link = TypeLink.newBuilder()
        .setText(getDisplayName(type));
    if (type.getJsdoc() != null && type.getJsdoc().isTypedef()) {
      NominalType parent = type.getParent();
      if (parent == null) {
        return null;
      }
      link.setHref(
          getFilePath(parent).getFileName() + "#" + parent.getName() + "." + type.getName());
    } else {
      link.setHref(getFilePath(type).getFileName().toString());
    }
    return link.build();
  }

  @Nullable
  public TypeLink getLink(final JSType to) {
    NominalType type = resolve(to);
    if (type == null) {
      return null;
    }
    return TypeLink.newBuilder()
        .setText(getDisplayName(type))
        .setHref(getFilePath(type).getFileName().toString())
        .build();
  }

  @Nullable
  private NominalType getType(String name, boolean checkContextModule) {
    NominalType type = typeRegistry.getNominalType(name);
    if (type == null) {
      type = typeRegistry.getModuleType(name);
    }
    if (type == null) {
      for (NominalType module: typeRegistry.getModules()) {
        if (name.equals(getDisplayName(module))) {
          return module;
        }
      }
    }
    if (type == null && !context.isEmpty() && checkContextModule) {
      NominalType t = context.peek();
      if (t.getModule() != null) {
        String exportedName = t.getModule().getExportedName(name);
        if (!isNullOrEmpty(exportedName)) {
          return getType(exportedName, false);
        }
      }
    }
    return type;
  }

  private static final String MDN_PREFIX =
      "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/";

  /**
   * Maps built-in objects to a link to their definition on the Mozilla Develper Network.
   * The Closure compiler's externs do not provide links for these types in its externs.
   */
  private static final ImmutableMap<String, String> BUILTIN_TO_MDN_LINK =
      ImmutableMap.<String, String>builder()
          .put("Arguments", MDN_PREFIX + "Functions/arguments")
          .put("Array", MDN_PREFIX + "Global_Objects/Array")
          .put("Boolean", MDN_PREFIX + "Global_Objects/Boolean")
          .put("Date", MDN_PREFIX + "Global_Objects/Date")
          .put("Error", MDN_PREFIX + "Global_Objects/Error")
          .put("Function", MDN_PREFIX + "Global_Objects/Function")
          .put("Infinity", MDN_PREFIX + "Global_Objects/Infinity")
          .put("Math", MDN_PREFIX + "Global_Objects/Math")
          .put("NaN", MDN_PREFIX + "Global_Objects/NaN")
          .put("Number", MDN_PREFIX + "Global_Objects/Number")
          .put("Object", MDN_PREFIX + "Global_Objects/Object")
          .put("RangeError", MDN_PREFIX + "Global_Objects/RangeError")
          .put("ReferenceError", MDN_PREFIX + "Global_Objects/ReferenceError")
          .put("RegExp", MDN_PREFIX + "Global_Objects/RegExp")
          .put("String", MDN_PREFIX + "Global_Objects/String")
          .put("SyntaxError", MDN_PREFIX + "Global_Objects/SyntaxError")
          .put("TypeError", MDN_PREFIX + "Global_Objects/TypeError")
          .put("URIError", MDN_PREFIX + "Global_Objects/URIError")
          .put("arguments", MDN_PREFIX + "Functions/arguments")
          .put("boolean", MDN_PREFIX + "Global_Objects/Boolean")
          .put("null", MDN_PREFIX + "Global_Objects/Null")
          .put("number", MDN_PREFIX + "Global_Objects/Number")
          .put("string", MDN_PREFIX + "Global_Objects/String")
          .put("undefined", MDN_PREFIX + "Global_Objects/Undefined")
          .build();

  /**
   * Returns a for one of the builtin extern types to its definition on the
   * <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/">Mozilla Developer
   * Network</a>.
   *
   * @param name The name of the extern to find a link for.
   * @return A link to the extern's type definition, or {@code null} if one could not be found.
   */
  @Nullable
  public TypeLink getExternLink(String name) {
    if (BUILTIN_TO_MDN_LINK.containsKey(name)) {
      return TypeLink.newBuilder()
          .setText(name)
          .setHref(BUILTIN_TO_MDN_LINK.get(name))
          .build();
    }
    return null;
  }

  /**
   * Parses the type expression into a {@link Comment} suitable for injection into a soy
   * template.
   */
  public Comment formatTypeExpression(JSTypeExpression expression) {
    return new CommentTypeParser().parse(
        typeRegistry.evaluate(expression),
        ParseModifier.forExpression(expression));
  }

  /**
   * Parses the type expression attached to the given node.
   */
  public Comment formatTypeExpression(Node node) {
    JSType type = node.getJSType();
    if (type == null) {
      return Comment.newBuilder().build();
    }
    return new CommentTypeParser().parse(type, ParseModifier.forNode(node));
  }

  /**
   * Parses the type into a {@link Comment} suitable for injection into a soy template.
   */
  public Comment formatTypeExpression(@Nullable JSType type) {
    if (type == null) {
      return Comment.newBuilder().build();
    }
    return new CommentTypeParser().parse(type, ParseModifier.NONE);
  }

  private static enum ParseModifier {
    NONE,
    OPTIONAL_ARG,
    VAR_ARGS;

    static ParseModifier forNode(Node node) {
      if (node.isVarArgs()) {
        return VAR_ARGS;
      } else if (node.isOptionalArg()) {
        return OPTIONAL_ARG;
      }
      return NONE;
    }

    static ParseModifier forExpression(JSTypeExpression expression) {
      if (expression.isVarArgs()) {
        return VAR_ARGS;
      } else if (expression.isOptionalArg()) {
        return OPTIONAL_ARG;
      }
      return NONE;
    }
  }

  /**
   * A {@link JSType} visitor that converts the type into a comment type expression.
   */
  private class CommentTypeParser implements Visitor<Void> {

    private final Comment.Builder comment = Comment.newBuilder();

    private String currentText = "";

    Comment parse(JSType type, ParseModifier modifier) {
      comment.clear();
      currentText = "";

      if (modifier == ParseModifier.VAR_ARGS) {
        currentText = "...";
      }

      if (modifier != ParseModifier.NONE && type.isUnionType()) {
        caseUnionType((UnionType) type, true);
      } else {
        type.visit(this);
      }

      if (modifier == ParseModifier.OPTIONAL_ARG) {
        currentText += "=";
      }

      if (!currentText.isEmpty()) {
        comment.addTokenBuilder().setText(currentText);
        currentText = "";
      }
      return comment.build();
    }

    private void appendText(String text) {
      currentText += text;
    }

    private void appendNativeType(String type) {
      appendLink(checkNotNull(getExternLink(type)));
    }

    private void appendLink(TypeLinkOrBuilder link) {
      appendLink(link.getText(), link.getHref());
    }

    private void appendLink(String text, String href) {
      if (!currentText.isEmpty()) {
        comment.addTokenBuilder().setText(currentText);
        currentText = "";
      }
      comment.addTokenBuilder().setText(text).setHref(href);
    }

    @Override
    public Void caseNoType(NoType type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void caseEnumElementType(EnumElementType type) {
      return type.getPrimitiveType().visit(this);
    }

    @Override
    public Void caseAllType() {
      appendText("*");
      return null;
    }

    @Override
    public Void caseBooleanType() {
      appendNativeType("boolean");
      return null;
    }

    @Override
    public Void caseNoObjectType() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void caseFunctionType(FunctionType type) {
      if ("Function".equals(type.getReferenceName())) {
        appendText("Function");
        return null;
      }
      appendText("function(");

      if (type.isConstructor()) {
        appendText("new: ");
        type.getTypeOfThis().visit(this);
        if (type.getParameters().iterator().hasNext()) {
          appendText(", ");
        }
      } else if (!type.getTypeOfThis().isUnknownType()
          || type.getTypeOfThis() instanceof NamedType) {
        appendText("this: ");
        type.getTypeOfThis().visit(this);
        if (type.getParameters().iterator().hasNext()) {
          appendText(", ");
        }
      }

      Iterator<Node> parameters = type.getParameters().iterator();
      while (parameters.hasNext()) {
        Node node = parameters.next();
        if (node.isVarArgs()) {
          appendText("...");
        }

        if (node.getJSType().isUnionType()) {
          caseUnionType((UnionType) node.getJSType(), node.isOptionalArg());
        } else {
          node.getJSType().visit(this);
        }

        if (node.isOptionalArg()) {
          appendText("=");
        }

        if (parameters.hasNext()) {
          appendText(", ");
        }
      }
      appendText(")");

      if (type.getReturnType() != null) {
        appendText(": ");
        type.getReturnType().visit(this);
      }
      return null;
    }

    @Override
    public Void caseObjectType(ObjectType type) {
      if (type.isRecordType()) {
        caseRecordType(type);
      } else if (type.isInstanceType()) {
        caseInstanceType(type);
      } else if (type instanceof PrototypeObjectType) {
        casePrototypeObjectType((PrototypeObjectType) type);
      } else {
        throw new UnsupportedOperationException();
      }
      return null;
    }

    private void casePrototypeObjectType(PrototypeObjectType type) {
      if (type.getOwnerFunction() != null) {
        ObjectType obj = type.getOwnerFunction().getTypeOfThis().toObjectType();

        NominalType nominalType = resolve(obj.getConstructor());
        if (nominalType != null) {
          appendLink(
              getDisplayName(nominalType) + ".prototype",
              getFilePath(nominalType).getFileName().toString());
        } else {
          caseInstanceType(obj.getReferenceName() + ".prototype", obj);
        }
      } else if (!type.getOwnPropertyNames().isEmpty()) {
        NominalType nominalType = resolve(type);
        if (nominalType != null) {
          appendLink(
              getDisplayName(nominalType),
              getFilePath(nominalType).getFileName().toString());
        } else {
          caseRecordType(type);
        }
      } else {
        verify("{}".equals(type.toString()), "Unexpected type: %s", type);
        type.getImplicitPrototype().visit(this);
      }
    }

    private void caseInstanceType(ObjectType type) {
      NominalType nominalType = resolve(type.getConstructor());
      String displayName = nominalType == null
          ? type.getReferenceName()
          : getDisplayName(nominalType);
      caseInstanceType(displayName, type);
    }

    private void caseInstanceType(String displayName, ObjectType type) {
      TypeLink link = getLink(type.getConstructor());
      if (link == null) {
        link = getExternLink(type.getReferenceName());
        appendLink(displayName, link == null ? "" : link.getHref());
      } else {
        appendLink(displayName, link.getHref());
      }
    }

    private void caseRecordType(final ObjectType type) {
      appendText("{");
      Iterator<Property> properties = FluentIterable.from(type.getOwnPropertyNames())
          .transform(new Function<String, Property>() {
            @Override
            public Property apply(String input) {
              return type.getOwnSlot(input);
            }
          })
          .filter(new Predicate<Property>() {
            @Override
            public boolean apply(@Nullable Property input) {
              return input != null && !input.getType().isNoType();
            }
          })
          .iterator();
      while (properties.hasNext()) {
        Property property = properties.next();
        appendText(property.getName() + ": ");
        property.getType().visit(this);
        if (properties.hasNext()) {
          appendText(", ");
        }
      }
      appendText("}");
    }

    @Override
    public Void caseUnknownType() {
      appendText("?");
      return null;
    }

    @Override
    public Void caseNullType() {
      appendNativeType("null");
      return null;
    }

    @Override
    public Void caseNamedType(NamedType type) {
      TypeLink link = getLink(type.getReferenceName());
      if (link != null) {
        appendLink(link);
      } else if (DossierModule.isExternModule(type.getReferenceName())) {
        appendText(DossierModule.externToOriginalName(type.getReferenceName()));
      } else {
        appendText(type.getReferenceName());
      }
      return null;
    }

    @Override
    public Void caseProxyObjectType(ProxyObjectType type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void caseNumberType() {
      appendNativeType("number");
      return null;
    }

    @Override
    public Void caseStringType() {
      appendNativeType("string");
      return null;
    }

    @Override
    public Void caseVoidType() {
      appendNativeType("undefined");
      return null;
    }

    @Override
    public Void caseUnionType(UnionType type) {
      caseUnionType(type, false);
      return null;
    }

    private void caseUnionType(UnionType type, boolean filterVoid) {
      int numAlternates = 0;
      int nullAlternates = 0;
      int voidAlternates = 0;
      boolean containsNonNullable = false;
      for (JSType alternate : type.getAlternates()) {
        numAlternates += 1;
        if (alternate.isNullType()) {
          nullAlternates += 1;
        }
        if (alternate.isVoidType() && filterVoid) {
          voidAlternates += 1;
        }
        containsNonNullable = containsNonNullable
            || (!alternate.isNullable()
            && !alternate.isInstanceType()
            && !(alternate instanceof NamedType));
      }

      Iterable<JSType> alternates = type.getAlternates();
      if (nullAlternates > 0 || voidAlternates > 0) {
        numAlternates -= nullAlternates;
        numAlternates -= voidAlternates;

        alternates = filter(alternates, new Predicate<JSType>() {
          @Override
          public boolean apply(JSType input) {
            return !input.isNullType() && !input.isVoidType();
          }
        });
      }

      if (containsNonNullable && nullAlternates > 0) {
        appendText("?");
      }

      if (numAlternates == 1) {
        alternates.iterator().next().visit(this);
      } else {
        appendText("(");
        Iterator<JSType> types = alternates.iterator();
        while (types.hasNext()) {
          types.next().visit(this);
          if (types.hasNext()) {
            appendText("|");
          }
        }
        appendText(")");
      }
    }

    @Override
    public Void caseTemplatizedType(TemplatizedType type) {
      type.getReferencedType().visit(this);
      appendText("<");
      Iterator<JSType> types = type.getTemplateTypes().iterator();
      while (types.hasNext()) {
        types.next().visit(this);
        if (types.hasNext()) {
          appendText(", ");
        }
      }
      appendText(">");
      return null;
    }

    @Override
    public Void caseTemplateType(TemplateType templateType) {
      appendText(templateType.getReferenceName());
      return null;
    }
  }
}
