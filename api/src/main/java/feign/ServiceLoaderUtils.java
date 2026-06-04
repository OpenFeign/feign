/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import java.util.ServiceLoader;

final class ServiceLoaderUtils {

  private static final String CORE_PACKAGE_PREFIX = "feign.core.";
  private static final String CORE_DEFAULTS_CLASS = "feign.core.CoreDefaults";

  private static volatile FeignDefaults defaults;

  private ServiceLoaderUtils() {
    // Utility class
  }

  /**
   * Resolves the {@link FeignDefaults} provider once and caches it. A single {@link ServiceLoader}
   * pass serves every default a builder needs, instead of one classpath scan per component.
   */
  static FeignDefaults defaults() {
    FeignDefaults result = defaults;
    if (result == null) {
      synchronized (ServiceLoaderUtils.class) {
        result = defaults;
        if (result == null) {
          result = resolve();
          defaults = result;
        }
      }
    }
    return result;
  }

  private static FeignDefaults resolve() {
    ServiceLoader<FeignDefaults> loader =
        ServiceLoader.load(FeignDefaults.class, FeignDefaults.class.getClassLoader());
    FeignDefaults coreImpl = null;
    FeignDefaults nonCoreImpl = null;
    for (FeignDefaults impl : loader) {
      if (impl.getClass().getName().startsWith(CORE_PACKAGE_PREFIX)) {
        coreImpl = impl;
      } else {
        nonCoreImpl = impl;
      }
    }
    if (nonCoreImpl != null) {
      return nonCoreImpl;
    }
    if (coreImpl != null) {
      return coreImpl;
    }
    try {
      return FeignDefaults.class.cast(
          Class.forName(CORE_DEFAULTS_CLASS).getDeclaredConstructor().newInstance());
    } catch (Exception e) {
      throw new IllegalStateException(
          "No feign.FeignDefaults provider found. Ensure feign-core is on the classpath.", e);
    }
  }
}
