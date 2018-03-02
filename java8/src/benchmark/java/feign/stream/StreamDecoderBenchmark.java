package feign.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonIterator;
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
    private static final int TOTAL_CARS = 1_000_000;

    private Callable<Iterator<Car>> cars;

    interface ListInterface {
        @RequestLine("GET /cars")
        List<Car> getCars();
    }

    interface StreamInterface {
        @RequestLine("GET /cars")
        Stream<Car> getCars();
    }

    @Param({"list", "stream"})
    private String type;

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
        String serverUrl = System.getProperty("server.url");
        switch (type) {
            case "list":
                ListInterface listInterface = Feign.builder()
                        .decoder(new JacksonDecoder())
                        .target(ListInterface.class, serverUrl);
                cars = () -> listInterface.getCars().iterator();
                break;
            case "stream":
                StreamInterface streamInterface = Feign.builder()
                        .decoder(new StreamDecoder((type, response) -> JacksonIterator.<StreamDecoderTest.StreamInterface.Car>builder().of(type).mapper(new ObjectMapper()).response(response).build()))
                        .closeAfterDecode(false)
                        .target(StreamInterface.class, serverUrl);
                cars = () -> streamInterface.getCars().iterator();
                break;
            default:
                throw new IllegalStateException("Unknown type: " + type);
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

        StringBuilder builder = new StringBuilder("[");
        builder.append(car);
        for (int i = 1; i < TOTAL_CARS; i++) {
            builder.append(",").append(car);
        }
        builder.append("]");

        Logger globalLogger = Logger.getLogger("");
        globalLogger.setLevel(Level.WARNING); // Disable logging of mock web server
        MockWebServer server = new MockWebServer();

        final String hugeAnswer = builder.toString();

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(final RecordedRequest request) throws InterruptedException {
                return new MockResponse().setBody(hugeAnswer);
            }
        });
        return server;
    }
}
