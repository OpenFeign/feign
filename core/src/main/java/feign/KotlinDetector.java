/*
 * Copyright 2012-2022 The Feign Authors
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
package feign;

import kotlin.Unit;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

@SuppressWarnings("unchecked")
public abstract class KotlinDetector {
  private static final boolean supportsKotlin;
  private static final Class<? extends Annotation> kotlinMetadata;

  static {
    ClassLoader classLoader = KotlinDetector.class.getClassLoader();
    supportsKotlin = ClassUtils.isPresent("feign.kotlin.MethodKt", classLoader);
    kotlinMetadata = supportsKotlin
        ? (Class<? extends Annotation>) ClassUtils.tryGetForName("kotlin.Metadata", classLoader)
        : null;
  }

  public static boolean isSuspendingFunction(Method method) {
    if (!KotlinDetector.isKotlinType(method.getDeclaringClass())) {
      return false;
    }

    Class<?>[] params = method.getParameterTypes();
    return params.length > 0
        && "kotlin.coroutines.Continuation".equals(params[params.length - 1].getName());
  }

  private static boolean isKotlinType(Class<?> clazz) {
    return supportsKotlin && clazz.getDeclaredAnnotation(kotlinMetadata) != null;
  }

  public static boolean isUnitType(Type type) {
    return supportsKotlin && Unit.class == type;
  }

  private static class ClassUtils {
    public static Class<?> tryGetForName(String name, ClassLoader classLoader) {
      try {
        return Class.forName(name, false, classLoader);
      } catch (ClassNotFoundException | NoClassDefFoundError ex) {
        return null;
      }
    }

    public static boolean isPresent(String className, ClassLoader classLoader) {
      try {
        return tryGetForName(className, classLoader) != null;
      } catch (IllegalAccessError err) {
        String message = String.format(
            "Readability mismatch in inheritance hierarchy of class [%s]: %s",
            className, err.getMessage());
        throw new IllegalStateException(message, err);
      } catch (Throwable ex) {
        return false;
      }
    }
  }
}
