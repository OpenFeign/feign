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

import static feign.ExceptionPropagationPolicy.NONE;
import feign.Feign.ResponseMappingDecoder;
import feign.Logger.NoOpLogger;
import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.querymap.FieldQueryMapEncoder;
import java.util.ArrayList;
import java.util.List;



public abstract class BaseBuilder<B extends BaseBuilder<B>> {

  private final B thisB;


  protected final List<RequestInterceptor> requestInterceptors =
      new ArrayList<>();
  protected Logger.Level logLevel = Logger.Level.NONE;
  protected Contract contract = new Contract.Default();
  protected Retryer retryer = new Retryer.Default();
  protected Logger logger = new NoOpLogger();
  protected Encoder encoder = new Encoder.Default();
  protected Decoder decoder = new Decoder.Default();
  protected QueryMapEncoder queryMapEncoder = new FieldQueryMapEncoder();
  protected ErrorDecoder errorDecoder = new ErrorDecoder.Default();
  protected Options options = new Options();
  protected InvocationHandlerFactory invocationHandlerFactory =
      new InvocationHandlerFactory.Default();
  protected boolean dismiss404;
  protected ExceptionPropagationPolicy propagationPolicy = NONE;
  protected List<Capability> capabilities = new ArrayList<>();

  public BaseBuilder() {
    super();
    thisB = (B) this;
  }

  public B logLevel(Logger.Level logLevel) {
    this.logLevel = logLevel;
    return thisB;
  }

  public B contract(Contract contract) {
    this.contract = contract;
    return thisB;
  }

  public B retryer(Retryer retryer) {
    this.retryer = retryer;
    return thisB;
  }

  public B logger(Logger logger) {
    this.logger = logger;
    return thisB;
  }

  public B encoder(Encoder encoder) {
    this.encoder = encoder;
    return thisB;
  }

  public B decoder(Decoder decoder) {
    this.decoder = decoder;
    return thisB;
  }

  public B queryMapEncoder(QueryMapEncoder queryMapEncoder) {
    this.queryMapEncoder = queryMapEncoder;
    return thisB;
  }

  /**
   * Allows to map the response before passing it to the decoder.
   */
  public B mapAndDecode(ResponseMapper mapper, Decoder decoder) {
    this.decoder = new ResponseMappingDecoder(mapper, decoder);
    return thisB;
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
   * fallback policy. If your server returns a different status for not-found, correct via a custom
   * {@link #client(Client) client}.
   *
   * @since 11.9
   */
  public B dismiss404() {
    this.dismiss404 = true;
    return thisB;
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
   * fallback policy. If your server returns a different status for not-found, correct via a custom
   * {@link #client(Client) client}.
   *
   * @since 8.12
   * @deprecated
   */
  @Deprecated
  public B decode404() {
    this.dismiss404 = true;
    return thisB;
  }


  public B errorDecoder(ErrorDecoder errorDecoder) {
    this.errorDecoder = errorDecoder;
    return thisB;
  }

  public B options(Options options) {
    this.options = options;
    return thisB;
  }

  /**
   * Adds a single request interceptor to the builder.
   */
  public B requestInterceptor(RequestInterceptor requestInterceptor) {
    this.requestInterceptors.add(requestInterceptor);
    return thisB;
  }

  /**
   * Sets the full set of request interceptors for the builder, overwriting any previous
   * interceptors.
   */
  public B requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
    this.requestInterceptors.clear();
    for (RequestInterceptor requestInterceptor : requestInterceptors) {
      this.requestInterceptors.add(requestInterceptor);
    }
    return thisB;
  }

  /**
   * Allows you to override how reflective dispatch works inside of Feign.
   */
  public B invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
    this.invocationHandlerFactory = invocationHandlerFactory;
    return thisB;
  }

  public B exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
    this.propagationPolicy = propagationPolicy;
    return thisB;
  }

  public B addCapability(Capability capability) {
    this.capabilities.add(capability);
    return thisB;
  }


}
