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

import com.google.common.collect.ImmutableList;

/** Describes the template that should be used when generating documentation. */
interface DocTemplate {

  /** Returns the list of additional files that should be copied to the output directory. */
  ImmutableList<TemplateFile> getAdditionalFiles();

  /** Returns the list of JS files that should be loaded in HEAD by each generated file. */
  ImmutableList<TemplateFile> getHeadJs();

  /**
   * Returns the list of JS files that should be loaded at the end of the BODY in each generated
   * file.
   */
  ImmutableList<TemplateFile> getTailJs();

  /** Returns the list of CSS files that should be included in each generated file. */
  ImmutableList<TemplateFile> getCss();
}
