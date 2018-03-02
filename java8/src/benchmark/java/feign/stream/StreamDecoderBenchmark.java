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
  private static final int FEW_CARS = 10;
  private static final int LOT_OF_CARS = (int) Math.pow(FEW_CARS, 6);

  private Callable<Iterator<Car>> hugeCars;
  private Callable<Iterator<Car>> fewCars;

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

  @Benchmark
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @Fork(5)
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void feignFew() throws Exception {
    fetch(this.fewCars.call());
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 5, time = 1)
  @Fork(3)
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void feignHuge() throws Exception {
    fetch(this.hugeCars.call());
  }

  private void fetch(Iterator<Car> cars) {
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
        fewCars = () -> listApi.getFewCars().iterator();
        hugeCars = () -> listApi.getHugeCars().iterator();
        break;
      case "iterator":
        IteratorApi iteratorApi = Feign.builder()
            .decoder(new JacksonIteratorDecoder())
            .closeAfterDecode(false)
            .target(IteratorApi.class, serverUrl);
        fewCars = iteratorApi::getFewCars;
        hugeCars = iteratorApi::getHugeCars;
        break;
      case "stream":
        StreamApi streamApi = Feign.builder()
            .decoder(new StreamDecoder(new JacksonIteratorDecoder()))
            .closeAfterDecode(false)
            .target(StreamApi.class, serverUrl);
        fewCars = () -> streamApi.getFewCars().iterator();
        hugeCars = () -> streamApi.getHugeCars().iterator();
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

    String small = "[]";
    StringBuilder builder = new StringBuilder("[");
    builder.append(car);
    for (int i = 1; i < LOT_OF_CARS; i++) {
      builder.append(",").append(car);
      if (i + 1 == FEW_CARS) {
        small = builder.toString() + "]";
      }
    }
    builder.append("]");

    Logger globalLogger = Logger.getLogger("");
    globalLogger.setLevel(Level.WARNING); // Disable logging of mock web server
    MockWebServer server = new MockWebServer();

    final String fewAnswer = small;
    final String hugeAnswer = builder.toString();

    server.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(final RecordedRequest request) throws InterruptedException {
        switch (request.getPath()) {
          case "/few-cars":
            return new MockResponse().setBody(fewAnswer);
          case "/huge-cars":
            return new MockResponse().setBody(hugeAnswer);
          default:
            throw new IllegalArgumentException(request.getPath());
        }
      }
    });
    return server;
  }
}
