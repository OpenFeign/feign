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
package feign.utils;

import feign.Experimental;

@Experimental
public final class BeanEvaluator {
  public static boolean isBean(Class<?> clazz) {
    if (clazz.isArray()) {
      return false;
    } else {
      Package pkg = clazz.getPackage();
      if (pkg == null) {
        return false;
      }
      String packageName = pkg.getName();
      if (packageName.startsWith("feign")) {
        return !clazz.isAnnotation();
      } else {
        return !packageName.startsWith("java") && !packageName.startsWith("javax")
            && !packageName.startsWith("jakarta");
      }
    }
  }
}
