/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.benchmark;

import feign.Feign;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonIteratorDecoder;
import feign.stream.StreamDecoder;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import okhttp3.OkHttpClient;
import org.openjdk.jmh.annotations.*;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * This test shows up memory consumption of different json array response processing implementations.
 */
@State(Scope.Thread)
public class FeignIteratorsBenchmark {
  private static final String HUNDRED = "100";
  private static final String MILLION = "1000000";

  interface ListApi {
    @RequestLine("GET /cars/{count}")
    List<Car> getCars(@feign.Param("count") String count);
  }

  interface IteratorApi {
    @RequestLine("GET /cars/{count}")
    Iterator<Car> getCars(@feign.Param("count") String count);
  }

  interface StreamApi {
    @RequestLine("GET /cars/{count}")
    Stream<Car> getCars(@feign.Param("count") String count);
  }

  @Param({"list", "iterator", "stream"})
  private String api;

  @Param({HUNDRED, MILLION})
  private String size;

  private Callable<Iterator<Car>> cars;

  @Benchmark
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @Fork(value = 3, jvmArgs = {"-server", "-Xms1G", "-Xmx1G"})
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  public void feign() throws Exception {
    final Iterator<Car> cars = this.cars.call();
    while (cars.hasNext()) {
      cars.next();
    }
  }

  private static final int SERVER_PORT = 8765;
  private HttpServer<ByteBuf, ByteBuf> server;

  @Setup
  public void setup() {
    server = createServer();
    final String serverUrl = "http://localhost:" + SERVER_PORT;
    OkHttpClient client = new OkHttpClient();
    client.retryOnConnectionFailure();

    switch (api) {
      case "list":
        ListApi listApi = Feign.builder()
            .decoder(new JacksonDecoder())
            .client(new feign.okhttp.OkHttpClient(client))
            .target(ListApi.class, serverUrl);
        cars = () -> listApi.getCars(size).iterator();
        break;
      case "iterator":
        IteratorApi iteratorApi = Feign.builder()
            .decoder(new JacksonIteratorDecoder())
            .closeAfterDecode(false)
            .client(new feign.okhttp.OkHttpClient(client))
            .target(IteratorApi.class, serverUrl);
        cars = () -> iteratorApi.getCars(size);
        break;
      case "stream":
        StreamApi streamApi = Feign.builder()
            .decoder(new StreamDecoder(new JacksonIteratorDecoder()))
            .closeAfterDecode(false)
            .client(new feign.okhttp.OkHttpClient(client))
            .target(StreamApi.class, serverUrl);
        cars = () -> streamApi.getCars(size).iterator();
        break;
      default:
        throw new IllegalStateException("Unknown api: " + api);
    }
  }

  @TearDown
  public void tearDown() throws InterruptedException {
    server.shutdown();
  }

  static class Car {
    public String name;
    public String manufacturer;
  }

  private HttpServer<ByteBuf, ByteBuf> createServer() {
    final String car = "{\"name\":\"c4\",\"manufacturer\":\"Citroën\"}";
    int few = Integer.parseInt(HUNDRED);
    int huge = Integer.parseInt(MILLION);

    String first = "[]";
    StringBuilder builder = new StringBuilder("[");
    builder.append(car);
    for (int i = 1; i < huge; i++) {
      builder.append(",").append(car);
      if (i + 1 == few) {
        first = builder.toString() + "]";
      }
    }
    builder.append("]");

    final String tenCars = first;
    final String allCars = builder.toString();

    HttpServer<ByteBuf, ByteBuf> server = RxNetty.createHttpServer(SERVER_PORT, new RequestHandler<ByteBuf, ByteBuf>() {
      public rx.Observable handle(HttpServerRequest<ByteBuf> request,
                                  HttpServerResponse<ByteBuf> response) {
        switch (request.getPath()) {
          case "/cars/" + HUNDRED:
            response.writeString(tenCars);
            break;
          case "/cars/" + MILLION:
            response.writeString(allCars);
            break;
          default:
            throw new IllegalArgumentException(request.getPath());
        }
        return response.flush();
      }
    });
    server.start();
    return server;
  }
}
