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

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;



public class MethodInfoTest {

  @Nested
  static class AsyncClientTest {
    public interface AsyncClient {
      CompletableFuture<String> log();
    }

    @Test
    void completableFutureOfString() throws Exception {
      MethodInfo mi = new MethodInfo(AsyncClient.class, AsyncClient.class.getMethod("log"));
      assertThat(mi.isAsyncReturnType()).isTrue();
      assertThat(mi.underlyingReturnType()).isEqualTo(String.class);
    }
  }

  @Nested
  static class GenericAsyncClientTest {
    public interface GenericAsyncClient<T> {
      T log();
    }

    public interface AsyncClient extends GenericAsyncClient<CompletableFuture<String>> {
    }

    @Test
    void genericCompletableFutureOfString() throws Exception {
      MethodInfo mi = new MethodInfo(AsyncClient.class, AsyncClient.class.getMethod("log"));
      assertThat(mi.isAsyncReturnType()).isTrue();
      assertThat(mi.underlyingReturnType()).isEqualTo(String.class);
    }
  }

  @Nested
  static class SyncClientTest {
    public interface SyncClient {
      String log();
    }

    @Test
    void string() throws Exception {
      MethodInfo mi = new MethodInfo(SyncClient.class, SyncClient.class.getMethod("log"));
      assertThat(mi.isAsyncReturnType()).isFalse();
      assertThat(mi.underlyingReturnType()).isEqualTo(String.class);
    }
  }

  @Nested
  static class GenericSyncClientTest {
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
    void listOfStrings() throws Exception {
      MethodInfo mi = new MethodInfo(SyncClient.class, SyncClient.class.getMethod("log"));
      assertThat(mi.isAsyncReturnType()).isFalse();
      assertThat(Types.equals(new ListOfStrings(), mi.underlyingReturnType())).isTrue();
    }
  }
}
