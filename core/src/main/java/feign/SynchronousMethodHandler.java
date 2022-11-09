/*
 * Copyright 2012-2022 The Feign Authors
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

  private final MethodMetadata metadata;
  private final Target<?> target;
  private final Client client;
  private final Retryer retryer;
  private final List<RequestInterceptor> requestInterceptors;
  private final Logger logger;
  private final Logger.Level logLevel;
  private final RequestTemplate.Factory buildTemplateFromArgs;
  private final Options options;
  private final ExceptionPropagationPolicy propagationPolicy;
  private final ResponseHandler responseHandler;

  private SynchronousMethodHandler(
      Target<?> target,
      Client client,
      Retryer retryer,
      List<RequestInterceptor> requestInterceptors,
      ResponseInterceptor responseInterceptor,
      Logger logger,
      Logger.Level logLevel,
      MethodMetadata metadata,
      RequestTemplate.Factory buildTemplateFromArgs,
      Options options,
      Decoder decoder,
      ErrorDecoder errorDecoder,
      boolean dismiss404,
      boolean closeAfterDecode,
      ExceptionPropagationPolicy propagationPolicy) {

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
    this.responseHandler =
        new ResponseHandler(
            logLevel,
            logger,
            decoder,
            errorDecoder,
            dismiss404,
            closeAfterDecode,
            responseInterceptor);
  }

  @Override
  public Object invoke(Object[] argv) throws Throwable {
    RequestTemplate template = buildTemplateFromArgs.create(argv);
    Options options = findOptions(argv);
    Retryer retryer = this.retryer.clone();
    while (true) {
      try {
        return executeAndDecode(template, options);
      } catch (RetryableException e) {
        try {
          retryer.continueOrPropagate(e);
        } catch (RetryableException th) {
          Throwable cause = th.getCause();
          if (propagationPolicy == UNWRAP && cause != null) {
            throw cause;
          } else {
            throw th;
          }
        }
        if (logLevel != Logger.Level.NONE) {
          logger.logRetry(metadata.configKey(), logLevel);
        }
        continue;
      }
    }
  }

  Object executeAndDecode(RequestTemplate template, Options options) throws Throwable {
    Request request = targetRequest(template);

    if (logLevel != Logger.Level.NONE) {
      logger.logRequest(metadata.configKey(), logLevel, request);
    }

    Response response;
    long start = System.nanoTime();
    try {
      response = client.execute(request, options);
      // ensure the request is set. TODO: remove in Feign 12
      response = response.toBuilder().request(request).requestTemplate(template).build();
    } catch (IOException e) {
      if (logLevel != Logger.Level.NONE) {
        logger.logIOException(metadata.configKey(), logLevel, e, elapsedTime(start));
      }
      throw errorExecuting(request, e);
    }

    long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    return responseHandler.handleResponse(
        metadata.configKey(), response, metadata.returnType(), elapsedTime);
  }

  long elapsedTime(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  Request targetRequest(RequestTemplate template) {
    for (RequestInterceptor interceptor : requestInterceptors) {
      interceptor.apply(template);
    }
    return target.apply(template);
  }

  Options findOptions(Object[] argv) {
    if (argv == null || argv.length == 0) {
      return this.options;
    }
    return Stream.of(argv)
        .filter(Options.class::isInstance)
        .map(Options.class::cast)
        .findFirst()
        .orElse(this.options);
  }

  static class Factory implements MethodHandler.Factory<Object> {

    private final Client client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final ResponseInterceptor responseInterceptor;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final boolean dismiss404;
    private final boolean closeAfterDecode;
    private final ExceptionPropagationPolicy propagationPolicy;
    private final Options options;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;

    Factory(
        Client client,
        Retryer retryer,
        List<RequestInterceptor> requestInterceptors,
        ResponseInterceptor responseInterceptor,
        Logger logger,
        Logger.Level logLevel,
        boolean dismiss404,
        boolean closeAfterDecode,
        ExceptionPropagationPolicy propagationPolicy,
        Options options,
        Decoder decoder,
        ErrorDecoder errorDecoder) {
      this.client = checkNotNull(client, "client");
      this.retryer = checkNotNull(retryer, "retryer");
      this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors");
      this.responseInterceptor = responseInterceptor;
      this.logger = checkNotNull(logger, "logger");
      this.logLevel = checkNotNull(logLevel, "logLevel");
      this.dismiss404 = dismiss404;
      this.closeAfterDecode = closeAfterDecode;
      this.propagationPolicy = propagationPolicy;
      this.options = checkNotNull(options, "options");
      this.errorDecoder = checkNotNull(errorDecoder, "errorDecoder");
      this.decoder = checkNotNull(decoder, "decoder");
    }

    public MethodHandler create(
        Target<?> target,
        MethodMetadata md,
        RequestTemplate.Factory buildTemplateFromArgs,
        Object requestContext) {
      return new SynchronousMethodHandler(
          target,
          client,
          retryer,
          requestInterceptors,
          responseInterceptor,
          logger,
          logLevel,
          md,
          buildTemplateFromArgs,
          options,
          decoder,
          errorDecoder,
          dismiss404,
          closeAfterDecode,
          propagationPolicy);
    }
  }
}
