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
package feign.http2client;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class Http2ClientContentLengthTest {

  private static HttpResponse<InputStream> responseWithContentLength(String contentLength) {
    final HttpHeaders headers =
        HttpHeaders.of(Map.of("Content-Length", List.of(contentLength)), (name, value) -> true);
    return new HttpResponse<>() {
      @Override
      public int statusCode() {
        return 200;
      }

      @Override
      public HttpRequest request() {
        return null;
      }

      @Override
      public Optional<HttpResponse<InputStream>> previousResponse() {
        return Optional.empty();
      }

      @Override
      public HttpHeaders headers() {
        return headers;
      }

      @Override
      public InputStream body() {
        return new ByteArrayInputStream(new byte[0]);
      }

      @Override
      public Optional<SSLSession> sslSession() {
        return Optional.empty();
      }

      @Override
      public URI uri() {
        return URI.create("http://localhost");
      }

      @Override
      public Version version() {
        return Version.HTTP_2;
      }
    };
  }

  private static HttpResponse<InputStream> gzipResponseWithContentLength(byte[] compressedBody) {
    final HttpHeaders headers =
        HttpHeaders.of(
            Map.of(
                "Content-Length", List.of(String.valueOf(compressedBody.length)),
                "Content-Encoding", List.of("gzip")),
            (name, value) -> true);
    return new HttpResponse<>() {
      @Override
      public int statusCode() {
        return 200;
      }

      @Override
      public HttpRequest request() {
        return null;
      }

      @Override
      public Optional<HttpResponse<InputStream>> previousResponse() {
        return Optional.empty();
      }

      @Override
      public HttpHeaders headers() {
        return headers;
      }

      @Override
      public InputStream body() {
        return new ByteArrayInputStream(compressedBody);
      }

      @Override
      public Optional<SSLSession> sslSession() {
        return Optional.empty();
      }

      @Override
      public URI uri() {
        return URI.create("http://localhost");
      }

      @Override
      public Version version() {
        return Version.HTTP_2;
      }
    };
  }

  private static byte[] gzip(String data) throws Exception {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
      gzip.write(data.getBytes(StandardCharsets.UTF_8));
    }
    return bos.toByteArray();
  }

  private static Request request() {
    return Request.create(
        HttpMethod.GET,
        "http://localhost",
        Collections.emptyMap(),
        null,
        StandardCharsets.UTF_8,
        null);
  }

  private static Response decode(String contentLength) {
    return new Http2Client().toFeignResponse(request(), responseWithContentLength(contentLength));
  }

  @Test
  void contentLengthAboveIntMaxIsReportedAsUnknown() {
    // 2^31, a valid Content-Length larger than Integer.MAX_VALUE
    assertThat(decode("2147483648").body().length()).isNull();
  }

  @Test
  void negativeContentLengthIsReportedAsUnknown() {
    assertThat(decode("-1").body().length()).isNull();
  }

  @Test
  void contentLengthWithinIntRangeIsPreserved() {
    assertThat(decode("1024").body().length()).isEqualTo(1024);
  }

  @Test
  void gzipDecodedBodyReportsUnknownLength() throws Exception {
    final byte[] compressed = gzip("Compressed Data");
    final Response response =
        new Http2Client().toFeignResponse(request(), gzipResponseWithContentLength(compressed));
    // the body is transparently decompressed, so the compressed Content-Length is not the body
    // length and must be reported as unknown
    assertThat(response.body().length()).isNull();
    assertThat(Util.toString(response.body().asReader(StandardCharsets.UTF_8)))
        .isEqualTo("Compressed Data");
  }
}
