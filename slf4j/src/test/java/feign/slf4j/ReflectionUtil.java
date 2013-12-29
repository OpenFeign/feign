/*
 * Copyright 2013 Netflix, Inc.
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
package feign.slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Lightweight approach to using reflection to bypass access restrictions for testing.  If this class grows, it may be
 * better to use a testing library instead, such as Powermock.
 */
class ReflectionUtil {
  static void setStaticField(Class<?> declaringClass, String fieldName, Object fieldValue) throws Exception {
    Field field = declaringClass.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(null, fieldValue);
  }

  static void invokeVoidNoArgMethod(Class<?> declaringClass, String methodName, Object instance) throws Exception {
    Method method = declaringClass.getDeclaredMethod(methodName);
    method.setAccessible(true);
    method.invoke(instance);
  }
}
