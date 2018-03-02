package feign.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import feign.Response;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonIteratorDecoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static feign.Util.UTF_8;

@State(Scope.Thread)
public class DecoderIteratorsBenchmark {

  @Param({"list", "iterator", "stream"})
  private String api;

  private Response response;

  private Decoder decoder;
  private Type type;

  @Benchmark
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @Fork(3)
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  public void decode() throws Exception {
    fetch(decoder.decode(response, type));
  }

  @SuppressWarnings("unchecked")
  private void fetch(Object o) {
    final Iterator<Car> cars;

    if (o instanceof Collection) {
      cars = ((Collection<Car>) o).iterator();
    } else if (o instanceof Stream) {
      cars = ((Stream<Car>) o).iterator();
    } else {
      cars = (Iterator<Car>) o;
    }

    while (cars.hasNext()) {
      cars.next();
    }
  }

  @Setup(Level.Invocation)
  public void buildResponse() {
    response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(carsJson(10), UTF_8)
        .build();
  }

  @Setup(Level.Trial)
  public void buildDecoder() {
    switch (api) {
      case "list":
        decoder = new JacksonDecoder();
        type = new TypeReference<List<Car>>() {
        }.getType();
        break;
      case "iterator":
        decoder = new JacksonIteratorDecoder();
        type = new TypeReference<Iterator<Car>>() {
        }.getType();
        break;
      case "stream":
        decoder = new StreamDecoder(new JacksonIteratorDecoder());
        type = new TypeReference<Stream<Car>>() {
        }.getType();
        break;
      default:
        throw new IllegalStateException("Unknown api: " + api);
    }
  }

  private String carsJson(int count) {
    final String car = "{\"name\":\"c4\",\"manufacturer\":\"Citroën\"}";
    StringBuilder builder = new StringBuilder("[");
    builder.append(car);
    for (int i = 1; i < count; i++) {
      builder.append(",").append(car);
    }
    return builder.append("]").toString();
  }

  static class Car {
    public String name;
    public String manufacturer;
  }

  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(DecoderIteratorsBenchmark.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }
}
