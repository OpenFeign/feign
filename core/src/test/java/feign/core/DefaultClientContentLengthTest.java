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
package feign.core;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultClientContentLengthTest {

  /**
   * {@link HttpURLConnection#getContentLength()} reports the {@code Content-Length} header as-is
   * once it fits in an {@code int}, so a malformed negative header reaches the client unchanged.
   */
  private static HttpURLConnection connectionWithContentLength(int contentLength)
      throws IOException {
    return new HttpURLConnection(new URL("http://localhost")) {

      @Override
      public int getResponseCode() {
        return 200;
      }

      @Override
      public String getResponseMessage() {
        return "OK";
      }

      @Override
      public Map<String, List<String>> getHeaderFields() {
        return Collections.singletonMap(
            "Content-Length", Collections.singletonList(String.valueOf(contentLength)));
      }

      @Override
      public int getContentLength() {
        return contentLength;
      }

      @Override
      public InputStream getInputStream() {
        return new ByteArrayInputStream(new byte[0]);
      }

      @Override
      public void connect() {}

      @Override
      public void disconnect() {}

      @Override
      public boolean usingProxy() {
        return false;
      }
    };
  }

  private static Response decode(int contentLength) throws IOException {
    final Request request =
        Request.create(
            HttpMethod.GET, "http://localhost", Collections.emptyMap(), (Request.Body) null, null);
    return new DefaultClient(null, null)
        .convertResponse(connectionWithContentLength(contentLength), request);
  }

  @Test
  void unknownContentLengthIsReportedAsUnknown() throws IOException {
    // -1 is also what getContentLength() returns for a header above Integer.MAX_VALUE
    assertThat(decode(-1).body().length()).isNull();
  }

  @Test
  void negativeContentLengthIsReportedAsUnknown() throws IOException {
    assertThat(decode(-5).body().length()).isNull();
  }

  @Test
  void contentLengthWithinIntRangeIsPreserved() throws IOException {
    assertThat(decode(1024).body().length()).isEqualTo(1024);
  }
}
