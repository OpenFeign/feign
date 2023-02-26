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
import feign.Logger.Level;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Enhances {@link Feign} to provide support for asynchronous clients. Context (for example for
 * session cookies or tokens) is explicit, as calls for the same session may be done across several
 * threads. <br>
 * <br>
 * {@link Retryer} is not supported in this model, as that is a blocking API.
 * {@link ExceptionPropagationPolicy} is made redundant as {@link RetryableException} is never
 * thrown. <br>
 * Alternative approaches to retrying can be handled through {@link AsyncClient clients}. <br>
 * <br>
 * Target interface methods must return {@link CompletableFuture} with a non-wildcard type. As the
 * completion is done by the {@link AsyncClient}, it is important that any subsequent processing on
 * the thread be short - generally, this should involve notifying some other thread of the work to
 * be done (for example, creating and submitting a task to an {@link ExecutorService}).
 */
@Experimental
public final class AsyncFeign<C> {
  public static <C> AsyncBuilder<C> builder() {
    return new AsyncBuilder<>();
  }

  /**
   * @deprecated use {@link #builder()} instead.
   */
  @Deprecated()
  public static <C> AsyncBuilder<C> asyncBuilder() {
    return builder();
  }

  private static class LazyInitializedExecutorService {

    private static final ExecutorService instance =
        Executors.newCachedThreadPool(
            r -> {
              final Thread result = new Thread(r);
              result.setDaemon(true);
              return result;
            });
  }

  public static class AsyncBuilder<C> extends BaseBuilder<AsyncBuilder<C>> {

    private AsyncContextSupplier<C> defaultContextSupplier = () -> null;
    private AsyncClient<C> client = new AsyncClient.Default<>(
        new Client.Default(null, null), LazyInitializedExecutorService.instance);
    private MethodInfoResolver methodInfoResolver = MethodInfo::new;

    @Deprecated
    public AsyncBuilder<C> defaultContextSupplier(Supplier<C> supplier) {
      this.defaultContextSupplier = supplier::get;
      return this;
    }

    public AsyncBuilder<C> client(AsyncClient<C> client) {
      this.client = client;
      return this;
    }

    public AsyncBuilder<C> methodInfoResolver(MethodInfoResolver methodInfoResolver) {
      this.methodInfoResolver = methodInfoResolver;
      return this;
    }

    @Override
    public AsyncBuilder<C> mapAndDecode(ResponseMapper mapper, Decoder decoder) {
      return super.mapAndDecode(mapper, decoder);
    }

    @Override
    public AsyncBuilder<C> decoder(Decoder decoder) {
      return super.decoder(decoder);
    }

    @Override
    @Deprecated
    public AsyncBuilder<C> decode404() {
      return super.decode404();
    }

    @Override
    public AsyncBuilder<C> dismiss404() {
      return super.dismiss404();
    }

    @Override
    public AsyncBuilder<C> errorDecoder(ErrorDecoder errorDecoder) {
      return super.errorDecoder(errorDecoder);
    }

    @Override
    public AsyncBuilder<C> doNotCloseAfterDecode() {
      return super.doNotCloseAfterDecode();
    }

    public AsyncBuilder<C> defaultContextSupplier(AsyncContextSupplier<C> supplier) {
      this.defaultContextSupplier = supplier;
      return this;
    }

    public <T> T target(Class<T> apiType, String url) {
      return target(new HardCodedTarget<>(apiType, url));
    }

    public <T> T target(Class<T> apiType, String url, C context) {
      return target(new HardCodedTarget<>(apiType, url), context);
    }

    public <T> T target(Target<T> target) {
      return build().newInstance(target);
    }

    public <T> T target(Target<T> target, C context) {
      return build().newInstance(target, context);
    }

    @Override
    public AsyncBuilder<C> logLevel(Level logLevel) {
      return super.logLevel(logLevel);
    }

    @Override
    public AsyncBuilder<C> contract(Contract contract) {
      return super.contract(contract);
    }

    @Override
    public AsyncBuilder<C> logger(Logger logger) {
      return super.logger(logger);
    }

    @Override
    public AsyncBuilder<C> encoder(Encoder encoder) {
      return super.encoder(encoder);
    }

    @Override
    public AsyncBuilder<C> queryMapEncoder(QueryMapEncoder queryMapEncoder) {
      return super.queryMapEncoder(queryMapEncoder);
    }

    @Override
    public AsyncBuilder<C> options(Options options) {
      return super.options(options);
    }

    @Override
    public AsyncBuilder<C> requestInterceptor(RequestInterceptor requestInterceptor) {
      return super.requestInterceptor(requestInterceptor);
    }

    @Override
    public AsyncBuilder<C> requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
      return super.requestInterceptors(requestInterceptors);
    }

    @Override
    public AsyncBuilder<C> invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
      return super.invocationHandlerFactory(invocationHandlerFactory);
    }

    public AsyncFeign<C> build() {
      super.enrich();

      AsyncResponseHandler responseHandler =
          (AsyncResponseHandler) Capability.enrich(
              new AsyncResponseHandler(
                  logLevel,
                  logger,
                  decoder,
                  errorDecoder,
                  dismiss404,
                  closeAfterDecode, responseInterceptor),
              AsyncResponseHandler.class,
              capabilities);

      final MethodHandler.Factory<C> methodHandlerFactory =
          new AsynchronousMethodHandler.Factory<>(
              client, retryer, requestInterceptors,
              responseHandler, logger, logLevel,
              propagationPolicy, methodInfoResolver,
              new RequestTemplateFactoryResolver(encoder, queryMapEncoder),
              options, decoder, errorDecoder);
      final ReflectiveFeign<C> feign =
          new ReflectiveFeign<>(contract, methodHandlerFactory, invocationHandlerFactory,
              defaultContextSupplier);
      return new AsyncFeign<>(feign);
    }
  }

  private final ReflectiveFeign<C> feign;

  private AsyncFeign(ReflectiveFeign<C> feign) {
    this.feign = feign;
  }

  public <T> T newInstance(Target<T> target) {
    return feign.newInstance(target);
  }

  public <T> T newInstance(Target<T> target, C context) {
    return feign.newInstance(target, context);
  }
}
