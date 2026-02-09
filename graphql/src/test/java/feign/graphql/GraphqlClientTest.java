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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.jackson.JacksonEncoder;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphqlClientTest {

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;

  public static class User {
    public String id;
    public String name;
    public String email;
  }

  public static class CreateUserInput {
    public String name;
    public String email;
  }

  public static class CreateUserResult {
    public String id;
    public String name;
  }

  @Headers("Content-Type: application/json")
  interface TestApi {

    @GraphqlQuery(
        "mutation createUser($input: CreateUserInput!) {"
            + " createUser(input: $input) { id name } }")
    CreateUserResult createUser(CreateUserInput input);

    @GraphqlQuery("query getUser($id: String!) {" + " getUser(id: $id) { id name email } }")
    User getUser(String id);

    @GraphqlQuery("query listPending { listPending { id name } }")
    List<User> listPending();

    @GraphqlQuery("query getUser($id: String!) {" + " getUser(id: $id) { id name email } }")
    @Headers("Authorization: {auth}")
    User getUserWithAuth(@Param("auth") String auth, String id);
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

  private TestApi buildClient() {
    GraphqlEncoder graphqlEncoder = new GraphqlEncoder(new JacksonEncoder(mapper));
    return Feign.builder()
        .contract(new GraphqlContract())
        .encoder(graphqlEncoder)
        .decoder(new GraphqlDecoder(mapper))
        .requestInterceptor(graphqlEncoder)
        .target(TestApi.class, server.url("/graphql").toString());
  }

  @Test
  void mutationWithVariables() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"data\":{\"createUser\":{\"id\":\"42\",\"name\":\"Alice\"}}}")
            .addHeader("Content-Type", "application/json"));

    CreateUserInput input = new CreateUserInput();
    input.name = "Alice";
    input.email = "alice@example.com";

    CreateUserResult result = buildClient().createUser(input);

    assertThat(result.id).isEqualTo("42");
    assertThat(result.name).isEqualTo("Alice");

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("POST");
    JsonNode body = mapper.readTree(recorded.getBody().readUtf8());
    assertThat(body.get("query").asText()).contains("createUser");
    assertThat(body.get("variables").get("input").get("name").asText()).isEqualTo("Alice");
  }

  @Test
  void queryWithStringVariable() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"data\":{\"getUser\":{\"id\":\"1\",\"name\":\"Bob\",\"email\":\"bob@test.com\"}}}")
            .addHeader("Content-Type", "application/json"));

    User user = buildClient().getUser("1");

    assertThat(user.id).isEqualTo("1");
    assertThat(user.name).isEqualTo("Bob");
    assertThat(user.email).isEqualTo("bob@test.com");

    RecordedRequest recorded = server.takeRequest();
    JsonNode body = mapper.readTree(recorded.getBody().readUtf8());
    assertThat(body.get("variables").get("id").asText()).isEqualTo("1");
  }

  @Test
  void noVariableQuerySetsBodyViaInterceptor() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"data\":{\"listPending\":[{\"id\":\"1\",\"name\":\"A\"},{\"id\":\"2\",\"name\":\"B\"}]}}")
            .addHeader("Content-Type", "application/json"));

    List<User> users = buildClient().listPending();

    assertThat(users).hasSize(2);
    assertThat(users.get(0).name).isEqualTo("A");

    RecordedRequest recorded = server.takeRequest();
    JsonNode body = mapper.readTree(recorded.getBody().readUtf8());
    assertThat(body.get("query").asText()).contains("listPending");
    assertThat(body.has("variables")).isFalse();
  }

  @Test
  void graphqlErrorsThrowException() {
    server.enqueue(
        new MockResponse()
            .setBody("{\"errors\":[{\"message\":\"Something went wrong\"}],\"data\":null}")
            .addHeader("Content-Type", "application/json"));

    CreateUserInput input = new CreateUserInput();
    input.name = "Alice";

    assertThatThrownBy(() -> buildClient().createUser(input))
        .isInstanceOf(GraphqlErrorException.class)
        .hasMessageContaining("createUser")
        .hasMessageContaining("Something went wrong");
  }

  @Test
  void authHeaderPassedThrough() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"data\":{\"getUser\":{\"id\":\"1\",\"name\":\"Bob\",\"email\":\"bob@test.com\"}}}")
            .addHeader("Content-Type", "application/json"));

    User user = buildClient().getUserWithAuth("Bearer mytoken", "1");

    assertThat(user.id).isEqualTo("1");

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer mytoken");
  }

  @Test
  void internalHeadersNotSentToServer() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"data\":{\"createUser\":{\"id\":\"1\",\"name\":\"X\"}}}")
            .addHeader("Content-Type", "application/json"));

    CreateUserInput input = new CreateUserInput();
    input.name = "X";
    buildClient().createUser(input);

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getHeader(GraphqlContract.HEADER_GRAPHQL_QUERY)).isNull();
    assertThat(recorded.getHeader(GraphqlContract.HEADER_GRAPHQL_VARIABLE)).isNull();
  }
}
