package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.Target;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Allows Feign interfaces to return HystrixCommand or rx.Observable or rx.Single objects. Also
 * decorates normal Feign methods with circuit breakers, but calls {@link HystrixCommand#execute()}
 * directly.
 */
public final class HystrixFeign {

  public static Builder builder() {
    return new Builder();
  }

  // Doesn't extend Feign.Builder for two reasons:
  // * Hide invocationHandlerFactory - as this isn't customizable
  // * Provide a path to the new fallback method w/o using covariant return types
  public static final class Builder {
    private final Feign.Builder delegate = new Feign.Builder();
    private Contract contract = new Contract.Default();

    /**
     * @see #target(Class, String, Object)
     */
    public <T> T target(Target<T> target, final T fallback) {
      delegate.invocationHandlerFactory(
          new InvocationHandlerFactory() {
            @Override
            public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
              return new HystrixInvocationHandler(target, dispatch, fallback);
            }
          });
      delegate.contract(new HystrixDelegatingContract(contract));
      return delegate.build().newInstance(target);
    }

    /**
     * Like {@link Feign#newInstance(Target)}, except with {@link HystrixCommand#getFallback()
     * fallback} support.
     *
     * <p>Fallbacks are known values, which you return when there's an error invoking an http
     * method. For example, you can return a cached result as opposed to raising an error to the
     * caller. To use this feature, pass a safe implementation of your target interface as the last
     * parameter.
     *
     * <p>Here's an example:
     *
     * <pre>{@code
     * // When dealing with fallbacks, it is less tedious to keep interfaces small.
     * interface GitHub {
     *   @RequestLine("GET /repos/{owner}/{repo}/contributors")
     *   List<String> contributors(@Param("owner") String owner, @Param("repo") String repo);
     * }
     *
     * // This instance will be invoked if there are errors of any kind.
     * GitHub fallback = (owner, repo) -> {
     *   if (owner.equals("Netflix") && repo.equals("feign")) {
     *     return Arrays.asList("stuarthendren"); // inspired this approach!
     *   } else {
     *     return Collections.emptyList();
     *   }
     * };
     *
     * GitHub github = HystrixFeign.builder()
     *                             ...
     *                             .target(GitHub.class, "https://api.github.com", fallback);
     * }</pre>
     *
     * @see #target(Target, Object)
     */
    public <T> T target(Class<T> apiType, String url, T fallback) {
      return target(new Target.HardCodedTarget<T>(apiType, url), fallback);
    }

    /**
     * @see feign.Feign.Builder#contract
     */
    public Builder contract(Contract contract) {
      this.contract = contract;
      return this;
    }

    /**
     * @see feign.Feign.Builder#build
     */
    public Feign build() {
      delegate.invocationHandlerFactory(new HystrixInvocationHandler.Factory());
      delegate.contract(new HystrixDelegatingContract(contract));
      return delegate.build();
    }

    // re-declaring methods in Feign.Builder is same work as covariant overrides,
    // but results in less complex bytecode.

    /**
     * @see feign.Feign.Builder#target(Class, String)
     */
    public <T> T target(Class<T> apiType, String url) {
      return target(new Target.HardCodedTarget<T>(apiType, url));
    }

    /**
     * @see feign.Feign.Builder#target(Target)
     */
    public <T> T target(Target<T> target) {
      return build().newInstance(target);
    }

    /**
     * @see feign.Feign.Builder#logLevel
     */
    public Builder logLevel(Logger.Level logLevel) {
      delegate.logLevel(logLevel);
      return this;
    }

    /**
     * @see feign.Feign.Builder#client
     */
    public Builder client(Client client) {
      delegate.client(client);
      return this;
    }

    /**
     * @see feign.Feign.Builder#retryer
     */
    public Builder retryer(Retryer retryer) {
      delegate.retryer(retryer);
      return this;
    }

    /**
     * @see feign.Feign.Builder#retryer
     */
    public Builder logger(Logger logger) {
      delegate.logger(logger);
      return this;
    }

    /**
     * @see feign.Feign.Builder#encoder
     */
    public Builder encoder(Encoder encoder) {
      delegate.encoder(encoder);
      return this;
    }

    /**
     * @see feign.Feign.Builder#decoder
     */
    public Builder decoder(Decoder decoder) {
      delegate.decoder(decoder);
      return this;
    }

    /**
     * @see feign.Feign.Builder#decode404
     */
    public Builder decode404() {
      delegate.decode404();
      return this;
    }

    /**
     * @see feign.Feign.Builder#errorDecoder
     */
    public Builder errorDecoder(ErrorDecoder errorDecoder) {
      delegate.errorDecoder(errorDecoder);
      return this;
    }

    /**
     * @see feign.Feign.Builder#options
     */
    public Builder options(Request.Options options) {
      delegate.options(options);
      return this;
    }

    /**
     * @see feign.Feign.Builder#requestInterceptor
     */
    public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
      delegate.requestInterceptor(requestInterceptor);
      return this;
    }

    /**
     * @see feign.Feign.Builder#requestInterceptors
     */
    public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
      delegate.requestInterceptors(requestInterceptors);
      return this;
    }
  }
}
