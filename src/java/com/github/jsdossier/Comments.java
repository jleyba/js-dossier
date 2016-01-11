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

import com.github.jsdossier.proto.Comment;

/**
 * Comment utilities.
 */
final class Comments {
  private Comments() {}

  public static boolean isVacuousTypeComment(Comment comment) {
    return comment.getTokenCount() == 1
        && ("undefined".equals(comment.getToken(0).getText())
        || "void".equals(comment.getToken(0).getText())
        || "?".equals(comment.getToken(0).getText())
        || "*".equals(comment.getToken(0).getText()));
  }
}
