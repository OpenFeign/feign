package feign.vertx;

import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.HashSet;

/**
 * Verify that Feign-Vertx does not leak sockets upon each request
 */
@RunWith(VertxUnitRunner.class)
public class VertxHttp11ClientLeakFixTest {
  private Vertx vertx = Vertx.vertx();
  private HttpServer httpServer = null;

  private HashSet<HttpConnection> connections = new HashSet<>();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void testHttpClientIssue(TestContext context) {

    Async async = context.async();

    HttpServerOptions serverOptions =
            new HttpServerOptions()
                    .setLogActivity(true)
                    .setPort(8089)
                    .setSsl(false);

    httpServer = this.vertx.createHttpServer(serverOptions);

    // add a request handler and save the connections.
    httpServer.requestHandler(request -> {
      if (request.connection() != null)
      {
        this.connections.add(request.connection());
      }
      request.response().end("Hello world");
    });

    httpServer.listen(res -> {
      if (res.failed()) {
        context.assertTrue(res.failed(), "Server failed to start");
      } else {
        // for HTTP 1.1 test, set up the pool size to 3. Then in the server side, there should be only 3 connections created per client.
        HttpClientOptions options = new HttpClientOptions();
        options.setMaxPoolSize(3);

        HelloServiceAPI client = VertxFeign
            .builder()
            .vertx(this.vertx)
            .options(options)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(HelloServiceAPI.class, "http://localhost:8089");

        // run 10 times call to the server, then check if there are 10 connections created in server or not.
        for (int numToCall = 10; numToCall > 0; numToCall--) {

          /* When */
          client.hello().setHandler(response -> {
            /* Then */
            if (response.succeeded()) {
                async.complete();
            } else {
              context.fail(response.cause());
            }
          });
        }
      }
    });
  }

  @After
  public void after(TestContext context) {
    System.out.println("Connection created in server " + this.connections.size());

    // before the fix of HttpClient issue in VertxHttpClient, the server will create 10 connections for 10 requests.
    // after the fix of the issue, server should only create 3 connection per same client.
    context.assertTrue(this.connections.size() == 3);

    // Stop the server
    httpServer.close();
  }
}
