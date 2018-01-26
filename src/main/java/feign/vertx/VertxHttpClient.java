package feign.vertx;

import static feign.Util.checkNotNull;

import feign.Request;
import feign.Response;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Like {@link feign.Client} but method {@link #execute} returns {@link Future} with
 * {@link Response}. HTTP request is executed asynchronously with Vert.x
 *
 * @author Alexei KLENIN
 * @author Gordon McKinney
 */
@SuppressWarnings("unused")
public final class VertxHttpClient {
  private final Vertx vertx;

  public VertxHttpClient(final Vertx vertx) {
    this.vertx = checkNotNull(vertx, "Argument vertx must not be null");
  }

  /**
   * Executes HTTP request and returns {@link Future} with response.
   *
   * @param request  request
   * @param options  HTTP request options
   *
   * @return future of HTTP response
   */
  public Future<Response> execute(final Request request, final HttpClientOptions options) {
    checkNotNull(request, "Argument request must be not null");
    checkNotNull(options, "Argument options must be not null");

    final HttpClient client = vertx.createHttpClient(options);
    final HttpClientRequest httpClientRequest;

    try {
      httpClientRequest = makeHttpClientRequest(request, client);
    } catch (final MalformedURLException unexpectedException) {
      return Future.failedFuture(unexpectedException);
    }

    final Future<Response> responseFuture = Future.future();

    httpClientRequest.exceptionHandler(responseFuture::fail);
    httpClientRequest.handler(response -> {
      final Map<String, Collection<String>> responseHeaders = StreamSupport
          .stream(response.headers().spliterator(), false)
          .collect(Collectors.groupingBy(
              Map.Entry::getKey,
              Collectors.mapping(
                  Map.Entry::getValue,
                  Collectors.toCollection(ArrayList::new))));

      response.exceptionHandler(responseFuture::fail);
      response.bodyHandler(body -> {
        final Response feignResponse = Response.create(
            response.statusCode(),
            response.statusMessage(),
            responseHeaders,
            body.getBytes());
        responseFuture.complete(feignResponse);
      });
    });

    /* Write body if exists */
    if (request.body() != null) {
      httpClientRequest.write(Buffer.buffer(request.body()));
    }

    httpClientRequest.end();

    return responseFuture;
  }

  /**
   * Creates {@link HttpClientRequest} (Vert.x) from {@link Request} (feign).
   *
   * @param request  feign request
   * @param client  vertx HTTP client
   *
   * @return fully formed HttpClientRequest
   */
  private HttpClientRequest makeHttpClientRequest(
      final Request request,
      final HttpClient client) throws MalformedURLException {
    final URL url = new URL(request.url());
    final int port = url.getPort() > -1
        ? url.getPort()
        : HttpClientOptions.DEFAULT_DEFAULT_PORT;
    final String host = url.getHost();
    final String requestUri = url.getFile();

    HttpClientRequest httpClientRequest = client.request(
        HttpMethod.valueOf(request.method()),
        port,
        host,
        requestUri);

    /* Add headers to request */
    for (final Map.Entry<String, Collection<String>> header : request.headers().entrySet()) {
      httpClientRequest = httpClientRequest.putHeader(header.getKey(), header.getValue());
    }

    return httpClientRequest;
  }
}
