/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.http2client;

import feign.Client;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import feign.Util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

public class Http2Client extends AbstractHttpClient implements Client {

  private final HttpClient client;

  public Http2Client() {
    this(HttpClient.newBuilder()
        .followRedirects(Redirect.ALWAYS)
        .version(Version.HTTP_2)
        .build());
  }

  public Http2Client(Options options) {
    this(newClientBuilder(options)
        .version(Version.HTTP_2)
        .build());
  }

  public Http2Client(HttpClient client) {
    this.client = Util.checkNotNull(client, "HttpClient must not be null");
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final HttpRequest httpRequest;
    try {
      httpRequest = newRequestBuilder(request, options)
              .version(Version.HTTP_2)
              .build();
    } catch (URISyntaxException e) {
      throw new IOException("Invalid uri " + request.url(), e);
    }

    HttpClient clientForRequest = getOrCreateClient(options);
    HttpResponse<byte[]> httpResponse;
    try {
      httpResponse = clientForRequest.send(httpRequest, BodyHandlers.ofByteArray());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Invalid uri " + request.url(), e);
    }

    return toFeignResponse(request, httpResponse);
  }

  private HttpClient getOrCreateClient(Options options) {
    if (doesClientConfigurationDiffer(options)) {
      // create a new client from the existing one - but with connectTimeout and followRedirect
      // settings from options
      java.net.http.HttpClient.Builder builder = newClientBuilder(options)
          .sslContext(client.sslContext())
          .sslParameters(client.sslParameters())
          .version(client.version());
      client.authenticator().ifPresent(builder::authenticator);
      client.cookieHandler().ifPresent(builder::cookieHandler);
      client.executor().ifPresent(builder::executor);
      client.proxy().ifPresent(builder::proxy);
      return builder.build();
    }
    return client;
  }

  private boolean doesClientConfigurationDiffer(Options options) {
    if ((client.followRedirects() == Redirect.ALWAYS) != options.isFollowRedirects()) {
      return true;
    }
    return client.connectTimeout()
        .map(timeout -> timeout.toMillis() != options.connectTimeoutMillis())
        .orElse(true);
  }

  private static java.net.http.HttpClient.Builder newClientBuilder(Options options) {
    return HttpClient
        .newBuilder()
        .followRedirects(options.isFollowRedirects() ? Redirect.ALWAYS : Redirect.NEVER)
        .connectTimeout(Duration.ofMillis(options.connectTimeoutMillis()));
  }
}
