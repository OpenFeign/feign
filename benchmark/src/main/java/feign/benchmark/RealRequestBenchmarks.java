/**
 * Copyright 2012-2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.benchmark;

import feign.Logger;
import feign.Logger.Level;
import feign.Retryer;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.server.ErrorHandler;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import feign.Feign;
import feign.Response;
import rx.Observable;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class RealRequestBenchmarks {

  private static final int SERVER_PORT = 8765;
  private HttpServer<ByteBuf, ByteBuf> server;
  private OkHttpClient client;
  private FeignTestInterface okFeign;
  private Request queryRequest;

  @Setup
  public void setup() {
    server = RxNetty.createHttpServer(SERVER_PORT, (request, response) -> response.flush());
    server.start();
    client = new OkHttpClient();
    client.retryOnConnectionFailure();
    okFeign = Feign.builder()
        .client(new feign.okhttp.OkHttpClient(client))
        .logLevel(Level.NONE)
        .logger(new Logger.ErrorLogger())
        .retryer(new Retryer.Default())
        .target(FeignTestInterface.class, "http://localhost:" + SERVER_PORT);
    queryRequest = new Request.Builder()
        .url("http://localhost:" + SERVER_PORT + "/?Action=GetUser&Version=2010-05-08&limit=1")
        .build();
  }

  @TearDown
  public void tearDown() throws InterruptedException {
    server.shutdown();
  }

  /**
   * How fast can we execute get commands synchronously?
   */
  @Benchmark
  public okhttp3.Response query_baseCaseUsingOkHttp() throws IOException {
    okhttp3.Response result = client.newCall(queryRequest).execute();
    result.body().close();
    return result;
  }

  /**
   * How fast can we execute get commands synchronously using Feign?
   */
  @Benchmark
  public boolean query_feignUsingOkHttp() {
    /* auto close the response */
    try (Response ignored = okFeign.query()) {
      return true;
    }
  }
}
