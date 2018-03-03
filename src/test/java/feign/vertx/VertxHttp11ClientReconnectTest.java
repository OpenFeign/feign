package feign.vertx;

import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Verify that Feign-Vertx are resilient to a server disconnect event.
 */
@RunWith(VertxUnitRunner.class)
public class VertxHttp11ClientReconnectTest {
  protected Vertx vertx = Vertx.vertx();
  protected HttpServer httpServer = null;
  HelloServiceAPI client = null;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  /**
   * Create a Feign Vertx client that is built once and used several times
   * during positive and negative test cases.
   * @param context
   */
  @Before
  public void before(TestContext context) {
    // for HTTP 1.1 test, set up the pool size to 3. Then in the server side, there should be only 3 connections created per client.
    HttpClientOptions options = new HttpClientOptions();
    options.setMaxPoolSize(3);

    client = VertxFeign
        .builder()
        .vertx(this.vertx)
        .options(options)
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .target(HelloServiceAPI.class, "http://localhost:8090");
  }

  /**
   * Shutdown the server
   * @param context
   */
  @After
  public void after(TestContext context) {
    closeServer();
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
            .setPort(8090)
            .setSsl(false);

    httpServer = this.vertx.createHttpServer(serverOptions);

    // Simple 200 handler
    httpServer.requestHandler( req -> req.response().setStatusCode(200).end("Success!") );

    // Listen! delegating to the future
    httpServer.listen( ret.completer() );

    return ret;
  }


  /**
   * Verify Feign-Vertx's HttpClient automatically reconnects
   * to a server that severed connections temporarily.
   * @param context
   */
  @Test
  public void testHttpReconnectTemporary(TestContext context) {
    // Async test context
    Async async = context.async();

    Future<Void> futSuccess = Future.future();

    // 1. Start the server
    // 2. Send a Volley of Requests to the server
    // 3. Close server!
    // 4. Start server again
    // 5. Send another Volley of Requests to the server
    // If all goes well the testFuture will signal success
    createServer()
        .compose( result -> requestVolley() )
        .compose( result -> closeServer() )
        .compose( result -> createServer() )
        .compose( result -> requestVolley() )
        .compose( result -> futSuccess.complete(), futSuccess);


    // This future will be triggered at the end of the test
    futSuccess.setHandler( result -> {
      if (result.succeeded()) {
        async.complete(); // success
      } else {
        context.fail(result.cause());
      }
    });
  }

  /**
   * Verify Feign-Vertx's HttpClient reconnects to a server
   * after an extended outage.
   * @param context
   */
  @Test
  public void testHttpReconnectOutage(TestContext context) {
    // Async test context
    Async async = context.async();

    Future<Void> futFailed = Future.future();
    Future<Void> futSuccess = Future.future();

    // 1. Start the server
    // 2. Send a Volley of Requests to the server
    // 3. Close server!
    // 4. Send another Volley of Requests to the server
    // Expect a failure and absorb it with futFailed
    createServer()
        .compose( result -> requestVolley() )
        .compose( result -> closeServer() )
        // do not restart the server, causing a failure on the next request
        .compose( result -> requestVolley() )
        .compose( result -> futFailed.complete(), futFailed);

    // We expect a failure when this future is called.
    futFailed.setHandler( result -> {
      if ( result.failed() ) {
        // We expect HTTP/1.1 "Connection was refused" or HTTP/2 "Connection was closed"
        if ( result.cause().getMessage().startsWith("Connection ") ) {
          // This is what we expect for this future!
          // 1. Start server again
          // 2. Re-using the same Feign-Vertx client as the failure above
          //    we send a new volley of requests
          // If all goes well futSuccess will succeed and end the unit test
          createServer()
              .compose( res2 -> requestVolley() )
              .compose( res2 -> futSuccess.complete(), futSuccess );
        } else {
          // Problem, we received an unexpected error
          context.fail("futFailed should have seen a \"Connection refused\" but received a \""+result.cause().getMessage()+"\"");
        }
      }
      else {
        // Problem, we unexpectedly succeeded!
        context.fail("futFailed should have received a failure but it actually succeeded!");
      }
    });

    // This is the final future that will be triggered upon a positive test case
    futSuccess.setHandler( result -> {
      if (result.succeeded()) {
        async.complete(); // success
      } else {
        context.fail(result.cause());
      }
    });
  }


  /**
   * This issues ten requests to the server via a shared
   * long lived Feign Vertx client.
   * @return
   */
  private CompositeFuture requestVolley() {
    List<Future> requests = new ArrayList<>(10);

    // Initiate 10 requests
    for( int i=0; i<10; i++ ) {
      Future<feign.Response> request = Future.future();
      client.hello().setHandler(request.completer());
      requests.add(request);
    }

    // Composite future for all of the requests
    return CompositeFuture.all(requests);
  }


  /**
   * Close the server connection
   * @return future for close completion
   */
  public Future<Void> closeServer() {
    // Close server
    Future<Void> fut = Future.future();
    httpServer.close(fut.completer());
    return fut;
  }
}
