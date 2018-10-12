/*
 Copyright 2013-2018 Jason Leyba
 
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

package com.google.javascript.jscomp;

/**
 * A horrible package invading utility that provides access to class with package-private
 * constructors.
 * 
 * TODO(jleyba): get the compiler to open up visibilities so this isn't needed.
 */
public final class DossierSnooper {
  private DossierSnooper() {}
  
  public static Es6RewriteDestructuring createEs6RewriteDestructuring(AbstractCompiler compiler) {
    return new Es6RewriteDestructuring.Builder(compiler).build();
  }
}
