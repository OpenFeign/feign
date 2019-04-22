/**
 * Copyright 2012-2019 The Feign Authors
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import feign.FeignConfig.FeignConfigBuilder;
import feign.ReflectiveFeign.ParseHandlersByName;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

/**
 * Feign's purpose is to ease development against http apis that feign restfulness. <br>
 * In implementation, Feign is a {@link Feign#newInstance factory} for generating {@link Target
 * targeted} http apis.
 */
public abstract class Feign {

  public static Builder builder() {
    return new Builder(FeignConfig.builder());
  }

  /**
   * Configuration keys are formatted as unresolved <a href=
   * "http://docs.oracle.com/javase/6/docs/jdk/api/javadoc/doclet/com/sun/javadoc/SeeTag.html" >see
   * tags</a>. This method exposes that format, in case you need to create the same value as
   * {@link MethodMetadata#configKey()} for correlation purposes.
   *
   * <p>
   * Here are some sample encodings:
   *
   * <pre>
   * <ul>
   *   <li>{@code Route53}: would match a class {@code route53.Route53}</li>
   *   <li>{@code Route53#list()}: would match a method {@code route53.Route53#list()}</li>
   *   <li>{@code Route53#listAt(Marker)}: would match a method {@code
   * route53.Route53#listAt(Marker)}</li>
   *   <li>{@code Route53#listByNameAndType(String, String)}: would match a method {@code
   * route53.Route53#listAt(String, String)}</li>
   * </ul>
   * </pre>
   *
   * Note that there is no whitespace expected in a key!
   *
   * @param targetType {@link feign.Target#type() type} of the Feign interface.
   * @param method invoked method, present on {@code type} or its super.
   * @see MethodMetadata#configKey()
   */
  public static String configKey(Class targetType, Method method) {
    final StringBuilder builder = new StringBuilder();
    builder.append(targetType.getSimpleName());
    builder.append('#').append(method.getName()).append('(');
    for (Type param : method.getGenericParameterTypes()) {
      param = Types.resolve(targetType, targetType, param);
      builder.append(Types.getRawType(param).getSimpleName()).append(',');
    }
    if (method.getParameterTypes().length > 0) {
      builder.deleteCharAt(builder.length() - 1);
    }
    return builder.append(')').toString();
  }

  /**
   * @deprecated use {@link #configKey(Class, Method)} instead.
   */
  @Deprecated
  public static String configKey(Method method) {
    return configKey(method.getDeclaringClass(), method);
  }

  /**
   * Returns a new instance of an HTTP API, defined by annotations in the {@link Feign Contract},
   * for the specified {@code target}. You should cache this result.
   */
  public abstract <T> T newInstance(Target<T> target);

  public static class Builder {

    private final List<RequestInterceptor> requestInterceptors =
        new ArrayList<RequestInterceptor>();
    private Contract contract = new Contract.Default();
    private InvocationHandlerFactory invocationHandlerFactory =
        new InvocationHandlerFactory.Default();
    private final FeignConfigBuilder feignConfigBuilder;

    protected Builder(FeignConfigBuilder feignConfigBuilder) {
      this.feignConfigBuilder = feignConfigBuilder;
    }

    public Builder logLevel(Logger.Level logLevel) {
      feignConfigBuilder.logLevel(logLevel);
      return this;
    }

    public Builder contract(Contract contract) {
      this.contract = contract;
      return this;
    }

    public Builder client(Client client) {
      feignConfigBuilder.client(client);
      return this;
    }

    public Builder retryer(Retryer retryer) {
      feignConfigBuilder.retryer(retryer);
      return this;
    }

    public Builder logger(Logger logger) {
      feignConfigBuilder.logger(logger);
      return this;
    }

    public Builder encoder(Encoder encoder) {
      feignConfigBuilder.encoder(encoder);
      return this;
    }

    public Builder decoder(Decoder decoder) {
      feignConfigBuilder.decoder(decoder);
      return this;
    }

