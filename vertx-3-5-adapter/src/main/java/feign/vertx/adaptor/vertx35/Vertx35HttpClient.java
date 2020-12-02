package feign.vertx.adaptor.vertx35;

import static feign.Util.checkNotNull;

import feign.Request;
import feign.Response;
import feign.vertx.adaptor.AbstractVertxHttpClient;
import feign.vertx.adaptor.VertxFuture;
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

public final class Vertx35HttpClient extends AbstractVertxHttpClient<Vertx, HttpClientOptions, Future<Response>> {
  private final HttpClient httpClient;

  public Vertx35HttpClient(final Vertx vertx, final HttpClientOptions options) {
    super(vertx, options);
    this.httpClient = vertx.createHttpClient(options);
  }

  /**
   * Executes HTTP request and returns {@link Future} with response.
   *
   * @param request  request
   * @return future of HTTP response
   */
  public VertxFuture<Future<Response>, Response> execute(final Request request) {
    checkNotNull(request, "Argument request must be not null");

    final HttpClientRequest httpClientRequest;

    try {
      httpClientRequest = makeHttpClientRequest(request);
    } catch (final MalformedURLException unexpectedException) {
      return Vertx35Future.failed(unexpectedException);
    }

    final VertxFuture<Future<Response>, Response> responseFuture = new Vertx35Future<>();

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
   * @return fully formed HttpClientRequest
   */
  private HttpClientRequest makeHttpClientRequest(final Request request)
      throws MalformedURLException {
    final URL url = new URL(request.url());
    final int port = url.getPort() > -1
        ? url.getPort()
        : HttpClientOptions.DEFAULT_DEFAULT_PORT;
    final String host = url.getHost();
    final String requestUri = url.getFile();

    HttpClientRequest httpClientRequest = httpClient.request(
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
