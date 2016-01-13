package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import feign.Contract;
import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.Target;

/**
 * Allows Feign interfaces to return HystrixCommand or rx.Observable or rx.Single objects. Also
 * decorates normal Feign methods with circuit breakers, but calls {@link HystrixCommand#execute()}
 * directly.
 */
public final class HystrixFeign {

  public static Builder builder() {
    return new Builder();
  }

  // Extends Feign.Builder because downstream projects use it incrementally.
  public static final class Builder extends Feign.Builder {

    public Builder() {
      // both needed if fallbacks, not used
      contract(new HystrixDelegatingContract(new Contract.Default()));
      super.invocationHandlerFactory(buildInvocationHandlerFactory(null));
    }

    private InvocationHandlerFactory buildInvocationHandlerFactory(final Object fallback) {
      return new InvocationHandlerFactory() {
        @Override
        public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
          return new HystrixInvocationHandler(target, dispatch, fallback);
        }
      };
    }

    /**
     * @see #target(Class, String, Object)
     */
    public <T> T target(Target<T> target, final T fallback) {
      super.invocationHandlerFactory(buildInvocationHandlerFactory(fallback));
      return build().newInstance(target);
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
     * GitHub github = HystrixFeign.builder(fallback)
     *                             ...
     *                             .target(GitHub.class, "https://api.github.com");
     * }</pre>
     */
    public <T> T target(Class<T> apiType, String url, T fallback) {
      return target(new Target.HardCodedTarget<T>(apiType, url), fallback);
    }

    @Override
    public Feign.Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
      //TODO: throw exception, log warning or do nothing?
      return this;
    }

    /**
     * @see feign.Feign.Builder#contract
     */
    public Feign.Builder contract(Contract contract) {
      return super.contract(new HystrixDelegatingContract(contract));
    }
  }
}
