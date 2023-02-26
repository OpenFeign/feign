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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class MethodInfoTest {

  public static class AsyncClientTest {
    public interface AsyncClient {
      CompletableFuture<String> log();
    }

    @Test
    public void testCompletableFutureOfString() throws Exception {
      MethodInfo mi = new MethodInfo(AsyncClient.class, AsyncClient.class.getMethod("log"));
      assertTrue(mi.isAsyncReturnType());
      assertEquals(String.class, mi.underlyingReturnType());
    }
  }

  public static class GenericAsyncClientTest {
    public interface GenericAsyncClient<T> {
      T log();
    }

    public interface AsyncClient extends GenericAsyncClient<CompletableFuture<String>> {
    }

    @Test
    public void testGenericCompletableFutureOfString() throws Exception {
      MethodInfo mi = new MethodInfo(AsyncClient.class, AsyncClient.class.getMethod("log"));
      assertTrue(mi.isAsyncReturnType());
      assertEquals(String.class, mi.underlyingReturnType());
    }
  }

  public static class SyncClientTest {
    public interface SyncClient {
      String log();
    }

    @Test
    public void testString() throws Exception {
      MethodInfo mi = new MethodInfo(SyncClient.class, SyncClient.class.getMethod("log"));
      assertFalse(mi.isAsyncReturnType());
      assertEquals(String.class, mi.underlyingReturnType());
    }
  }

  public static class GenericSyncClientTest {
    public interface GenericSyncClient<T> {
      T log();
    }

    public interface SyncClient extends GenericSyncClient<List<String>> {
    }

    public static class ListOfStrings implements ParameterizedType {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[] {String.class};
      }

      @Override
      public Type getRawType() {
        return List.class;
      }

      @Override
      public Type getOwnerType() {
        return null;
      }
    }

    @Test
    public void testListOfStrings() throws Exception {
      MethodInfo mi = new MethodInfo(SyncClient.class, SyncClient.class.getMethod("log"));
      assertFalse(mi.isAsyncReturnType());
      assertTrue(Types.equals(new ListOfStrings(), mi.underlyingReturnType()));
    }
  }
}
