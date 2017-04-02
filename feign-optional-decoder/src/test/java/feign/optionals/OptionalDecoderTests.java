package feign.optionals;

import feign.Feign;
import feign.RequestLine;
import feign.codec.Decoder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionalDecoderTests {

    interface OptionalInterface {
        @RequestLine("GET /")
        Optional<String> get();
    }

    @Test
    public void simpleOptionalTest() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setBody("foo"));

        OptionalInterface api = Feign.builder()
                .decode404()
                .decoder(new OptionalDecoder(new Decoder.Default()))
                .target(OptionalInterface.class, server.url("/").toString());

        assertThat(api.get().isPresent()).isFalse();
        assertThat(api.get().get()).isEqualTo("foo");
    }
}
