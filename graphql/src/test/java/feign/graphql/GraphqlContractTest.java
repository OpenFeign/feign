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

import feign.Request.HttpMethod;
import org.junit.jupiter.api.Test;

class GraphqlContractTest {

  private final GraphqlContract contract = new GraphqlContract();

  interface MutationApi {
    @GraphqlQuery(
        "mutation backendUpdateRuntimeStatus($event: RuntimeStatusInput!) {"
            + " backendUpdateRuntimeStatus(event: $event) { _uuid deploymentId } }")
    Object updateStatus(Object event);
  }

  interface QueryWithVariableApi {
    @GraphqlQuery(
        "query backendProjectsLookup($projectId: String!) {"
            + " backendProjectsLookup(projectId: $projectId) { projectId orgId } }")
    Object lookup(String projectId);
  }

  interface NoVariableQueryApi {
    @GraphqlQuery(
        "query backendPendingDeployments {"
            + " backendPendingDeployments { projectId environment } }")
    Object pending();
  }

  @Test
  void mutationSetsPostMethod() {
    var metadata = contract.parseAndValidateMetadata(MutationApi.class);
    assertThat(metadata).hasSize(1);
    assertThat(metadata.getFirst().template().method()).isEqualTo(HttpMethod.POST.name());
  }

  @Test
  void mutationStoresQueryMetadata() {
    contract.parseAndValidateMetadata(MutationApi.class);
    assertThat(contract.queryMetadata()).hasSize(1);
    var meta = contract.queryMetadata().values().iterator().next();
    assertThat(meta.query).contains("backendUpdateRuntimeStatus");
    assertThat(meta.variableName).isEqualTo("event");
  }

  @Test
  void queryWithVariableStoresMetadata() {
    contract.parseAndValidateMetadata(QueryWithVariableApi.class);
    assertThat(contract.queryMetadata()).hasSize(1);
    var meta = contract.queryMetadata().values().iterator().next();
    assertThat(meta.query).contains("backendProjectsLookup");
    assertThat(meta.variableName).isEqualTo("projectId");
  }

  @Test
  void noVariableQueryHasNullVariableName() {
    contract.parseAndValidateMetadata(NoVariableQueryApi.class);
    assertThat(contract.queryMetadata()).hasSize(1);
    var meta = contract.queryMetadata().values().iterator().next();
    assertThat(meta.query).contains("backendPendingDeployments");
    assertThat(meta.variableName).isNull();
  }

  @Test
  void noHeadersSetOnTemplate() {
    var metadata = contract.parseAndValidateMetadata(MutationApi.class);
    var headers = metadata.getFirst().template().headers();
    assertThat(headers).isEmpty();
  }

  @Test
  void extractOperationFieldFromMutation() {
    var query =
        "mutation backendUpdateRuntimeStatus($event: RuntimeStatusInput!) {"
            + " backendUpdateRuntimeStatus(event: $event) { _uuid } }";
    assertThat(GraphqlContract.extractOperationField(query))
        .isEqualTo("backendUpdateRuntimeStatus");
  }

  @Test
  void extractOperationFieldFromSimpleQuery() {
    var query = "query backendPendingDeployments { backendPendingDeployments { projectId } }";
    assertThat(GraphqlContract.extractOperationField(query)).isEqualTo("backendPendingDeployments");
  }

  @Test
  void extractOperationFieldFromAnonymousQuery() {
    var query = "{ user(id: \"1\") { id name } }";
    assertThat(GraphqlContract.extractOperationField(query)).isEqualTo("user");
  }

  @Test
  void extractFirstVariableFromMutation() {
    var query = "mutation backendUpdateRuntimeStatus($event: RuntimeStatusInput!) { x }";
    assertThat(GraphqlContract.extractFirstVariable(query)).isEqualTo("event");
  }

  @Test
  void extractFirstVariableReturnsNullWhenNone() {
    var query = "query backendPendingDeployments { x }";
    assertThat(GraphqlContract.extractFirstVariable(query)).isNull();
  }
}
