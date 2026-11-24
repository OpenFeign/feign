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
package feign.hc5;

import static feign.Util.enumForName;

import feign.*;
import feign.Request.Options;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.io.CloseMode;

/**
 * This module directs Feign's http requests to Apache's
 * <a href="https://hc.apache.org/httpcomponents-client-5.0.x/index.html">HttpClient 5</a>. Ex.
 *
 * <pre>
 * GitHub github = Feign.builder().client(new ApacheHttp5Client()).target(GitHub.class,
 * "https://api.github.com");
 */
/*
 */
public final class AsyncApacheHttp5Client implements AsyncClient<HttpClientContext>, AutoCloseable {

  private static final String ACCEPT_HEADER_NAME = "Accept";

  private final CloseableHttpAsyncClient client;

  private volatile UnaryOperator<FutureCallback<SimpleHttpResponse>> futureCallbackFactory;

  public AsyncApacheHttp5Client() {
    this(createStartedClient());
  }

  public AsyncApacheHttp5Client(CloseableHttpAsyncClient client) {
    this.client = client;
  }

  private static CloseableHttpAsyncClient createStartedClient() {
    final CloseableHttpAsyncClient client = HttpAsyncClients.custom().build();
    client.start();
    return client;
  }

  /**
   * Returns the factory currently used to decorate the {@link FutureCallback} created for each
   * request, or {@code null} if the default, undecorated callback is used.
   */
  public UnaryOperator<FutureCallback<SimpleHttpResponse>> getFutureCallbackFactory() {
    return futureCallbackFactory;
  }

  /**
   * Sets a factory decorating the {@link FutureCallback} this client registers for each request
   * before handing it to the underlying {@link CloseableHttpAsyncClient}.
   *
   * <p>The callback is invoked on one of the HTTP client's internal I/O dispatch threads, where
   * thread-local state of the calling thread, such as SLF4J's MDC, is no longer available. The
   * factory is applied on the calling thread just before the request is dispatched, so it can
   * capture that state and return a callback wrapping the supplied one, which restores the context
   * before delegating. Ex.
   *
   * <pre>{@code
   * AsyncApacheHttp5Client client = new AsyncApacheHttp5Client();
   * client.setFutureCallbackFactory(
   *     delegate -> {
   *       Map<String, String> context = MDC.getCopyOfContextMap();
   *       return new FutureCallback<SimpleHttpResponse>() {
   *
   *         @Override
   *         public void completed(SimpleHttpResponse response) {
   *           MDC.setContextMap(context);
   *           try {
   *             delegate.completed(response);
   *           } finally {
   *             MDC.clear();
   *           }
   *         }
   *         // delegate failed() and cancelled() the same way
   *       };
   *     });
   * }</pre>
   *
   * <p>Defaults to the identity function, passing the callback through unchanged and preserving the
   * behavior of previous releases. Passing {@code null} restores the default.
   *
   * @param futureCallbackFactory the factory to apply to each callback, or {@code null} for none
   */
  public void setFutureCallbackFactory(
      UnaryOperator<FutureCallback<SimpleHttpResponse>> futureCallbackFactory) {
    this.futureCallbackFactory = futureCallbackFactory;
  }

  @Override
  public CompletableFuture<Response> execute(
      Request request, Options options, Optional<HttpClientContext> requestContext) {
    final SimpleHttpRequest httpUriRequest = toClassicHttpRequest(request, options);

    final CompletableFuture<Response> result = new CompletableFuture<>();
    final FutureCallback<SimpleHttpResponse> callback =
        decorateCallback(createCallback(request, result));

    client.execute(
        httpUriRequest,
        configureTimeoutsAndRedirection(options, requestContext.orElseGet(HttpClientContext::new)),
        callback);

    return result;
  }

  private FutureCallback<SimpleHttpResponse> createCallback(
      Request request, CompletableFuture<Response> result) {
    return new FutureCallback<SimpleHttpResponse>() {

      @Override
      public void completed(SimpleHttpResponse httpResponse) {
        result.complete(toFeignResponse(httpResponse, request));
      }

      @Override
      public void failed(Exception ex) {
        result.completeExceptionally(ex);
      }

      @Override
      public void cancelled() {
        result.cancel(false);
      }
    };
  }

