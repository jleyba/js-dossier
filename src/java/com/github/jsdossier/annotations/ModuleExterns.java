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

package com.github.jsdossier.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

/**
 * Qualifier for input files that should be processed as extern CommonJS module definitions. The
 * files will be provided as an {@link com.google.common.collect.ImmutableSet ImmutableSet} of
 * {@link java.nio.file.Path Path} objects.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleExterns {}
