/*
 Copyright 2013-2015 Jason Leyba
 
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

import com.google.javascript.rhino.jstype.Property;

import java.util.Comparator;

/**
 * Compares properties by name.
 */
final class PropertyNameComparator implements Comparator<Property> {
  @Override
  public int compare(Property a, Property b) {
    return a.getName().compareTo(b.getName());
  }
}