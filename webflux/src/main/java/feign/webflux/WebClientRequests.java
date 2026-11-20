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
import feign.Util;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Translates a Feign {@link Request} into a WebClient request spec. Shared by the buffered {@link
 * WebClientAsyncClient} and the streaming {@link WebClientSseClient} so both apply the same header
 * and body semantics.
 */
final class WebClientRequests {

  private WebClientRequests() {}

  static WebClient.RequestHeadersSpec<?> create(WebClient webClient, Request request) {
    final WebClient.RequestBodySpec spec =
        webClient
            .method(HttpMethod.valueOf(request.httpMethod().name()))
            .uri(URI.create(request.url()));

    boolean hasContentType = false;
    for (final Map.Entry<String, Collection<String>> header : request.headers().entrySet()) {
      // the transport computes Content-Length from the body it sends
      if (Util.CONTENT_LENGTH.equalsIgnoreCase(header.getKey())) {
        continue;
      }
      if (Util.CONTENT_TYPE.equalsIgnoreCase(header.getKey())) {
        hasContentType = true;
      }
      spec.header(header.getKey(), header.getValue().toArray(new String[0]));
    }

    final byte[] body = request.body();
    if (body == null) {
      return spec;
    }

    if (!hasContentType) {
      // writing a byte[] body requires a content type to select an HttpMessageWriter
      spec.contentType(MediaType.APPLICATION_OCTET_STREAM);
    }
    return spec.bodyValue(body);
  }
}
