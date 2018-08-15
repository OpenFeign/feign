/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.jaxrs2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.*;
import feign.Client;
import feign.Request.Options;

/**
 * This module directs Feign's http requests to javax.ws.rs.client.Client . Ex:
 *
 * <pre>
 * GitHub github =
 *     Feign.builder().client(new JaxRSClient()).target(GitHub.class, "https://api.github.com");
 * </pre>
 */
public class JAXRSClient implements Client {

  private final ClientBuilder clientBuilder;

  public JAXRSClient() {
    this(ClientBuilder.newBuilder());
  }

  public JAXRSClient(ClientBuilder clientBuilder) {
    this.clientBuilder = clientBuilder;
  }

  @Override
  public feign.Response execute(feign.Request request, Options options) throws IOException {
    final Response response = clientBuilder
        .connectTimeout(options.connectTimeoutMillis(), TimeUnit.MILLISECONDS)
        .readTimeout(options.readTimeoutMillis(), TimeUnit.MILLISECONDS)
        .build()
        .target(request.url())
        .request()
        .headers(toMultivaluedMap(request.headers()))
        .method(request.httpMethod().name(), createRequestEntity(request));

    return feign.Response.builder()
        .request(request)
        .body(response.readEntity(InputStream.class),
            integerHeader(response, HttpHeaders.CONTENT_LENGTH))
        .headers(toMap(response.getStringHeaders()))
        .status(response.getStatus())
        .reason(response.getStatusInfo().getReasonPhrase())
        .build();
  }

  private Entity<byte[]> createRequestEntity(feign.Request request) {
    if (request.body() == null) {
      return null;
    }

    return Entity.entity(
        request.body(),
        new Variant(mediaType(request.headers()), locale(request.headers()),
            encoding(request.charset())));
  }

  private Integer integerHeader(Response response, String header) {
    final MultivaluedMap<String, String> headers = response.getStringHeaders();
    if (!headers.containsKey(header)) {
      return null;
    }

    try {
      return new Integer(headers.getFirst(header));
    } catch (final NumberFormatException e) {
      // not a number or too big to fit Integer
      return null;
    }
  }

  private String encoding(Charset charset) {
    if (charset == null)
      return null;

    return charset.name();
  }

  private String locale(Map<String, Collection<String>> headers) {
    if (!headers.containsKey(HttpHeaders.CONTENT_LANGUAGE))
      return null;

    return headers.get(HttpHeaders.CONTENT_LANGUAGE).iterator().next();
  }

  private MediaType mediaType(Map<String, Collection<String>> headers) {
    if (!headers.containsKey(HttpHeaders.CONTENT_TYPE))
      return null;

    return MediaType.valueOf(headers.get(HttpHeaders.CONTENT_TYPE).iterator().next());
  }

  private MultivaluedMap<String, Object> toMultivaluedMap(Map<String, Collection<String>> headers) {
    final MultivaluedHashMap<String, Object> mvHeaders = new MultivaluedHashMap<>();

    headers.forEach((key, value1) -> value1
        .forEach(value -> mvHeaders.add(key, value)));

    return mvHeaders;
  }

  private Map<String, Collection<String>> toMap(MultivaluedMap<String, String> headers) {
    return headers.entrySet().stream()
        .collect(Collectors.toMap(
            Entry::getKey,
            Entry::getValue));
  }

}

