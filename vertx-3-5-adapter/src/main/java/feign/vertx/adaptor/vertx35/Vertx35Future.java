package feign.vertx.adaptor.vertx35;

import feign.vertx.adaptor.VertxFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.function.Consumer;

public final class Vertx35Future<T> implements VertxFuture<Future<T>, T> {
  private final Future<T> delegate;

  Vertx35Future() {
    this.delegate = Future.future();
  }

  static <R> Vertx35Future<R> fromAsyncResult(final AsyncResult<R> asyncResult) {
    Vertx35Future<R> vertxFuture = new Vertx35Future<>();

    if (asyncResult.succeeded()) {
      vertxFuture.complete(asyncResult.result());
    } else {
      vertxFuture.fail(asyncResult.cause());
    }

    return vertxFuture;
  }

  static <R> Vertx35Future<R> succeed(R result) {
    Vertx35Future<R> future = new Vertx35Future<>();
    future.complete(result);
    return future;
  }

  static <R> Vertx35Future<R> failed(Throwable failure) {
    Vertx35Future<R> future = new Vertx35Future<>();
    future.fail(failure);
    return future;
  }

  @Override
  public boolean succeeded() {
    return this.delegate.succeeded();
  }

  @Override
  public void complete() {
    this.delegate.complete();
  }

  @Override
  public void complete(final T result) {
    this.delegate.complete(result);
  }

  @Override
  public T result() {
    return this.delegate.result();
  }

  @Override
  public void fail(final Throwable failure) {
    this.delegate.fail(failure);
  }

  @Override
  public Throwable cause() {
    return this.delegate.cause();
  }

  @Override
  public void setHandler(final Consumer<VertxFuture<Future<T>, T>> handler) {
    Handler<AsyncResult<T>> vertxHandler = (AsyncResult<T> asyncResult) -> {
      Vertx35Future<T> vertxFuture = Vertx35Future.fromAsyncResult(asyncResult);
      handler.accept(vertxFuture);
    };
    this.delegate.setHandler(vertxHandler);
  }

  @Override
  public Future<T> asFuture() {
    return delegate;
  }
}
