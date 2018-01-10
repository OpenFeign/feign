package feign;

import static feign.Util.checkNotNull;
import static feign.Util.isDefault;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.vertx.VertxDelegatingContract;
import feign.vertx.VertxHttpClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Allows Feign interfaces to return vertx {@code Futures}.
 *
 * @author Alexei KLENIN
 */
public class VertxFeign extends Feign {
  private final ParseHandlersByName targetToHandlersByName;
  private final InvocationHandlerFactory factory;

  private VertxFeign(
      final ParseHandlersByName targetToHandlersByName,
      final InvocationHandlerFactory factory) {
    this.targetToHandlersByName = targetToHandlersByName;
    this.factory = factory;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T newInstance(Target<T> target) {
    final Map<String, MethodHandler> nameToHandler =
        targetToHandlersByName.apply(target);
    final Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<>();
    final List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<>();

    for (final Method method : target.type().getMethods()) {
      if (isDefault(method)) {
        final DefaultMethodHandler handler = new DefaultMethodHandler(method);
        defaultMethodHandlers.add(handler);
        methodToHandler.put(method, handler);
      } else {
        methodToHandler.put(method, nameToHandler.get(
            Feign.configKey(target.type(), method)));
      }
    }

    final InvocationHandler handler = factory.create(target, methodToHandler);
    T proxy = (T) Proxy.newProxyInstance(
        target.type().getClassLoader(),
        new Class<?>[]{target.type()}, handler);

    for (final DefaultMethodHandler defaultMethodHandler :
        defaultMethodHandlers) {
      defaultMethodHandler.bindTo(proxy);
    }

    return proxy;
  }

  /**
   * VertxFeign builder.
   */
  public static final class Builder extends Feign.Builder {
    private Vertx vertx;
    private final List<RequestInterceptor> requestInterceptors =
        new ArrayList<>();
    private Logger.Level logLevel = Logger.Level.NONE;
    private Contract contract =
        new VertxDelegatingContract(new Contract.Default());
    private VertxHttpClient client;
    private Retryer retryer = new Retryer.Default();
    private Logger logger = new Logger.NoOpLogger();
    private Encoder encoder = new Encoder.Default();
    private Decoder decoder = new Decoder.Default();
    private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
    private HttpClientOptions options = new HttpClientOptions();
    private InvocationHandlerFactory invocationHandlerFactory =
        new VertxInvocationHandler.Factory();
    private boolean decode404;

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
     *
     * @return this builder
     */
    public Builder vertx(final Vertx vertx) {
      this.vertx = vertx;
      this.client = new VertxHttpClient(vertx);
      return this;
    }

    /**
     * Sets log level.
     *
     * @param logLevel log level
     *
     * @return this builder
     */
    @Override
    public Builder logLevel(final Logger.Level logLevel) {
      this.logLevel = logLevel;
      return this;
    }

    /**
     * Sets contract. Provided contract will be wrapped in
     * {@link VertxDelegatingContract}
     *
     * @param contract contract.
     *
     * @return this builder
     */
    @Override
    public Builder contract(final Contract contract) {
      this.contract = new VertxDelegatingContract(contract);
      return this;
    }

    /**
     * Sets retryer.
     *
     * @param retryer retryer
     *
     * @return this builder
     */
    @Override
    public Builder retryer(final Retryer retryer) {
      this.retryer = retryer;
      return this;
    }

    /**
     * Sets logger.
     *
     * @param logger logger
     *
     * @return this builder
     */
    @Override
    public Builder logger(final Logger logger) {
      this.logger = logger;
      return this;
    }

    /**
     * Sets encoder.
     *
     * @param encoder encoder
     *
     * @return this builder
     */
    @Override
    public Builder encoder(final Encoder encoder) {
      this.encoder = encoder;
      return this;
    }

    /**
     * Sets decoder.
     *
     * @param decoder decoder
     *
     * @return this builder
     */
    @Override
    public Builder decoder(final Decoder decoder) {
      this.decoder = decoder;
      return this;
    }

    /**
     * This flag indicates that the {@link #decoder(Decoder) decoder} should
     * process responses with 404 status, specifically returning null or empty
     * instead of throwing {@link FeignException}.
     *
     * <p>All first-party (ex gson) decoders return well-known empty values
     * defined by {@link Util#emptyValueOf}. To customize further, wrap an
     * existing {@link #decoder(Decoder) decoder} or make your own.
     *
     * <p>This flag only works with 404, as opposed to all or arbitrary status
     * codes. This was an explicit decision: 404 - empty is safe, common and
     * doesn't complicate redirection, retry or fallback policy.
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
     *
     * @return this builder
     */
    @Override
    public Builder errorDecoder(final ErrorDecoder errorDecoder) {
      this.errorDecoder = errorDecoder;
      return this;
    }

    /**
     * Sets request options using Vert.x {@link HttpClientOptions}
     *
     * @param options {@link HttpClientOptions} for full customization of the underlying Vert.x {@link HttpClient}
     *
     * @return this builder
     */
    public Builder options(final HttpClientOptions options) {
      this.options = options;
      return this;
    }

    /**
     * Sets request options using Feign {@link Request.Options}
     *
     * @param options Feign {@link Request.Options} object
     *
     * @return this builder
     */
    @Override
    public Builder options(final Request.Options options) {
      this.options = new HttpClientOptions()
                        .setConnectTimeout(options.connectTimeoutMillis())
                        .setIdleTimeout(options.readTimeoutMillis());
      return this;
    }


    /**
     * Adds a single request interceptor to the builder.
     *
     * @param requestInterceptor request interceptor to add
     *
     * @return this builder
     */
    @Override
    public Builder requestInterceptor(
        final RequestInterceptor requestInterceptor) {
      this.requestInterceptors.add(requestInterceptor);
      return this;
    }

    /**
     * Sets the full set of request interceptors for the builder, overwriting
     * any previous interceptors.
     *
     * @param requestInterceptors set of request interceptors
     *
     * @return this builder
     */
    @Override
    public Builder requestInterceptors(
        final Iterable<RequestInterceptor> requestInterceptors) {
      this.requestInterceptors.clear();
      for (RequestInterceptor requestInterceptor : requestInterceptors) {
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
     *
     * @return built client
     */
    @Override
    public <T> T target(final Class<T> apiType, final String url) {
      return target(new Target.HardCodedTarget<>(apiType, url));
    }

    /**
     * Defines target and builds client.
     *
     * @param target target instance
     * @param <T> class of API interface
     *
     * @return built client
     */
    @Override
    public <T> T target(final Target<T> target) {
      return build().newInstance(target);
    }

    @Override
    public VertxFeign build() {
      checkNotNull(this.vertx,
          "Vertx instance wasn't provided in VertxFeign builder");

      final AsynchronousMethodHandler.Factory methodHandlerFactory =
          new AsynchronousMethodHandler.Factory(client, retryer,
              requestInterceptors, logger, logLevel, decode404);
      final ParseHandlersByName handlersByName = new ParseHandlersByName(
          contract, options, encoder, decoder, errorDecoder,
          methodHandlerFactory);
      return new VertxFeign(handlersByName, invocationHandlerFactory);
    }
  }

  private static final class ParseHandlersByName {
    private final Contract contract;
    private final HttpClientOptions options;
    private final Encoder encoder;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final AsynchronousMethodHandler.Factory factory;

    ParseHandlersByName(
        final Contract contract,
        final HttpClientOptions options,
        final Encoder encoder,
        final Decoder decoder,
        final ErrorDecoder errorDecoder,
        final AsynchronousMethodHandler.Factory factory) {
      this.contract = contract;
      this.options = options;
      this.factory = factory;
      this.errorDecoder = errorDecoder;
      this.encoder = checkNotNull(encoder, "encoder must not be null");
      this.decoder = checkNotNull(decoder, "decoder must not be null");
    }

    Map<String, MethodHandler> apply(final Target key) {
      final List<MethodMetadata> metadata = contract
          .parseAndValidatateMetadata(key.type());
      final Map<String, MethodHandler> result = new LinkedHashMap<>();

      for (final MethodMetadata md : metadata) {
        BuildTemplateByResolvingArgs buildTemplate;

        if (!md.formParams().isEmpty()
            && md.template().bodyTemplate() == null) {
          buildTemplate = new BuildTemplateByResolvingArgs
              .BuildFormEncodedTemplateFromArgs(md, encoder);
        } else if (md.bodyIndex() != null) {
          buildTemplate = new BuildTemplateByResolvingArgs
              .BuildEncodedTemplateFromArgs(md, encoder);
        } else {
          buildTemplate = new BuildTemplateByResolvingArgs(md);
        }

        result.put(md.configKey(), factory.create(
                key, md, buildTemplate, options, decoder, errorDecoder));
      }

      return result;
    }
  }
}
