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
package feign;

import static feign.ExceptionPropagationPolicy.UNWRAP;
import static feign.FeignException.errorExecuting;
import static feign.Util.checkNotNull;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Request.Options;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

final class AsynchronousMethodHandler<C> implements MethodHandler {

  private final AsyncClient<C> client;
  private final C requestContext;
  private final AsyncResponseHandler asyncResponseHandler;
  private final MethodInfo methodInfo;
  private final MethodHandlerConfiguration methodHandlerConfiguration;

  private AsynchronousMethodHandler(
      MethodHandlerConfiguration methodHandlerConfiguration,
      AsyncClient<C> client,
      AsyncResponseHandler asyncResponseHandler,
      C requestContext,
      MethodInfo methodInfo) {
    this.methodHandlerConfiguration =
        checkNotNull(methodHandlerConfiguration, "methodHandlerConfiguration");
    this.client = checkNotNull(client, "client for %s", methodHandlerConfiguration.getTarget());
    this.requestContext = requestContext;
    this.asyncResponseHandler = asyncResponseHandler;
    this.methodInfo = methodInfo;
  }

  @Override
  public Object invoke(Object[] argv) throws Throwable {
    RequestTemplate template = methodHandlerConfiguration.getBuildTemplateFromArgs().create(argv);
    Options options = findOptions(argv);
    Retryer retryer = this.methodHandlerConfiguration.getRetryer().clone();
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

  private CompletableFuture<Object> executeAndDecode(
      RequestTemplate template, Options options, Retryer retryer) {
    CancellableFuture<Object> resultFuture = new CancellableFuture<>();

    executeAndDecode(template, options)
        .whenComplete(
            (response, throwable) -> {
              if (throwable != null) {
                if (!resultFuture.isDone() && shouldRetry(retryer, throwable, resultFuture)) {
                  if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
                    methodHandlerConfiguration
                        .getLogger()
                        .logRetry(
                            methodHandlerConfiguration.getMetadata().configKey(),
                            methodHandlerConfiguration.getLogLevel());
                  }

                  resultFuture.setInner(executeAndDecode(template, options, retryer));
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

    private static <T> BiConsumer<? super T, ? super Throwable> pipeTo(
        CompletableFuture<T> completableFuture) {
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

  private boolean shouldRetry(
      Retryer retryer, Throwable throwable, CompletableFuture<Object> resultFuture) {
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
      if (methodHandlerConfiguration.getPropagationPolicy() == UNWRAP && cause != null) {
        resultFuture.completeExceptionally(cause);
      } else {
        resultFuture.completeExceptionally(th);
      }
      return false;
    }
  }

  private CompletableFuture<Object> executeAndDecode(RequestTemplate template, Options options) {
    Request request = targetRequest(template);

    if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
      methodHandlerConfiguration
          .getLogger()
          .logRequest(
              methodHandlerConfiguration.getMetadata().configKey(),
              methodHandlerConfiguration.getLogLevel(),
              request);
    }

    long start = System.nanoTime();
    return client
        .execute(request, options, Optional.ofNullable(requestContext))
        .thenApply(
            response ->
                // TODO: remove in Feign 12
                ensureRequestIsSet(response, template, request))
        .exceptionally(
            throwable -> {
              CompletionException completionException =
                  throwable instanceof CompletionException
                      ? (CompletionException) throwable
                      : new CompletionException(throwable);
              if (completionException.getCause() instanceof IOException) {
                IOException ioException = (IOException) completionException.getCause();
                if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
                  methodHandlerConfiguration
                      .getLogger()
                      .logIOException(
                          methodHandlerConfiguration.getMetadata().configKey(),
                          methodHandlerConfiguration.getLogLevel(),
                          ioException,
                          elapsedTime(start));
                }

                throw errorExecuting(request, ioException);
              } else {
                throw completionException;
              }
            })
        .thenCompose(response -> handleResponse(response, elapsedTime(start)));
  }

  private static Response ensureRequestIsSet(
      Response response, RequestTemplate template, Request request) {
    return response.toBuilder().request(request).requestTemplate(template).build();
  }

  private CompletableFuture<Object> handleResponse(Response response, long elapsedTime) {
    return asyncResponseHandler.handleResponse(
        methodHandlerConfiguration.getMetadata().configKey(), response,
        methodInfo.underlyingReturnType(), elapsedTime);
  }

  private long elapsedTime(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  private Request targetRequest(RequestTemplate template) {
    for (RequestInterceptor interceptor : methodHandlerConfiguration.getRequestInterceptors()) {
      interceptor.apply(template);
    }
    return methodHandlerConfiguration.getTarget().apply(template);
  }

  private Options findOptions(Object[] argv) {
    if (argv == null || argv.length == 0) {
      return this.methodHandlerConfiguration.getOptions();
    }
    return Stream.of(argv)
        .filter(Options.class::isInstance)
        .map(Options.class::cast)
        .findFirst()
        .orElse(this.methodHandlerConfiguration.getOptions());
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

    Factory(
        AsyncClient<C> client,
        Retryer retryer,
        List<RequestInterceptor> requestInterceptors,
        AsyncResponseHandler responseHandler,
        Logger logger,
        Logger.Level logLevel,
        ExceptionPropagationPolicy propagationPolicy,
        MethodInfoResolver methodInfoResolver,
        RequestTemplateFactoryResolver requestTemplateFactoryResolver,
        Options options) {
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
    }

    @Override
    public MethodHandler create(Target<?> target, MethodMetadata metadata, C requestContext) {
      final RequestTemplate.Factory buildTemplateFromArgs =
          requestTemplateFactoryResolver.resolve(target, metadata);

      MethodHandlerConfiguration methodHandlerConfiguration =
          new MethodHandlerConfiguration(
              metadata,
              target,
              retryer,
              requestInterceptors,
              logger,
              logLevel,
              buildTemplateFromArgs,
              options,
              propagationPolicy);
      return new AsynchronousMethodHandler<C>(
          methodHandlerConfiguration,
          client,
          responseHandler,
          requestContext,
          methodInfoResolver.resolve(target.type(), metadata.method()));
    }
  }
}
