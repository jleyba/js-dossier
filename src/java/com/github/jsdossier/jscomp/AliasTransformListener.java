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

package com.github.jsdossier.jscomp;

import com.github.jsdossier.annotations.Input;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.rhino.SourcePosition;

import java.nio.file.FileSystem;

import javax.inject.Inject;

/**
 * Records alias transformations.
 */
public final class AliasTransformListener implements CompilerOptions.AliasTransformationHandler {
  
  private final FileSystem inputFs;
  private final TypeRegistry2 typeRegistry;

  @Inject
  AliasTransformListener(@Input FileSystem inputFs, TypeRegistry2 typeRegistry) {
    this.inputFs = inputFs;
    this.typeRegistry = typeRegistry;
  }

  @Override
  public CompilerOptions.AliasTransformation logAliasTransformation(
      String sourceFile, SourcePosition<CompilerOptions.AliasTransformation> position) {
    AliasRegion region = AliasRegion.builder()
        .setPath(inputFs.getPath(sourceFile))
        .setRange(position)
        .build();
    typeRegistry.addAliasRegion(region);
    position.setItem(region);
    return region;
  }
}
