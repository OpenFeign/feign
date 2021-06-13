/**
 * Copyright 2012-2021 The Feign Authors
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



import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import feign.InvocationHandlerFactory.MethodHandler;
//import kotlin.jvm.internal.Intrinsics;

//
// import static org.graalvm.compiler.debug.DebugOptions.Log;

/**
 * Handles default methods by directly invoking the default method code on the interface. The bindTo
 * method must be called on the result before invoke is called.
 */
final class DefaultMethodHandler implements MethodHandler {
  // Uses Java 7 MethodHandle based reflection. As default methods will only exist when
  // run on a Java 8 JVM this will not affect use on legacy JVMs.
  // When Feign upgrades to Java 7, remove the @IgnoreJRERequirement annotation.
  public MethodHandle unboundHandle;

  // handle is effectively final after bindTo has been called.
  private MethodHandle handle;



  public DefaultMethodHandler(Method defaultMethod) {
      /**
       * 13/06/2021
       *
       * Modified to work with Android. Previous commit worked up to API level 28 (Android 8)
       * however, Android 9+ enforces more strict checking with reflection.
       *
       * Solution is to use double reflection, as below.
       *
       * Jamie (xrpdevs-at-xrpdevs-co-uk)
       *
       * TODO: Integrate back into version forked from, keep original legacyReadLookup and safeReadLookup
       *        and add conditional branch if running on Dalvik (android java runtime)
       *
       *        if (System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik"))
       *
       **/

    try {

      Class classReference = Class.class;

      Class[] classType = new Class[] {Class.class};

      Method var10001 = classReference.getDeclaredMethod(
             "getDeclaredConstructor", Class[].class);

        var10001.setAccessible(true);
      Constructor<Lookup> someHiddenMethod =
              (Constructor) var10001.invoke(Lookup.class, (Object) classType);

      Class<?> declaringClass = defaultMethod.getDeclaringClass();

      Lookup lookup = someHiddenMethod.newInstance(declaringClass);

      this.unboundHandle = lookup.unreflectSpecial(defaultMethod, declaringClass);

    } catch (IllegalAccessException ex) {
      throw new IllegalStateException(ex);
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
        e.printStackTrace();
    }

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

    System.out.println("UnboundHandle: "+Dumper.dump(unboundHandle));

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
