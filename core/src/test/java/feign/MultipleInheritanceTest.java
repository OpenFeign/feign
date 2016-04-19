package feign;

import com.google.gson.Gson;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.Map;

import static feign.assertj.MockWebServerAssertions.assertThat;

public class MultipleInheritanceTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private final Multiple api = new Feign.Builder()
        .decoder(new Decoder.Default())
        .encoder(new Encoder() {
            @Override
            public void encode(Object object, Type bodyType, RequestTemplate template) {
                if (object instanceof Map) {
                    template.body(new Gson().toJson(object));
                } else {
                    template.body(object.toString());
                }
            }
        }).target(Multiple.class, "http://localhost:" + server.getPort());

    @Test
    public void requestFromFirstInterface() throws Exception {
        server.enqueue(new MockResponse().setBody("foo"));

        api.first();

        assertThat(server.takeRequest())
            .hasPath("/first");
    }
    @Test
    public void requestFromSecondInterface() throws Exception {
        server.enqueue(new MockResponse().setBody("foo"));

        api.second();

        assertThat(server.takeRequest())
            .hasPath("/second");
    }

    private interface Multiple extends First, Second {}

    interface First {
        @RequestLine("GET /first")
        Response first();
    }

    interface Second {
        @RequestLine("GET /second")
        Response second();
    }
}
