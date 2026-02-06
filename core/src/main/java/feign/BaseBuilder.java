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

import static feign.ExceptionPropagationPolicy.NONE;

import feign.Feign.ResponseMappingDecoder;
import feign.Logger.NoOpLogger;
import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class BaseBuilder<B extends BaseBuilder<B, T>, T> implements Cloneable {
  protected final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
  protected final List<ResponseInterceptor> responseInterceptors = new ArrayList<>();
  protected Logger.Level logLevel = Logger.Level.NONE;
  protected Contract contract = new Contract.Default();
  protected Retryer retryer = new Retryer.Default();
  protected Logger logger = new NoOpLogger();
  protected Encoder encoder = new Encoder.Default();
  protected Decoder decoder = new Decoder.Default();
  protected boolean closeAfterDecode = true;
  protected boolean decodeVoid = false;
  protected QueryMapEncoder queryMapEncoder = QueryMap.MapEncoder.FIELD.instance();
  protected ErrorDecoder errorDecoder = new ErrorDecoder.Default();
  protected Options options = new Options();
  protected InvocationHandlerFactory invocationHandlerFactory =
      new InvocationHandlerFactory.Default();
  protected boolean dismiss404;
  protected ExceptionPropagationPolicy propagationPolicy = NONE;
  protected List<Capability> capabilities = new ArrayList<>();

  public BaseBuilder() {
    super();
  }

  @SuppressWarnings("unchecked")
  public B logLevel(Logger.Level logLevel) {
    this.logLevel = logLevel;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B contract(Contract contract) {
    this.contract = contract;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B retryer(Retryer retryer) {
    this.retryer = retryer;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B logger(Logger logger) {
    this.logger = logger;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B encoder(Encoder encoder) {
    this.encoder = encoder;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B decoder(Decoder decoder) {
    this.decoder = decoder;
    return (B) this;
  }

  /**
   * This flag indicates that the response should not be automatically closed upon completion of
   * decoding the message. This should be set if you plan on processing the response into a
   * lazy-evaluated construct, such as a {@link java.util.Iterator}. Feign standard decoders do not
   * have built in support for this flag. If you are using this flag, you MUST also use a custom
   * Decoder, and be sure to close all resources appropriately somewhere in the Decoder (you can use
   * {@link Util#ensureClosed} for convenience).
   *
   * @since 9.6
   */
  @SuppressWarnings("unchecked")
  public B doNotCloseAfterDecode() {
    this.closeAfterDecode = false;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B decodeVoid() {
    this.decodeVoid = true;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B queryMapEncoder(QueryMapEncoder queryMapEncoder) {
    this.queryMapEncoder = queryMapEncoder;
    return (B) this;
  }

  /** Allows to map the response before passing it to the decoder. */
  @SuppressWarnings("unchecked")
  public B mapAndDecode(ResponseMapper mapper, Decoder decoder) {
    this.decoder = new ResponseMappingDecoder(mapper, decoder);
    return (B) this;
  }

  /**
   * This flag indicates that the {@link #decoder(Decoder) decoder} should process responses with
   * 404 status, specifically returning null or empty instead of throwing {@link FeignException}.
   *
   * <p>All first-party (ex gson) decoders return well-known empty values defined by {@link
   * Util#emptyValueOf}. To customize further, wrap an existing {@link #decoder(Decoder) decoder} or
   * make your own.
   *
   * <p>This flag only works with 404, as opposed to all or arbitrary status codes. This was an
   * explicit decision: 404 -> empty is safe, common and doesn't complicate redirection, retry or
   * fallback policy. If your server returns a different status for not-found, correct via a custom
   * {@link #client(Client) client}.
   *
   * @since 11.9
   */
  @SuppressWarnings("unchecked")
  public B dismiss404() {
    this.dismiss404 = true;
    return (B) this;
  }

  /**
   * This flag indicates that the {@link #decoder(Decoder) decoder} should process responses with
   * 404 status, specifically returning null or empty instead of throwing {@link FeignException}.
   *
   * <p>All first-party (ex gson) decoders return well-known empty values defined by {@link
   * Util#emptyValueOf}. To customize further, wrap an existing {@link #decoder(Decoder) decoder} or
   * make your own.
   *
   * <p>This flag only works with 404, as opposed to all or arbitrary status codes. This was an
   * explicit decision: 404 -> empty is safe, common and doesn't complicate redirection, retry or
   * fallback policy. If your server returns a different status for not-found, correct via a custom
   * {@link #client(Client) client}.
   *
   * @since 8.12
   * @deprecated use {@link #dismiss404()} instead.
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  public B decode404() {
    this.dismiss404 = true;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B errorDecoder(ErrorDecoder errorDecoder) {
    this.errorDecoder = errorDecoder;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B options(Options options) {
    this.options = options;
    return (B) this;
  }

  /** Adds a single request interceptor to the builder. */
  @SuppressWarnings("unchecked")
  public B requestInterceptor(RequestInterceptor requestInterceptor) {
    this.requestInterceptors.add(requestInterceptor);
    return (B) this;
  }

  /**
   * Sets the full set of request interceptors for the builder, overwriting any previous
   * interceptors.
   */
  @SuppressWarnings("unchecked")
  public B requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
    this.requestInterceptors.clear();
    for (RequestInterceptor requestInterceptor : requestInterceptors) {
      this.requestInterceptors.add(requestInterceptor);
    }
    return (B) this;
  }

  /**
   * Sets the full set of request interceptors for the builder, overwriting any previous
   * interceptors.
   */
  @SuppressWarnings("unchecked")
  public B responseInterceptors(Iterable<ResponseInterceptor> responseInterceptors) {
    this.responseInterceptors.clear();
    for (ResponseInterceptor responseInterceptor : responseInterceptors) {
      this.responseInterceptors.add(responseInterceptor);
    }
    return (B) this;
  }

  /** Adds a single response interceptor to the builder. */
  @SuppressWarnings("unchecked")
  public B responseInterceptor(ResponseInterceptor responseInterceptor) {
    this.responseInterceptors.add(responseInterceptor);
    return (B) this;
  }

  /** Allows you to override how reflective dispatch works inside of Feign. */
  @SuppressWarnings("unchecked")
  public B invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
    this.invocationHandlerFactory = invocationHandlerFactory;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
    this.propagationPolicy = propagationPolicy;
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  public B addCapability(Capability capability) {
    this.capabilities.add(capability);
    return (B) this;
  }

  @SuppressWarnings("unchecked")
  B enrich() {
    if (capabilities.isEmpty()) {
      return (B) this;
    }

    try {
      B clone = (B) this.clone();

      getFieldsToEnrich()
          .forEach(
              field -> {
                field.setAccessible(true);
                try {
                  final Object originalValue = field.get(clone);
                  final Object enriched;
                  if (originalValue instanceof List) {
                    Type ownerType =
                        ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                    enriched =
                        ((List) originalValue)
                            .stream()
                                .map(
                                    value ->
                                        Capability.enrich(
                                            value, (Class<?>) ownerType, capabilities))
                                .collect(Collectors.toList());
                  } else {
                    enriched = Capability.enrich(originalValue, field.getType(), capabilities);
                  }
                  field.set(clone, enriched);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                  throw new RuntimeException("Unable to enrich field " + field, e);
                } finally {
                  field.setAccessible(false);
                }
              });

      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError(e);
    }
  }

  List<Field> getFieldsToEnrich() {
    return Util.allFields(getClass()).stream()
        // exclude anything generated by compiler
        .filter(field -> !field.isSynthetic())
        // and capabilities itself
        .filter(field -> !Objects.equals(field.getName(), "capabilities"))
        // skip primitive types
        .filter(field -> !field.getType().isPrimitive())
        // skip enumerations
        .filter(field -> !field.getType().isEnum())
        .collect(Collectors.toList());
  }

  public final T build() {
    return enrich().internalBuild();
  }

  protected abstract T internalBuild();

  protected ResponseInterceptor.Chain responseInterceptorChain() {
    ResponseInterceptor.Chain endOfChain = ResponseInterceptor.Chain.DEFAULT;
    ResponseInterceptor.Chain executionChain =
        this.responseInterceptors.stream()
            .reduce(ResponseInterceptor::andThen)
            .map(interceptor -> interceptor.apply(endOfChain))
            .orElse(endOfChain);

    return (ResponseInterceptor.Chain)
        Capability.enrich(executionChain, ResponseInterceptor.Chain.class, capabilities);
  }
}
