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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestTemplate;
import feign.jackson.JacksonEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphqlEncoderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final GraphqlContract contract = new GraphqlContract();
  private final JacksonEncoder jacksonEncoder = new JacksonEncoder(mapper);
  private final GraphqlEncoder encoder = new GraphqlEncoder(jacksonEncoder, contract);
  private final GraphqlRequestInterceptor interceptor =
      new GraphqlRequestInterceptor(jacksonEncoder, contract);

  interface MutationApi {
    @GraphqlQuery(
        "mutation createUser($input: CreateUserInput!) { createUser(input: $input) { id } }")
    Object createUser(Object input);
  }

  interface NoVariableApi {
    @GraphqlQuery("query pending { pending { id } }")
    Object pending();
  }

  private RequestTemplate templateFor(Class<?> apiClass) {
    var metadataList = contract.parseAndValidateMetadata(apiClass);
    var md = metadataList.getFirst();
    var template = new RequestTemplate();
    template.methodMetadata(md);
    return template;
  }

  private byte[] bodyAsByteArray(Request.Body body) {
    return assertDoesNotThrow(body::writeToByteArray);
  }

  private String bodyAsUtf8String(Request.Body body) {
    return assertDoesNotThrow(() -> body.writeToString(StandardCharsets.UTF_8));
  }

  private byte[] requestBodyBytes(RequestTemplate template) {
    return template.requestBody().map(this::bodyAsByteArray).orElse(null);
  }

  private String requestBodyString(RequestTemplate template) {
    return template.requestBody().map(this::bodyAsUtf8String).orElse(null);
  }

  @Test
  void encodesBodyWithVariables() throws Exception {
    var template = templateFor(MutationApi.class);
    template.header("Content-Type", "application/json");
    var body = Map.of("name", "John", "email", "john@example.com");
    encoder.encode(body, Map.class, template);

    var result = mapper.readTree(requestBodyBytes(template));
    assertThat(result.has("query")).isTrue();
    assertThat(result.get("query").asText()).contains("createUser");
    assertThat(result.has("variables")).isTrue();
    assertThat(result.get("variables").has("input")).isTrue();
    assertThat(result.get("variables").get("input").get("name").asText()).isEqualTo("John");
  }

  @Test
  void delegatesToWrappedEncoderForNonGraphql() {
    var template = new RequestTemplate();
    template.header("Content-Type", "application/json");
    encoder.encode("plain body", String.class, template);
    assertThat(template.requestBody()).isPresent();
  }

  @Test
  void interceptorSetsBodyForNoVariableQuery() throws Exception {
    var template = templateFor(NoVariableApi.class);
    template.header("Content-Type", "application/json");
    interceptor.apply(template);

    var result = mapper.readTree(requestBodyBytes(template));
    assertThat(result.get("query").asText()).contains("pending");
    assertThat(result.has("variables")).isFalse();
  }

  @Test
  void interceptorSkipsWhenBodyAlreadySet() {
    var template = templateFor(MutationApi.class);
    template.body(Request.Body.of("already set"));
    interceptor.apply(template);
    assertThat(requestBodyString(template)).isEqualTo("already set");
  }

  @Test
  void interceptorSkipsForNonGraphql() {
    var template = new RequestTemplate();
    template.body(Request.Body.of("some body"));
    interceptor.apply(template);
    assertThat(requestBodyString(template)).isEqualTo("some body");
  }
}
