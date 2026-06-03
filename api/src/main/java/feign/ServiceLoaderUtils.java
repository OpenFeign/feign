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

  private ServiceLoaderUtils() {
    // Utility class
  }

  public static <T> T resolve(Class<T> serviceClass, String defaultClassName) {
    ServiceLoader<T> loader = ServiceLoader.load(serviceClass, serviceClass.getClassLoader());
    T coreImpl = null;
    T nonCoreImpl = null;
    for (T impl : loader) {
      if (isCoreImplementation(impl.getClass().getName())) {
        if (impl.getClass().getName().equals(defaultClassName)) {
          coreImpl = impl;
        } else if (coreImpl == null) {
          coreImpl = impl;
        }
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
    if (defaultClassName != null) {
      try {
        Class<?> clazz = Class.forName(defaultClassName);
        return serviceClass.cast(clazz.getDeclaredConstructor().newInstance());
      } catch (Exception e) {
        // ignore
      }
    }
    return null;
  }

  private static boolean isCoreImplementation(String className) {
    return className.startsWith("feign.core.");
  }
}
