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
package feign.graphql;

import feign.Experimental;
import feign.Request;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import feign.codec.JsonDecoder;
import feign.graphql.GraphqlSubscriptionClient.Subscription;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Experimental
public class GraphqlDecoder implements Decoder {

  /** How long a blocking subscription call waits for an event before giving up. */
  public static final Duration DEFAULT_EVENT_TIMEOUT = Duration.ofSeconds(60);

  private final JsonDecoder jsonDecoder;
  private final long eventTimeoutMillis;
  private final Executor executor;

  public GraphqlDecoder(JsonDecoder jsonDecoder) {
    this(jsonDecoder, DEFAULT_EVENT_TIMEOUT, Runnable::run);
  }

  public GraphqlDecoder(JsonDecoder jsonDecoder, Duration eventTimeout, Executor executor) {
    if (eventTimeout.isNegative()) {
      throw new IllegalArgumentException("eventTimeout must not be negative: " + eventTimeout);
    }
    this.jsonDecoder = jsonDecoder;
    this.eventTimeoutMillis = eventTimeout.toMillis();
    this.executor = executor;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.body() instanceof Subscription subscription) {
      return subscribe(subscription, type);
    }

    Type targetType = type;
    boolean optional = isOptionalType(type);
    if (optional) {
      targetType = extractOptionalInnerType(type);
    }

