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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@State(Scope.Thread)
public class StreamDecoderBenchmark {
  private static final int FEW_CARS = 1_000;
  private static final int LOT_OF_CARS = FEW_CARS * FEW_CARS;

  private Callable<Iterator<Car>> cars;

  interface ListApi {
    @RequestLine("GET /cars")
    List<Car> getLotOfCars();

    @RequestLine("GET /few-cars")
    List<Car> getFewCars();
  }

  interface IteratorApi {
    @RequestLine("GET /cars")
    Iterator<Car> getLotOfCars();

    @RequestLine("GET /few-cars")
    Iterator<Car> getFewCars();
  }

  interface StreamApi {
    @RequestLine("GET /cars")
    Stream<Car> getLotOfCars();

    @RequestLine("GET /few-cars")
    Stream<Car> getFewCars();
  }

  @Param({"list", "iterator", "stream"})
  private String api;

  @Param({"small", "huge"})
  private String size;

  @Benchmark
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 5, time = 1)
  @Fork(3)
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.SECONDS)
  public void feign() throws Exception {
    final Iterator<Car> cars = this.cars.call();
    while (cars.hasNext()) {
      cars.next();
    }
  }

  @Setup
  public void setup() {
    final String serverUrl = System.getProperty("server.url");
    switch (api) {
      case "list":
        ListApi listApi = Feign.builder()
            .decoder(new JacksonDecoder())
            .target(ListApi.class, serverUrl);
        switch (size) {
          case "small":
            cars = () -> listApi.getFewCars().iterator();
            break;
          case "huge":
            cars = () -> listApi.getLotOfCars().iterator();
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
          case "small":
            cars = iteratorApi::getFewCars;
            break;
          case "huge":
            cars = iteratorApi::getLotOfCars;
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
          case "small":
            cars = () -> streamApi.getFewCars().iterator();
            break;
          case "huge":
            cars = () -> streamApi.getLotOfCars().iterator();
            break;
          default:
            throw new IllegalStateException("Unknown size: " + size);
        }
        break;
      default:
        throw new IllegalStateException("Unknown api: " + api);
    }
  }

  static class Car {
    public String name;
    public String manufacturer;
  }

  public static void main(String[] args) throws Exception {
    MockWebServer mockServer = createMockServer();

    Options opt = new OptionsBuilder()
        .include(StreamDecoderBenchmark.class.getSimpleName())
        .addProfiler(GCProfiler.class)
        .jvmArgs("-server", "-Xms1G", "-Xmx1G", "-Dserver.url=" + mockServer.url("/").toString())
        .build();

    new Runner(opt).run();

    mockServer.shutdown();
  }

  private static MockWebServer createMockServer() {
    String car = "{\"name\":\"c4\",\"manufacturer\":\"Citroën\"}";

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

    Logger globalLogger = Logger.getLogger("");
    globalLogger.setLevel(Level.WARNING); // Disable logging of mock web server
    MockWebServer server = new MockWebServer();

    final String smallAnswer = fewAnswer;
    final String hugeAnswer = builder.toString();

    server.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(final RecordedRequest request) throws InterruptedException {
        switch (request.getPath()) {
          case "/cars":
            return new MockResponse().setBody(hugeAnswer);
          case "/few-cars":
            return new MockResponse().setBody(smallAnswer);
          default:
            throw new IllegalArgumentException(request.getPath());
        }
      }
    });
    return server;
  }
}
