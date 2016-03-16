package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import feign.RequestLine;
import feign.gson.GsonDecoder;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;

public final class HystrixCustomTest {
    @Rule
    public final MockWebServer server = new MockWebServer();

    @Test
    public void testKey() {
        this.server.enqueue(new MockResponse().setBody("OK"));

        final HystrixCommand<String> status = this.target().status();
        MatcherAssert.assertThat(
            status.getCommandKey().name(),
            CoreMatchers.equalTo("MyKey")
        );
    }

    private Testable target() {
        return HystrixFeign.builder()
            .decoder(new GsonDecoder())
            .target(Testable.class, "http://localhost:" + server.getPort());
    }

    @HystrixKey("MyKey")
    interface Testable {
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }
}