    var result = doDecode(response, targetType);
    if (result == null && isCollectionOrArrayType(targetType)) {
      result = Util.emptyValueOf(targetType);
    }
    return optional ? Optional.ofNullable(result) : result;
  }

  @SuppressWarnings("unchecked")
  private Object doDecode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204) {
      return Util.emptyValueOf(type);
    }
    if (response.body() == null) {
      return Util.emptyValueOf(type);
    }

    var root = (Map<String, Object>) jsonDecoder.decode(response, Map.class);
    if (root == null) {
      return Util.emptyValueOf(type);
    }

    return unwrap(root, type, response.status(), response.request());
  }

  @SuppressWarnings("unchecked")
  private Object unwrap(Map<String, Object> root, Type type, int status, Request request)
      throws IOException {
    var errors = root.get("errors");
    if (errors instanceof List<?> errorList && !errorList.isEmpty()) {
      var operationField = resolveOperationField(root, request);
      throw new GraphqlErrorException(status, operationField, errors.toString(), request);
    }

    var data = root.get("data");
    if (!(data instanceof Map)) {
      return Util.emptyValueOf(type);
    }

    var dataMap = (Map<String, Object>) data;
    if (dataMap.isEmpty()) {
      return Util.emptyValueOf(type);
    }

    // A single root field is the operation result itself; several root fields are its components,
    // so the whole data map binds to the return type and no field gets dropped.
    if (dataMap.size() > 1) {
      return jsonDecoder.convert(dataMap, type);
    }

    var operationData = dataMap.values().iterator().next();
    if (operationData == null) {
      return Util.emptyValueOf(type);
    }

    if (operationData instanceof List<?> list && !isCollectionOrArrayType(type)) {
      if (list.isEmpty()) {
        return Util.emptyValueOf(type);
      }
      operationData = list.get(0);
    }

    return jsonDecoder.convert(operationData, type);
  }

  @SuppressWarnings("unchecked")
  private String resolveOperationField(Map<String, Object> root, Request request) {
    var data = root.get("data");
    if (data instanceof Map) {
      var dataMap = (Map<String, Object>) data;
      var names = dataMap.keySet().iterator();
      if (names.hasNext()) {
        return names.next();
      }
    }

    if (request != null && request.body() != null) {
      try {
        var fakeResponse =
            Response.builder()
                .status(200)
                .headers(Collections.emptyMap())
                .request(request)
                .body(request.body())
                .build();
        var requestBody = (Map<String, Object>) jsonDecoder.decode(fakeResponse, Map.class);
        if (requestBody != null) {
          var query = requestBody.get("query");
          if (query instanceof String queryStr) {
            return GraphqlContract.extractOperationField(queryStr);
          }
        }
      } catch (Exception e) {
        // ignore parsing errors
      }
    }

    return "unknown";
  }

  /**
   * The return type picks the semantics: {@code Stream<T>} blocks on every element and {@code
   * Flow.Publisher<T>} pushes them, while {@code T} and {@code Optional<T>} block for the first
   * event only and {@code CompletableFuture<T>} delivers that first event asynchronously. Every
   * single-value form unsubscribes as soon as it has its event.
   *
   * <p>The blocking forms are bounded by the configured event timeout; the asynchronous ones are
   * not, since their caller already owns the deadline.
   */
  private Object subscribe(Subscription subscription, Type type) {
    subscription.detach();

    if (isRawType(type, Stream.class)) {
      return elements(subscription, typeArgument(type), eventTimeoutMillis);
    }
    if (isRawType(type, Flow.Publisher.class)) {
      return publish(subscription, typeArgument(type));
    }
    if (isRawType(type, CompletableFuture.class)) {
      return futureOf(subscription, typeArgument(type));
    }
    if (isRawType(type, Optional.class)) {
      return first(subscription, typeArgument(type), eventTimeoutMillis);
    }
    return first(subscription, type, eventTimeoutMillis).orElseGet(() -> Util.emptyValueOf(type));
  }

  private Optional<Object> first(Subscription subscription, Type elementType, long timeoutMillis) {
    try (var elements = elements(subscription, elementType, timeoutMillis)) {
      return elements.findFirst();
    }
  }

  private CompletableFuture<Object> futureOf(Subscription subscription, Type elementType) {
    var future = new CompletableFuture<>();
    // Cancelling must reach the socket, or a cancelled future leaks the connection and its worker.
    future.whenComplete(
        (ignored, error) -> {
          if (future.isCancelled()) {
            subscription.unsubscribe();
          }
        });
    try {
      executor.execute(
          () -> {
            try {
              future.complete(first(subscription, elementType, 0).orElse(null));
            } catch (Throwable e) {
              future.completeExceptionally(e);
            }
          });
    } catch (RejectedExecutionException e) {
      subscription.unsubscribe();
      future.completeExceptionally(e);
    }
    return future;
  }

  private Stream<Object> elements(Subscription subscription, Type elementType, long timeoutMillis) {
    return subscription
        .payloads(timeoutMillis)
        .map(
            payload -> {
              try {
                return unwrap(payload, elementType, 200, subscription.request());
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  private Flow.Publisher<Object> publish(Subscription subscription, Type elementType) {
    // Runnable::run delivers on the pump thread: one worker per subscription in total, delivery can
    // never be rejected by a busy pool, and onNext is inherently ordered.
    var publisher = new SubmissionPublisher<>(Runnable::run, Flow.defaultBufferSize());
    var started = new AtomicBoolean();
    // Pumping starts on the first subscribe, so hasSubscribers() is meaningful from the first
    // element onwards and there is no pre-subscribe window to latch around.
    return subscriber -> {
      publisher.subscribe(subscriber);
      if (!started.compareAndSet(false, true)) {
        return;
      }
      try {
        executor.execute(
            () -> {
              try (var elements = elements(subscription, elementType, 0)) {
                var iterator = elements.iterator();
                while (iterator.hasNext() && publisher.hasSubscribers()) {
                  publisher.submit(iterator.next());
                }
                publisher.close();
              } catch (Throwable e) {
                publisher.closeExceptionally(e);
              }
            });
      } catch (RejectedExecutionException e) {
        // A subscriber must always get a terminal signal; stranding it is worse than failing it.
        subscription.unsubscribe();
        publisher.closeExceptionally(e);
      }
    };
  }

  private static boolean isRawType(Type type, Class<?> raw) {
    return type instanceof ParameterizedType pt && pt.getRawType() == raw;
  }

  private static Type typeArgument(Type type) {
    return ((ParameterizedType) type).getActualTypeArguments()[0];
  }

  private boolean isOptionalType(Type type) {
    if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> cls) {
      return cls == Optional.class;
    }
    if (type instanceof Class<?> cls) {
      return cls == Optional.class;
    }
    return false;
  }

  private Type extractOptionalInnerType(Type type) {
    if (type instanceof ParameterizedType pt) {
      return pt.getActualTypeArguments()[0];
    }
    return Object.class;
  }

  private boolean isCollectionOrArrayType(Type type) {
    if (type instanceof Class<?> cls) {
      return cls.isArray() || Iterable.class.isAssignableFrom(cls);
    }
    if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> cls) {
      return Iterable.class.isAssignableFrom(cls);
    }
    return false;
  }
}
