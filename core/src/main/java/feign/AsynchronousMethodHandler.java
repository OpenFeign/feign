/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import static feign.ExceptionPropagationPolicy.UNWRAP;
import static feign.FeignException.errorExecuting;
import static feign.Util.checkNotNull;

final class AsynchronousMethodHandler<C> implements MethodHandler {

  private final MethodMetadata metadata;
  private final Target<?> target;
  private final AsyncClient<C> client;
  private final Retryer retryer;
  private final List<RequestInterceptor> requestInterceptors;
  private final Logger logger;
  private final Logger.Level logLevel;
  private final RequestTemplate.Factory buildTemplateFromArgs;
  private final Options options;
  private final ExceptionPropagationPolicy propagationPolicy;
  private final C requestContext;
  private final AsyncResponseHandler asyncResponseHandler;
  private final MethodInfo methodInfo;


  private AsynchronousMethodHandler(Target<?> target, AsyncClient<C> client, Retryer retryer,
      List<RequestInterceptor> requestInterceptors,
      Logger logger, Logger.Level logLevel, MethodMetadata metadata,
      RequestTemplate.Factory buildTemplateFromArgs, Options options,
      AsyncResponseHandler asyncResponseHandler, ExceptionPropagationPolicy propagationPolicy,
      C requestContext, MethodInfo methodInfo) {

    this.target = checkNotNull(target, "target");
    this.client = checkNotNull(client, "client for %s", target);
    this.retryer = checkNotNull(retryer, "retryer for %s", target);
    this.requestInterceptors =
        checkNotNull(requestInterceptors, "requestInterceptors for %s", target);
    this.logger = checkNotNull(logger, "logger for %s", target);
    this.logLevel = checkNotNull(logLevel, "logLevel for %s", target);
    this.metadata = checkNotNull(metadata, "metadata for %s", target);
    this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
    this.options = checkNotNull(options, "options for %s", target);
    this.propagationPolicy = propagationPolicy;
    this.requestContext = requestContext;
    this.asyncResponseHandler = asyncResponseHandler;
    this.methodInfo = methodInfo;
  }

  @Override
  public Object invoke(Object[] argv) throws Throwable {
    RequestTemplate template = buildTemplateFromArgs.create(argv);
    Options options = findOptions(argv);
    Retryer retryer = this.retryer.clone();
    try {
      if (methodInfo.isAsyncReturnType()) {
        return executeAndDecode(template, options, retryer);
      } else {
        return executeAndDecode(template, options, retryer).join();
      }
    } catch (CompletionException e) {
      throw e.getCause();
    }
  }

  private CompletableFuture<Object> executeAndDecode(RequestTemplate template,
                                                     Options options,
                                                     Retryer retryer) {
    CancellableFuture<Object> resultFuture = new CancellableFuture<>();

    executeAndDecode(template, options)
        .whenComplete((response, throwable) -> {
          if (throwable != null) {
            if (!resultFuture.isDone() && shouldRetry(retryer, throwable, resultFuture)) {
              if (logLevel != Logger.Level.NONE) {
                logger.logRetry(metadata.configKey(), logLevel);
              }

              resultFuture.setInner(
                  executeAndDecode(template, options, retryer));
            }
          } else {
            resultFuture.complete(response);
          }
        });

    return resultFuture;
  }

  private static class CancellableFuture<T> extends CompletableFuture<T> {
    private CompletableFuture<T> inner = null;

    public void setInner(CompletableFuture<T> value) {
      inner = value;
      inner.whenComplete(pipeTo(this));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      final boolean result = super.cancel(mayInterruptIfRunning);
      if (inner != null) {
        inner.cancel(mayInterruptIfRunning);
      }
      return result;
    }

    private static <T> BiConsumer<? super T, ? super Throwable> pipeTo(CompletableFuture<T> completableFuture) {
      return (value, throwable) -> {
        if (completableFuture.isDone()) {
          return;
        }

        if (throwable != null) {
          completableFuture.completeExceptionally(throwable);
        } else {
          completableFuture.complete(value);
        }
      };
    }
  }

  private boolean shouldRetry(Retryer retryer,
                              Throwable throwable,
                              CompletableFuture<Object> resultFuture) {
    if (throwable instanceof CompletionException) {
      throwable = throwable.getCause();
    }

    if (!(throwable instanceof RetryableException)) {
      resultFuture.completeExceptionally(throwable);
      return false;
    }

    RetryableException retryableException = (RetryableException) throwable;
    try {
      retryer.continueOrPropagate(retryableException);
      return true;
    } catch (RetryableException th) {
      Throwable cause = th.getCause();
      if (propagationPolicy == UNWRAP && cause != null) {
        resultFuture.completeExceptionally(cause);
      } else {
        resultFuture.completeExceptionally(th);
      }
      return false;
    }
  }

