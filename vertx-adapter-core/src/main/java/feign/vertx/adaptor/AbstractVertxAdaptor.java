package feign.vertx.adaptor;

import feign.Request;

import java.lang.reflect.Type;
import java.util.Objects;

public abstract class AbstractVertxAdaptor<V, O, F> {
  protected static final String VERTX_FUTURE_CLASSNAME = "io.vertx.core.Future";

  public String vertxFutureClassname() {
    return VERTX_FUTURE_CLASSNAME;
  }

  public boolean isVertxFuture(final Type type) {
    return type.getTypeName().equals(VERTX_FUTURE_CLASSNAME);
  }

  @SuppressWarnings("unchecked")
  public V assertVertx(Object vertx) {
    Objects.requireNonNull(vertx, "Vertx instance should be not null.");
    return (V) vertx;
  }

  @SuppressWarnings("unchecked")
  public O assertVertxClientOptions(Object options) {
    Objects.requireNonNull(options, "HttpClientOptions should be not null.");
    return (O) options;
  }

  public abstract Object defaultClientOptions();

  public abstract Object makeOptions(final Request.Options options);

  public abstract Type futureContentType(final Type futureType);

  public abstract AbstractVertxHttpClient createHttpClient(final V vertx, final O options);

  public abstract <R> VertxFuture<? extends F, R> future();

  public abstract <R> VertxFuture<? extends F, R> succeedFuture(final R result);

  public abstract <R> VertxFuture<? extends F, R> failedFuture(final Throwable throwable);
}
