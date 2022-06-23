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

import feign.ReflectiveFeign.ParseHandlersByName;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Feign's purpose is to ease development against http apis that feign restfulness. <br>
 * In implementation, Feign is a {@link Feign#newInstance factory} for generating {@link Target
 * targeted} http apis.
 */
public abstract class Feign {

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Configuration keys are formatted as unresolved <a href=
   * "http://docs.oracle.com/javase/6/docs/jdk/api/javadoc/doclet/com/sun/javadoc/SeeTag.html" >see
   * tags</a>. This method exposes that format, in case you need to create the same value as {@link
   * MethodMetadata#configKey()} for correlation purposes.
   *
   * <p>Here are some sample encodings:
   *
   * <pre>
   * <ul>
   *   <li>{@code
   * Route53
   * }: would match a class {@code
   * route53.Route53
   * }</li>
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
    StringBuilder builder = new StringBuilder();
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

  public static class Builder extends BaseBuilder<Builder> {

    private Client client = new Client.Default(null, null);
    private boolean forceDecoding = false;

    public Builder client(Client client) {
      this.client = client;

      return this;
    }

    /** Internal - used to indicate that the decoder should be immediately called */
    Builder forceDecoding() {
      this.forceDecoding = true;
      return this;
    }

    public <T> T target(Class<T> apiType, String url) {
      return target(new HardCodedTarget<>(apiType, url));
    }

    public <T> T target(Target<T> target) {
      return build().newInstance(target);
    }

    public Feign build() {
      super.enrich();

      SynchronousMethodHandler.Factory synchronousMethodHandlerFactory =
          new SynchronousMethodHandler.Factory(
              client,
              retryer,
              requestInterceptors,
              responseInterceptor,
              logger,
              logLevel,
              dismiss404,
              closeAfterDecode,
              propagationPolicy,
              forceDecoding);
      ParseHandlersByName handlersByName =
          new ParseHandlersByName(
              contract,
              options,
              encoder,
              decoder,
              queryMapEncoder,
              errorDecoder,
              synchronousMethodHandlerFactory);
      return new ReflectiveFeign(handlersByName, invocationHandlerFactory, queryMapEncoder);
    }
  }

  public static class ResponseMappingDecoder implements Decoder {

    private final ResponseMapper mapper;
    private final Decoder delegate;

    public ResponseMappingDecoder(ResponseMapper mapper, Decoder decoder) {
      this.mapper = mapper;
      this.delegate = decoder;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
      return delegate.decode(mapper.map(response, type), type);
    }
  }
}