    public Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
      feignConfigBuilder.queryMapEncoder(queryMapEncoder);
      return this;
    }

    /**
     * Allows to map the response before passing it to the decoder.
     */
    public Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
      feignConfigBuilder.decoder(new ResponseMappingDecoder(mapper, decoder));
      return this;
    }

    /**
     * This flag indicates that the {@link #decoder(Decoder) decoder} should process responses with
     * 404 status, specifically returning null or empty instead of throwing {@link FeignException}.
     *
     * <p/>
     * All first-party (ex gson) decoders return well-known empty values defined by
     * {@link Util#emptyValueOf}. To customize further, wrap an existing {@link #decoder(Decoder)
     * decoder} or make your own.
     *
     * <p/>
     * This flag only works with 404, as opposed to all or arbitrary status codes. This was an
     * explicit decision: 404 -> empty is safe, common and doesn't complicate redirection, retry or
     * fallback policy. If your server returns a different status for not-found, correct via a
     * custom {@link #client(Client) client}.
     *
     * @since 8.12
     */
    public Builder decode404() {
      feignConfigBuilder.decode404(true);
      return this;
    }

    public Builder errorDecoder(ErrorDecoder errorDecoder) {
      feignConfigBuilder.errorDecoder(errorDecoder);
      return this;
    }

    public Builder options(Options options) {
      feignConfigBuilder.options(options);
      return this;
    }

    /**
     * Adds a single request interceptor to the builder.
     */
    public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
      requestInterceptors.add(requestInterceptor);
      feignConfigBuilder.requestInterceptors(requestInterceptors);
      return this;
    }

    /**
     * Sets the full set of request interceptors for the builder, overwriting any previous
     * interceptors.
     */
    public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
      this.requestInterceptors.clear();
      for (final RequestInterceptor requestInterceptor : requestInterceptors) {
        this.requestInterceptors.add(requestInterceptor);
      }
      feignConfigBuilder.requestInterceptors(this.requestInterceptors);
      return this;
    }

    /**
     * Allows you to override how reflective dispatch works inside of Feign.
     */
    public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
      this.invocationHandlerFactory = invocationHandlerFactory;
      return this;
    }

    /**
     * This flag indicates that the response should not be automatically closed upon completion of
     * decoding the message. This should be set if you plan on processing the response into a
     * lazy-evaluated construct, such as a {@link java.util.Iterator}.
     *
     * </p>
     * Feign standard decoders do not have built in support for this flag. If you are using this
     * flag, you MUST also use a custom Decoder, and be sure to close all resources appropriately
     * somewhere in the Decoder (you can use {@link Util#ensureClosed} for convenience).
     *
     * @since 9.6
     *
     */
    public Builder doNotCloseAfterDecode() {
      feignConfigBuilder.closeAfterDecode(false);
      return this;
    }

    public Builder exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
      feignConfigBuilder.propagationPolicy(propagationPolicy);
      return this;
    }

    public <T> T target(Class<T> apiType, String url) {
      return target(new HardCodedTarget<T>(apiType, url));
    }

    public <T> T target(Target<T> target) {
      return build().newInstance(target);
    }

    public Feign build() {
      final FeignConfig feignConfig = feignConfigBuilder.build();
      final SynchronousMethodHandler.Factory synchronousMethodHandlerFactory =
          new SynchronousMethodHandler.Factory(feignConfig);
      final ParseHandlersByName handlersByName =
          new ParseHandlersByName(contract, feignConfig, synchronousMethodHandlerFactory);
      return new ReflectiveFeign(handlersByName, invocationHandlerFactory,
          feignConfig.queryMapEncoder);
    }
  }

  static class ResponseMappingDecoder implements Decoder {

    private final ResponseMapper mapper;
    private final Decoder delegate;

    ResponseMappingDecoder(ResponseMapper mapper, Decoder decoder) {
      this.mapper = mapper;
      this.delegate = decoder;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
      return delegate.decode(mapper.map(response, type), type);
    }
  }
}
