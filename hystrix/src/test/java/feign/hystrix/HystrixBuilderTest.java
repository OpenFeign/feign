package feign.hystrix;

import static feign.assertj.MockWebServerAssertions.assertThat;

import com.netflix.hystrix.HystrixCommand;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import feign.Headers;
import feign.RequestLine;
import feign.gson.GsonDecoder;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class HystrixBuilderTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Rule public final MockWebServer server = new MockWebServer();

  @Test
  public void hystrixCommand() {
    server.enqueue(new MockResponse().setBody("\"foo\""));

    TestInterface api = target();

    HystrixCommand<String> command = api.command();

    assertThat(command).isNotNull();
    assertThat(command.execute()).isEqualTo("foo");
  }

  @Test
  public void hystrixCommandInt() {
    server.enqueue(new MockResponse().setBody("1"));

    TestInterface api = target();

    HystrixCommand<Integer> command = api.intCommand();

    assertThat(command).isNotNull();
    assertThat(command.execute()).isEqualTo(new Integer(1));
  }

  @Test
  public void hystrixCommandList() {
    server.enqueue(new MockResponse().setBody("[\"foo\",\"bar\"]"));

    TestInterface api = target();

    HystrixCommand<List<String>> command = api.listCommand();

    assertThat(command).isNotNull();
    assertThat(command.execute()).hasSize(2).contains("foo", "bar");
  }

  @Test
  public void plainString() {
    server.enqueue(new MockResponse().setBody("\"foo\""));

    TestInterface api = target();

    String string = api.get();

    assertThat(string).isEqualTo("foo");
  }

  @Test
  public void plainList() {
    server.enqueue(new MockResponse().setBody("[\"foo\",\"bar\"]"));

    TestInterface api = target();

    List<String> list = api.getList();

    assertThat(list).isNotNull().hasSize(2).contains("foo", "bar");
  }

  private TestInterface target() {
    return HystrixFeign.builder()
        .decoder(new GsonDecoder())
        .target(TestInterface.class, "http://localhost:" + server.getPort());
  }

  interface TestInterface {

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    HystrixCommand<List<String>> listCommand();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    HystrixCommand<String> command();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    HystrixCommand<Integer> intCommand();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    String get();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    List<String> getList();
  }
}
