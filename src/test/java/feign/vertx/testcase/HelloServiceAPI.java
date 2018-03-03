package feign.vertx.testcase;

import feign.Headers;
import feign.RequestLine;
import feign.Response;
import io.vertx.core.Future;

/**
 * Example of an API to to test number of Http2 connections of Feign.
 *
 * @author James Xu
 */
@Headers({ "Accept: application/json" })
public interface HelloServiceAPI {

  @RequestLine("GET /hello")
  Future<Response> hello();

}
