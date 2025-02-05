/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.vertx;

import static feign.Util.checkNotNull;

import feign.Request;
import feign.Response;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Like {@link feign.Client} but method {@link #execute} returns {@link Future} with {@link
 * Response}. HTTP request is executed asynchronously with Vert.x
 *
 * @author Alexei KLENIN
 * @author Gordon McKinney
 */
@SuppressWarnings("unused")
public final class VertxHttpClient {
  private final WebClient webClient;
  private final long timeout;
  private final UnaryOperator<HttpRequest<Buffer>> requestPreProcessor;

  /**
   * Constructor from {@link Vertx} instance, HTTP client options and request timeout.
   *
   * @param webClient vertx WebClient
   * @param timeout request timeout
   * @param requestPreProcessor request pre-processor
   */
  public VertxHttpClient(
      final WebClient webClient,
      final long timeout,
      final UnaryOperator<HttpRequest<Buffer>> requestPreProcessor) {
    checkNotNull(webClient, "Argument webClient must not be null");
    checkNotNull(requestPreProcessor, "Argument requestPreProcessor must be not null");

    this.webClient = webClient;
    this.timeout = timeout;
    this.requestPreProcessor = requestPreProcessor;
  }

  /**
   * Executes HTTP request and returns {@link Future} with response.
   *
   * @param request request
   * @return future of HTTP response
   */
  public Future<Response> execute(final Request request) {
    checkNotNull(request, "Argument request must be not null");

    final HttpRequest<Buffer> httpClientRequest;

    try {
      httpClientRequest = makeHttpClientRequest(request);
    } catch (final MalformedURLException unexpectedException) {
      return Future.failedFuture(unexpectedException);
    }

    final Future<HttpResponse<Buffer>> responseFuture =
        request.body() != null
            ? httpClientRequest.sendBuffer(Buffer.buffer(request.body()))
            : httpClientRequest.send();

    return responseFuture.compose(
        response -> {
          final Map<String, Collection<String>> responseHeaders =
              StreamSupport.stream(response.headers().spliterator(), false)
                  .collect(
                      Collectors.groupingBy(
                          Map.Entry::getKey,
                          Collectors.mapping(
                              Map.Entry::getValue, Collectors.toCollection(ArrayList::new))));

          byte[] body = response.body() != null ? response.body().getBytes() : null;

          return Future.succeededFuture(
              Response.builder()
                  .status(response.statusCode())
                  .reason(response.statusMessage())
                  .headers(responseHeaders)
                  .body(body)
                  .request(request)
                  .build());
        });
  }

  private HttpRequest<Buffer> makeHttpClientRequest(final Request request)
      throws MalformedURLException {
    final URL url = new URL(request.url());
    final String host = url.getHost();
    final String requestUri = url.getFile();

    int port;
    if (url.getPort() > -1) {
      port = url.getPort();
    } else if (url.getProtocol().equalsIgnoreCase("https")) {
      port = 443;
    } else {
      port = HttpClientOptions.DEFAULT_DEFAULT_PORT;
    }

    HttpRequest<Buffer> httpClientRequest =
        webClient
            .request(HttpMethod.valueOf(request.httpMethod().name()), port, host, requestUri)
            .timeout(timeout);

    /* Add headers to request */
    for (final Map.Entry<String, Collection<String>> header : request.headers().entrySet()) {
      httpClientRequest = httpClientRequest.putHeader(header.getKey(), header.getValue());
    }

    return requestPreProcessor.apply(httpClientRequest);
  }
}
