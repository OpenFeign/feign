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
package feign.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.spring.SpringContract;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

class GraphqlSpringContractTest {

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;

  public static class User {
    public String id;
    public String name;
    public String email;
  }

  interface SpringGraphqlApi {

    @PostMapping(value = "/graphql", consumes = "application/json")
    @GraphqlQuery("query getUser($id: String!) { getUser(id: $id) { id name email } }")
    User getUser(String id);

    @PostMapping(value = "/graphql", consumes = "application/json")
    @GraphqlQuery("query getUser($id: String!) { getUser(id: $id) { id name email } }")
    User getUserWithAuth(@RequestHeader("Authorization") String auth, String id);
  }

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private SpringGraphqlApi buildClient() {
    var contract = new GraphqlContract(new SpringContract());
    var graphqlEncoder = new GraphqlEncoder(new JacksonEncoder(mapper), contract);
    return Feign.builder()
        .contract(contract)
        .encoder(graphqlEncoder)
        .decoder(new GraphqlDecoder(new JacksonDecoder(mapper)))
        .requestInterceptor(graphqlEncoder)
        .target(SpringGraphqlApi.class, server.url("/").toString());
  }

  @Test
  void queryWithSpringContract() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"data\":{\"getUser\":{\"id\":\"1\",\"name\":\"Bob\",\"email\":\"bob@test.com\"}}}")
            .addHeader("Content-Type", "application/json"));

    var user = buildClient().getUser("1");

    assertThat(user.id).isEqualTo("1");
    assertThat(user.name).isEqualTo("Bob");

    var recorded = server.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("POST");
    assertThat(recorded.getPath()).isEqualTo("/graphql");
    var body = mapper.readTree(recorded.getBody().readUtf8());
    assertThat(body.get("query").asText()).contains("getUser");
    assertThat(body.get("variables").get("id").asText()).isEqualTo("1");
  }

  @Test
  void authHeaderWithSpringContract() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"data\":{\"getUser\":{\"id\":\"1\",\"name\":\"Bob\",\"email\":\"bob@test.com\"}}}")
            .addHeader("Content-Type", "application/json"));

    var user = buildClient().getUserWithAuth("Bearer mytoken", "1");

    assertThat(user.id).isEqualTo("1");

    var recorded = server.takeRequest();
    assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer mytoken");
  }
}
