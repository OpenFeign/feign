package feign.stream;

import feign.Feign;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonIteratorDecoder;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@State(Scope.Thread)
public class StreamDecoderBenchmark {
  private static final int FEW_CARS = 10;
  private static final int LOT_OF_CARS = (int) Math.pow(FEW_CARS, 6);

  interface ListApi {
    @RequestLine("GET /huge-cars")
    List<Car> getHugeCars();

    @RequestLine("GET /few-cars")
    List<Car> getFewCars();
  }

  interface IteratorApi {
    @RequestLine("GET /huge-cars")
    Iterator<Car> getHugeCars();

    @RequestLine("GET /few-cars")
    Iterator<Car> getFewCars();
  }

  interface StreamApi {
    @RequestLine("GET /huge-cars")
    Stream<Car> getHugeCars();

    @RequestLine("GET /few-cars")
    Stream<Car> getFewCars();
  }

  @Param({"list", "iterator", "stream"})
  private String api;

  @Param({"few", "huge"})
  private String size;

  private Callable<Iterator<Car>> cars;

  @Benchmark
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @Fork(3)
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void feign() throws Exception {
    final Iterator<Car> cars = this.cars.call();
    while (cars.hasNext()) {
      cars.next();
    }
  }

  private MockWebServer mockServer;

  @Setup
  public void setup() {
    mockServer = createMockServer();
    final String serverUrl = mockServer.url("/").toString();
    switch (api) {
      case "list":
        ListApi listApi = Feign.builder()
            .decoder(new JacksonDecoder())
            .target(ListApi.class, serverUrl);
        switch (size) {
          case "few":
            cars = () -> listApi.getFewCars().iterator();
            break;
          case "huge":
            cars = () -> listApi.getHugeCars().iterator();
            break;
          default:
            throw new IllegalStateException("Unknown size: " + size);
        }
        break;
      case "iterator":
        IteratorApi iteratorApi = Feign.builder()
            .decoder(new JacksonIteratorDecoder())
            .closeAfterDecode(false)
            .target(IteratorApi.class, serverUrl);
        switch (size) {
          case "few":
            cars = iteratorApi::getFewCars;
            break;
          case "huge":
            cars = iteratorApi::getHugeCars;
            break;
          default:
            throw new IllegalStateException("Unknown size: " + size);
        }
        break;
      case "stream":
        StreamApi streamApi = Feign.builder()
            .decoder(new StreamDecoder(new JacksonIteratorDecoder()))
            .closeAfterDecode(false)
            .target(StreamApi.class, serverUrl);
        switch (size) {
          case "few":
            cars = () -> streamApi.getFewCars().iterator();
            break;
          case "huge":
            cars = () -> streamApi.getHugeCars().iterator();
            break;
          default:
            throw new IllegalStateException("Unknown size: " + size);
        }
        break;
      default:
        throw new IllegalStateException("Unknown api: " + api);
    }
  }

  @TearDown
  public void tearDown() throws IOException {
    mockServer.shutdown();
  }

  static class Car {
    public String name;
    public String manufacturer;
  }

  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(StreamDecoderBenchmark.class.getSimpleName())
        .addProfiler(GCProfiler.class)
        .jvmArgs("-server", "-Xms1G", "-Xmx1G")
        .build();

    new Runner(opt).run();
  }

  private MockWebServer createMockServer() {
    final String car = "{\"name\":\"c4\",\"manufacturer\":\"Citroën\"}";

    String fewAnswer = "[]";
    StringBuilder builder = new StringBuilder("[");
    builder.append(car);
    for (int i = 1; i < LOT_OF_CARS; i++) {
      builder.append(",").append(car);
      if (i + 1 == FEW_CARS) {
        fewAnswer = builder.toString() + "]";
      }
    }
    builder.append("]");

    Logger.getLogger("").setLevel(Level.WARNING); // Disable logging of mock web server
    MockWebServer server = new MockWebServer();

    final MockResponse fewCarsResponse = new MockResponse().setBody(fewAnswer);
    final MockResponse hugeCarsResponse = new MockResponse().setBody(builder.toString());

    server.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(final RecordedRequest request) throws InterruptedException {
        switch (request.getPath()) {
          case "/huge-cars":
            return hugeCarsResponse;
          case "/few-cars":
            return fewCarsResponse;
          default:
            throw new IllegalArgumentException(request.getPath());
        }
      }
    });
    return server;
  }
}
