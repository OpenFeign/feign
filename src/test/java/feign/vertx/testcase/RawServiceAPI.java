package feign.vertx.testcase;

import feign.Headers;
import feign.RequestLine;
import feign.Response;
import feign.vertx.testcase.domain.Bill;
import io.vertx.core.Future;

/**
 * Example of an API to to test rarely used features of Feign.
 *
 * @author Alexei KLENIN
 */
@Headers({ "Accept: application/json" })
public interface RawServiceAPI {

  @RequestLine("GET /icecream/flavors")
  Future<Response> getAvailableFlavors();

  @RequestLine("POST /icecream/bills/pay")
  @Headers("Content-Type: application/json")
  Future<Response> payBill(Bill bill);
}