  private CompletableFuture<Object> executeAndDecode(RequestTemplate template, Options options) {
    Request request = targetRequest(template);

    if (logLevel != Logger.Level.NONE) {
      logger.logRequest(metadata.configKey(), logLevel, request);
    }

    long start = System.nanoTime();
    return client.execute(request, options, Optional.ofNullable(requestContext))
        .thenApply(response -> {
          // TODO: remove in Feign 12
          return ensureRequestIsSet(response, template, request);
        })
        .exceptionally(throwable -> {
          CompletionException completionException = throwable instanceof CompletionException
              ? (CompletionException) throwable
              : new CompletionException(throwable);
          if (completionException.getCause() instanceof IOException) {
            IOException ioException = (IOException) completionException.getCause();
            if (logLevel != Logger.Level.NONE) {
              logger.logIOException(metadata.configKey(), logLevel, ioException,
                  elapsedTime(start));
            }

            throw errorExecuting(request, ioException);
          } else {
            throw completionException;
          }
        })
        .thenCompose(response -> handleResponse(response, elapsedTime(start)));
  }

  private static Response ensureRequestIsSet(Response response,
                                             RequestTemplate template,
                                             Request request) {
    return response.toBuilder()
        .request(request)
        .requestTemplate(template)
        .build();
  }

  private CompletableFuture<Object> handleResponse(Response response, long elapsedTime) {
    return asyncResponseHandler.handleResponse(
        metadata.configKey(), response, methodInfo.underlyingReturnType(), elapsedTime);
  }

  private long elapsedTime(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  private Request targetRequest(RequestTemplate template) {
    for (RequestInterceptor interceptor : requestInterceptors) {
      interceptor.apply(template);
    }
    return target.apply(template);
  }

  private Options findOptions(Object[] argv) {
    if (argv == null || argv.length == 0) {
      return this.options;
    }
    return Stream.of(argv)
        .filter(Options.class::isInstance)
        .map(Options.class::cast)
        .findFirst()
        .orElse(this.options);
  }

  static class Factory<C> implements MethodHandler.Factory<C> {

    private final AsyncClient<C> client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final AsyncResponseHandler responseHandler;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final ExceptionPropagationPolicy propagationPolicy;
    private final MethodInfoResolver methodInfoResolver;
    private final RequestTemplateFactoryResolver requestTemplateFactoryResolver;
    private final Options options;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;

    Factory(AsyncClient<C> client, Retryer retryer, List<RequestInterceptor> requestInterceptors,
        AsyncResponseHandler responseHandler,
        Logger logger, Logger.Level logLevel,
        ExceptionPropagationPolicy propagationPolicy,
        MethodInfoResolver methodInfoResolver,
        RequestTemplateFactoryResolver requestTemplateFactoryResolver,
        Options options,
        Decoder decoder,
        ErrorDecoder errorDecoder) {
      this.client = checkNotNull(client, "client");
      this.retryer = checkNotNull(retryer, "retryer");
      this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors");
      this.responseHandler = responseHandler;
      this.logger = checkNotNull(logger, "logger");
      this.logLevel = checkNotNull(logLevel, "logLevel");
      this.propagationPolicy = propagationPolicy;
      this.methodInfoResolver = methodInfoResolver;
      this.requestTemplateFactoryResolver =
          checkNotNull(requestTemplateFactoryResolver, "requestTemplateFactoryResolver");
      this.options = checkNotNull(options, "options");
      this.errorDecoder = checkNotNull(errorDecoder, "errorDecoder");
      this.decoder = checkNotNull(decoder, "decoder");
    }

    public MethodHandler create(Target<?> target,
                                MethodMetadata md,
                                C requestContext) {
      final RequestTemplate.Factory buildTemplateFromArgs =
          requestTemplateFactoryResolver.resolve(target, md);
      return new AsynchronousMethodHandler<C>(target, client, retryer, requestInterceptors,
          logger, logLevel, md, buildTemplateFromArgs, options, responseHandler,
          propagationPolicy, requestContext,
          methodInfoResolver.resolve(target.type(), md.method()));
    }
  }
}
