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
package feign.webflux;

import feign.Request;
import feign.Response;
import feign.Util;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Translates between Feign and WebClient requests and responses. Both the buffered {@link
 * WebClientAsyncClient} and the streaming {@link WebClientSseClient} use it, so both apply the same
 * header and body semantics.
 */
final class WebClientExchanges {

  private WebClientExchanges() {}

  static WebClient.RequestHeadersSpec<?> fromFeignRequest(
      WebClient webClient, Request feignRequest) {
    final WebClient.RequestBodySpec webClientRequest =
        webClient
            .method(HttpMethod.valueOf(feignRequest.httpMethod().name()))
            .uri(URI.create(feignRequest.url()));

    boolean hasContentType = false;
    for (final Map.Entry<String, Collection<String>> header : feignRequest.headers().entrySet()) {
      // the transport computes Content-Length from the body it sends
      if (Util.CONTENT_LENGTH.equalsIgnoreCase(header.getKey())) {
        continue;
      }
      if (Util.CONTENT_TYPE.equalsIgnoreCase(header.getKey())) {
        hasContentType = true;
      }
      webClientRequest.header(header.getKey(), header.getValue().toArray(new String[0]));
    }

    final byte[] body = feignRequest.body();
    if (body == null) {
      return webClientRequest;
    }

    if (!hasContentType) {
      // writing a byte[] body requires a content type to select an HttpMessageWriter
      webClientRequest.contentType(MediaType.APPLICATION_OCTET_STREAM);
    }
    return webClientRequest.bodyValue(body);
  }

  static Response toFeignResponse(final ResponseEntity<byte[]> entity, final Request request) {
    final int status = entity.getStatusCode().value();
    return Response.builder()
        .status(status)
        .reason(findStandardReasonPhrase(status))
        .headers(toFeignHeaders(entity.getHeaders()))
        .body(entity.getBody())
        .request(request)
        .build();
  }

  /** WebClient does not expose the reason phrase sent by the server. */
  private static String findStandardReasonPhrase(final int status) {
    final HttpStatus httpStatus = HttpStatus.resolve(status);
    return httpStatus != null ? httpStatus.getReasonPhrase() : null;
  }

  private static Map<String, Collection<String>> toFeignHeaders(final HttpHeaders headers) {
    final Map<String, Collection<String>> feignHeaders = new LinkedHashMap<>();
    headers.forEach((name, values) -> feignHeaders.put(name, new ArrayList<>(values)));
    return feignHeaders;
  }
}
