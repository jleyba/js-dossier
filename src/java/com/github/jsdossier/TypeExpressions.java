/*
Copyright 2013-2016 Jason Leyba

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

import com.github.jsdossier.proto.TypeExpression;

final class TypeExpressions {
  private TypeExpressions() {}

  public static final TypeExpression ANY_TYPE =
      TypeExpression.newBuilder().setAnyType(true).build();

  public static final TypeExpression UNKNOWN_TYPE =
      TypeExpression.newBuilder().setUnknownType(true).build();

  public static final TypeExpression VOID_TYPE =
      TypeExpression.newBuilder().setVoidType(true).build();

  public static final TypeExpression NULL_TYPE =
      TypeExpression.newBuilder().setNullType(true).build();
}
