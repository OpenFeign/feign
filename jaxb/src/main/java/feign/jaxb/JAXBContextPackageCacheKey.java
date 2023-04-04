/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.jaxb;

import java.util.Objects;

/**
 * Encapsulate data used to build the cache key of JAXBContext when created using package mode.
 */
final class JAXBContextPackageCacheKey implements JAXBContextCacheKey {

  private final String packageName;

  private final ClassLoader classLoader;

  JAXBContextPackageCacheKey(String packageName, ClassLoader classLoader) {
    this.packageName = packageName;
    this.classLoader = classLoader;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    JAXBContextPackageCacheKey that = (JAXBContextPackageCacheKey) o;
    return packageName.equals(that.packageName) && classLoader.equals(that.classLoader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packageName, classLoader);
  }
}
