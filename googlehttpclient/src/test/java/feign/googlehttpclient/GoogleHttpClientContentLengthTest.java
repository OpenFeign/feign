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
package feign.googlehttpclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Request.Options;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class GoogleHttpClientContentLengthTest {

  private static Response decode(String contentLength) throws IOException {
    final MockLowLevelHttpResponse lowLevelResponse =
        new MockLowLevelHttpResponse().setStatusCode(200).setContent("");
    lowLevelResponse.addHeader("Content-Length", contentLength);
    final MockHttpTransport transport =
        new MockHttpTransport.Builder().setLowLevelHttpResponse(lowLevelResponse).build();
    final Request request =
        Request.create(
            HttpMethod.GET,
            "http://localhost",
            Collections.emptyMap(),
            null,
            StandardCharsets.UTF_8,
            null);
    return new GoogleHttpClient(transport).execute(request, new Options());
  }

  @Test
  void contentLengthAboveIntMaxIsReportedAsUnknown() throws IOException {
    // 2^31, a valid Content-Length larger than Integer.MAX_VALUE
    assertThat(decode("2147483648").body().length()).isNull();
  }

  @Test
  void negativeContentLengthIsReportedAsUnknown() throws IOException {
    assertThat(decode("-1").body().length()).isNull();
  }

  @Test
  void contentLengthWithinIntRangeIsPreserved() throws IOException {
    assertThat(decode("1024").body().length()).isEqualTo(1024);
  }
}
