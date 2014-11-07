package com.github.jleyba.dossier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.javascript.rhino.jstype.JSType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DocRegistry}.
 */
@RunWith(JUnit4.class)
public class DocRegistryTest {

  private final DocRegistry registry = new DocRegistry();

  @Test
  public void resolveReturnsNullIfNotFound() {
    assertNull(registry.resolve("not.there"));
  }

  @Test
  public void resolveExtern() {
    Descriptor extern = object("Element").build();
    registry.addExtern(extern);

    assertSame(extern, registry.resolve("Element"));
  }

  @Test
  public void resolveType() {
    Descriptor type = object("Element").build();
    registry.addType(type);

    assertSame(type, registry.resolve("Element"));
  }

  @Test
  public void resolvingJustPrototypeReturnsConstructorDescriptor() {
    Descriptor type = object("Element").build();
    registry.addType(type);

    assertSame(type, registry.resolve("Element.prototype"));
  }

  @Test
  public void resolveInstanceProperty() {
    Descriptor bar = object("Foo.prototype.bar").build();
    Descriptor foo = object("Foo")
        .addInstanceProperty(bar)
        .build();

    registry.addType(foo);

    assertSame(bar, registry.resolve("Foo.prototype.bar"));
    assertSame(bar, registry.resolve("Foo#bar"));
  }

  @Test
  public void resolveStaticProperty() {
    Descriptor func = object("foo.bar.baz.DoWork").build();
    Descriptor namespace = object("foo.bar.baz")
        .addStaticProperty(func)
        .build();

    registry.addType(namespace);

    assertSame(func, registry.resolve(func.getFullName()));
  }

  @Test
  public void resolveNamespacedProperty() {
    Descriptor func = object("foo.bar.baz.DoWork").build();

    Descriptor namespace = object("foo.bar")
        .addStaticProperty(object("foo.bar.baz").addStaticProperty(func))
        .build();

    registry.addType(namespace);

    assertSame(func, registry.resolve(func.getFullName()));
  }

  @Test
  public void resolveNamespacedInstanceProperty() {
    Descriptor func = object("foo.bar.baz.SomeClass.prototype.doWork").build();

    Descriptor namespace = object("foo.bar")
        .addStaticProperty(object("foo.bar.baz")
            .addStaticProperty(object("foo.bar.baz.SomeClass")
                .addInstanceProperty(func)))
        .build();

    registry.addType(namespace);

    assertSame(func, registry.resolve("foo.bar.baz.SomeClass.prototype.doWork"));
    assertSame(func, registry.resolve("foo.bar.baz.SomeClass#doWork"));
  }

  @Test
  public void resolvesExternName() {
    Descriptor type = object("Element").build();
    registry.addExtern(type);

    assertSame(type, registry.resolve("Element"));
  }

  @Test
  public void resolvesNamespacedExtern() {
    Descriptor type = object("foo.bar").build();
    registry.addExtern(type);

    assertSame(type, registry.resolve("foo.bar"));
  }

  @Test
  public void resolveModuleByModuleName() {
    ModuleDescriptor module = object("foo.bar").buildModule();

    registry.addModule(module);

    assertSame(module.getDescriptor(), registry.resolve("foo.bar"));
  }

  @Test
  public void resolveModuleByExports() {
    ModuleDescriptor module = object("foo.bar.exports").buildModule();

    registry.addModule(module);

    assertSame(module.getDescriptor(), registry.resolve("foo.bar.exports"));
  }

  @Test
  public void resolveExportedModuleApi() {
    ModuleDescriptor module = object("foo.bar.exports").buildModule();
    registry.addModule(module);

    Descriptor one = object("Baz.one").build();
    Descriptor two = object("Baz.prototype.two").build();

    Descriptor baz = object("Baz")
        .addStaticProperty(one)
        .addInstanceProperty(two)
        .build();
    module.addExportedProperty(baz);

    assertSame(baz, registry.resolve("foo.bar.exports.Baz"));
    assertSame(one, registry.resolve("foo.bar.exports.Baz.one"));
    assertSame(two, registry.resolve("foo.bar.exports.Baz.prototype.two"));
    assertSame(two, registry.resolve("foo.bar.exports.Baz#two"));
  }

  @Test
  public void resolveUnqualifiedExportedModuleApi() {
    ModuleDescriptor module = object("foo.bar.exports").buildModule();
    registry.addModule(module);

    Descriptor one = object("Baz.one").build();
    Descriptor two = object("Baz.prototype.two").build();

    Descriptor baz = object("Baz")
        .addStaticProperty(one)
        .addInstanceProperty(two)
        .build();
    module.addExportedProperty(baz);

    assertNull("Not given relative module", registry.resolve("Baz"));
    assertSame(baz, registry.resolve("Baz", module));
    assertSame(one, registry.resolve("Baz.one", module));
    assertSame(two, registry.resolve("Baz.prototype.two", module));
    assertSame(two, registry.resolve("Baz#two", module));
  }

  @Test
  public void resolvesNamespaceReferences() {
    Descriptor oneTwoThree = object("one.two.three").build();
    Descriptor oneTwo = object("one.two")
        .addStaticProperty(oneTwoThree)
        .build();
    Descriptor one = object("one")
        .addStaticProperty(oneTwo)
        .build();
    registry.addType(one);
    registry.addType(oneTwo);

    Descriptor ab = object("a.b").build();
    Descriptor a = object("a").addStaticProperty(ab).build();
    ModuleDescriptor module = object("module").buildModule();
    module.addExportedProperty(a);
    module.addExportedProperty(ab);
    registry.addModule(module);

    assertSame(oneTwoThree, registry.resolve("one.two.three."));
    assertSame(oneTwo, registry.resolve("one.two."));
    assertSame(one, registry.resolve("one."));

    assertSame(ab, registry.resolve("module.a.b."));
    assertSame(a, registry.resolve("module.a."));
    assertSame(module.getDescriptor(), registry.resolve("module."));
    assertSame(ab, registry.resolve("a.b.", module));
    assertSame(a, registry.resolve("a.", module));
  }

  @Test
  public void canIdentifyKnownTypes() {
    Descriptor fooBar = object("foo.bar").build();
    Descriptor foo = object("foo").addStaticProperty(fooBar).build();
    registry.addExtern(foo);
    registry.addExtern(fooBar);

    Descriptor oneTwo = object("one.two").build();
    Descriptor one = object("one").build();
    registry.addType(one);
    registry.addType(oneTwo);

    Descriptor ab = object("a.b").build();
    Descriptor a = object("a").addStaticProperty(ab).build();
    ModuleDescriptor module = object("module").buildModule();
    module.addExportedProperty(a);
    module.addExportedProperty(ab);
    registry.addModule(module);

    assertFalse(registry.isKnownType("not.there"));
    assertTrue(registry.isKnownType("foo"));
    assertTrue(registry.isKnownType("foo.bar"));
    assertTrue(registry.isKnownType("one"));
    assertTrue(registry.isKnownType("one.two"));
    assertTrue(registry.isKnownType("module"));
    assertTrue(registry.isKnownType("a"));
    assertTrue(registry.isKnownType("a.b"));
  }

  private static TestDescriptorBuilder object(String name) {
    return new TestDescriptorBuilder(name);
  }
}
