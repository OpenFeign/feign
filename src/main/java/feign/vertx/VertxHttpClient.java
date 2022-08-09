package feign.vertx;

import static feign.Util.checkNotNull;

import feign.Request;
import feign.Response;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
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
 * Like {@link feign.Client} but method {@link #execute} returns {@link Future} with
 * {@link Response}. HTTP request is executed asynchronously with Vert.x
 *
 * @author Alexei KLENIN
 * @author Gordon McKinney
 */
@SuppressWarnings("unused")
public final class VertxHttpClient {
  private final HttpClient httpClient;
  private final long timeout;
  private final UnaryOperator<HttpRequest<Buffer>> requestPreProcessor;

  /**
   * Constructor from {@link Vertx} instance, HTTP client options and request timeout.
   * @param vertx  vertx instance
   * @param options  HTTP options
   * @param timeout  request timeout
   * @param requestPreProcessor  request pre-processor
   */
  public VertxHttpClient(
      final Vertx vertx,
      final HttpClientOptions options,
      final long timeout,
      final UnaryOperator<HttpRequest<Buffer>> requestPreProcessor) {
    checkNotNull(vertx, "Argument vertx must not be null");
    checkNotNull(options, "Argument options must be not null");
    checkNotNull(requestPreProcessor, "Argument requestPreProcessor must be not null");

    this.httpClient = vertx.createHttpClient(options);
    this.timeout = timeout;
    this.requestPreProcessor = requestPreProcessor;
  }

  /**
   * Executes HTTP request and returns {@link Future} with response.
   *
   * @param request  request
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

    final Future<HttpResponse<Buffer>> responseFuture = request.body() != null
        ? httpClientRequest.sendBuffer(Buffer.buffer(request.body()))
        : httpClientRequest.send();

    return responseFuture.compose(response -> {
      final Map<String, Collection<String>> responseHeaders = StreamSupport
          .stream(response.headers().spliterator(), false)
          .collect(Collectors.groupingBy(
              Map.Entry::getKey,
              Collectors.mapping(
                  Map.Entry::getValue,
                  Collectors.toCollection(ArrayList::new))));

      byte[] body = response.body() != null ? response.body().getBytes() : null;

      return Future.succeededFuture(Response
          .builder()
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

    HttpRequest<Buffer> httpClientRequest = WebClient
        .wrap(this.httpClient)
        .request(HttpMethod.valueOf(request.httpMethod().name()),
            port,
            host,
            requestUri)
        .timeout(timeout);

    /* Add headers to request */
    for (final Map.Entry<String, Collection<String>> header : request.headers().entrySet()) {
      httpClientRequest = httpClientRequest.putHeader(header.getKey(), header.getValue());
    }

    return requestPreProcessor.apply(httpClientRequest);
  }
}
