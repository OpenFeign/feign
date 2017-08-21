package feign.optionals;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import feign.Feign;
import feign.RequestLine;
import feign.codec.DecodeException;
import feign.jackson.JacksonIterator;
import feign.stream.Java8StreamDecoder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamDecoderTest {

  interface StreamInterface {
    @RequestLine("GET /")
    Stream<String> get();

    @RequestLine("GET /zone")
    Stream<Car> getCars();

    class Car {
      public String name;
      public String manufacturer;
    }
  }

  private String carsJson = ""//
      + "[\n"//
      + "  {\n"//
      + "    \"name\": \"Megane\",\n"//
      + "    \"manufacturer\": \"Renault\"\n"//
      + "  },\n"//
      + "  {\n"//
      + "    \"name\": \"C4\",\n"//
      + "    \"manufacturer\": \"Citroën\"\n"//
      + "  }\n"//
      + "]\n";

  @Test
  public void simpleStreamTest() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo\nbar"));

    StreamInterface api = Feign.builder()
        .decoder(new Java8StreamDecoder((type, response) -> {
          try {
            return new BufferedReader(new InputStreamReader(response.body().asInputStream())).lines().iterator();
          } catch (IOException e) {
            throw new DecodeException(e.getMessage(), e);
          }
        })).target(StreamInterface.class, server.url("/").toString());

    try (Stream<String> stream = api.get()) {
      assertThat(stream.collect(Collectors.toList())).isEqualTo(Arrays.asList("foo", "bar"));
    }
  }

  @Test
  public void simpleJsonStreamTest() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody(carsJson));

    ObjectMapper mapper = new ObjectMapper();

    StreamInterface api = Feign.builder()
        .decoder(new Java8StreamDecoder((type, response) -> JacksonIterator.<StreamInterface.Car>builder().of(type).mapper(mapper).response(response).build()))
              .target(StreamInterface.class, server.url("/").toString());

    try (Stream<StreamInterface.Car> stream = api.getCars()) {
      assertThat(stream.collect(Collectors.toList())).hasSize(2);
    }
  }
}
