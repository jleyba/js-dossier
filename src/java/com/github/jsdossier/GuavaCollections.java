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
import com.google.common.collect.ImmutableSet;
import java.util.stream.Collector;

/** Utilities for working with Guava collections. */
final class GuavaCollections {
  private GuavaCollections() {}

  public static <T> Collector<T, ?, ImmutableSet<T>> toImmutableSet() {
    return Collector.of(
        ImmutableSet::<T>builder,
        ImmutableSet.Builder::add,
        (dst, src) -> dst.addAll(src.build()),
        ImmutableSet.Builder::build);
  }

  public static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return Collector.of(
        ImmutableList::<T>builder,
        ImmutableList.Builder::add,
        (dst, src) -> dst.addAll(src.build()),
        ImmutableList.Builder::build);
  }
}
