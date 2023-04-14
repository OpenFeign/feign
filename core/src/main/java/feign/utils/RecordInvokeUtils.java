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

import static java.lang.invoke.MethodType.methodType;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import feign.Experimental;

/**
 * 
 * Utility methods for record serialization, using MethodHandles.
 * 
 * @author FrauBoes
 * @see RecComponent
 * @see <a href="https://github.com/FrauBoes/record-s11n-util">record-s11n-util</a>
 */
@Experimental
public class RecordInvokeUtils {
  private static final MethodHandle MH_IS_RECORD;
  private static final MethodHandle MH_GET_RECORD_COMPONENTS;
  private static final MethodHandle MH_GET_NAME;
  private static final MethodHandle MH_GET_TYPE;
  protected static final MethodHandles.Lookup LOOKUP;

  static {
    MethodHandle MH_isRecord;
    MethodHandle MH_getRecordComponents;
    MethodHandle MH_getName;
    MethodHandle MH_getType;
    LOOKUP = MethodHandles.lookup();
    try {
      // reflective machinery required to access the record components
      // without a static dependency on Java SE 14 APIs
      Class<?> c = Class.forName("java.lang.reflect.RecordComponent");
      MH_isRecord = LOOKUP.findVirtual(Class.class, "isRecord", methodType(boolean.class));
      MH_getRecordComponents = LOOKUP
          .findVirtual(Class.class, "getRecordComponents",
              methodType(Array.newInstance(c, 0).getClass()))
          .asType(methodType(Object[].class, Class.class));
      MH_getName = LOOKUP.findVirtual(c, "getName", methodType(String.class))
          .asType(methodType(String.class, Object.class));
      MH_getType = LOOKUP.findVirtual(c, "getGenericType", methodType(Type.class))
          .asType(methodType(Type.class, Object.class));
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      // pre-Java-14
      MH_isRecord = null;
      MH_getRecordComponents = null;
      MH_getName = null;
      MH_getType = null;
    } catch (IllegalAccessException unexpected) {
      throw new AssertionError(unexpected);
    }

    MH_IS_RECORD = MH_isRecord;
    MH_GET_RECORD_COMPONENTS = MH_getRecordComponents;
    MH_GET_NAME = MH_getName;
    MH_GET_TYPE = MH_getType;
  }

  /** Returns true if, and only if, the given class is a record class. */
  public static boolean isRecord(Class<?> type) {
    try {
      return (boolean) MH_IS_RECORD.invokeExact(type);
    } catch (WrongMethodTypeException e) {
      return false;
    } catch (Throwable t) {
      throw new RuntimeException("Could not determine type (" + type + ")");
    }
  }

  /**
   * Returns an ordered array of the record components for the given record class. The order is
   * imposed by the given comparator. If the given comparator is null, the order is that of the
   * record components in the record attribute of the class file.
   */
  public static <T> RecComponent[] recordComponents(Class<T> type,
                                                    Comparator<RecComponent> comparator) {
    try {
      Object[] rawComponents = (Object[]) MH_GET_RECORD_COMPONENTS.invokeExact(type);
      RecComponent[] recordComponents = new RecComponent[rawComponents.length];
      for (int i = 0; i < rawComponents.length; i++) {
        final Object comp = rawComponents[i];
        String name = (String) MH_GET_NAME.invokeExact(comp);
        Annotation[] recordComponentAnnotation =
            (Annotation[]) ((AnnotatedElement) comp).getAnnotations(),
            fieldAnnotation = type.getDeclaredField(name).getAnnotations(),
            annotations =
                Stream.of(recordComponentAnnotation, fieldAnnotation).flatMap(Stream::of).distinct()
                    .toArray(Annotation[]::new);
        recordComponents[i] = new RecComponent(name,
            (Type) MH_GET_TYPE.invokeExact(comp), i, annotations);
      }
      if (comparator != null)
        Arrays.sort(recordComponents, comparator);
      return recordComponents;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException("Could not retrieve record components (" + type.getName() + ")");
    }
  }

  /** Retrieves the value of the record component for the given record object. */
  public static Object componentValue(Object recordObject, RecComponent recordComponent) {
    try {
      MethodHandle MH_get = LOOKUP.findVirtual(recordObject.getClass(), recordComponent.name(),
          methodType((Class<?>) recordComponent.type()));
      return (Object) MH_get.invoke(recordObject);
    } catch (Throwable t) {
      throw new RuntimeException(
          "Could not retrieve record components (" + recordObject.getClass().getName() + ")");
    }
  }
  @SuppressWarnings("unchecked")
  public static <T> T invokeCanonicalConstructor(Class<T> recordType, Object[] args) {
    try {
      RecComponent[] recordComponents = recordComponents(recordType, null);
      Type[] paramTypes =
          Arrays.stream(recordComponents).map(RecComponent::type).toArray(Type[]::new);
      paramTypes = Arrays.stream(paramTypes)
          .map(v -> v instanceof ParameterizedType ? (Class<?>) ((ParameterizedType) v).getRawType()
              : (Class<?>) v)
          .toArray(Class[]::new);
      MethodHandle MH_canonicalConstructor = LOOKUP
          .findConstructor(recordType, methodType(void.class, (Class<?>[]) paramTypes))
          .asType(methodType(Object.class, (Class<?>[]) paramTypes));
      return (T) MH_canonicalConstructor.invokeWithArguments(args);
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException("Could not construct type (" + recordType.getName() + ")");
    }
  }
}
