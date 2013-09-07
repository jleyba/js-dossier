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
