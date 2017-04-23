/*
 * Copyright 2015 Netflix, Inc.
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import feign.Feign;
import feign.Feign.Builder;
import feign.Request;
import feign.client.AbstractClientTest;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class ApacheHttpClientTest extends AbstractClientTest {

  ApacheHttpClient apacheHttpClient;

  CloseableHttpClient httpClient;
  Request.Options options;
  Request request;


  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttpClient());
  }


  @Before
  public void before() {

    this.httpClient = HttpClients.createDefault();
    this.request =
        Request.create("GET", "http://example.com", new HashMap<>(), null, Charset.forName("UTF8"));
    this.options = new Request.Options();
  }

  @After
  public void after() throws IOException {
    this.httpClient.close();
  }


  @Test
  public void shouldUseEmbeddedRequestConfigDefaultClient() throws Exception {

    ApacheHttpClient apacheHttpClient = new ApacheHttpClient();
    HttpRequestBase httpRequest =
        (HttpRequestBase) apacheHttpClient.toHttpUriRequest(this.request, this.options);

    assertNotNull(httpRequest.getConfig());
  }


  @Test
  public void shouldUseEmbeddedRequestConfigUserClient() throws Exception {

    ApacheHttpClient apacheHttpClient = new ApacheHttpClient(this.httpClient);
    HttpRequestBase httpRequest =
        (HttpRequestBase) apacheHttpClient.toHttpUriRequest(this.request, this.options);

    assertNotNull(httpRequest.getConfig());
  }


  @Test
  public void shouldUseDefaultRequestConfigUserClient() throws Exception {

    ApacheHttpClient apacheHttpClient = new ApacheHttpClient(this.httpClient, true);
    HttpRequestBase httpRequest =
        (HttpRequestBase) apacheHttpClient.toHttpUriRequest(this.request, this.options);

    assertNull(httpRequest.getConfig());
  }
}
