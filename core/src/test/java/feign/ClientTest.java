package feign;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientTest {

  @Test
  void testClientExecute() throws IOException {
    Client client = mock(Client.class);
    RequestTemplate requestTemplate = mock(RequestTemplate.class);
    Request request =
        Request.create(Request.HttpMethod.GET, "http://example.com", Collections.emptyMap(),
            null, requestTemplate);
    when(client.execute(ArgumentMatchers.any(Request.class),
        ArgumentMatchers.any())).thenReturn(Response.builder()
            .status(200).request(request)
            .body("Hello, World!", Charset.defaultCharset())
            .build());
    Response response = client.execute(request, null);
    String result = Util.toString(response.body().asReader(Charset.defaultCharset()));
    assertEquals("Hello, World!", result);
  }

  @Test
  void testConvertAndSendWithAcceptEncoding() throws IOException {
    Map<String, Collection<String>> headers = new HashMap<>();
    List<String> acceptEncoding = new ArrayList<>();
    acceptEncoding.add("gzip");
    acceptEncoding.add("deflate");
    headers.put(Util.ACCEPT_ENCODING, acceptEncoding);

    RequestTemplate requestTemplate = mock(RequestTemplate.class);
    Request.Body body = mock(Request.Body.class);
    Request.Options options = mock(Request.Options.class);
    Client client = mock(Client.class);

    Request request = Request.create(Request.HttpMethod.GET, "http://example.com", headers, body,
        requestTemplate);
    Client.Default defaultClient = new Client.Default(null, null);
    HttpURLConnection urlConnection = defaultClient.convertAndSend(request, options);
    Map<String, List<String>> requestProperties = urlConnection.getRequestProperties();
    // Test Avoid add "Accept-encoding" twice or more when "compression" option is enabled
    assertEquals(1, requestProperties.get(Util.ACCEPT_ENCODING).size());
  }

  @Test
  void testConvertAndSendWithContentLength() throws IOException {
    Map<String, Collection<String>> headers = new HashMap<>();
    List<String> acceptEncoding = new ArrayList<>();
    headers.put(Util.CONTENT_LENGTH, Collections.singletonList("100"));

    RequestTemplate requestTemplate = mock(RequestTemplate.class);
    Request.Body body = mock(Request.Body.class);
    Request.Options options = mock(Request.Options.class);
    Client client = mock(Client.class);

    Request request = Request.create(Request.HttpMethod.GET, "http://example.com", headers, body,
        requestTemplate);
    Client.Default defaultClient = new Client.Default(null, null);
    HttpURLConnection urlConnection = defaultClient.convertAndSend(request, options);
    Map<String, List<String>> requestProperties = urlConnection.getRequestProperties();
    String requestProperty = urlConnection.getRequestProperty(Util.CONTENT_LENGTH);
    // "Content-Length" not be added to HttpURLConnection at convertAndSend Method
    // See @sun.net.www.protocol.http.HttpURLConnection.addRequestProperty
    assertNull(requestProperties.get(Util.CONTENT_LENGTH));
  }

}
