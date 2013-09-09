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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates {@link DocWriter} objects.
 */
class DocWriterFactory {

  private final LinkResolver resolver;

  DocWriterFactory(LinkResolver resolver) {
    this.resolver = checkNotNull(resolver);
  }

  /**
   * Creates a new {@link DocWriter} for the given {@code config}.
   */
  DocWriter createDocWriter(Config config, DocRegistry registry) {
    return new HtmlDocWriter(config, registry, resolver);
  }
}
