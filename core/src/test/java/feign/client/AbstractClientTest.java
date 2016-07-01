package feign.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import feign.Client;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Headers;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.assertj.MockWebServerAssertions;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static java.util.Arrays.asList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import static feign.Util.UTF_8;

/**
 * {@link AbstractClientTest} can be extended to run a set of tests against any {@link Client} implementation.
 */
public abstract class AbstractClientTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Rule
    public final MockWebServer server = new MockWebServer();

    /**
     * Create a Feign {@link Builder} with a client configured
     */
    public abstract Builder newBuilder();

    /**
     * Some client implementation tests should override this
     * test if the PATCH operation is unsupported.
     */
    @Test
    public void testPatch() throws Exception {
        server.enqueue(new MockResponse().setBody("foo"));
        server.enqueue(new MockResponse());

        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        assertEquals("foo", api.patch(""));

        MockWebServerAssertions.assertThat(server.takeRequest())
                .hasHeaders("Accept: text/plain", "Content-Length: 0") // Note: OkHttp adds content length.
                .hasNoHeaderNamed("Content-Type")
                .hasMethod("PATCH");
    }

    @Test
    public void parsesRequestAndResponse() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("foo").addHeader("Foo: Bar"));

        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        Response response = api.post("foo");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.reason()).isEqualTo("OK");
        assertThat(response.headers())
                .containsEntry("Content-Length", asList("3"))
                .containsEntry("Foo", asList("Bar"));
        assertThat(response.body().asInputStream())
                .hasContentEqualTo(new ByteArrayInputStream("foo".getBytes(UTF_8)));

        MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("POST")
                .hasPath("/?foo=bar&foo=baz&qux=")
                .hasHeaders("Foo: Bar", "Foo: Baz", "Qux: ", "Accept: */*", "Content-Length: 3")
                .hasBody("foo");
    }

    @Test
    public void reasonPhraseIsOptional() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setStatus("HTTP/1.1 " + 200));

        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        Response response = api.post("foo");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.reason()).isNullOrEmpty();
    }

    @Test
    public void parsesErrorResponse() throws IOException, InterruptedException {
        thrown.expect(FeignException.class);
        thrown.expectMessage("status 500 reading TestInterface#get(); content:\n" + "ARGHH");

        server.enqueue(new MockResponse().setResponseCode(500).setBody("ARGHH"));

        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        api.get();
    }

    @Test
    public void safeRebuffering() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("foo"));

        TestInterface api = newBuilder()
                .logger(new Logger(){
                    @Override
                    protected void log(String configKey, String format, Object... args) {
                    }
                })
                .logLevel(Logger.Level.FULL) // rebuffers the body
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        api.post("foo");
    }

    /** This shows that is a no-op or otherwise doesn't cause an NPE when there's no content. */
    @Test
    public void safeRebuffering_noContent() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(204));

        TestInterface api = newBuilder()
                .logger(new Logger(){
                    @Override
                    protected void log(String configKey, String format, Object... args) {
                    }
                })
                .logLevel(Logger.Level.FULL) // rebuffers the body
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        api.post("foo");
    }

    @Test
    public void noResponseBodyForPost() {
        server.enqueue(new MockResponse());

        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        api.noPostBody();
    }

    @Test
    public void noResponseBodyForPut() {
        server.enqueue(new MockResponse());

        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        api.noPutBody();
    }

    @Test
    public void parsesResponseMissingLength() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setChunkedBody("foo", 1));

        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        Response response = api.post("testing");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.reason()).isEqualTo("OK");
        assertThat(response.body().length()).isNull();
        assertThat(response.body().asInputStream())
                .hasContentEqualTo(new ByteArrayInputStream("foo".getBytes(UTF_8)));
    }

    @Test
    public void postWithSpacesInPath() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("foo"));

        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        Response response = api.post("current documents", "foo");

        MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("POST")
                .hasPath("/path/current%20documents/resource")
                .hasBody("foo");
    }

    @Test
    public void testVeryLongResponseNullLength() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("AAAAAAAA")
                .addHeader("Content-Length", Long.MAX_VALUE));
        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        Response response = api.post("foo");
        // Response length greater than Integer.MAX_VALUE should be null
        assertThat(response.body().length()).isNull();
    }

    @Test
    public void testResponseLength() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("test"));
        TestInterface api = newBuilder()
                .target(TestInterface.class, "http://localhost:" + server.getPort());

        Integer expected = 4;
        Response response = api.post("");
        Integer actual = response.body().length();
        assertEquals(expected, actual);
    }

    public interface TestInterface {

        @RequestLine("POST /?foo=bar&foo=baz&qux=")
        @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: text/plain"})
        Response post(String body);

        @RequestLine("POST /path/{to}/resource")
        @Headers("Accept: text/plain")
        Response post(@Param("to") String to, String body);

        @RequestLine("GET /")
        @Headers("Accept: text/plain")
        String get();

        @RequestLine("PATCH /")
        @Headers("Accept: text/plain")
        String patch(String body);

        @RequestLine("POST")
        String noPostBody();

        @RequestLine("PUT")
        String noPutBody();
    }

}
