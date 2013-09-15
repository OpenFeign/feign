/*
 * Copyright 2013 Netflix, Inc.
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


import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import feign.Logger.NoOpLogger;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Feign's purpose is to ease development against http apis that feign
 * restfulness.
 * <br>
 * In implementation, Feign is a {@link Feign#newInstance factory} for
 * generating {@link Target targeted} http apis.
 */
public abstract class Feign {

  /**
   * Returns a new instance of an HTTP API, defined by annotations in the
   * {@link Feign Contract}, for the specified {@code target}. You should
   * cache this result.
   */
  public abstract <T> T newInstance(Target<T> target);

  public static Builder builder() {
    return new Builder();
  }

  public static <T> T create(Class<T> apiType, String url, Object... modules) {
    return create(new HardCodedTarget<T>(apiType, url), modules);
  }

  /**
   * Shortcut to {@link #newInstance(Target) create} a single {@code targeted}
   * http api using {@link ReflectiveFeign reflection}.
   */
  public static <T> T create(Target<T> target, Object... modules) {
    return create(modules).newInstance(target);
  }

  /**
   * Returns a {@link ReflectiveFeign reflective} factory for generating
   * {@link Target targeted} http apis.
   */
  public static Feign create(Object... modules) {
    return ObjectGraph.create(modulesForGraph(modules).toArray()).get(Feign.class);
  }


  /**
   * Returns an {@link ObjectGraph Dagger ObjectGraph} that can inject a
   * {@link ReflectiveFeign reflective} Feign.
   */
  public static ObjectGraph createObjectGraph(Object... modules) {
    return ObjectGraph.create(modulesForGraph(modules).toArray());
  }

  @SuppressWarnings("rawtypes")
  @dagger.Module(complete = false, injects = {Feign.class, Builder.class}, library = true)
  public static class Defaults {

    @Provides Logger.Level logLevel() {
      return Logger.Level.NONE;
    }

    @Provides Contract contract() {
      return new Contract.Default();
    }

    @Provides SSLSocketFactory sslSocketFactory() {
      return SSLSocketFactory.class.cast(SSLSocketFactory.getDefault());
    }

    @Provides HostnameVerifier hostnameVerifier() {
      return HttpsURLConnection.getDefaultHostnameVerifier();
    }

    @Provides Client httpClient(Client.Default client) {
      return client;
    }

    @Provides Retryer retryer() {
      return new Retryer.Default();
    }

    @Provides Logger noOp() {
      return new NoOpLogger();
    }

    @Provides
    Encoder defaultEncoder() {
      return new Encoder.Default();
    }

    @Provides
    Decoder defaultDecoder() {
      return new Decoder.Default();
    }

    @Provides ErrorDecoder errorDecoder() {
      return new ErrorDecoder.Default();
    }

    @Provides Options options() {
      return new Options();
    }
  }

  /**
   * <br>
   * Configuration keys are formatted as unresolved <a href=
   * "http://docs.oracle.com/javase/6/docs/jdk/api/javadoc/doclet/com/sun/javadoc/SeeTag.html"
   * >see tags</a>.
   * <br>
   * For example.
   * <ul>
   * <li>{@code Route53}: would match a class such as
   * {@code denominator.route53.Route53}
   * <li>{@code Route53#list()}: would match a method such as
   * {@code denominator.route53.Route53#list()}
   * <li>{@code Route53#listAt(Marker)}: would match a method such as
   * {@code denominator.route53.Route53#listAt(denominator.route53.Marker)}
   * <li>{@code Route53#listByNameAndType(String, String)}: would match a
   * method such as {@code denominator.route53.Route53#listAt(String, String)}
   * </ul>
   * <br>
   * Note that there is no whitespace expected in a key!
   */
  public static String configKey(Method method) {
    StringBuilder builder = new StringBuilder();
    builder.append(method.getDeclaringClass().getSimpleName());
    builder.append('#').append(method.getName()).append('(');
    for (Class<?> param : method.getParameterTypes())
      builder.append(param.getSimpleName()).append(',');
    if (method.getParameterTypes().length > 0)
      builder.deleteCharAt(builder.length() - 1);
    return builder.append(')').toString();
  }

