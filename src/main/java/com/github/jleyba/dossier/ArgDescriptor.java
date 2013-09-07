package com.github.jleyba.dossier;

import com.google.common.base.Strings;
import com.google.javascript.rhino.JSTypeExpression;

import javax.annotation.Nullable;

/**
 * Describes a function argument.
 */
class ArgDescriptor {

  private final String name;
  private final JSTypeExpression type;
  private final String description;

  ArgDescriptor(String name, @Nullable JSTypeExpression type, @Nullable String description) {
    this.name = name;
    this.type = type;
    this.description = Strings.nullToEmpty(description);
  }

  String getName() {
    return name;
  }

  @Nullable JSTypeExpression getType() {
    return type;
  }

  String getDescription() {
    return description;
  }
}
