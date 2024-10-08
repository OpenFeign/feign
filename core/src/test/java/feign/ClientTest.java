/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ClientTest {

  @Test
  void testClientExecute() throws IOException {
    Client client = mock(Client.class);
    RequestTemplate requestTemplate = mock(RequestTemplate.class);
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "http://example.com",
            Collections.emptyMap(),
            null,
            requestTemplate);
    when(client.execute(ArgumentMatchers.any(Request.class), ArgumentMatchers.any()))
        .thenReturn(
            Response.builder()
                .status(200)
                .request(request)
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

    Request request =
        Request.create(
            Request.HttpMethod.GET, "http://example.com", headers, body, requestTemplate);
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

    Request request =
        Request.create(
            Request.HttpMethod.GET, "http://example.com", headers, body, requestTemplate);
    Client.Default defaultClient = new Client.Default(null, null);
    HttpURLConnection urlConnection = defaultClient.convertAndSend(request, options);
    Map<String, List<String>> requestProperties = urlConnection.getRequestProperties();
    String requestProperty = urlConnection.getRequestProperty(Util.CONTENT_LENGTH);
    /*
     * By default, "Content-Length" will not be added because this key is in the restrictedHeaderSet
     * of HttpURLConnection. Unless set system property "sun.net.http.allowRestrictedHeaders" to
     * "true"
     */
    assertNull(requestProperties.get(Util.CONTENT_LENGTH));
  }
}