  private static List<Object> modulesForGraph(Object... modules) {
    List<Object> modulesForGraph = new ArrayList<Object>(3);
    modulesForGraph.add(new Defaults());
    modulesForGraph.add(new ReflectiveFeign.Module());
    if (modules != null)
      for (Object module : modules)
        modulesForGraph.add(module);
    return modulesForGraph;
  }

  public static class Builder {
    private final Set<RequestInterceptor> requestInterceptors = new LinkedHashSet<RequestInterceptor>();
    @Inject Logger.Level logLevel;
    @Inject Contract contract;
    @Inject Client client;
    @Inject Retryer retryer;
    @Inject Logger logger;
    @Inject Encoder encoder;
    @Inject Decoder decoder;
    @Inject ErrorDecoder errorDecoder;
    @Inject Options options;

    Builder() {
      ObjectGraph.create(new Defaults()).inject(this);
    }

    public Builder logLevel(Logger.Level logLevel) {
      this.logLevel = logLevel;
      return this;
    }

    public Builder contract(Contract contract) {
      this.contract = contract;
      return this;
    }

    public Builder client(Client client) {
      this.client = client;
      return this;
    }

    public Builder retryer(Retryer retryer) {
      this.retryer = retryer;
      return this;
    }

    public Builder logger(Logger logger) {
      this.logger = logger;
      return this;
    }

    public Builder encoder(Encoder encoder) {
      this.encoder = encoder;
      return this;
    }

    public Builder decoder(Decoder decoder) {
      this.decoder = decoder;
      return this;
    }

    public Builder errorDecoder(ErrorDecoder errorDecoder) {
      this.errorDecoder = errorDecoder;
      return this;
    }

    public Builder options(Options options) {
      this.options = options;
      return this;
    }

    /**
     * Adds a single request interceptor to the builder.
     */
    public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
      this.requestInterceptors.add(requestInterceptor);
      return this;
    }

    /**
     * Sets the full set of request interceptors for the builder, overwriting any previous interceptors.
     */
    public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
      this.requestInterceptors.clear();
      for (RequestInterceptor requestInterceptor : requestInterceptors) {
        this.requestInterceptors.add(requestInterceptor);
      }
      return this;
    }

    public <T> T target(Class<T> apiType, String url) {
      return target(new HardCodedTarget<T>(apiType, url));
    }

    public <T> T target(Target<T> target) {
      BuilderModule module = new BuilderModule(this);
      return create(module).newInstance(target);
    }
  }

  @Module(library = true, overrides = true, addsTo = Defaults.class)
  static class BuilderModule {
    private final Logger.Level logLevel;
    private final Contract contract;
    private final Client client;
    private final Retryer retryer;
    private final Logger logger;
    private final Encoder encoder;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final Options options;
    private final Set<RequestInterceptor> requestInterceptors;

    BuilderModule(Builder builder) {
      this.logLevel = builder.logLevel;
      this.contract = builder.contract;
      this.client = builder.client;
      this.retryer = builder.retryer;
      this.logger = builder.logger;
      this.encoder = builder.encoder;
      this.decoder = builder.decoder;
      this.errorDecoder = builder.errorDecoder;
      this.options = builder.options;
      this.requestInterceptors = builder.requestInterceptors;
    }

    @Provides Logger.Level logLevel() {
      return logLevel;
    }

    @Provides Contract contract() {
      return contract;
    }

    @Provides Client client() {
      return client;
    }

    @Provides Retryer retryer() {
      return retryer;
    }

    @Provides Logger logger() {
      return logger;
    }

    @Provides
    Encoder encoder() {
      return encoder;
    }

    @Provides
    Decoder decoder() {
      return decoder;
    }

    @Provides ErrorDecoder errorDecoder() {
      return errorDecoder;
    }

    @Provides Options options() {
      return options;
    }

    @Provides(type = Provides.Type.SET_VALUES) Set<RequestInterceptor> requestInterceptors() {
      return requestInterceptors;
    }
  }
}
