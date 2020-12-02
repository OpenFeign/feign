package feign.vertx.adaptor;

import static feign.Util.checkNotNull;

import feign.Request;
import feign.Response;

/**
 * Like {@link feign.Client} but method {@link #execute} returns {@code Future} with
 * {@link Response}. HTTP request is executed asynchronously with Vert.x
 *
 * @author Alexei KLENIN
 * @author Gordon McKinney
 */
@SuppressWarnings("unused")
public abstract class AbstractVertxHttpClient<V, O, F> {

  /**
   * Constructor from {@code Vertx} instance and HTTP client options.
   *
   * @param vertx  vertx instance
   * @param options  HTTP options
   */
  public AbstractVertxHttpClient(final V vertx, final O options) {
    checkNotNull(vertx, "Argument vertx must not be null");
    checkNotNull(options, "Argument options must be not null");
  }

  /**
   * Executes HTTP request and returns {@code Future} with response.
   *
   * @param request  request
   * @return future of HTTP response
   */
  public abstract VertxFuture<F, Response> execute(final Request request);
}