  private FutureCallback<SimpleHttpResponse> decorateCallback(
      FutureCallback<SimpleHttpResponse> callback) {
    final UnaryOperator<FutureCallback<SimpleHttpResponse>> futureCallbackFactory =
        this.futureCallbackFactory;
    return futureCallbackFactory != null ? futureCallbackFactory.apply(callback) : callback;
  }

  protected HttpClientContext configureTimeoutsAndRedirection(
      Request.Options options, HttpClientContext context) {
    // per request timeouts
    final RequestConfig requestConfig =
        (client instanceof Configurable
                ? RequestConfig.copy(((Configurable) client).getConfig())
                : RequestConfig.custom())
            .setConnectTimeout(options.connectTimeout(), options.connectTimeoutUnit())
            .setResponseTimeout(options.readTimeout(), options.readTimeoutUnit())
            .setRedirectsEnabled(options.isFollowRedirects())
            .build();
    context.setRequestConfig(requestConfig);
    return context;
  }

  SimpleHttpRequest toClassicHttpRequest(Request request, Request.Options options) {
    final SimpleHttpRequest httpRequest =
        new SimpleHttpRequest(request.httpMethod().name(), request.url());

    // request headers
    boolean hasAcceptHeader = false;
    boolean isGzip = false;
    boolean isDeflate = false;
    for (final Map.Entry<String, Collection<String>> headerEntry : request.headers().entrySet()) {
      final String headerName = headerEntry.getKey();
      if (headerName.equalsIgnoreCase(ACCEPT_HEADER_NAME)) {
        hasAcceptHeader = true;
      }

      if (headerName.equalsIgnoreCase(Util.CONTENT_LENGTH)) {
        // The 'Content-Length' header is always set by the Apache client and it
        // doesn't like us to set it as well.
        continue;
      }
      if (headerName.equalsIgnoreCase(Util.CONTENT_ENCODING)) {
        isGzip = headerEntry.getValue().stream().anyMatch(Util.ENCODING_GZIP::equalsIgnoreCase);
        isDeflate =
            headerEntry.getValue().stream().anyMatch(Util.ENCODING_DEFLATE::equalsIgnoreCase);
      }

      for (final String headerValue : headerEntry.getValue()) {
        httpRequest.addHeader(headerName, headerValue);
      }
    }
    // some servers choke on the default accept string, so we'll set it to anything
    if (!hasAcceptHeader) {
      httpRequest.addHeader(ACCEPT_HEADER_NAME, "*/*");
    }

    // request body
    // final Body requestBody = request.requestBody();
    byte[] data = request.body();
    if (isGzip && data != null) {
      data = gzip(data);
    } else if (isDeflate && data != null) {
      data = deflate(data);
    }
    if (data != null) {
      httpRequest.setBody(data, getContentType(request));
    }

    return httpRequest;
  }

  private static byte[] gzip(byte[] data) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(data);
      gzip.finish();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to gzip request body", e);
    }
  }

  private static byte[] deflate(byte[] data) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream deflater = new DeflaterOutputStream(baos)) {
      deflater.write(data);
      deflater.finish();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to deflate request body", e);
    }
  }

  private ContentType getContentType(Request request) {
    ContentType contentType = null;
    for (final Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
      if (entry.getKey().equalsIgnoreCase("Content-Type")) {
        final Collection<String> values = entry.getValue();
        if (values != null && !values.isEmpty()) {
          contentType = ContentType.parse(values.iterator().next());
          if (contentType.getCharset() == null) {
            contentType = contentType.withCharset(request.charset());
          }
          break;
        }
      }
    }
    return contentType;
  }

  Response toFeignResponse(SimpleHttpResponse httpResponse, Request request) {
    final int statusCode = httpResponse.getCode();

    final String reason = httpResponse.getReasonPhrase();

    final Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    for (final Header header : httpResponse.getHeaders()) {
      final String name = header.getName();
      final String value = header.getValue();

      Collection<String> headerValues = headers.get(name);
      if (headerValues == null) {
        headerValues = new ArrayList<String>();
        headers.put(name, headerValues);
      }
      headerValues.add(value);
    }

    return Response.builder()
        .protocolVersion(
            enumForName(Request.ProtocolVersion.class, httpResponse.getVersion().format()))
        .status(statusCode)
        .reason(reason)
        .headers(headers)
        .request(request)
        .body(httpResponse.getBodyBytes())
        .build();
  }

  @Override
  public void close() throws Exception {
    client.close(CloseMode.GRACEFUL);
  }
}
