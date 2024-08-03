/*
 * Copyright 2012-2024 The Feign Authors
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

import static feign.ExceptionPropagationPolicy.UNWRAP;
import static feign.FeignException.errorExecuting;
import static feign.Util.checkNotNull;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

final class SynchronousMethodHandler implements MethodHandler {

  private final Client client;
  private final ResponseHandler responseHandler;
  private MethodHandlerConfiguration methodHandlerConfiguration =
      new MethodHandlerConfiguration(null, null, null, null, null, null, null, null, null);


  private SynchronousMethodHandler(Target<?> target, Client client, Retryer retryer,
      List<RequestInterceptor> requestInterceptors,
      Logger logger, Logger.Level logLevel, MethodMetadata metadata,
      RequestTemplate.Factory buildTemplateFromArgs, Options options,
      ResponseHandler responseHandler, ExceptionPropagationPolicy propagationPolicy) {

    this.methodHandlerConfiguration.setTarget(checkNotNull(target, "target"));
    this.client = checkNotNull(client, "client for %s", target);
    this.methodHandlerConfiguration.setRetryer(checkNotNull(retryer, "retryer for %s", target));
    this.methodHandlerConfiguration.setRequestInterceptors(
        checkNotNull(requestInterceptors, "requestInterceptors for %s", target));
    this.methodHandlerConfiguration.setLogger(checkNotNull(logger, "logger for %s", target));
    this.methodHandlerConfiguration.setLogLevel(checkNotNull(logLevel, "logLevel for %s", target));
    this.methodHandlerConfiguration.setMetadata(checkNotNull(metadata, "metadata for %s", target));
    this.methodHandlerConfiguration
        .setBuildTemplateFromArgs(checkNotNull(buildTemplateFromArgs, "metadata for %s", target));
    this.methodHandlerConfiguration.setOptions(checkNotNull(options, "options for %s", target));
    this.methodHandlerConfiguration.setPropagationPolicy(propagationPolicy);
    this.responseHandler = responseHandler;
  }

  @Override
  public Object invoke(Object[] argv) throws Throwable {
    RequestTemplate template = methodHandlerConfiguration.getBuildTemplateFromArgs().create(argv);
    Options options = findOptions(argv);
    Retryer retryer = this.methodHandlerConfiguration.getRetryer().clone();
    while (true) {
      try {
        return executeAndDecode(template, options);
      } catch (RetryableException e) {
        try {
          retryer.continueOrPropagate(e);
        } catch (RetryableException th) {
          Throwable cause = th.getCause();
          if (methodHandlerConfiguration.getPropagationPolicy() == UNWRAP && cause != null) {
            throw cause;
          } else {
            throw th;
          }
        }
        if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
          methodHandlerConfiguration.getLogger().logRetry(
              methodHandlerConfiguration.getMetadata().configKey(),
              methodHandlerConfiguration.getLogLevel());
        }
        continue;
      }
    }
  }

  Object executeAndDecode(RequestTemplate template, Options options) throws Throwable {
    Request request = targetRequest(template);

    if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
      methodHandlerConfiguration.getLogger().logRequest(
          methodHandlerConfiguration.getMetadata().configKey(),
          methodHandlerConfiguration.getLogLevel(), request);
    }

    Response response;
    long start = System.nanoTime();
    try {
      response = client.execute(request, options);
      // ensure the request is set. TODO: remove in Feign 12
      response = response.toBuilder()
          .request(request)
          .requestTemplate(template)
          .build();
    } catch (IOException e) {
      if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
        methodHandlerConfiguration.getLogger().logIOException(
            methodHandlerConfiguration.getMetadata().configKey(),
            methodHandlerConfiguration.getLogLevel(), e, elapsedTime(start));
      }
      throw errorExecuting(request, e);
    }

    long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    return responseHandler.handleResponse(
        methodHandlerConfiguration.getMetadata().configKey(), response,
        methodHandlerConfiguration.getMetadata().returnType(), elapsedTime);
  }

  long elapsedTime(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  Request targetRequest(RequestTemplate template) {
    for (RequestInterceptor interceptor : methodHandlerConfiguration.getRequestInterceptors()) {
      interceptor.apply(template);
    }
    return methodHandlerConfiguration.getTarget().apply(template);
  }

  Options findOptions(Object[] argv) {
    if (argv == null || argv.length == 0) {
      return this.methodHandlerConfiguration.getOptions()
          .getMethodOptions(methodHandlerConfiguration.getMetadata().method().getName());
    }
    return Stream.of(argv)
        .filter(Options.class::isInstance)
        .map(Options.class::cast)
        .findFirst()
        .orElse(this.methodHandlerConfiguration.getOptions()
            .getMethodOptions(methodHandlerConfiguration.getMetadata().method().getName()));
  }

  static class Factory implements MethodHandler.Factory<Object> {

    private final Client client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final ResponseHandler responseHandler;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final ExceptionPropagationPolicy propagationPolicy;
    private final RequestTemplateFactoryResolver requestTemplateFactoryResolver;
    private final Options options;

    Factory(Client client, Retryer retryer, List<RequestInterceptor> requestInterceptors,
        ResponseHandler responseHandler,
        Logger logger, Logger.Level logLevel,
        ExceptionPropagationPolicy propagationPolicy,
        RequestTemplateFactoryResolver requestTemplateFactoryResolver,
        Options options) {
      this.client = checkNotNull(client, "client");
      this.retryer = checkNotNull(retryer, "retryer");
      this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors");
      this.responseHandler = checkNotNull(responseHandler, "responseHandler");
      this.logger = checkNotNull(logger, "logger");
      this.logLevel = checkNotNull(logLevel, "logLevel");
      this.propagationPolicy = propagationPolicy;
      this.requestTemplateFactoryResolver =
          checkNotNull(requestTemplateFactoryResolver, "requestTemplateFactoryResolver");
      this.options = checkNotNull(options, "options");
    }

    public MethodHandler create(Target<?> target,
                                MethodMetadata md,
                                Object requestContext) {
      final RequestTemplate.Factory buildTemplateFromArgs =
          requestTemplateFactoryResolver.resolve(target, md);
      return new SynchronousMethodHandler(target, client, retryer, requestInterceptors,
          logger, logLevel, md, buildTemplateFromArgs, options,
          responseHandler, propagationPolicy);
    }
  }
}
