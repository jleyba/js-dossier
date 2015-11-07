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

import static com.google.common.collect.Iterables.getOnlyElement;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;
import java.nio.file.Paths;

import javax.inject.Inject;

/**
 * Tests for tracking aliases created by the compiler.
 */
@RunWith(JUnit4.class)
public class AliasTransformationTest {
  
  @Rule
  public GuiceRule guice = GuiceRule.builder(this).build();

  @Inject @Input private FileSystem inputFs;
  @Inject private TypeRegistry typeRegistry;
  @Inject private JSTypeRegistry jsTypeRegistry;
  @Inject private CompilerUtil util;
  
  @Test
  public void doesNotResolveAliasIfThereWereNoTransformations() {
    util.compile(inputFs.getPath("foo/bar.js"),
        "/** @constructor */ function X() {};",
        "/** @constructor */ function Y() {};");
    jsTypeRegistry.getType("foo.bar");
  }
}
