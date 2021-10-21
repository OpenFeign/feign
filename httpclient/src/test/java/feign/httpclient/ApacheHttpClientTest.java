/**
 * Copyright 2012-2020 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.httpclient;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import feign.Feign;
import feign.Feign.Builder;
import feign.Response;
import feign.Retryer;
import feign.client.AbstractClientTest;
import feign.codec.ErrorDecoder;
import feign.jaxrs.JAXRSContract;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class ApacheHttpClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttpClient());
  }

  @Test
  public void queryParamsAreRespectedWhenBodyIsEmpty() throws InterruptedException {
    final HttpClient httpClient = HttpClientBuilder.create().build();
    final JaxRsTestInterface testInterface = Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttpClient(httpClient))
        .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());

    server.enqueue(new MockResponse().setBody("foo"));
    server.enqueue(new MockResponse().setBody("foo"));

    assertEquals("foo", testInterface.withBody("foo", "bar"));
    final RecordedRequest request1 = server.takeRequest();
    assertEquals("/withBody?foo=foo", request1.getPath());
    assertEquals("bar", request1.getBody().readString(StandardCharsets.UTF_8));

    assertEquals("foo", testInterface.withoutBody("foo"));
    final RecordedRequest request2 = server.takeRequest();
    assertEquals("/withoutBody?foo=foo", request2.getPath());
    assertEquals("", request2.getBody().readString(StandardCharsets.UTF_8));
  }

  @Test
  public void errorDecoderIsInvokedWhenCheckedExceptionIsThrown() throws InterruptedException {
    final HttpClient httpClient = HttpClientBuilder.create().build();
    final ErrorDecoder decoder = mock(ErrorDecoder.class);
    final JaxRsTestInterface testInterface = Feign.builder()
            .contract(new JAXRSContract())
            .errorDecoder(decoder)
            .retryer(Retryer.NEVER_RETRY)
            .client(new ApacheHttpClient(httpClient))
            .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());
    when(decoder.decode(anyString(), any(Response.class)))
            .thenReturn(new RuntimeException("Exception thrown by decoder"));
    // Invalid redirection caused because Location header is missing.
    server.enqueue(new MockResponse().setResponseCode(303));

    assertThrows(RuntimeException.class, testInterface::redirect);
    ArgumentCaptor<String> methodKey = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
    verify(decoder).decode(methodKey.capture(), response.capture());
    assertEquals("JaxRsTestInterface#redirect()", methodKey.getValue());
    assertEquals(303, response.getValue().status());
    assertFalse(response.getValue().headers().containsKey("location"));
  }

  @Path("/")
  public interface JaxRsTestInterface {
    @PUT
    @Path("/withBody")
    public String withBody(@QueryParam("foo") String foo, String bar);

    @PUT
    @Path("/withoutBody")
    public String withoutBody(@QueryParam("foo") String foo);

    @GET
    @Path("/redirect")
    public String redirect();
  }
}
