// Copyright 2013 Jason Leyba
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.UnionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import javax.annotation.Nullable;

class Descriptor {

  private final String name;
  @Nullable private final JSType type;
  @Nullable private final Descriptor parent;
  @Nullable private final JSDocInfo info;

  private boolean namespace = false;

  private final Set<Descriptor> children = new HashSet<>();

  Descriptor(String name, @Nullable JSType type, @Nullable JSDocInfo info) {
    this(name, type, info, null);
  }

  Descriptor(
      String name, @Nullable JSType type, @Nullable JSDocInfo info,
      @Nullable Descriptor parent) {
    this.name = name;
    this.type = type;
    this.info = info;
    this.parent = parent;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, parent);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Descriptor) {
      Descriptor that = (Descriptor) o;
      return this.name.equals(that.name)
          && Objects.equals(this.type, that.type)
          && Objects.equals(this.parent, that.parent);
    }
    return false;
  }

  String getName() {
    return name;
  }

  String getFullName() {
    if (parent != null) {
      return parent.getFullName() + "." + name;
    }
    return name;
  }

  @Nullable JSType getType() {
    return type;
  }

  @Nullable JSDocInfo getInfo() {
    return info;
  }

  @Nullable String getSource() {
    if (info != null) {
      return info.getSourceName();
    }

    // If this descriptor doesn't have an explicit source, but all of its children share
    // a source, then we can use that. This will be the case for goog.provide'd namespaces.
    Set<String> sources = new HashSet<>();
    for (Descriptor child : children) {
      if (child.info != null && child.info.getSourceName() != null) {
        if (sources.add(child.info.getSourceName()) && sources.size() > 1) {
          return null;  // Children are distributed amongst multiple files.
        }
      }
    }
    return sources.isEmpty() ? null : sources.iterator().next();
  }

  /**
   * Returns the line number this instance's described type is defined on in its source file;
   * will trivially return 0 if this line number cannot be determined.
   */
  int getLineNum() {
    if (info != null) {
      Node node = info.getAssociatedNode();
      if (node != null) {
        return Math.max(node.getLineno(), 0);
      }
    }
    return 0;
  }

  boolean isNamespace() {
    return namespace;
  }

  void setIsNamespace(boolean isNamespace) {
    namespace = isNamespace;
  }

  Iterable<Descriptor> getChildren() {
    return Iterables.unmodifiableIterable(children);
  }

  ObjectType toObjectType() {
    checkState(isObject());
    ObjectType obj = ObjectType.cast(type);
    if (obj == null && type.isUnionType()) {
      for (JSType t : ((UnionType) type).getAlternates()) {
        obj = ObjectType.cast(t);
        if (obj != null) {
          break;
        }
      }
    }
    checkState(obj != null);
    return obj;
  }

  boolean isObject() {
    return type != null && type.isObject();
  }

  boolean isFunction() {
    return type != null && type.isFunctionType();
  }

  boolean isConstructor() {
    if (info != null) {
      return info.isConstructor();
    }

    return !isInterface() && type != null && type.isConstructor();
  }

  boolean isInterface() {
    if (info != null) {
      return info.isInterface();
    }
    return type != null && type.isInterface();
  }

  boolean isEnum() {
    return (type != null && type.isEnumType())
        || (info != null && info.getEnumParameterType() != null);
  }

  boolean isDeprecated() {
    return (info != null && info.isDeprecated());
  }

  @Nullable
  String getDeprecationReason() {
    checkState(isDeprecated(), "%s is not deprecated", getFullName());
    assert info != null;
    return info.getDeprecationReason();
  }

  /**
   * Returns all of the super types for this descriptor as a stack with this descriptor at the
   * bottom and the root ancestor at the top (Object is excluded as it is implied).
   *
   * <p>The returned stack will be empty if this descriptor is not for a class.
   */
  Stack<String> getAllTypes(JSTypeRegistry registry) {
    Stack<String> stack = new Stack<>();
    if (!isConstructor()) {
      return stack;
    }
    stack.push(getFullName());
    for (String type = getBaseType(getFullName(), registry);
         type != null;
         type = getBaseType(type, registry)) {
      stack.push(type);
    }
    return stack;
  }

  /**
   * Returns all of the interfaces that this type implements. The returned set will be empty if
   * this descriptor is not for a class.
   */
  Set<String> getImplementedInterfaces(JSTypeRegistry registry) {
    Set<String> interfaces = new HashSet<>();
    if (!isConstructor()) {
      return interfaces;
    }

    Stack<String> allTypes = getAllTypes(registry);
    while (!allTypes.isEmpty()) {
      String name = allTypes.pop();
      JSType type = registry.getType(name);
      if (type != null) {
        JSDocInfo info = type.getJSDocInfo();
        for (JSTypeExpression expr : info.getImplementedInterfaces()) {
          interfaces.add(expr.evaluate(null, registry).toString());
        }
      }
    }

    return interfaces;
  }

  /**
   * Returns the interfaces extended by this type. If this descriptor is not for an interface,
   * the returned set will be empty.
   */
  Set<String> getExtendedInterfaces(JSTypeRegistry registry) {
    if (!isInterface()) {
      return new HashSet<>();
    }
    return getExtendedInterfaces(info, registry);
  }

  /**
   * Returns the names of types this object can be assigned.  That is, if {@code x} is an
   * an instance of class {@code X}, which extends {@code Y} and implements {@code Z},
   * {@code x} may be assigned to {@code X}, {@code Y}, or {@code Z}.
   */
  Iterable<String> getAssignableTypes(JSTypeRegistry registry) {
    if (isConstructor()) {
      return getAllTypes(registry);
    } else if (isInterface()) {
      return Iterables.concat(
          Lists.newArrayList(getFullName()),
          getExtendedInterfaces(registry));
    } else {
      return ImmutableList.of();
    }
  }

  /**
   * Returns the argument descriptors for this instance.
   *
   * @throws IllegalStateException If this instance does not describe a function.
   */
  List<ArgDescriptor> getArgs() {
    checkState(isConstructor() || isFunction(), "%s is not a function!", getFullName());
    ObjectType obj = toObjectType();
    if (isConstructor() && obj.getConstructor() != null) {
      obj = obj.getConstructor();
    }

    Node source = ((FunctionType) obj).getSource();
    if (source == null) {
      if (info != null) {
        // If we don't have access to the function node, assume the JSDocInfo has the correct info.
        List<ArgDescriptor> args = new LinkedList<>();
        for (JSDocInfo.Marker marker : info.getMarkers()) {
          if ("param".equals(marker.getAnnotation().getItem())) {
            String name = marker.getNameNode().getItem().getString();
            args.add(new ArgDescriptor(
                name,
                info.getParameterType(name),
                info.getDescriptionForParameter(name)));
          }
        }
        return args;
      }
      return new ArrayList<>();
    }

    // JSDocInfo does not guarantee parameter names will be returned in the order declared,
    // so we have to parse the function declaration.
    Node paramList = source  // function node
        .getFirstChild()     // name node
        .getNext();          // param list node
    checkState(paramList.isParamList());
    List<String> names = Lists.newArrayList();
    for (Node paramName = paramList.getFirstChild(); paramName != null; paramName = paramName.getNext()) {
      names.add(paramName.getString());
    }
    return Lists.transform(names, new Function<String, ArgDescriptor>() {
      @Override
      public ArgDescriptor apply(String name) {
        if (info == null) {
          return new ArgDescriptor(name, null, "");
        }
        return new ArgDescriptor(
            name, info.getParameterType(name), info.getDescriptionForParameter(name));
      }
    });
  }

  /**
   * Returns the list of (static) properties defined on this type.
   */
  List<Descriptor> getProperties() {
    List<Descriptor> properties = new LinkedList<>();
    if (!isObject()) {
      return properties;
    }

    ObjectType obj = toObjectType();
    if (isConstructor() && obj.getConstructor() != null) {
      obj = obj.getConstructor();
    }

    for (String prop : obj.getOwnPropertyNames()) {
      if ("prototype".equals(prop)) {
        continue;  // Collected separately.
      }

      // Sometimes the JSCompiler picks up the builtin call and apply functions off of a
      // function object.  We should always skip these.
      if (type != null && type.isFunctionType()
          && ("apply".equals(prop) || "call".equals(prop))) {
        continue;
      }

      Node node = obj.getPropertyNode(prop);
      if (null == node) {
        continue;
      }

      JSDocInfo info = node.getJSDocInfo();
      if (null == info && null != node.getParent() && node.getParent().isAssign()) {
        info = node.getParent().getJSDocInfo();
      }

      properties.add(new Descriptor(prop, obj.getPropertyType(prop), info, this));
    }

    return properties;
  }

  /**
   * Returns the instance properties defined on this type.
   */
  Set<Descriptor> getInstanceProperties() {
    Set<Descriptor> properties = new HashSet<>();
    if (!isConstructor() && !isInterface()) {
      return properties;
    }

    ObjectType obj = toObjectType();
    if ((isConstructor() || isInterface()) && obj.getConstructor() != null) {
      obj = obj.getConstructor();
    }

    Descriptor protoDescriptor = new Descriptor("prototype", null, null, this);

    ObjectType instance = ((FunctionType) obj).getInstanceType();
    for (String prop : instance.getOwnPropertyNames()) {
      Node node = instance.getPropertyNode(prop);
      if (null == node) {
        continue;
      }

      JSDocInfo info = node.getJSDocInfo();
      if (null == info && null != node.getParent() && node.getParent().isAssign()) {
        info = node.getParent().getJSDocInfo();
      }

      properties.add(new Descriptor(prop, instance.getPropertyType(prop), info, protoDescriptor));
    }

    ObjectType proto = ((FunctionType) obj).getPrototype();
    for (String prop : proto.getOwnPropertyNames()) {
      if ("constructor".equals(prop) || "prototype".equals(prop)) {
        continue;
      }

      Node node = proto.getPropertyNode(prop);
      if (null == node) {
        continue;
      }

      JSDocInfo info = node.getJSDocInfo();
      if (null == info && null != node.getParent() && node.getParent().isAssign()) {
        info = node.getParent().getJSDocInfo();
      }

      properties.add(new Descriptor(prop, proto.getPropertyType(prop), info, protoDescriptor));
    }

    return properties;
  }

  boolean hasOwnInstanceProprety(String name) {
    if (!isConstructor() && !isInterface()) {
      return false;
    }
    ObjectType obj = toObjectType();
    if (isConstructor() && obj.getConstructor() != null) {
      obj = obj.getConstructor();
    }
    FunctionType ctor = (FunctionType) obj;
    return ctor.getInstanceType().hasOwnProperty(name)
        || ctor.getPrototype().hasOwnProperty(name);
  }

  private static Set<String> getExtendedInterfaces(JSDocInfo info, JSTypeRegistry registry) {
    Set<String> interfaces = new HashSet<>();
    if (info == null) {
      return interfaces;
    }

    for (JSTypeExpression expression : info.getExtendedInterfaces()) {
      JSType type = expression.evaluate(null, registry);
      if (interfaces.add(type.toString())) {
        interfaces.addAll(getExtendedInterfaces(type.getJSDocInfo(), registry));
      }
    }

    return interfaces;
  }

  @Nullable
  private static String getBaseType(String name, JSTypeRegistry registry) {
    JSType type = registry.getType(name);
    if (type == null) {
      return null;
    }

    JSDocInfo info = type.getJSDocInfo();
    if (info == null) {
      return null;
    }

    JSTypeExpression baseType = info.getBaseType();
    if (baseType == null) {
      return null;
    }

    return baseType.evaluate(null, registry).toString();
  }
}
