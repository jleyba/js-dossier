package com.github.jsdossier;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.jscomp.DossierCompiler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Tests for {@link ProvidedSymbolsCollectionPass}.
 */
@RunWith(JUnit4.class)
public class ProvidedSymbolsCollectionPassTest {

  private FileSystem fileSystem;
  private TypeRegistry typeRegistry;
  private CompilerUtil util;

  @Before
  public void setUp() {
    DossierCompiler compiler = new DossierCompiler(System.err, ImmutableSet.<Path>of());

    fileSystem = Jimfs.newFileSystem();
    typeRegistry = new TypeRegistry(compiler.getTypeRegistry());

    ProvidedSymbolsCollectionPass pass = new ProvidedSymbolsCollectionPass(
        compiler, typeRegistry, fileSystem);
    util = new CompilerUtil(compiler, createOptions(pass));
  }

  @Test
  public void collectsProvidedSymbols() {
    util.compile(FileSystems.getDefault().getPath("foo/bar.js"),
        "goog.provide('Foo');",
        "goog.provide('foo.Bar');",
        "goog.provide('foo.bar.Baz');",
        "goog.provide('one.two.three.Four');");

    assertThat(typeRegistry.getProvidedSymbols()).containsExactly(
        "Foo", "foo.Bar", "foo.bar.Baz", "one.two.three.Four");
    assertThat(typeRegistry.getImplicitNamespaces()).containsExactly(
        "Foo", "foo", "foo.Bar", "foo.bar", "foo.bar.Baz",
        "one", "one.two", "one.two.three", "one.two.three.Four");
  }

  @Test
  public void collectsClosureModules() {
    util.compile(FileSystems.getDefault().getPath("module.js"),
        "goog.module('sample.module');");

    assertThat(typeRegistry.getProvidedSymbols()).containsExactly("sample.module");
    assertThat(typeRegistry.getImplicitNamespaces()).containsExactly("sample", "sample.module");

    ModuleDescriptor module = typeRegistry.getModuleDescriptor("sample.module");
    assertThat(module).isNotNull();
    assertFalse(module.isCommonJsModule());
  }

  @Test
  public void collectsJsDocForModuleInternalVars() {
    util.compile(FileSystems.getDefault().getPath("module.js"),
        "goog.module('sample.module');",
        "",
        "function noDocs() {}",
        "",
        "/** Has docs. */",
        "function foo() {}",
        "",
        "/** More docs */",
        "var bar = function() {};",
        "",
        "/** value-less var docs. */",
        "var baz;",
        "",
        "/** Var docs */",
        "var x = 123;",
        "var noDocs2 = 345;");

    assertThat(typeRegistry.getProvidedSymbols()).containsExactly("sample.module");
    assertThat(typeRegistry.getImplicitNamespaces()).containsExactly("sample", "sample.module");
    ModuleDescriptor module = typeRegistry.getModuleDescriptor("sample.module");
    assertThat(module).isNotNull();

    assertThat(module.getInternalVarDocs().keySet()).containsExactly("foo", "bar", "baz", "x");
  }

  @Test
  public void buildsExportToInternalNameMap() {
    util.compile(FileSystems.getDefault().getPath("module.js"),
        "goog.module('sample.module');",
        "",
        "function internalFunction1() {}",
        "var internalFunction2 = function() {}",
        "var internalX = 1234;",
        "var internalObj = {};",
        "",
        "exports.publicFunction1 = internalFunction1",
        "exports.publicFunction2 = internalFunction2",
        "exports.publicX = internalX",
        "exports = internalObj");

    assertThat(typeRegistry.getProvidedSymbols()).containsExactly("sample.module");
    assertThat(typeRegistry.getImplicitNamespaces()).containsExactly("sample", "sample.module");

    ModuleDescriptor module = typeRegistry.getModuleDescriptor("sample.module");
    assertThat(module).isNotNull();

    assertThat(module.getExportedNames().keySet()).containsExactly(
        "internalFunction1", "internalFunction2", "internalX", "internalObj");
    assertThat(module.getExportedNames()).containsEntry(
        "internalFunction1", "sample.module.publicFunction1");
    assertThat(module.getExportedNames()).containsEntry(
        "internalFunction2", "sample.module.publicFunction2");
    assertThat(module.getExportedNames()).containsEntry(
        "internalX", "sample.module.publicX");
    assertThat(module.getExportedNames()).containsEntry(
        "internalObj", "sample.module");
  }

  @Test
  public void identifiesCommonJsModules() {
    DossierCompiler compiler = new DossierCompiler(System.err,
        ImmutableSet.of(fileSystem.getPath("/module/foo.js")));
    typeRegistry = new TypeRegistry(compiler.getTypeRegistry());
    ProvidedSymbolsCollectionPass pass = new ProvidedSymbolsCollectionPass(
        compiler, typeRegistry, fileSystem);
    util = new CompilerUtil(compiler, createOptions(pass));

    util.compile(fileSystem.getPath("/module/foo.js"), "exports.foo = function() {};");

    ModuleDescriptor module = typeRegistry.getModuleDescriptor("dossier$$module__$module$foo");
    assertEquals(fileSystem.getPath("/module/foo.js"), module.getPath());
    assertTrue(module.isCommonJsModule());
  }

  private static CompilerOptions createOptions(CompilerPass pass) {
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    Multimap<CustomPassExecutionTime, CompilerPass> customPasses;
    customPasses = Multimaps.newListMultimap(
        Maps.<CustomPassExecutionTime, Collection<CompilerPass>>newHashMap(),
        new Supplier<List<CompilerPass>>() {
          @Override
          public List<CompilerPass> get() {
            return newLinkedList();
          }
        });
    customPasses.put(CustomPassExecutionTime.BEFORE_CHECKS, pass);
    options.setCustomPasses(customPasses);

    return options;
  }
}
