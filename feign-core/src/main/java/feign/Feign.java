package feign;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.ObjectGraph;
import dagger.Provides;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.Wire.NoOpWire;
import feign.codec.BodyEncoder;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.codec.FormEncoder;
import java.lang.reflect.Method;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;

/**
 * Feign's purpose is to ease development against http apis that feign restfulness.
 *
 * <p>In implementation, Feign is a {@link Feign#newInstance factory} for generating {@link Target
 * targeted} http apis.
 */
public abstract class Feign {

  /**
   * Returns a new instance of an HTTP API, defined by annotations in the {@link Feign Contract},
   * for the specified {@code target}. You should cache this result.
   */
  public abstract <T> T newInstance(Target<T> target);

  public static <T> T create(Class<T> apiType, String url, Object... modules) {
    return create(new HardCodedTarget<T>(apiType, url), modules);
  }

  /**
   * Shortcut to {@link #newInstance(Target) create} a single {@code targeted} http api using {@link
   * ReflectiveFeign reflection}.
   */
  public static <T> T create(Target<T> target, Object... modules) {
    return create(modules).newInstance(target);
  }

  /**
   * Returns a {@link ReflectiveFeign reflective} factory for generating {@link Target targeted}
   * http apis.
   */
  public static Feign create(Object... modules) {
    Object[] modulesForGraph =
        ImmutableList.builder() //
            .add(new Defaults()) //
            .add(new ReflectiveFeign.Module()) //
            .add(Optional.fromNullable(modules).or(new Object[] {}))
            .build()
            .toArray();
    return ObjectGraph.create(modulesForGraph).get(Feign.class);
  }

  /**
   * Returns an {@link ObjectGraph Dagger ObjectGraph} that can inject a {@link ReflectiveFeign
   * reflective} Feign.
   */
  public static ObjectGraph createObjectGraph(Object... modules) {
    Object[] modulesForGraph =
        ImmutableList.builder() //
            .add(new Defaults()) //
            .add(new ReflectiveFeign.Module()) //
            .add(Optional.fromNullable(modules).or(new Object[] {}))
            .build()
            .toArray();
    return ObjectGraph.create(modulesForGraph);
  }

  @dagger.Module(complete = false, injects = Feign.class, library = true)
  public static class Defaults {

    @Provides
    SSLSocketFactory sslSocketFactory() {
      return SSLSocketFactory.class.cast(SSLSocketFactory.getDefault());
    }

    @Provides
    Client httpClient(Client.Default client) {
      return client;
    }

    @Provides
    Retryer retryer() {
      return new Retryer.Default();
    }

    @Provides
    Wire noOp() {
      return new NoOpWire();
    }

    @Provides
    Map<String, Options> noOptions() {
      return ImmutableMap.of();
    }

    @Provides
    Map<String, BodyEncoder> noBodyEncoders() {
      return ImmutableMap.of();
    }

    @Provides
    Map<String, FormEncoder> noFormEncoders() {
      return ImmutableMap.of();
    }

    @Provides
    Map<String, Decoder> noDecoders() {
      return ImmutableMap.of();
    }

    @Provides
    Map<String, ErrorDecoder> noErrorDecoders() {
      return ImmutableMap.of();
    }
  }

  /**
   * Configuration keys are formatted as unresolved <a href=
   * "http://docs.oracle.com/javase/6/docs/jdk/api/javadoc/doclet/com/sun/javadoc/SeeTag.html" >see
   * tags</a>.
   *
   * <p>For example.
   *
   * <ul>
   *   <li>{@code Route53}: would match a class such as {@code denominator.route53.Route53}
   *   <li>{@code Route53#list()}: would match a method such as {@code
   *       denominator.route53.Route53#list()}
   *   <li>{@code Route53#listAt(Marker)}: would match a method such as {@code
   *       denominator.route53.Route53#listAt(denominator.route53.Marker)}
   *   <li>{@code Route53#listByNameAndType(String, String)}: would match a method such as {@code
   *       denominator.route53.Route53#listAt(String, String)}
   * </ul>
   *
   * <p>Note that there is no whitespace expected in a key!
   */
  public static String configKey(Method method) {
    StringBuilder builder = new StringBuilder();
    builder.append(method.getDeclaringClass().getSimpleName());
    builder.append('#').append(method.getName()).append('(');
    for (Class<?> param : method.getParameterTypes())
      builder.append(param.getSimpleName()).append(',');
    if (method.getParameterTypes().length > 0) builder.deleteCharAt(builder.length() - 1);
    return builder.append(')').toString();
  }

  Feign() {}
}
