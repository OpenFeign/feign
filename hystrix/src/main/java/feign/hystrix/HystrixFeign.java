package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;

import feign.Contract;
import feign.Feign;

/**
 * Allows Feign interfaces to return HystrixCommand or rx.Observable or rx.Single objects.
 * Also decorates normal Feign methods with circuit breakers, but calls {@link HystrixCommand#execute()} directly.
 */
public final class HystrixFeign {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder extends Feign.Builder {

    public Builder() {
      invocationHandlerFactory(new HystrixInvocationHandler.Factory());
      contract(new HystrixDelegatingContract(new Contract.Default()));
    }

    @Override
    public Feign.Builder contract(Contract contract) {
      return super.contract(new HystrixDelegatingContract(contract));
    }
  }
}
