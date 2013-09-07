package com.github.jleyba.dossier;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;

/**
 * Creates {@link DocWriter} objects.
 */
class DocWriterFactory {

  private final LinkResolver resolver;

  DocWriterFactory(LinkResolver resolver) {
    this.resolver = checkNotNull(resolver);
  }

  /**
   * Creates a new {@link DocWriter} for the given {@code descriptor}.
   */
  DocWriter createDocWriter(Config config, DocRegistry registry, Descriptor descriptor) {
    return new HtmlDocWriter(config, registry, descriptor, resolver);
  }
}
