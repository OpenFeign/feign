package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

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

/**
 * Allows Feign interfaces to return HystrixCommand or rx.Observable or rx.Single objects. Also
 * decorates normal Feign methods with circuit breakers, but calls {@link HystrixCommand#execute()}
 * directly.
 */
public final class HystrixFeign {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder extends Feign.Builder {

    private Contract contract = new Contract.Default();

    /**
     * @see #target(Class, String, Object)
     */
    public <T> T target(Target<T> target, final T fallback) {
      super.invocationHandlerFactory(new InvocationHandlerFactory() {
        @Override
        public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
          return new HystrixInvocationHandler(target, dispatch, fallback);
        }
      });
      super.contract(new HystrixDelegatingContract(contract));
      return super.build().newInstance(target);
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
     * Here's an example:
     * <pre>
     * {@code
     *
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

    @Override
    public Feign.Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Builder contract(Contract contract) {
      this.contract = contract;
      return this;
    }

    @Override
    public Feign build() {
      super.invocationHandlerFactory(new HystrixInvocationHandler.Factory());
      super.contract(new HystrixDelegatingContract(contract));
      return super.build();
    }

    // Covariant overrides to support chaining to new fallback method.
    @Override
    public Builder logLevel(Logger.Level logLevel) {
      return (Builder) super.logLevel(logLevel);
    }

    @Override
    public Builder client(Client client) {
      return (Builder) super.client(client);
    }

    @Override
    public Builder retryer(Retryer retryer) {
      return (Builder) super.retryer(retryer);
    }

    @Override
    public Builder logger(Logger logger) {
      return (Builder) super.logger(logger);
    }

    @Override
    public Builder encoder(Encoder encoder) {
      return (Builder) super.encoder(encoder);
    }

    @Override
    public Builder decoder(Decoder decoder) {
      return (Builder) super.decoder(decoder);
    }

    @Override
    public Builder decode404() {
      return (Builder) super.decode404();
    }

    @Override
    public Builder errorDecoder(ErrorDecoder errorDecoder) {
      return (Builder) super.errorDecoder(errorDecoder);
    }

    @Override
    public Builder options(Request.Options options) {
      return (Builder) super.options(options);
    }

    @Override
    public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
      return (Builder) super.requestInterceptor(requestInterceptor);
    }

    @Override
    public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
      return (Builder) super.requestInterceptors(requestInterceptors);
    }
  }
}
