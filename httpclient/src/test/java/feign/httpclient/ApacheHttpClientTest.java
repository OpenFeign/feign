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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.Charset;
import java.util.HashMap;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.RequestBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import feign.Feign;
import feign.Feign.Builder;
import feign.Request;
import feign.client.AbstractClientTest;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class ApacheHttpClientTest extends AbstractClientTest {

  ApacheHttpClient apacheHttpClient;

  @Mock
  RequestBuilder requestBuilder;

  @Mock
  HttpClient httpClient;

  Request request;
  Request.Options options;


  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttpClient());
  }


  @Before
  public void before() {

    MockitoAnnotations.initMocks(this);
    when(requestBuilder.getCharset()).thenReturn(Charset.forName("UTF8"));

    this.request =
        Request.create("GET", "http://example.com", new HashMap<>(), null, Charset.forName("UTF8"));
    this.options = new Request.Options();
  }


  @Test
  public void shouldUseEmbeddedRequestConfigDefaultClient() throws Exception {
    this.apacheHttpClient = new ApacheHttpClient() {
      @Override
      protected RequestBuilder createRequestBuilder(String method) {
        return ApacheHttpClientTest.this.requestBuilder;
      }
    };

    this.apacheHttpClient.toHttpUriRequest(this.request, this.options);
    verify(this.requestBuilder, times(1)).setConfig(any(RequestConfig.class));
  }


  @Test
  public void shouldUseEmbeddedRequestConfigUserClient() throws Exception {

    this.apacheHttpClient = new ApacheHttpClient(this.httpClient) {
      @Override
      protected RequestBuilder createRequestBuilder(String method) {
        return ApacheHttpClientTest.this.requestBuilder;
      }
    };

    this.apacheHttpClient.toHttpUriRequest(this.request, this.options);
    verify(this.requestBuilder, times(1)).setConfig(any(RequestConfig.class));
  }


  @Test
  public void shouldUseDefaultRequestConfigDefaultClient() throws Exception {

    this.apacheHttpClient = new ApacheHttpClient(this.httpClient, true) {
      @Override
      protected RequestBuilder createRequestBuilder(String method) {
        return ApacheHttpClientTest.this.requestBuilder;
      }
    };

    this.apacheHttpClient.toHttpUriRequest(this.request, this.options);
    verify(this.requestBuilder, never()).setConfig(any(RequestConfig.class));
  }
}
