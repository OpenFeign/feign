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

import static feign.Util.checkNotNull;
import static feign.Util.isDefault;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.querymap.FieldQueryMapEncoder;
import feign.vertx.VertxDelegatingContract;
import feign.vertx.VertxHttpClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Allows Feign interfaces to return Vert.x {@link io.vertx.core.Future Future}s.
 *
 * @author Alexei KLENIN
 * @author Gordon McKinney
 */
public final class VertxFeign extends Feign {
  private final ParseHandlersByName targetToHandlersByName;
  private final InvocationHandlerFactory factory;

  private VertxFeign(
      final ParseHandlersByName targetToHandlersByName, final InvocationHandlerFactory factory) {
    this.targetToHandlersByName = targetToHandlersByName;
    this.factory = factory;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T newInstance(final Target<T> target) {
    checkNotNull(target, "Argument target must be not null");

    final Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
    final Map<Method, MethodHandler> methodToHandler = new HashMap<>();
    final List<DefaultMethodHandler> defaultMethodHandlers = new ArrayList<>();

    for (final Method method : target.type().getMethods()) {
      if (isDefault(method)) {
        final DefaultMethodHandler handler = new DefaultMethodHandler(method);
        defaultMethodHandlers.add(handler);
        methodToHandler.put(method, handler);
      } else {
        methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
      }
    }

    final InvocationHandler handler = factory.create(target, methodToHandler);
    final T proxy =
        (T)
            Proxy.newProxyInstance(
                target.type().getClassLoader(), new Class<?>[] {target.type()}, handler);

    for (final DefaultMethodHandler defaultMethodHandler : defaultMethodHandlers) {
      defaultMethodHandler.bindTo(proxy);
    }

    return proxy;
  }

  /** VertxFeign builder. */
  public static final class Builder extends Feign.Builder {
    private Vertx vertx;
    private final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
    private Logger.Level logLevel = Logger.Level.NONE;
    private Contract contract = new VertxDelegatingContract(new Contract.Default());
    private Retryer retryer = new Retryer.Default();
    private Logger logger = new Logger.NoOpLogger();
    private Encoder encoder = new Encoder.Default();
    private Decoder decoder = new Decoder.Default();
    private QueryMapEncoder queryMapEncoder = new FieldQueryMapEncoder();
    private List<Capability> capabilities = new ArrayList<>();
    private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
    private HttpClientOptions options = new HttpClientOptions();
    private long timeout = -1;
    private boolean decode404;
    private UnaryOperator<HttpClientRequest> requestPreProcessor = UnaryOperator.identity();

    /** Unsupported operation. */
    @Override
    public Builder client(final Client client) {
      throw new UnsupportedOperationException();
    }

    /** Unsupported operation. */
    @Override
    public Builder invocationHandlerFactory(
        final InvocationHandlerFactory invocationHandlerFactory) {
      throw new UnsupportedOperationException();
    }

    /**
     * Sets a vertx instance to use to make the client.
     *
     * @param vertx vertx instance
     * @return this builder
     */
    public Builder vertx(final Vertx vertx) {
      this.vertx = checkNotNull(vertx, "Argument vertx must be not null");
      return this;
    }

    /**
     * Sets log level.
     *
     * @param logLevel log level
     * @return this builder
     */
    @Override
    public Builder logLevel(final Logger.Level logLevel) {
      this.logLevel = checkNotNull(logLevel, "Argument logLevel must be not null");
      return this;
    }

    /**
     * Sets contract. Provided contract will be wrapped in {@link VertxDelegatingContract}.
     *
     * @param contract contract
     * @return this builder
     */
    @Override
    public Builder contract(final Contract contract) {
      checkNotNull(contract, "Argument contract must be not null");
      this.contract = new VertxDelegatingContract(contract);
      return this;
    }

    /**
     * Sets retryer.
     *
     * @param retryer retryer
     * @return this builder
     */
    @Override
    public Builder retryer(final Retryer retryer) {
      this.retryer = checkNotNull(retryer, "Argument retryer must be not null");
      return this;
    }

    /**
     * Sets logger.
     *
     * @param logger logger
     * @return this builder
     */
    @Override
    public Builder logger(final Logger logger) {
      this.logger = checkNotNull(logger, "Argument logger must be not null");
      return this;
    }

    /**
     * Sets encoder.
     *
     * @param encoder encoder
     * @return this builder
     */
    @Override
    public Builder encoder(final Encoder encoder) {
      this.encoder = checkNotNull(encoder, "Argument encoder must be not null");
      return this;
    }

    /**
     * Sets decoder.
     *
     * @param decoder decoder
     * @return this builder
     */
    @Override
    public Builder decoder(final Decoder decoder) {
      this.decoder = checkNotNull(decoder, "Argument decoder must be not null");
      return this;
    }

    /**
     * Sets query map encoder.
     *
     * @param queryMapEncoder query map encoder
     * @return this builder
     */
    @Override
    public Builder queryMapEncoder(final QueryMapEncoder queryMapEncoder) {
      this.queryMapEncoder =
          checkNotNull(queryMapEncoder, "Argument queryMapEncoder must be not null");
      return this;
    }

    /**
     * Adds a single capability to the builder.
     *
     * @param capability capability
     * @return this builder
     */
    @Override
    public Builder addCapability(Capability capability) {
      checkNotNull(capability, "Argument capability must be not null");
      this.capabilities.add(capability);
      return this;
    }

    /**
     * This flag indicates that the {@link #decoder(Decoder) decoder} should process responses with
     * 404 status, specifically returning null or empty instead of throwing {@link FeignException}.
     *
     * <p>All first-party (ex gson) decoders return well-known empty values defined by {@link
     * Util#emptyValueOf}. To customize further, wrap an existing {@link #decoder(Decoder) decoder}
     * or make your own.
     *
     * <p>This flag only works with 404, as opposed to all or arbitrary status codes. This was an
     * explicit decision: 404 - empty is safe, common and doesn't complicate redirection, retry or
     * fallback policy.
     *
     * @return this builder
     */
    @Override
    public Builder decode404() {
      this.decode404 = true;
      return this;
    }

    /**
     * Sets error decoder.
     *
     * @param errorDecoder error deoceder
     * @return this builder
     */
    @Override
    public Builder errorDecoder(final ErrorDecoder errorDecoder) {
      this.errorDecoder = checkNotNull(errorDecoder, "Argument errorDecoder must be not null");
      return this;
    }

    /**
     * Sets request options using Vert.x {@link HttpClientOptions}.
     *
     * @param options {@code HttpClientOptions} for full customization of the underlying Vert.x
     *     {@link HttpClient}
     * @return this builder
     */
    public Builder options(final HttpClientOptions options) {
      this.options = checkNotNull(options, "Argument options must be not null");
      return this;
    }

    /**
     * Sets request options using Feign {@link Request.Options}.
     *
     * @param options Feign {@code Request.Options} object
     * @return this builder
     */
    @Override
    public Builder options(final Request.Options options) {
      checkNotNull(options, "Argument options must be not null");
      this.options =
          new HttpClientOptions()
              .setConnectTimeout(options.connectTimeoutMillis())
              .setIdleTimeout(options.readTimeoutMillis());
      return this;
    }

    /**
     * Configures the amount of time in milliseconds after which if the request does not return any
     * data within the timeout period an {@link java.util.concurrent.TimeoutException} fails the
     * request.
     *
     * <p>Setting zero or a negative {@code value} disables the timeout.
     *
     * @param timeout The quantity of time in milliseconds.
     * @return this builder
     */
    public Builder timeout(long timeout) {
      this.timeout = timeout;
      return this;
    }

    /**
     * Defines operation to execute on each {@link HttpClientRequest} before it is sent. Used to
     * make setup on request level.
     *
     * <p>Example:
     *
     * <pre>
     * var client = VertxFeign
     *     .builder()
     *     .vertx(vertx)
     *     .requestPreProcessor(req -&#62; req.putHeader("version", "v1"));
     * </pre>
     *
     * @param requestPreProcessor operation to execute on each request
     * @return updated request
     */
    public Builder requestPreProcessor(UnaryOperator<HttpClientRequest> requestPreProcessor) {
      this.requestPreProcessor =
          checkNotNull(requestPreProcessor, "Argument requestPreProcessor must be not null");
      return this;
    }

    /**
     * Adds a single request interceptor to the builder.
     *
     * @param requestInterceptor request interceptor to add
     * @return this builder
     */
    @Override
    public Builder requestInterceptor(final RequestInterceptor requestInterceptor) {
      checkNotNull(requestInterceptor, "Argument requestInterceptor must be not null");
      this.requestInterceptors.add(requestInterceptor);
      return this;
    }

    /**
     * Sets the full set of request interceptors for the builder, overwriting any previous
     * interceptors.
     *
     * @param requestInterceptors set of request interceptors
     * @return this builder
     */
    @Override
    public Builder requestInterceptors(final Iterable<RequestInterceptor> requestInterceptors) {
      checkNotNull(requestInterceptors, "Argument requestInterceptors must be not null");

      this.requestInterceptors.clear();

      for (final RequestInterceptor requestInterceptor : requestInterceptors) {
        this.requestInterceptors.add(requestInterceptor);
      }

      return this;
    }

    /**
     * Defines target and builds client.
     *
     * @param apiType API interface
     * @param url base URL
     * @param <T> class of API interface
     * @return built client
     */
    @Override
    public <T> T target(final Class<T> apiType, final String url) {
      checkNotNull(apiType, "Argument apiType must be not null");
      checkNotNull(url, "Argument url must be not null");

      return target(new Target.HardCodedTarget<>(apiType, url));
    }

    /**
     * Defines target and builds client.
     *
     * @param target target instance
     * @param <T> class of API interface
     * @return built client
     */
    @Override
    public <T> T target(final Target<T> target) {
      return build().newInstance(target);
    }

    @Override
    public VertxFeign internalBuild() {
      checkNotNull(this.vertx, "Vertx instance wasn't provided in VertxFeign builder");

      final VertxHttpClient client =
          new VertxHttpClient(vertx, options, timeout, requestPreProcessor);
      final VertxMethodHandler.Factory methodHandlerFactory =
          new VertxMethodHandler.Factory(
              client, retryer, requestInterceptors, logger, logLevel, decode404);
      final ParseHandlersByName handlersByName =
          new ParseHandlersByName(
              contract,
              options,
              encoder,
              decoder,
              queryMapEncoder,
              capabilities,
              errorDecoder,
              methodHandlerFactory);
      final InvocationHandlerFactory invocationHandlerFactory =
          new VertxInvocationHandler.Factory();

      return new VertxFeign(handlersByName, invocationHandlerFactory);
    }
  }

  private static final class ParseHandlersByName {
    private final Contract contract;
    private final HttpClientOptions options;
    private final Encoder encoder;
    private final Decoder decoder;
    private final QueryMapEncoder queryMapEncoder;
    private final List<Capability> capabilities;
    private final ErrorDecoder errorDecoder;
    private final VertxMethodHandler.Factory factory;

    private ParseHandlersByName(
        final Contract contract,
        final HttpClientOptions options,
        final Encoder encoder,
        final Decoder decoder,
        final QueryMapEncoder queryMapEncoder,
        final List<Capability> capabilities,
        final ErrorDecoder errorDecoder,
        final VertxMethodHandler.Factory factory) {
      this.contract = contract;
      this.options = options;
      this.factory = factory;
      this.encoder = encoder;
      this.decoder = decoder;
      this.queryMapEncoder = queryMapEncoder;
      this.capabilities = capabilities;
      this.errorDecoder = errorDecoder;
    }

    private Map<String, MethodHandler> apply(final Target<?> target) {
      final List<MethodMetadata> metadata = contract.parseAndValidateMetadata(target.type());
      final Map<String, MethodHandler> result = new HashMap<>();

      for (final MethodMetadata metadatum : metadata) {
        RequestTemplateFactoryResolver.BuildTemplateByResolvingArgs buildTemplate;

        if (!metadatum.formParams().isEmpty() && metadatum.template().bodyTemplate() == null) {
          buildTemplate =
              new RequestTemplateFactoryResolver.BuildFormEncodedTemplateFromArgs(
                  metadatum, encoder, queryMapEncoder, target);
        } else if (metadatum.bodyIndex() != null || metadatum.alwaysEncodeBody()) {
          buildTemplate =
              new RequestTemplateFactoryResolver.BuildEncodedTemplateFromArgs(
                  metadatum, encoder, queryMapEncoder, target);
        } else {
          buildTemplate =
              new RequestTemplateFactoryResolver.BuildTemplateByResolvingArgs(
                  metadatum, queryMapEncoder, target);
        }

        result.put(
            metadatum.configKey(),
            factory.create(target, metadatum, buildTemplate, decoder, errorDecoder));
      }

      return result;
    }
  }
}
