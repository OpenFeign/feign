package feign.vertx.adaptor.vertx35;

import static feign.Util.resolveLastTypeParameter;

import feign.Request;
import feign.Response;
import feign.vertx.adaptor.AbstractVertxAdaptor;
import feign.vertx.adaptor.AbstractVertxHttpClient;
import feign.vertx.adaptor.VertxFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

import java.lang.reflect.Type;

public class Vertx35Adaptor extends AbstractVertxAdaptor<Vertx, HttpClientOptions, Future> {
  @Override
  public Vertx assertVertx(final Object vertx) {
    super.assertVertx(vertx);

    if (!(vertx instanceof Vertx)) {
      throw new IllegalArgumentException(
          String.format("Vertx instance must be of type %s.", vertx.getClass().getName()));
    }

    return (Vertx) vertx;
  }

  @Override
  public HttpClientOptions assertVertxClientOptions(final Object options) {
    super.assertVertxClientOptions(options);

    if (!(options instanceof HttpClientOptions)) {
      throw new IllegalArgumentException(
          String.format("Options must be of type %s.", options.getClass().getName()));
    }

    return (HttpClientOptions) options;
  }

  @Override
  public Object defaultClientOptions() {
    return new HttpClientOptions();
  }

  @Override
  public Object makeOptions(final Request.Options options) {
    return new HttpClientOptions()
        .setConnectTimeout(options.connectTimeoutMillis())
        .setIdleTimeout(options.readTimeoutMillis());
  }

  @Override
  public AbstractVertxHttpClient<Vertx, HttpClientOptions, Future<Response>> createHttpClient(
      final Vertx vertx,
      final HttpClientOptions options) {
    return new Vertx35HttpClient(vertx, options);
  }

  @Override
  public boolean isVertxFuture(final Type type) {
    return Future.class.isAssignableFrom((Class<?>) type);
  }

  public Type futureContentType(final Type futureType) {
    return resolveLastTypeParameter(futureType, Future.class);
  }

  @Override
  public <R> VertxFuture<Future<R>, R> future() {
    return new Vertx35Future<>();
  }

  @Override
  public <R> VertxFuture<Future<R>, R> succeedFuture(final R result) {
    Vertx35Future<R> future = new Vertx35Future<>();
    future.complete(result);
    return future;
  }

  @Override
  public <R> VertxFuture<Future<R>, R> failedFuture(final Throwable throwable) {
    Vertx35Future<R> future = new Vertx35Future<>();
    future.fail(throwable);
    return future;
  }
}
