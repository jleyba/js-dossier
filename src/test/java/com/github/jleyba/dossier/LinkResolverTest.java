package com.github.jleyba.dossier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.javascript.rhino.jstype.JSType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(JUnit4.class)
public class LinkResolverTest {

  @Test
  public void testGetLink() {
    Path outputDir = Paths.get("");
    DocRegistry mockRegistry = mock(DocRegistry.class);
    LinkResolver resolver = new LinkResolver(outputDir, mockRegistry);

    assertNull("No types are known", resolver.getLink("goog.Foo"));

    Descriptor mockGoog = mock(Descriptor.class);
    when(mockRegistry.getType("goog")).thenReturn(mockGoog);
    when(mockGoog.getFullName()).thenReturn("goog");
    assertEquals("namespace_goog.html#goog.Foo", resolver.getLink("goog.Foo"));
    assertEquals("namespace_goog.html#goog.Foo", resolver.getLink("goog.Foo()"));

    Descriptor mockGoogFoo = mock(Descriptor.class);
    when(mockRegistry.getType("goog.Foo")).thenReturn(mockGoogFoo);
    when(mockGoogFoo.getFullName()).thenReturn("goog.Foo");
    assertEquals("namespace_goog_Foo.html", resolver.getLink("goog.Foo"));
    assertEquals("namespace_goog_Foo.html", resolver.getLink("goog.Foo()"));

    assertEquals("namespace_goog_Foo.html#goog.Foo.bar", resolver.getLink("goog.Foo.bar"));
    assertEquals("namespace_goog_Foo.html#goog.Foo.bar", resolver.getLink("goog.Foo.bar()"));
    assertEquals("namespace_goog_Foo.html#goog.Foo$bar", resolver.getLink("goog.Foo#bar"));
    assertEquals("namespace_goog_Foo.html#goog.Foo$bar", resolver.getLink("goog.Foo#bar()"));
  }
}
