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
package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Handles default methods by directly invoking the default method code on the interface. The bindTo
 * method must be called on the result before invoke is called.
 */
final class DefaultMethodHandler implements MethodHandler {
  // Uses Java 7 MethodHandle based reflection. As default methods will only exist when
  // run on a Java 8 JVM this will not affect use on legacy JVMs.
  // When Feign upgrades to Java 7, remove the @IgnoreJRERequirement annotation.
  private final MethodHandle unboundHandle;

  // handle is effectively final after bindTo has been called...
  private MethodHandle handle;

  public DefaultMethodHandler(Method defaultMethod) {
    Class<?> declaringClass = defaultMethod.getDeclaringClass();

    try {
      Lookup lookup = readLookup(declaringClass);
      this.unboundHandle = lookup.unreflectSpecial(defaultMethod, declaringClass);
    } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException ex) {
      throw new IllegalStateException(ex);
    }

  }

  private Lookup readLookup(Class<?> declaringClass)
      throws IllegalAccessException, InvocationTargetException, NoSuchFieldException {
    try {
      return safeReadLookup(declaringClass);
    } catch (NoSuchMethodException e) {
      try {
        return androidLookup(declaringClass);
      } catch (InstantiationException | NoSuchMethodException instantiationException) {
        return legacyReadLookup();
      }
    }
  }

  public Lookup androidLookup(Class<?> declaringClass) throws InstantiationException,
      InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Lookup lookup;
    try {
      // Android 9+ double reflection
      Class<?> classReference = Class.class;
      Class<?>[] classType = new Class[] {Class.class};
      Method reflectedGetDeclaredConstructor = classReference.getDeclaredMethod(
          "getDeclaredConstructor",
          Class[].class);
      reflectedGetDeclaredConstructor.setAccessible(true);
      Constructor<?> someHiddenMethod =
          (Constructor<?>) reflectedGetDeclaredConstructor.invoke(Lookup.class, (Object) classType);
      lookup = (Lookup) someHiddenMethod.newInstance(declaringClass);
    } catch (IllegalAccessException ex0) {
      // Android < 9 reflection
      Constructor<Lookup> lookupConstructor = Lookup.class.getDeclaredConstructor(Class.class);
      lookupConstructor.setAccessible(true);
      lookup = lookupConstructor.newInstance(declaringClass);
    }
    return (lookup);
  }


  /**
   * equivalent to:
   *
   * <pre>
   * return MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
   * </pre>
   *
   * @param declaringClass
   * @return
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  private Lookup safeReadLookup(Class<?> declaringClass)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Lookup lookup = MethodHandles.lookup();

    Object privateLookupIn =
        MethodHandles.class.getMethod("privateLookupIn", Class.class, Lookup.class)
            .invoke(null, declaringClass, lookup);
    return (Lookup) privateLookupIn;
  }

  private Lookup legacyReadLookup() throws NoSuchFieldException, IllegalAccessException {
    Field field = Lookup.class.getDeclaredField("IMPL_LOOKUP");
    field.setAccessible(true);
    Lookup lookup = (Lookup) field.get(null);
    return lookup;
  }

  /**
   * Bind this handler to a proxy object. After bound, DefaultMethodHandler#invoke will act as if it
   * was called on the proxy object. Must be called once and only once for a given instance of
   * DefaultMethodHandler
   */
  public void bindTo(Object proxy) {
    if (handle != null) {
      throw new IllegalStateException(
          "Attempted to rebind a default method handler that was already bound");
    }
    handle = unboundHandle.bindTo(proxy);
  }

  /**
   * Invoke this method. DefaultMethodHandler#bindTo must be called before the first time invoke is
   * called.
   */
  @Override
  public Object invoke(Object[] argv) throws Throwable {
    if (handle == null) {
      throw new IllegalStateException(
          "Default method handler invoked before proxy has been bound.");
    }
    return handle.invokeWithArguments(argv);
  }
}
