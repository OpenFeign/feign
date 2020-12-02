package feign.vertx.testcase;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.vertx.VertxDelegatingContract;
import feign.vertx.testcase.domain.Bill;
import feign.vertx.testcase.domain.Flavor;
import feign.vertx.testcase.domain.IceCreamOrder;
import feign.vertx.testcase.domain.Mixin;
import io.vertx.core.Future;

import java.util.Collection;

/**
 * API of an iceream web service with one method that doesn't returns
 * {@link Future} and violates {@link VertxDelegatingContract}s rules.
 *
 * @author Alexei KLENIN
 */
public interface IcecreamServiceApiBroken {

  @RequestLine("GET /icecream/flavors")
  Future<Collection<Flavor>> getAvailableFlavors();

  @RequestLine("GET /icecream/mixins")
  Future<Collection<Mixin>> getAvailableMixins();

  @RequestLine("POST /icecream/orders")
  @Headers("Content-Type: application/json")
  Future<Bill> makeOrder(IceCreamOrder order);

  /** Method that doesn't respects contract. */
  @RequestLine("GET /icecream/orders/{orderId}")
  IceCreamOrder findOrder(@Param("orderId") int orderId);
}
