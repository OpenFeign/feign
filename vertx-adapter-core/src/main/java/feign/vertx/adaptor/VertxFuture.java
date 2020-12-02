package feign.vertx.adaptor;

import java.util.function.Consumer;

public interface VertxFuture<F, T> {
  boolean succeeded();
  void complete();
  void complete(T result);
  T result();
  void fail(Throwable failure);
  Throwable cause();
  void setHandler(Consumer<VertxFuture<F, T>> handler);
  F asFuture();
}
