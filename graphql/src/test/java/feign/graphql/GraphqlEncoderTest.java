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

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestTemplate;
import feign.jackson.JacksonEncoder;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphqlEncoderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final GraphqlContract contract = new GraphqlContract();
  private final GraphqlEncoder encoder = new GraphqlEncoder(new JacksonEncoder(mapper), contract);

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

  @Test
  void encodesBodyWithVariables() throws Exception {
    var template = templateFor(MutationApi.class);
    var body = Map.of("name", "John", "email", "john@example.com");
    encoder.encode(body, Map.class, template);

    var result = mapper.readTree(template.body());
    assertThat(result.has("query")).isTrue();
    assertThat(result.get("query").asText()).contains("createUser");
    assertThat(result.has("variables")).isTrue();
    assertThat(result.get("variables").has("input")).isTrue();
    assertThat(result.get("variables").get("input").get("name").asText()).isEqualTo("John");
  }

  @Test
  void delegatesToWrappedEncoderForNonGraphql() {
    var template = new RequestTemplate();
    encoder.encode("plain body", String.class, template);
    assertThat(template.body()).isNotNull();
  }

  @Test
  void interceptorSetsBodyForNoVariableQuery() throws Exception {
    var template = templateFor(NoVariableApi.class);
    encoder.apply(template);

    var result = mapper.readTree(template.body());
    assertThat(result.get("query").asText()).contains("pending");
    assertThat(result.has("variables")).isFalse();
  }

  @Test
  void interceptorSkipsWhenBodyAlreadySet() {
    var template = templateFor(MutationApi.class);
    template.body("already set");
    encoder.apply(template);
    assertThat(new String(template.body())).isEqualTo("already set");
  }

  @Test
  void interceptorSkipsForNonGraphql() {
    var template = new RequestTemplate();
    template.body("some body");
    encoder.apply(template);
    assertThat(new String(template.body())).isEqualTo("some body");
  }
}
