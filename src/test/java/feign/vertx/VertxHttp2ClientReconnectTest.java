package feign.vertx;

import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * Verify that Feign-Vertx are resilient to a server disconnect event.
 */
@RunWith(VertxUnitRunner.class)
public class VertxHttp2ClientReconnectTest extends VertxHttp11ClientReconnectTest {
  /**
   * Create a Feign Vertx client that is built once and used several times
   * during positive and negative test cases.
   * @param context
   */
  @Before
  public void before(TestContext context) {
    // for HTTP2 test, set up the protocol and the pool size to 1.
    HttpClientOptions options = new HttpClientOptions();
    options
        .setProtocolVersion(HttpVersion.HTTP_2)
        .setHttp2MaxPoolSize(1);

    client = VertxFeign
        .builder()
        .vertx(this.vertx)
        .options(options)
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .target(HelloServiceAPI.class, "http://localhost:8091");
  }


  /**
   * Create an HTTP Server and return a future for the startup result
   * @return Future for handling the serve open event
   */
  protected Future<HttpServer> createServer() {
    Future<HttpServer> ret = Future.future();

    HttpServerOptions serverOptions =
        new HttpServerOptions()
            .setLogActivity(true)
            .setPort(8091)
            .setSsl(false);

    httpServer = this.vertx.createHttpServer(serverOptions);

    // Simple 200 handler
    httpServer.requestHandler( req -> req.response().setStatusCode(200).end("Success!") );

    // Listen! delegating to the future
    httpServer.listen( ret.completer() );

    return ret;
  }
}
