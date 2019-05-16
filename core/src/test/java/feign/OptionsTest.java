package feign;

import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author pengfei.zhao
 */
public class OptionsTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void socketTimeoutTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api = Feign.builder()
        .options(new Request.Options(1000, 1000))
        .target(OptionsInterface.class, server.url("/").toString());

    thrown.expect(FeignException.class);
    thrown.expectCause(CoreMatchers.isA(SocketTimeoutException.class));

    api.get();
  }

  @Test
  public void normalResponseTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api = Feign.builder()
        .options(new Request.Options(1000, 1000))
        .target(OptionsInterface.class, server.url("/").toString());

    assertThat(api.get(new Request.Options(1000, 4 * 1000))).isEqualTo("foo");
  }


  interface OptionsInterface {
    @RequestLine("GET /")
    String get(Request.Options options);

    @RequestLine("GET /")
    String get();
  }
}
