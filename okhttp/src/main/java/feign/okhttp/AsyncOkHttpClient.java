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
package feign.okhttp;

import feign.AsyncClient;
import feign.Request;
import feign.Response;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AsyncOkHttpClient extends AbstractOkHttpClient implements AsyncClient<Object> {
  private okhttp3.OkHttpClient delegate;

  public AsyncOkHttpClient() {
    super();
  }

  public AsyncOkHttpClient(OkHttpClient delegate) {
    super(delegate);
  }

  @Override
  public CompletableFuture<Response> execute(Request input,
                                             Request.Options options,
                                             Optional<Object> requestContext) {
    OkHttpClient requestScoped = getClient(options);
    okhttp3.Request request = toOkHttpRequest(input);
    CompletableFuture<Response> responseFuture = new CompletableFuture<>();
    requestScoped.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NotNull Call call, @NotNull IOException e) {
        responseFuture.completeExceptionally(e);
      }

      @Override
      public void onResponse(@NotNull Call call, @NotNull okhttp3.Response response)
          throws IOException {
        final Response r = toFeignResponse(response, input).toBuilder().request(input).build();
        responseFuture.complete(r);
      }
    });
    return responseFuture;
  }
}
