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

import java.io.IOException;
import feign.Client;
import okhttp3.*;

/**
 * This module directs Feign's http requests to
 * <a href="http://square.github.io/okhttp/">OkHttp</a>, which enables SPDY and better network
 * control. Ex.
 *
 * <pre>
 * GitHub github = Feign.builder().client(new OkHttpClient()).target(GitHub.class,
 * "https://api.github.com");
 */
public final class OkHttpClient extends AbstractOkHttpClient implements Client {

  public OkHttpClient() {
    super();
  }

  public OkHttpClient(okhttp3.OkHttpClient delegate) {
    super(delegate);
  }

  @Override
  public feign.Response execute(feign.Request input, feign.Request.Options options)
      throws IOException {
    okhttp3.OkHttpClient requestScoped = getClient(options);
    Request request = toOkHttpRequest(input);
    Response response = requestScoped.newCall(request).execute();
    return toFeignResponse(response, input).toBuilder().request(input).build();
  }
}
