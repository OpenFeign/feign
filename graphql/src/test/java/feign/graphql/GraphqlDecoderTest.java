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
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphqlDecoderTest {

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final GraphqlDecoder decoder = new GraphqlDecoder(mapper);

  public static class User {
    public String id;
    public String name;
  }

  @Test
  void decodesDataField() throws Exception {
    String json = "{\"data\":{\"getUser\":{\"id\":\"1\",\"name\":\"Alice\"}}}";
    Response response = buildResponse(json, "getUser");

    User user = (User) decoder.decode(response, User.class);

    assertThat(user.id).isEqualTo("1");
    assertThat(user.name).isEqualTo("Alice");
  }

  @Test
  void decodesListResponse() throws Exception {
    String json =
        "{\"data\":{\"listUsers\":[{\"id\":\"1\",\"name\":\"Alice\"},{\"id\":\"2\",\"name\":\"Bob\"}]}}";
    Response response = buildResponse(json, "listUsers");

    @SuppressWarnings("unchecked")
    List<User> users =
        (List<User>)
            decoder.decode(
                response, mapper.getTypeFactory().constructCollectionType(List.class, User.class));

    assertThat(users).hasSize(2);
    assertThat(users.get(0).name).isEqualTo("Alice");
    assertThat(users.get(1).name).isEqualTo("Bob");
  }

  @Test
  void throwsGraphqlErrorExceptionOnErrors() {
    String json = "{\"errors\":[{\"message\":\"Not found\"}],\"data\":null}";
    Response response = buildResponse(json, "getUser");

    assertThatThrownBy(() -> decoder.decode(response, User.class))
        .isInstanceOf(GraphqlErrorException.class)
        .hasMessageContaining("getUser")
        .hasMessageContaining("Not found");
  }

  @Test
  void returnsNullForNullData() throws Exception {
    String json = "{\"data\":{\"getUser\":null}}";
    Response response = buildResponse(json, "getUser");

    Object result = decoder.decode(response, User.class);
    assertThat(result).isNull();
  }

  @Test
  void returnsNullForMissingOperation() throws Exception {
    String json = "{\"data\":{}}";
    Response response = buildResponse(json, "getUser");

    Object result = decoder.decode(response, User.class);
    assertThat(result).isNull();
  }

  @Test
  void returnsEmptyFor404() throws Exception {
    Response response =
        Response.builder()
            .status(404)
            .reason("Not Found")
            .headers(Collections.emptyMap())
            .request(buildRequest("getUser"))
            .body(new byte[0])
            .build();

    Object result = decoder.decode(response, User.class);
    assertThat(result).isNull();
  }

  private Response buildResponse(String body, String operationField) {
    return Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(buildRequest(operationField))
        .body(body, StandardCharsets.UTF_8)
        .build();
  }

  private Request buildRequest(String operationField) {
    Map<String, Collection<String>> headers =
        Map.of(GraphqlContract.HEADER_GRAPHQL_OPERATION, List.of(operationField));
    return Request.create(
        HttpMethod.POST, "http://localhost/graphql", headers, Request.Body.empty(), null);
  }
}
