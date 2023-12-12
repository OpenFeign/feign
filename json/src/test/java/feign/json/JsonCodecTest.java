/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.json;

import static feign.Util.toByteArray;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import feign.Feign;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;


interface GitHub {

  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  JSONArray contributors(@Param("owner") String owner, @Param("repo") String repo);

  @RequestLine("POST /repos/{owner}/{repo}/contributors")
  JSONObject create(@Param("owner") String owner,
                    @Param("repo") String repo,
                    JSONObject contributor);

}


class JsonCodecTest {

  private GitHub github;
  private MockClient mockClient;

  @BeforeEach
  void setUp() {
    mockClient = new MockClient();
    github = Feign.builder()
        .decoder(new JsonDecoder())
        .encoder(new JsonEncoder())
        .client(mockClient)
        .target(new MockTarget<>(GitHub.class));
  }

  @Test
  void decodes() throws IOException {
    try (InputStream input = getClass().getResourceAsStream("/fixtures/contributors.json")) {
      byte[] response = toByteArray(input);
      mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign/contributors", response);
      JSONArray contributors = github.contributors("openfeign", "feign");
      assertThat(contributors.toList()).hasSize(30);
      contributors.forEach(contributor -> ((JSONObject) contributor).getString("login"));
    }
  }

  @Test
  void encodes() {
    JSONObject contributor = new JSONObject();
    contributor.put("login", "radio-rogal");
    contributor.put("contributions", 0);
    mockClient.ok(HttpMethod.POST, "/repos/openfeign/feign/contributors",
        "{\"login\":\"radio-rogal\",\"contributions\":0}");
    JSONObject response = github.create("openfeign", "feign", contributor);
    Request request = mockClient.verifyOne(HttpMethod.POST, "/repos/openfeign/feign/contributors");
    assertThat(request.body()).isNotNull();
    String json = new String(request.body());
    assertThat(json).contains("\"login\":\"radio-rogal\"");
    assertThat(json).contains("\"contributions\":0");
    assertThat(response.getString("login")).isEqualTo("radio-rogal");
    assertThat(response.getInt("contributions")).isEqualTo(0);
  }

}
