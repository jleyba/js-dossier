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

import static com.google.common.base.Preconditions.checkState;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.internal.CircularDependencyProxy;
import java.util.HashMap;
import java.util.Map;

/**
 * A custom scope that provides simple controls for entering and exiting the scope.
 * This class is not thread-safe.
 */
final class ExplicitScope implements Scope {
  private static final Object NULL_SENTINEL = new Object();

  private Map<Key<?>, Object> scope;

  public void enter() {
    checkState(scope == null, "Already in scope");
    scope = new HashMap<>();
  }

  public void exit() {
    scope = null;
  }

  @Override
  public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscoped) {
    return new Provider<T>() {
      @Override
      public T get() {
        if (scope == null) {
          throw new OutOfScopeException("Not in scope");
        }

        Object value = scope.get(key);
        if (value == null) {
          T provided = unscoped.get();
          if (provided instanceof CircularDependencyProxy) {
            return provided;
          }
          value = (provided == null) ? NULL_SENTINEL : provided;
          scope.put(key, value);
        }

        @SuppressWarnings("unchecked")
        T result = (value != NULL_SENTINEL) ? (T) value : null;
        return result;
      }
    };
  }
}
